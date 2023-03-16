package com.yps.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.yps.common.VMSystem;
import com.yps.conf.OrderConfig;
import com.yps.config.TopicConfig;
import com.yps.contract.VendoutReq;
import com.yps.contract.VendoutReqData;
import com.yps.contract.VendoutResp;
import com.yps.contract.server.OrderCheck;
import com.yps.dao.OrderDao;
import com.yps.emq.MqttProducer;
import com.yps.entity.OrderEntity;
import com.yps.feignService.UserService;
import com.yps.feignService.VMService;
import com.yps.http.viewModel.CreateOrderReq;
import com.yps.http.viewModel.OrderResp;
import com.yps.service.OrderCollectService;
import com.yps.service.OrderService;
import com.yps.utils.JsonUtil;
import com.yps.viewmodel.*;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.*;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {

    //@Autowired
    //private MqttProducer mqttProducer;


    @Autowired
    private OrderCollectService orderCollectService;

    @Autowired
    private VMService vmService;

    @Autowired
    private UserService userService;
    @Resource
    private MqttProducer mqttProducer;

    @Override
    public OrderEntity createOrder(CreateOrder createOrder) {
        VendingMachineViewModel vm = vmService.getVMInfo(createOrder.getInnerCode());
        SkuViewModel sku = vmService.getSku(createOrder.getInnerCode(), createOrder.getSkuId());
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setAddr(vm.getNodeAddr());
        orderEntity.setNodeId(vm.getNodeId());
        orderEntity.setNodeName(vm.getNodeName());
        orderEntity.setSkuId(sku.getSkuId());
        orderEntity.setSkuName(sku.getSkuName());
        orderEntity.setAmount(sku.getRealPrice());
        orderEntity.setClassId(sku.getClassId());
        orderEntity.setPrice(sku.getPrice());
        orderEntity.setBusinessId(vm.getBusinessId());
        orderEntity.setBusinessName(vm.getBusinessName());
        orderEntity.setInnerCode(createOrder.getInnerCode());
        orderEntity.setOpenId(createOrder.getOpenId());
        orderEntity.setPayStatus(VMSystem.PAY_STATUS_NOPAY);
        orderEntity.setRegionId(vm.getRegionId());
        orderEntity.setRegionName(vm.getRegionName());
        orderEntity.setOrderNo(createOrder.getInnerCode() + createOrder.getSkuId() + System.nanoTime());
        //微信支付
        orderEntity.setPayType(createOrder.getPayType());

        orderEntity.setStatus(VMSystem.ORDER_STATUS_CREATE);
        orderEntity.setOwnerId(vm.getOwnerId());
        //todo: 合作商分成计算
        PartnerViewModel partner = userService.getPartner(vm.getOwnerId()); // 获取合作商
        BigDecimal price = new BigDecimal(sku.getPrice()); // 商品价格
        // 分成比例计算 商品价格*分成比例/（100，不保留小数，模式为：四舍五入）
        BigDecimal bill = price.multiply(new BigDecimal(partner.getRatio())).divide(new BigDecimal(100), 0, RoundingMode.HALF_UP);
        orderEntity.setBill(bill.intValue()); // 添加分成金额到订单中

        this.save(orderEntity);
        //订单创建完成之后，将订单放到延迟队列中，10分钟后检查支付状态
        OrderCheck orderCheck = new OrderCheck();
        orderCheck.setOrderNo(orderEntity.getOrderNo());
        try {
            mqttProducer.send("$delayed/600/" + OrderConfig.ORDER_DELAY_CHECK_TOPIC, 2, orderCheck);
        } catch (JsonProcessingException e) {
            log.error("send to emq error", e);
        }
        return orderEntity;
    }


    @Override
    public boolean vendoutResult(VendoutResp vendoutResp) {
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderNo(vendoutResp.getVendoutResult().getOrderNo());
        UpdateWrapper<OrderEntity> uw = new UpdateWrapper<>();
        LambdaUpdateWrapper<OrderEntity> lambdaUpdateWrapper = uw.lambda();
        lambdaUpdateWrapper.set(OrderEntity::getPayStatus, 1);
        if (vendoutResp.getVendoutResult().isSuccess()) {
            lambdaUpdateWrapper.set(OrderEntity::getStatus, VMSystem.ORDER_STATUS_VENDOUT_SUCCESS);
        } else {
            lambdaUpdateWrapper.set(OrderEntity::getStatus, VMSystem.ORDER_STATUS_VENDOUT_FAIL);
        }
        lambdaUpdateWrapper.eq(OrderEntity::getOrderNo, vendoutResp.getVendoutResult().getOrderNo());

        return this.update(lambdaUpdateWrapper);
    }


    @Override
    public OrderEntity getByOrderNo(String orderNo) {
        QueryWrapper<OrderEntity> qw = new QueryWrapper<>();
        qw.lambda()
                .eq(OrderEntity::getOrderNo, orderNo);

        return this.getOne(qw);
    }

    @Override
    public Boolean cancel(String orderNo) {
        var order = this.getByOrderNo(orderNo);
        if (order.getStatus() > VMSystem.ORDER_STATUS_CREATE)
            return true;

        order.setStatus(VMSystem.ORDER_STATUS_INVALID);
        order.setCancelDesc("用户取消");

        return true;
    }


    /**
     * @param orderNo
     */
    private void sendVendout(String orderNo) {
        OrderEntity orderEntity = this.getByOrderNo(orderNo); // 获取订单封装的实体对象
        // 1. 封装 订单出货的请求数据
        VendoutReqData vendoutReqData = new VendoutReqData(); // 获取售货机出货请求数据封装实体对象
        vendoutReqData.setOrderNo(orderNo); // 在请求数据中封装 订单号
        vendoutReqData.setSkuId(orderEntity.getSkuId()); // 在请求数据中封装 商品id
        vendoutReqData.setPayPrice(orderEntity.getAmount()); // 在请求数据中封装 订单金额
        vendoutReqData.setTimeout(60); // 封装订单超时时间
        vendoutReqData.setPayType(Integer.parseInt(orderEntity.getPayType())); // 封装 支付方式
        vendoutReqData.setRequestTime(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)); //出货请求时间

        // 2. 封装出货请求
        VendoutReq vendoutReq = new VendoutReq(); // 获取出货请求对象
        vendoutReq.setVendoutData(vendoutReqData); // 封装 出货请求数据
        vendoutReq.setSn(System.nanoTime()); //协议通信匹配码
        vendoutReq.setInnerCode(orderEntity.getInnerCode()); // 封装 售货机编号
        vendoutReq.setNeedResp(true); // 是否需要响应数据

        // 3. 向售货机发送出货请求 -- 将售货机出货请求的消息-发送到 emq
        try {
            mqttProducer.send(TopicConfig.getVendoutTopic(orderEntity.getInnerCode()), 2, vendoutReq);
        } catch (JsonProcessingException e) {
            log.error("send vendout req error.", e);
        }
    }

    /**
     * 微信支付完成
     *
     * @param orderNo
     * @return
     */
    @Override
    public Boolean payComplete(String orderNo) {
        sendVendout(orderNo);
        return true;
    }

    /**
     * 查询订单
     *
     * @param pageIndex
     * @param pageSize
     * @return
     */
    @Resource
    private RestHighLevelClient client;

    @Override
    public Pager<OrderViewModel> search(Integer pageIndex, Integer pageSize,
                                        String orderNo, String openId, String startDate, String endDate) {

        SearchRequest searchRequest = new SearchRequest("order"); // 获取搜索索引库请求对象
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder(); // 搜索资源构建器
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder(); // 搜索布尔构建器

        if (!Strings.isNullOrEmpty(orderNo)) {
            boolQueryBuilder.must(QueryBuilders.termQuery("order_no", orderNo)); // 根据订单号模糊查询
        }

        if (!Strings.isNullOrEmpty(openId)) {
            boolQueryBuilder.must(QueryBuilders.termQuery("open_id", openId)); //根据openId查询
        }

        if (!Strings.isNullOrEmpty(startDate) && !Strings.isNullOrEmpty(endDate)) {
            RangeQueryBuilder rangedQuery = QueryBuilders.rangeQuery("update_time"); //范围查询构建器
            rangedQuery.gte(startDate); // 大于等于 开始时间
            rangedQuery.lte(endDate); // 小于等于结束时间
            boolQueryBuilder.must(rangedQuery); // 根据时间 范围查询
        }

        //按照最后更新时间由近到远的排序规则排序
        searchSourceBuilder.from((pageIndex - 1) * pageSize); // 分页数据
        searchSourceBuilder.size(pageSize);
        searchSourceBuilder.sort("update_time", SortOrder.DESC); // 时间排序
        searchSourceBuilder.trackTotalHits(true); // 开启命中次数
        searchSourceBuilder.query(boolQueryBuilder);
        searchRequest.source(searchSourceBuilder);

        try {
            // 发起请求，获取结果对象
            SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
            SearchHits hits = response.getHits(); // 获取查询的结果
            SearchHit[] searchHits = hits.getHits();

            List<OrderViewModel> viewModelList = Lists.newArrayList(); // 封装最终结果的集合

            // 流式遍历 -- hits.getHits(); searchHits 数组转成 stream流
            Arrays.stream(searchHits).forEach(hit -> {
                String sourceAsString = hit.getSourceAsString(); // 以字符串形式获取源
                OrderViewModel order = new OrderViewModel(); // 获取视图封装对象
                try {
                    JsonNode jsonNode = JsonUtil.getTreeNode(sourceAsString); // es 数据
                    // 封装结果对象
                    order.setId(jsonNode.findPath("id").asLong()); // id
                    order.setStatus(jsonNode.findPath("status").asInt()); // status
                    order.setBill(jsonNode.findPath("bill").asInt()); // bill 账单
                    order.setOwnerId(jsonNode.findPath("owner_id").asInt());// ownerId
                    order.setPayType(jsonNode.findPath("pay_type").asText()); // 支付方式-- 微信/支付宝
                    order.setOrderNo(jsonNode.findPath("order_no").asText()); // 订单号
                    order.setInnerCode(jsonNode.findPath("inner_code").asText()); // 售货机编号
                    order.setSkuName(jsonNode.findPath("sku_name").asText()); // 商品name
                    order.setSkuId(jsonNode.findPath("sku_id").asLong()); // 商品id
                    order.setPayStatus(jsonNode.findPath("pay_status").asInt()); // 支付状态
                    order.setBusinessName(jsonNode.findPath("business_name").asText()); //企业name
                    order.setBusinessId(jsonNode.findPath("business_id").asInt()); // 企业id
                    order.setRegionId(jsonNode.findPath("region_id").asLong()); // 区域id
                    order.setRegionName(jsonNode.findPath("region_name").asText()); /// 区域名称
                    order.setPrice(jsonNode.findPath("price").asInt()); //商品价格
                    order.setAmount(jsonNode.findPath("amount").asInt()); // 订单金额
                    order.setAddr(jsonNode.findPath("addr").asText()); // 收获地址
                    order.setOpenId(jsonNode.findPath("open_id").asText()); /// 用户openId
                    order.setCreateTime(LocalDateTime.parse(jsonNode.findPath("create_time").asText(),
                            DateTimeFormatter.ISO_DATE_TIME)); // 开始时间
                    order.setUpdateTime(LocalDateTime.parse(jsonNode.findPath("update_time").asText(),
                            DateTimeFormatter.ISO_DATE_TIME)); // 结束时间

                    viewModelList.add(order); // 封装结果对象到结果集中
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });
            // 分页数据
            Pager<OrderViewModel> pager = new Pager<>();
            pager.setPageSize(hits.getTotalHits().value);
            pager.setPageIndex(pageIndex);
            pager.setTotalCount(searchHits.length);
            pager.setCurrentPageRecords(viewModelList); // 数据
            return pager;
        } catch (Exception e) {
            log.error("查询es失败", e);
            return Pager.buildEmpty();
        }
    }

    /**
     * 获取商圈下销量最好的前10商品
     *
     * @param businessId 商圈id
     * @return
     */
    @Override
    public List<Long> getTop10Sku(Integer businessId) {
        SearchRequest searchRequest = new SearchRequest("order"); // 获取请求索引库对象
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder(); // 获取请求资源构建器
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder(); // 获取布尔构建器

        // 1. 范围查询---查询条件：最近三个月
        RangeQueryBuilder rangedQuery = QueryBuilders.rangeQuery("update_time"); // 通过 queryBuilders 获取范围查询构建器
        rangedQuery.gte(LocalDateTime.now().plusMonths(-3).format(DateTimeFormatter.ISO_DATE_TIME));// 时间范围 >= 前三个月
        rangedQuery.lte(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)); // 当前时间
        boolQueryBuilder.must(rangedQuery); // 布尔构建器进行模糊查询--must方法
        boolQueryBuilder.must(QueryBuilders.termQuery("business_id", businessId)); // 根据商圈id进行模糊查询
        sourceBuilder.query(boolQueryBuilder); // 总：根据商圈id,时间范围进行模糊查询--将结果构建器封装到 请求资源构间器中

        // 2. 将根据条件查询查到的数据进行聚合-->聚合排序--> 根据商品id进行聚合排序--只获取前10 条数据
        AggregationBuilder aggregationBuilder = AggregationBuilders
                .terms("sku") // 自定义聚合名称-- 用于提取聚合的结果--相当于里面封装了本次聚合的结果
                .field("sku_id") // 根据 skuId进行聚合--相当于根据 skuId 进行分类
                .subAggregation(AggregationBuilders.count("count").field("sku_id")) //对分组之后的sku_id进行count，
                // 取别名为count -- 相当于SQL语句中的 count(sku_id)--根据id进行count统计;
                .order(BucketOrder.aggregation("count", false)) //BucketOrder.aggregation("count",false)，
                // 相当于sql中的`order by` 排序，传入`false`是倒序排序
                .size(10); // 只要前10条数据 -- limit 0,10
        sourceBuilder.aggregation(aggregationBuilder);
        searchRequest.source(sourceBuilder);

        // 3.发起请求，获取封装的结果集
        try {
            SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
            Aggregations aggregations = response.getAggregations(); // 提取聚合数据
            if (aggregations == null) {
                return Lists.newArrayList();
            }

            Terms terms = aggregations.get("sku"); // 根据自定义的聚合名称--提取去集合的结果
            List<? extends Terms.Bucket> buckets = terms.getBuckets(); // 获取桶--只有先获取到桶，才能获取里面的数据
            // 集合 stream 式遍历-- 先要使用map-最后再 toList 转成list 集合就ok;
            return buckets.stream().map(b -> Long.valueOf(b.getKey().toString())).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return Lists.newArrayList();
        }
    }
}

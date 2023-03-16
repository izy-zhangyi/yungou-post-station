package com.yps.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.base.Strings;
import com.yps.common.VMSystem;
import com.yps.entity.OrderCollectEntity;
import com.yps.http.viewModel.BarCharCollect;
import com.yps.service.OrderCollectService;
import com.yps.service.ReportService;
import com.yps.viewmodel.Pager;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.*;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.ParsedSum;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private final OrderCollectService orderCollectService;
    private final RestHighLevelClient client;

    @Override
    public Pager<OrderCollectEntity> getPartnerCollect(Long pageIndex, Long pageSize, String name, LocalDate startTime, LocalDate endTime) {
        Page<OrderCollectEntity> page = new Page<>(pageIndex, pageSize);
        QueryWrapper<OrderCollectEntity> queryWrapper = new QueryWrapper<>();
        //order_count
        //total_bill
        //order_total_money
        //ratio
        //owner_name
        //date
        queryWrapper.select(
                "IFNULL(sum(order_count),0) as order_count",
                "IFNULL(sum(total_bill),0) as total_bill",
                "IFNULL(sum(order_total_money),0) as order_total_money",
                "IFNULL(sum(ratio),0) as ratio",
                "owner_name", "date");
        if (!Strings.isNullOrEmpty(name)) {
            queryWrapper.lambda().like(OrderCollectEntity::getOwnerName, name);
        }

        queryWrapper.lambda()
                .ge(OrderCollectEntity::getDate, startTime)
                .le(OrderCollectEntity::getDate, endTime)
                .groupBy(OrderCollectEntity::getOwnerName, OrderCollectEntity::getDate)
                .orderByDesc(OrderCollectEntity::getDate);
        // 获取聚合之后得分页数据orderCollectService.page(page, queryWrapper);
        return Pager.build(orderCollectService.page(page, queryWrapper)); // 返回数据
    }

    /**
     * 获取合作商前12条点位分账数据
     *
     * @param partnerId
     * @return
     */
    @Override
    public List<OrderCollectEntity> getTop12(Integer partnerId) {
        LambdaQueryWrapper<OrderCollectEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(
                        OrderCollectEntity::getDate, OrderCollectEntity::getNodeName,
                        OrderCollectEntity::getOrderCount, OrderCollectEntity::getTotalBill)
                .eq(OrderCollectEntity::getOwnerId, partnerId)
                .orderByDesc(OrderCollectEntity::getDate)
                .last("limit 12");
        return orderCollectService.list(queryWrapper);
    }

    /**
     * 合作商点位分账搜索
     *
     * @param partnerId
     * @param nodeName
     * @param start
     * @param end
     * @return
     */
    @Override
    public Pager<OrderCollectEntity> search(Long pageIndex, Long pageSize, Integer partnerId, String nodeName, LocalDate start, LocalDate end) {

        Page<OrderCollectEntity> page = new Page<>(pageIndex, pageSize);

        LambdaQueryWrapper<OrderCollectEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(
                OrderCollectEntity::getDate, OrderCollectEntity::getNodeName,
                OrderCollectEntity::getTotalBill, OrderCollectEntity::getOrderCount);

        if (!Strings.isNullOrEmpty(nodeName)) {
            queryWrapper.like(OrderCollectEntity::getNodeName, nodeName);
        }

        if (!Objects.deepEquals(start, null) && !Objects.deepEquals(end, null)) {
            queryWrapper.ge(OrderCollectEntity::getDate, start).le(OrderCollectEntity::getDate, end);
        }

        queryWrapper.orderByDesc(OrderCollectEntity::getDate);

        return Pager.build(orderCollectService.page(page, queryWrapper));
    }

    /**
     * 获取分账数据列表
     *
     * @param partnerId
     * @param nodeName
     * @param start
     * @param end
     * @return
     */
    @Override
    public List<OrderCollectEntity> getList(Integer partnerId, String nodeName, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<OrderCollectEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderCollectEntity::getOwnerId, partnerId)
                .ge(OrderCollectEntity::getDate, start)
                .le(OrderCollectEntity::getDate, end);
        if (!Strings.isNullOrEmpty(nodeName)) {
            queryWrapper.like(OrderCollectEntity::getNodeName, nodeName);
        }
        return orderCollectService.list(queryWrapper);
    }

    /**
     * 获取一定日期内合作商的收益统计
     *
     * @param partnerId
     * @param start
     * @param end
     * @return
     */
    @Override
    public BarCharCollect getCollect(Integer partnerId, LocalDate start, LocalDate end) {
        QueryWrapper<OrderCollectEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("IFNULL(sum(total_bill),0) as total_bill", "date")// 根据分成总金额，时间进行聚合查询
                .lambda()
                .eq(OrderCollectEntity::getOwnerId, partnerId)
                .ge(OrderCollectEntity::getDate, start)
                .le(OrderCollectEntity::getDate, end)
                .groupBy(OrderCollectEntity::getDate)
                .orderByDesc(OrderCollectEntity::getDate);

        Map<LocalDate, Integer> map = orderCollectService.list(queryWrapper).stream()
                .collect(Collectors.toMap(OrderCollectEntity::getDate, OrderCollectEntity::getTotalBill));
        BarCharCollect barCharCollect = new BarCharCollect(); // 获取柱状图封装对象
        /**
         * start-开始时间，datesUntil-工具类，end.plusDays(1)-结束时间-最后一天-ey:(1-8)-没有.plusDays--(1-7)
         * 有的话--（1-8），Period.ofDays(1)-每天循环一次
         */// 从开始时间开始循环，每天循环一次
        start.datesUntil(end.plusDays(1), Period.ofDays(1)).forEach(date -> { // date-每天
            barCharCollect.getXAxis().add(date.format(DateTimeFormatter.ISO_LOCAL_DATE));// 将拿到的时间-填充到 x 轴
            // 没有相应数据 填充为0，也就是当天时间在Y轴对应的在坐标原点 getOrDefault--里面有一个三元运算符
            barCharCollect.getYAxis().add(map.getOrDefault(date, 0));// 在map 集合中根据当前得到的天数为key 可以找到数据-- 填充到 y 轴
        });
        // getOrDefault--->int i== null? i : "i is null"
        // 返回柱状图数据
        return barCharCollect;
    }

    /**
     * 获取一定日期内的销售额统计
     *
     * @param start
     * @param end
     * @param collectType 收集类型 -- 年-1 ，月--2
     * @return date --默认是按年划分
     */
    @Override
    public BarCharCollect getAmountCollect(LocalDate start, LocalDate end, Integer collectType) {
        String groupColumn = collectType == 2 ? "MONTH(date)" : "date"; // 分组列

        String dateColumn = collectType == 2 ? "min(date) as date" : "date"; // 日期列
//        // 日期划分格式
        DateTimeFormatter formatter = collectType == 2 ? DateTimeFormatter.ofPattern("yyyy-MM") : DateTimeFormatter.ISO_LOCAL_DATE;
//
        Period period = collectType == 2 ? Period.ofMonths(1) : Period.ofDays(1); // 统计周期-- 1月一次/一天一次-周

        QueryWrapper<OrderCollectEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("IFNULL(sum(order_total_money),0) as order_total_money", dateColumn)
                .groupBy(groupColumn)
                .lambda()
                .ge(OrderCollectEntity::getDate, start)
                .le(OrderCollectEntity::getDate, end)
                .orderByAsc(OrderCollectEntity::getDate);
        // 构建一个 key 为日期维度，value 为销售额得map
        Map<String, Integer> map = orderCollectService.list(queryWrapper).stream().collect(Collectors.toMap(
                o -> o.getDate().format(formatter), OrderCollectEntity::getOrderTotalMoney));
        BarCharCollect barCharCollect = new BarCharCollect();
        // 以开始日期到结束如期为范围，日期统计类型为 维度构建曲线图数据
        start.datesUntil(end.plusDays(1), period).forEach(date -> {
            String key = date.format(formatter);
            barCharCollect.getXAxis().add(key);
            barCharCollect.getYAxis().add(map.getOrDefault(key, 0));
//            if(map.containsKey(key)){
//                barCharCollect.getYAxis().add(map.get(key));
//            }else {
//                barCharCollect.getYAxis().add(0);
//            }
        });

        return barCharCollect;
    }

    /**
     * 获取地区销量统计 --ey: 北京2街道，北京6街道 --区域 --2
     * 订单汇总表中并没有记录区域名称，所以我们没有办法根据订单汇总表来进行统计了。
     * 我们可以从elasticsearch的订单库根据区域名称进行聚合统计。
     *
     * @param start
     * @param end
     * @return
     */
    @Override
    public BarCharCollect getCollectByRegion(LocalDate start, LocalDate end) {
       SearchRequest request = new SearchRequest("order");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
       BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 1. 根据时间范围，支付状态过滤数据
        boolQueryBuilder.filter(QueryBuilders.rangeQuery("create_time").gte(start).lte(end));
        boolQueryBuilder.filter(QueryBuilders.termQuery("pay_status", VMSystem.PAY_STATUS_PAYED));
        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(0); // 不需要查询具体的数据

        // 2. 根据区域名称聚合
        AggregationBuilder aggregationBuilder = AggregationBuilders.terms("region") // 用户后期提取聚合结果的自定义名称
                .field("region_name") // 需要聚合的字段 -- 在SQL语句中相当于根据region_name 进行分组
                // 对分组之后的数据，根据amount字段再次分组进行聚合统计--得出同区域内的销量总额 --amount_sum：聚合字段别名
                .subAggregation(AggregationBuilders.sum("amount_sum").field("amount")) // 本次聚合需要的条件--求和
                .order(BucketOrder.aggregation("amount_sum", false))
                .size(30);
        sourceBuilder.aggregation(aggregationBuilder);
        request.source(sourceBuilder);

        // 3. 发请求-获取响应结果集
        BarCharCollect barCharCollect = new BarCharCollect(); // 柱状图封装对象
        try {
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            Aggregations aggregations = response.getAggregations(); // 从响应中获取聚合数据
            if (aggregations == null) {
                return barCharCollect;
            }
            Terms region = aggregations.get("region"); // 根据聚合结果名称-提取聚合结果数据
            List<? extends Terms.Bucket> buckets = region.getBuckets(); // 只有获取到了桶，才能获取里面封装的数据
            buckets.stream().forEach(bucket -> {
                barCharCollect.getXAxis().add(bucket.getKeyAsString());
                // 从桶中，提取具体的聚合统计的值 get(聚合统计的字段名/别名);
                ParsedSum amountSum = (ParsedSum)bucket.getAggregations().get("amount_sum");// 从聚合中获取value值
                double value = amountSum.getValue(); // 获取聚合统计结果
                barCharCollect.getYAxis().add((int) value);
            });
        } catch (Exception e) {
            log.error("根据区域汇总数据出错", e);
        }
        return barCharCollect;
    }
}

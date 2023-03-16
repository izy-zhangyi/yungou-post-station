package com.yps.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.util.BeanUtil;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.yps.common.VMSystem;
import com.yps.config.TopicConfig;
import com.yps.contract.*;
import com.yps.contract.server.SupplyTask;
import com.yps.dao.VendingMachineDao;
import com.yps.emq.MqttProducer;
import com.yps.entity.*;
import com.yps.exception.LogicException;
import com.yps.feignService.UserService;
import com.yps.http.viewModel.CreateVMReq;
import com.yps.service.*;
import com.yps.utils.JsonUtil;
import com.yps.viewmodel.*;
import com.xxl.job.core.log.XxlJobLogger;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.GeoDistanceQueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.metrics.GeoCentroidAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.GeoDistanceSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VendingMachineServiceImpl extends ServiceImpl<VendingMachineDao, VendingMachineEntity> implements VendingMachineService {

    @Autowired
    private VendoutRunningService vendoutRunningService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private VmCfgVersionService versionService;

    @Autowired
    private UserService userService;

    @Autowired
    private SkuService skuService;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private VmTypeService vmTypeService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MqttProducer mqttProducer;

//    @Autowired
//    private Sender sender;


    @Override
    public VendingMachineEntity findByInnerCode(String innerCode) {
        LambdaQueryWrapper<VendingMachineEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VendingMachineEntity::getInnerCode, innerCode);

        return this.getOne(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = {Exception.class})
    public boolean add(CreateVMReq vendingMachine) {
        VendingMachineEntity vendingMachineEntity = new VendingMachineEntity();
        vendingMachineEntity.setInnerCode("");
        vendingMachineEntity.setNodeId(Long.valueOf(vendingMachine.getNodeId()));
        vendingMachineEntity.setVmType(vendingMachine.getVmType());
        NodeEntity nodeEntity = nodeService.getById(vendingMachine.getNodeId());
        if (nodeEntity == null) {
            throw new LogicException("所选点位不存在");
        }
        String cityCode = nodeEntity.getArea().getCityCode();
        vendingMachineEntity.setAreaId(nodeEntity.getArea().getId());
        vendingMachineEntity.setBusinessId(nodeEntity.getBusinessId());
        vendingMachineEntity.setRegionId(nodeEntity.getRegionId());

        vendingMachineEntity.setCityCode(cityCode);
        vendingMachineEntity.setCreateUserId(Long.valueOf(vendingMachine.getCreateUserId()));
        vendingMachineEntity.setOwnerId(nodeEntity.getOwnerId());
        vendingMachineEntity.setOwnerName(nodeEntity.getOwnerName());


        //调用用户服务获取创建者姓名
        vendingMachineEntity.setCreateUserName(userService.getUser(vendingMachine.getCreateUserId()).getUserName());
        this.save(vendingMachineEntity);

        //设置售货机的innerCode
        UpdateWrapper<VendingMachineEntity> uw = new UpdateWrapper<>();
        String innerCode = generateInnerCode(vendingMachineEntity.getNodeId());
        uw.lambda()
                .set(VendingMachineEntity::getInnerCode, innerCode)
                .eq(VendingMachineEntity::getId, vendingMachineEntity.getId());
        this.update(uw);

        vendingMachineEntity.setInnerCode(innerCode);
        vendingMachineEntity.setClientId(generateClientId(innerCode));
        //创建货道数据
        createChannel(vendingMachineEntity);

        //创建版本数据
        versionService.initVersionCfg(vendingMachineEntity.getId(), innerCode);

        return true;
    }

    @Override
    public boolean update(Long id, Long nodeId) {
        VendingMachineEntity vm = this.getById(id);
        if (vm.getVmStatus() == VMSystem.VM_STATUS_RUNNING)
            throw new LogicException("改设备正在运营");
        NodeEntity nodeEntity = nodeService.getById(nodeId);
        vm.setNodeId(nodeId);
        vm.setRegionId(nodeEntity.getRegionId());
        vm.setBusinessId(nodeEntity.getBusinessId());
        vm.setOwnerName(nodeEntity.getOwnerName());
        vm.setOwnerId(nodeEntity.getOwnerId());

        return this.updateById(vm);
    }

    @Override
    public List<ChannelEntity> getAllChannel(String innerCode) {
        return channelService.getChannelesByInnerCode(innerCode);
    }

    @Override
    public List<SkuViewModel> getSkuList(String innerCode) {
        // 1.获取商品的货道信息--同时过滤出有商品的货道
        List<ChannelEntity> channelList = this.getAllChannel(innerCode)
                .stream()
                .filter(c -> c.getSkuId() > 0 && c.getSku() != null)
                .collect(Collectors.toList());
        // 2. 获取商品库存余量 groupingBy(指定要按照什么进行分组，聚合:Collectors.summingInt(根据什么进行聚合))
        // summingInt--相当于就是做聚合运算的-等同于数据库查询中的find
        Map<SkuEntity, Integer> skuMap = channelList.stream()
                .collect(Collectors.groupingBy(ChannelEntity::getSku,
                        Collectors.summingInt(ChannelEntity::getCurrentCapacity)));

        // 3. 获取商品的价格表 -- 商品id分组--商品价格进行聚合
        Map<Long, IntSummaryStatistics> skuPrice = channelList.stream()
                .collect(Collectors.groupingBy(ChannelEntity::getSkuId,
                        Collectors.summarizingInt(ChannelEntity::getPrice)));


        return skuMap.entrySet().stream().map(entry -> {
                    SkuEntity sku = entry.getKey();
                    // 1. 从 entry 中根据 key 提取商品
                    sku.setRealPrice(skuPrice.get(sku.getSkuId()).getMin()); // 商品真实价格
                    // 2. 由于需要返回的是一个 SkuViewModel--获取SkuViewModel对象
                    SkuViewModel skuViewModel = new SkuViewModel();
                    BeanUtils.copyProperties(sku, skuViewModel);
                    skuViewModel.setImage(sku.getSkuImage());
                    skuViewModel.setCapacity(entry.getValue());
                    return skuViewModel;
                })
                .sorted(Comparator.comparing(SkuViewModel::getCapacity).reversed()) // 排序--倒序排序
                .collect(Collectors.toList());
    }

    @Override
    public SkuEntity getSku(String innerCode, long skuId) {
        SkuEntity skuEntity = skuService.getById(skuId);
        skuEntity.setRealPrice(channelService.getRealPrice(innerCode, skuId));
        LambdaQueryWrapper<ChannelEntity> qw = new LambdaQueryWrapper<>();
        qw
                .eq(ChannelEntity::getSkuId, skuId)
                .eq(ChannelEntity::getInnerCode, innerCode);
        List<ChannelEntity> channelList = channelService.list(qw);
        int capacity = 0;
        if (channelList == null || channelList.size() <= 0)
            capacity = 0;
        else
            capacity = channelList
                    .stream()
                    .map(ChannelEntity::getCurrentCapacity)
                    .reduce(Integer::sum).get();
        skuEntity.setCapacity(capacity);

        return skuEntity;
    }

    @Override
    @Transactional(rollbackFor = {Exception.class})
    public boolean supply(SupplyCfg supply) {
        VendingMachineEntity vendingMachineEntity = this.findByInnerCode(supply.getInnerCode());
        vendingMachineEntity.setLastSupplyTime(LocalDateTime.now());
        this.updateById(vendingMachineEntity);
        List<ChannelEntity> channelList = channelService.getChannelesByInnerCode(supply.getInnerCode());
        supply.getSupplyData()
                .forEach(
                        c -> {
                            Optional<ChannelEntity> item =
                                    channelList.stream()
                                            .filter(channel -> channel.getChannelCode().equals(c.getChannelId()))
                                            .findFirst();
                            if (item.isPresent()) {
                                ChannelEntity channelEntity = item.get();
                                channelEntity.setCurrentCapacity(channelEntity.getCurrentCapacity() + c.getCapacity());
                                channelEntity.setLastSupplyTime(LocalDateTime.now());
                                channelService.supply(channelEntity);
                            }
                        });
        //更新补货版本号；
        versionService.updateSupplyVersion(supply.getInnerCode());
        notifyGoodsStatus(supply.getInnerCode(), false);

        return true;
    }

    @Override
    @Transactional
    public boolean vendOutResult(VendoutResp vendoutResp) {
        try {
            String key = "vmService.outResult." + vendoutResp.getVendoutResult().getOrderNo();

            //对结果做校验，防止重复上传(从redis校验)
            Object redisValue = redisTemplate.opsForValue().get(key);
            redisTemplate.delete(key);

            if (redisValue != null) {
                log.info("出货重复上传");
                return false;
            }


            //存入出货流水数据
            VendoutRunningEntity vendoutRunningEntity = new VendoutRunningEntity();
            vendoutRunningEntity.setInnerCode(vendoutResp.getInnerCode());
            vendoutRunningEntity.setOrderNo(vendoutResp.getVendoutResult().getOrderNo());
            vendoutRunningEntity.setStatus(vendoutResp.getVendoutResult().isSuccess());
            vendoutRunningEntity.setPrice(vendoutResp.getVendoutResult().getPrice());
            vendoutRunningEntity.setSkuId(vendoutResp.getVendoutResult().getSkuId());
            vendoutRunningService.save(vendoutRunningEntity);


            //存入redis
            redisTemplate.opsForValue().set(key, key);
            redisTemplate.expire(key, 7, TimeUnit.DAYS);

            //减货道库存
            ChannelEntity channel = channelService.getChannelInfo(vendoutResp.getInnerCode(), vendoutResp.getVendoutResult().getChannelId());
            int currentCapacity = channel.getCurrentCapacity() - 1;
            if (currentCapacity < 0) {
                log.info("缺货");
                notifyGoodsStatus(vendoutResp.getInnerCode(), true);

                return true;
            }

            channel.setCurrentCapacity(currentCapacity);
            channelService.updateById(channel);
        } catch (Exception e) {
            log.error("update vendout result error.", e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

            return false;
        }

        return true;
    }

    @Override
    public Pager<String> getAllInnerCodes(boolean isRunning, long pageIndex, long pageSize) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VendingMachineEntity> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageIndex, pageSize);

        QueryWrapper<VendingMachineEntity> qw = new QueryWrapper<>();
        if (isRunning) {
            qw.lambda()
                    .select(VendingMachineEntity::getInnerCode)
                    .eq(VendingMachineEntity::getVmStatus, 1);
        } else {
            qw.lambda()
                    .select(VendingMachineEntity::getInnerCode)
                    .ne(VendingMachineEntity::getVmStatus, 1);
        }
        this.page(page, qw);
        Pager<String> result = new Pager<>();
        result.setCurrentPageRecords(page.getRecords().stream().map(VendingMachineEntity::getInnerCode).collect(Collectors.toList()));
        result.setPageIndex(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setTotalCount(page.getTotal());

        return result;
    }

    @Override
    public Pager<VendingMachineEntity> query(Long pageIndex, Long pageSize, Integer status, String innerCode) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VendingMachineEntity> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageIndex, pageSize);
        LambdaQueryWrapper<VendingMachineEntity> queryWrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            queryWrapper.eq(VendingMachineEntity::getVmStatus, status);
        }
        if (!Strings.isNullOrEmpty(innerCode)) {
            queryWrapper.likeLeft(VendingMachineEntity::getInnerCode, innerCode);
        }
        this.page(page, queryWrapper);

        return Pager.build(page);
    }


    @Override
    public Integer getCountByOwnerId(Integer ownerId) {
        LambdaQueryWrapper<VendingMachineEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(VendingMachineEntity::getOwnerId, ownerId);

        return this.count(qw);
    }

    /*

     */
/**
 * 从ES中删除售货机
 * @param innerCode
 * @return
 *//*

    private void removeVmInES(String innerCode){
        DeleteRequest request = new DeleteRequest("vm").id(innerCode);
        try {
            esClient.delete(request,RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.error("从ES中删除售货机失败",e);
        }
    }
*/

    /**
     * 生成售货机InnerCode
     *
     * @param nodeId 点位Id
     * @return
     */
    private String generateInnerCode(long nodeId) {
        NodeEntity nodeEntity = nodeService.getById(nodeId);

        StringBuilder sbInnerCode = new StringBuilder(nodeEntity.getArea().getCityCode());

        int count = getCountByArea(nodeEntity.getArea());
        sbInnerCode.append(Strings.padStart(String.valueOf(count + 1), 5, '0'));

        return sbInnerCode.toString();
    }

    /**
     * 创建货道
     *
     * @param vm
     * @return
     */
    private boolean createChannel(VendingMachineEntity vm) {
        VmTypeEntity vmType = vmTypeService.getById(vm.getVmType());

        for (int i = 1; i <= vmType.getVmRow(); i++) {
            for (int j = 1; j <= vmType.getVmCol(); j++) {
                ChannelEntity channel = new ChannelEntity();
                channel.setChannelCode(i + "-" + j);
                channel.setCurrentCapacity(0);
                channel.setInnerCode(vm.getInnerCode());
                channel.setLastSupplyTime(vm.getLastSupplyTime());
                channel.setMaxCapacity(vmType.getChannelMaxCapacity());
                channel.setVmId(vm.getId());
                channelService.save(channel);
            }
        }

        return true;
    }

    /**
     * 获取某一地区下售货机数量
     *
     * @param area
     * @return
     */
    private int getCountByArea(AreaEntity area) {
        QueryWrapper<VendingMachineEntity> qw = new QueryWrapper<>();
        qw.lambda()
                .eq(VendingMachineEntity::getCityCode, area.getCityCode())
                .isNotNull(VendingMachineEntity::getInnerCode)
                .ne(VendingMachineEntity::getInnerCode, "");

        return this.count(qw);
    }

    /**
     * 发送缺货告警信息
     *
     * @param innerCode
     * @param isFault   true--缺货状态;false--不缺货状态
     */
    private void notifyGoodsStatus(String innerCode, boolean isFault) {
        VmStatusContract contract = new VmStatusContract();
        contract.setNeedResp(false);
        contract.setSn(0);
        contract.setInnerCode(innerCode);

        StatusInfo statusInfo = new StatusInfo();
        statusInfo.setStatus(isFault);
        statusInfo.setStatusCode("10003");
        List<StatusInfo> statusInfos = Lists.newArrayList();
        statusInfos.add(statusInfo);
        contract.setStatusInfo(statusInfos);

        try {
            //  发送设备不缺货消息(置设备为不缺货)
            mqttProducer.send(TopicConfig.VM_STATUS_TOPIC, 2, contract);
        } catch (JsonProcessingException e) {
            log.error("serialize error.", e);
        }
    }

    /**
     * 生成售货机的clientId
     *
     * @param innerCode
     * @return
     */
    private String generateClientId(String innerCode) {
        String clientId = System.currentTimeMillis() + innerCode;

        return org.springframework.util.DigestUtils.md5DigestAsHex(clientId.getBytes());
    }

    /**
     * 货道扫描与补货消息
     * 统计货道容量小于警戒值得售货机货道
     *
     * @param percent
     * @param vendingMachineEntity
     * @return
     */
    @Override
    public int inventory(int percent, VendingMachineEntity vendingMachineEntity) {
        // 计算售货机货道的警戒线
        Integer maxCapacity = vendingMachineEntity.getType().getChannelMaxCapacity(); // 售货机货道最大容量
        int alertValue = (int) (maxCapacity * (float) percent / 100); //得到该售货机警戒容量
        //统计当前库存小于等于警戒线的货道数量
        LambdaQueryWrapper<ChannelEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChannelEntity::getVmId, vendingMachineEntity.getId())
                .le(ChannelEntity::getCurrentCapacity, alertValue) // 售货机当前货道容量 < 警戒容量
                .ne(ChannelEntity::getSkuId, 0L); // 货道内的商品id 为 0-- 当前货道内没有商品
        // 返回查询的数量
        return channelService.count(queryWrapper);
    }

    /**
     * 更新机器状态
     *
     * @param innerCode
     * @param status
     * @return
     */
    @Override
    public Boolean updateStatus(String innerCode, Integer status) {
        try {
            LambdaUpdateWrapper<VendingMachineEntity> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(VendingMachineEntity::getInnerCode, innerCode)
                    .set(VendingMachineEntity::getVmStatus, status);
            this.update(updateWrapper);
        } catch (Exception e) {
            log.error("updateStatus error,innerCode is " + innerCode + " status is " + status, e);
            return false;
        }
        return true;
    }

    /**
     * 发送补货工单
     *
     * @param vendingMachineEntity
     */
    @Override
    public void sendSupplyTask(VendingMachineEntity vendingMachineEntity) {
        //  1. 查询售货机货道
        LambdaQueryWrapper<ChannelEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChannelEntity::getVmId, vendingMachineEntity.getId())// 售货机id
                .ne(ChannelEntity::getSkuId, 0L); // 货道商品id = 0

        // 2. 获取符合要求的货道列表
        List<ChannelEntity> channelList = channelService.list(queryWrapper);

        // 3. 遍历货道列表，补货列表
        List<SupplyChannel> supplyChannelList = channelList.stream().map(c -> {
            // 3.1 补货货道数据 -- 获取 补货货道的 属性封装对象--基本信息
            SupplyChannel supplyChannel = new SupplyChannel();
            supplyChannel.setChannelId(c.getChannelCode()); // 货道编号
            supplyChannel.setCapacity(c.getMaxCapacity() - c.getCurrentCapacity()); // 补货容量
            supplyChannel.setSkuId(c.getSkuId()); // 商品id
            supplyChannel.setSkuName(c.getSku().getSkuName()); // 从商品中获取商品名称
            supplyChannel.setSkuImage(c.getSku().getSkuImage()); // 从商品中获取商品图片
            return supplyChannel;

        }).collect(Collectors.toList());

        // 4. 构建补货协议数据
        SupplyCfg supplyCfg = new SupplyCfg();
        supplyCfg.setInnerCode(vendingMachineEntity.getInnerCode()); // 售货机编号
        supplyCfg.setSupplyData(supplyChannelList); // 补货货道数据
        supplyCfg.setMsgType("supplyTask"); // 发送消息类型

        // 5. 发送补货消息到 emq
        try {
            mqttProducer.send(TopicConfig.SUPPLY_TOPIC, 2, supplyCfg);
            XxlJobLogger.log("发送补货数据：" + JsonUtil.serialize(supplyCfg));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 商品是否还有余量
     *
     * @param skuId
     * @return
     */
    @Override
    public Boolean hasCapacity(String innerCode, Long skuId) {
        LambdaQueryWrapper<ChannelEntity> queryWrapper = new LambdaQueryWrapper<>();
        // 查询售货机中货道容量 >=0 的货道
        queryWrapper.eq(ChannelEntity::getInnerCode, innerCode)
                .eq(ChannelEntity::getSkuId, skuId)
                .gt(ChannelEntity::getCurrentCapacity, 0);
        // > 0 --true- 有余量  < = -- false --货道中当前商品没有余量
        return channelService.count(queryWrapper) > 0;
    }

    /**
     * 设置售货机位置信息
     *
     * @param vmDistance
     * @return
     */
    @Resource
    private RestHighLevelClient client;

    @Override
    public Boolean setVmDistance(VMDistance vmDistance) {
        VendingMachineEntity vendingMachine = this.findByInnerCode(vmDistance.getInnerCode()); // 查询售货机
        if (vendingMachine == null) {
            throw new LogicException("该售货机编号不存在：" + vmDistance.getInnerCode());
        }

        // 向es 中存入数据-- 获取 index 请求对象，传入索引库名称，id为索引库id
        IndexRequest indexRequest = new IndexRequest("vm").id(vmDistance.getInnerCode());
        indexRequest.source(
                "addr", vendingMachine.getNode().getAddr(), // 售货机地址
                "innerCode", vendingMachine.getInnerCode(), // 售货机编号
                "nodeName", vendingMachine.getNode().getName(), // 点位名称
                "location", vmDistance.getLon() + "," + vmDistance.getLat(), // 所在经纬度
                "typeName", vendingMachine.getType().getName()); // 类型名称
        try {
            client.index(indexRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.error("添加售货机位置信息失败", e);
            return false;
        }
        // 保存到数据库
        vendingMachine.setLatitude(vmDistance.getLat());
        vendingMachine.setLongitudes(vmDistance.getLon());
        this.updateById(vendingMachine);
        return true;
    }

    /**
     * 根据条件搜索售货机
     *
     * @param vmSearch
     * @return
     */
    @Override
    public List<VmInfoDto> search(VmSearch vmSearch) {
        SearchRequest searchRequest = new SearchRequest("vm"); // 获取 搜索请求对象
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder(); // 获取搜索资源构建器

        GeoDistanceQueryBuilder distanceQueryBuilder = new GeoDistanceQueryBuilder("location"); // 获取中心点构建器
        distanceQueryBuilder.distance(vmSearch.getDistance(), DistanceUnit.DEFAULT);// 设置半径
        distanceQueryBuilder.point(vmSearch.getLat(), vmSearch.getLon()); // 中心点坐标

        //从近到远排序规则构建
        GeoDistanceSortBuilder distanceSortBuilder = new GeoDistanceSortBuilder("location",
                vmSearch.getLat(), vmSearch.getLon());
        distanceSortBuilder.unit(DistanceUnit.DEFAULT); // 规定所搜单位：km
        distanceSortBuilder.order(SortOrder.ASC); // 排序
        distanceSortBuilder.geoDistance(GeoDistance.ARC); // 指定算法  PLANE:比较快，精准度低，ARC 与之相反

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder(); // 获取布尔构建器对象
        boolQueryBuilder.must(distanceQueryBuilder); // 中心点范围查询
        searchSourceBuilder.query(boolQueryBuilder); // 搜索
        searchSourceBuilder.sort(distanceSortBuilder); // 搜索结果排序
        searchRequest.source(searchSourceBuilder); // 搜索源

        try {
            // 封装查询方法
            SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT); // 获取查询请求结果对象
            SearchHits hits = response.getHits(); // 提取查询结果
            if (hits.getTotalHits().value <= 0) {
                return Lists.newArrayList(); // 数据为空，返回一个空数组
            }

            List<VmInfoDto> vmInfoDtoList = Lists.newArrayList(); // 数据不为空，使用一个新的VmInfoDto 集合封装数据
            // Arrays.stream(hits.getHits()) -- 相当于将 hits.getHits()数组转成 stream 流
            Arrays.stream(hits.getHits()).forEach(h -> {
                VmInfoDto vmInfoDto = null;
                try {
                    vmInfoDto = JsonUtil.getByJson(h.getSourceAsString(), VmInfoDto.class); // 将获取的 JSON数据 转成 对象
                    //BigDecimal bigDecimal = BigDecimal.valueOf((Double) h.getSortValues()[0] * 1000); // 将 千米 转成 米
                    // vmInfoDto.setDistance(bigDecimal.intValue()); // 查询结果距离圆心的长度
                    vmInfoDto.setDistance((int)(double) h.getSortValues()[0]); // 查询结果距离圆心的长度
                } catch (IOException e) {
                    log.error("convert vminfo error", e);
                }
                if (vmInfoDto != null) {
                    vmInfoDtoList.add(vmInfoDto); // 将结果封装到集合
                }
            });
            return vmInfoDtoList; // 返回封装结果
        } catch (IOException e) {
            log.error("search location error", e);
            return Lists.newArrayList();
        }
    }
}

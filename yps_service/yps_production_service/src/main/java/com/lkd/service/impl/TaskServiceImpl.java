package com.yps.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.yps.common.VMSystem;
import com.yps.config.TopicConfig;
import com.yps.contract.SupplyCfg;
import com.yps.contract.SupplyChannel;
import com.yps.contract.TaskCompleteContract;
import com.yps.dao.TaskDao;
import com.yps.emq.MqttProducer;
import com.yps.emq.MqttService;
import com.yps.entity.TaskDetailsEntity;
import com.yps.entity.TaskEntity;
import com.yps.entity.TaskStatusTypeEntity;
import com.yps.exception.LogicException;
import com.yps.feignService.UserService;
import com.yps.feignService.VMService;
import com.yps.http.viewModel.CancelTaskViewModel;
import com.yps.http.viewModel.TaskReportInfo;
import com.yps.http.viewModel.TaskViewModel;
import com.yps.service.TaskDetailsService;
import com.yps.service.TaskService;
import com.yps.service.TaskStatusTypeService;
import com.yps.viewmodel.Pager;
import com.yps.viewmodel.UserViewModel;
import com.yps.viewmodel.UserWork;
import com.yps.viewmodel.VendingMachineViewModel;
import io.netty.util.concurrent.CompleteFuture;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Integers;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TaskServiceImpl extends ServiceImpl<TaskDao, TaskEntity> implements TaskService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TaskDetailsService taskDetailsService;

    @Autowired
    private VMService vmService;

    @Autowired
    private TaskStatusTypeService statusTypeService;

    @Autowired
    private UserService userService;

    @Resource
    private MqttProducer mqttProducer; // 注入消息生产者--用于消息的发送

    /**
     * 创建工单
     *
     * @param taskViewModel
     * @return
     * @throws LogicException
     */
    @Override
    @Transactional(rollbackFor = {Exception.class}, noRollbackFor = {LogicException.class})
    public boolean createTask(TaskViewModel taskViewModel) throws LogicException {
        checkCreateTask(taskViewModel.getInnerCode(), taskViewModel.getProductType());//验证
        if (hasTask(taskViewModel.getInnerCode(), taskViewModel.getProductType())) {
            throw new LogicException("该机器有未完成的同类型工单");
        }

        //向工单表插入一条记录
        TaskEntity taskEntity = new TaskEntity();
        BeanUtils.copyProperties(taskViewModel, taskEntity); // 将工单视图中的属性拷贝到 工单实体类对象中
        taskEntity.setTaskCode(this.generateTaskCode()); // 工单编码--工单号
        taskEntity.setTaskStatus(VMSystem.TASK_STATUS_CREATE);
        taskEntity.setProductTypeId(taskViewModel.getProductType()); // 填充工单类型-否则默认是投放工单
//        String userName = userService.getUser(taskViewModel.getUserId()).getUserName();
//        taskEntity.setUserName(userName);
        // 填充售货机地址，点位信息,区域id
        VendingMachineViewModel vmInfo = vmService.getVMInfo(taskViewModel.getInnerCode());
        taskEntity.setAddr(vmInfo.getNodeAddr());
        taskEntity.setRegionId(vmInfo.getRegionId());
        this.save(taskEntity);

//        VendingMachineViewModel vm = vmService.getVMInfo(taskViewModel.getInnerCode());
//        taskEntity.setAddr(vm.getNodeAddr());
//        taskEntity.setRegionId(  vm.getRegionId() );

        //如果是补货工单，向 工单明细表插入记录taskEntity.getProductTypeId() == VMSystem.TASK_TYPE_SUPPLY
        if (Objects.equals(taskEntity.getProductTypeId(), VMSystem.TASK_TYPE_SUPPLY)) {
            taskViewModel.getDetails().forEach(d -> {
                TaskDetailsEntity detailsEntity = new TaskDetailsEntity();
                BeanUtils.copyProperties(d, detailsEntity);
                detailsEntity.setTaskId(taskEntity.getTaskId());
                taskDetailsService.save(detailsEntity);
            });
        }
        // 创建 工单量分之 +1
        updateTaskZset(taskEntity, 1);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeTask(long id) {
        return completeTask(id, 0D, 0D, "");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeTask(long id, Double lat, Double lon, String addr) {
        TaskEntity taskEntity = this.getById(id);
        if (Objects.deepEquals(taskEntity.getTaskStatus(), VMSystem.TASK_STATUS_FINISH) || Objects.deepEquals(taskEntity.getTaskStatus(), VMSystem.TASK_STATUS_CANCEL)) {
            throw new LogicException("工单已经结束");
        }
        taskEntity.setTaskStatus(VMSystem.TASK_STATUS_FINISH); // 工单完成-修改工单状态
       // taskEntity.setAddr(addr); // 填充工单地址
        this.updateById(taskEntity);
        Integer productTypeId = taskEntity.getProductTypeId();
        System.out.println(productTypeId);
        // 如果是投放或是撤机工单--封装与下发投放或是撤机协议
        if (Objects.deepEquals(taskEntity.getProductTypeId(), VMSystem.TASK_TYPE_DEPLOY) || Objects.deepEquals(taskEntity.getProductTypeId(), VMSystem.TASK_TYPE_REVOKE)) {
            noticeVMServiceStatus(lat, lon, taskEntity);
        }

        //  如果是补货工单--下发补货协议
        if (Objects.deepEquals(taskEntity.getProductTypeId(), VMSystem.TASK_TYPE_SUPPLY)) {
            noticeVMServiceSupply(taskEntity);
        }
        return true;
    }


    @Override
    public List<TaskStatusTypeEntity> getAllStatus() {
        QueryWrapper<TaskStatusTypeEntity> qw = new QueryWrapper<>();
        qw.lambda().ge(TaskStatusTypeEntity::getStatusId, VMSystem.TASK_STATUS_CREATE);

        return statusTypeService.list(qw);
    }

    @Override
    public Pager<TaskEntity> search(Long pageIndex, Long pageSize, String innerCode, Integer userId, String taskCode, Integer status, Boolean isRepair, String start, String end) {
        Page<TaskEntity> page = new Page<>(pageIndex, pageSize);
        LambdaQueryWrapper<TaskEntity> qw = new LambdaQueryWrapper<>();
        if (!Strings.isNullOrEmpty(innerCode)) {
            qw.eq(TaskEntity::getInnerCode, innerCode);
        }
        if (userId != null && userId > 0) {
            qw.eq(TaskEntity::getAssignorId, userId);
        }
        if (!Strings.isNullOrEmpty(taskCode)) {
            qw.like(TaskEntity::getTaskCode, taskCode);
        }
        if (status != null && status > 0) {
            qw.eq(TaskEntity::getTaskStatus, status);
        }
        if (isRepair != null) {
            if (isRepair) {
                qw.ne(TaskEntity::getProductTypeId, VMSystem.TASK_TYPE_SUPPLY);
            } else {
                qw.eq(TaskEntity::getProductTypeId, VMSystem.TASK_TYPE_SUPPLY);
            }
        }
        if (!Strings.isNullOrEmpty(start) && !Strings.isNullOrEmpty(end)) {
            qw.ge(TaskEntity::getCreateTime, LocalDate.parse(start, DateTimeFormatter.ISO_LOCAL_DATE)).le(TaskEntity::getCreateTime, LocalDate.parse(end, DateTimeFormatter.ISO_LOCAL_DATE));
        }
        //根据最后更新时间倒序排序
        qw.orderByDesc(TaskEntity::getUpdateTime);

        return Pager.build(this.page(page, qw));
    }

    /**
     * 运维工单封装与下发
     *
     * @param taskEntity
     */
    private void noticeVMServiceStatus(Double lat, Double lon, TaskEntity taskEntity) {
        //todo: 向消息队列发送消息，通知售货机更改状态
        // 1. 获取发送消息的 封装协议对象
        TaskCompleteContract completeContract = new TaskCompleteContract();
        completeContract.setInnerCode(taskEntity.getInnerCode()); // 在协议中封装 售货机的编号
        completeContract.setTaskType(taskEntity.getProductTypeId()); // 在协议中封装 工单类型
        completeContract.setLon(lon); // 经度
        completeContract.setLat(lat); // 纬度
        // 2. 协议封装完成--发送消息到emq
        try {
            mqttProducer.send(TopicConfig.COMPLETED_TASK_TOPIC, 2, completeContract);
        } catch (Exception e) {
            log.error("发送工单完成协议出错");
            throw new LogicException("发送工单完成协议出错");
        }
    }

    /**
     * 补货协议封装与下发
     *
     * @param taskEntity
     */
    private void noticeVMServiceSupply(TaskEntity taskEntity) {

        // 协议封装内容
        // 1. 根据工单id，查询工单明细表
        QueryWrapper<TaskDetailsEntity> qw = new QueryWrapper<>();
        qw.lambda().eq(TaskDetailsEntity::getTaskId, taskEntity.getTaskId());
        List<TaskDetailsEntity> details = taskDetailsService.list(qw);
        // 2. 构建协议内容-- 获取协议封装类对象
        SupplyCfg supplyCfg = new SupplyCfg();
        // 向协议中 填充售货机编号
        supplyCfg.setInnerCode(taskEntity.getInnerCode());
        // 3.初始化补货数据
        List<SupplyChannel> supplyChannels = Lists.newArrayList();
        // 4. 从工单详细表中提取数据，添加到补货数据中
        details.forEach(d -> {
            SupplyChannel channel = new SupplyChannel();
            channel.setChannelId(d.getChannelCode());
            channel.setCapacity(d.getExpectCapacity());
            supplyChannels.add(channel);
        });
        // 5.  填充 补货数据到 协议中
        supplyCfg.setSupplyData(supplyChannels);

        //TODO: 下发补货协议 -- 也就是将消息发送到emq
        try {
            mqttProducer.send(TopicConfig.COMPLETED_TASK_TOPIC, 2, supplyCfg);
        } catch (Exception e) {
            log.error("发送补货协议出错");
            throw new LogicException("发送补货协议出错");
        }

    }

    /**
     * 同一台设备下是否存在未完成的工单
     *
     * @param innerCode
     * @param productionType
     * @return
     */
    private boolean hasTask(String innerCode, int productionType) {
        QueryWrapper<TaskEntity> qw = new QueryWrapper<>();
        qw.lambda().select(TaskEntity::getTaskId).eq(TaskEntity::getInnerCode, innerCode).eq(TaskEntity::getProductTypeId, productionType).le(TaskEntity::getTaskStatus, VMSystem.TASK_STATUS_PROGRESS);

        return this.count(qw) > 0;
    }

    private void checkCreateTask(String innerCode, int productType) throws LogicException {
        VendingMachineViewModel vmInfo = vmService.getVMInfo(innerCode);//根据设备编号查询设备
        // 售货机校验
        if (vmInfo == null) {
            throw new LogicException("设备校验失败");
        }

        // 投放工单校验 productType == VMSystem.TASK_TYPE_DEPLOY && vmInfo.getVmStatus() == VMSystem.VM_STATUS_RUNNING
        if (Objects.deepEquals(productType, VMSystem.TASK_TYPE_DEPLOY) && Objects.deepEquals(vmInfo.getVmStatus(), VMSystem.VM_STATUS_RUNNING)) {
            throw new LogicException("该设备已在运营");
        }

        // 补货工单校验productType == VMSystem.TASK_TYPE_SUPPLY && vmInfo.getVmStatus() != VMSystem.VM_STATUS_RUNNING
        if (Objects.deepEquals(productType, VMSystem.TASK_TYPE_SUPPLY) && !Objects.deepEquals(vmInfo.getVmStatus(), VMSystem.VM_STATUS_RUNNING)) {
            throw new LogicException("该设备不在运营状态");
        }

        // 撤机工单校验productType == VMSystem.TASK_TYPE_REVOKE && vmInfo.getVmStatus() != VMSystem.VM_STATUS_RUNNING
        if (Objects.deepEquals(productType, VMSystem.TASK_TYPE_REVOKE) && !Objects.deepEquals(vmInfo.getVmStatus(), VMSystem.VM_STATUS_RUNNING)) {
            throw new LogicException("该设备不在运营状态");
        }
    }


    /**
     * 生成工单编码
     * generateTaskCode
     * Generate
     *
     * @return
     */
    private String generateTaskCode() {
        // 获取当天的时间 --yyyy-MM-dd
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 存入 redis 中的 key-- 也是工单编号的开头部分
        String key = "yps.task.code." + date;

        // 根据 key 从redis 中去获取对应的数据-- 工单
        Object obj = redisTemplate.opsForValue().get(key);
        if (Objects.deepEquals(obj, null)) {
            // 在 redis 缓存中没有得到数据 -- 保存 数据到 redis 设置1天有效期
            redisTemplate.opsForValue().set(key, 1L, Duration.ofDays(1));
            // 返回工单编号信息--date + "0001"--当天的 第一笔工单
            return date + "0001";
        }

        // 有数据 -- 生成新的工单 -- 编号在原有的基础上 +1 ，
        // 可以使用increment方法，不仅可以避免锁的问题，也有效减少代码量
        // increment 方法内部 已经处理好了锁的问题，不需要先get获取，再次赋值了  Strings.padStart()方法用于补字符占位。
        return date + Strings.padStart(String.valueOf(redisTemplate.opsForValue().increment(key, 1)), 4, '0');

    }
//    ISO_LOCAL_DATE = new DateTimeFormatterBuilder()
//                .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
//                .appendLiteral('-')
//                .appendValue(MONTH_OF_YEAR, 2)
//                .appendLiteral('-')
//                .appendValue(DAY_OF_MONTH, 2)
//                .toFormatter(ResolverStyle.STRICT, IsoChronology.INSTANCE);

    /**
     * 接受工单
     *
     * @param id
     * @return
     */
    @Override
    public Boolean accept(String id) {
        // 1. 根据工单id 去查询工单
        TaskEntity task = this.getById(id);
        if (task == null) {
            throw new RuntimeException("当前工单不存在");
        }
        // 2. 校验工单是不是待处理状态
        if (!Objects.deepEquals(task.getTaskStatus(), VMSystem.TASK_STATUS_CREATE)) {
            throw new RuntimeException("工单状态不是待处理");
        }

        // 3. 修改工单状态--进行中
        task.setTaskStatus(VMSystem.TASK_STATUS_PROGRESS);
        // 修改
        return this.updateById(task);
    }

    /**
     * 拒绝工单
     *
     * @param id                  工单id
     * @param cancelTaskViewModel 拒绝理由
     * @return
     */
    @Override
    public Boolean cancelTask(String id, CancelTaskViewModel cancelTaskViewModel) {
        // 1. 根据id查询工单信息
        TaskEntity task = this.getById(id);
        if (task == null) {
            throw new RuntimeException("当前工单不存在");
        }

        // 2. 工单状态校验
        if (Objects.deepEquals(task.getTaskStatus(), VMSystem.TASK_STATUS_FINISH) || Objects.deepEquals(task.getTaskStatus(), VMSystem.TASK_STATUS_CANCEL)) {
            throw new RuntimeException("当前工单已经完成或取消");
        }

        // 3. 修改工单状态,填充理由
        task.setTaskStatus(VMSystem.TASK_STATUS_CANCEL);
        task.setDesc(cancelTaskViewModel.getDesc());

        // 取消工单，工单量分值 -1
        updateTaskZset(task, -1);
        return this.updateById(task);
    }

    /**
     * 更新工单量列表
     *
     * @param taskEntity
     * @param score
     */
    private void updateTaskZset(TaskEntity taskEntity, int score) {
        // 1. 判断工单状态--指派工作人员
        // 1.1 定义默认是 维修工单 -- 指派维修人员
        String roleCode = "1003";
        if (Objects.deepEquals(taskEntity.getProductTypeId(), 2)) {
            // 工单是 运营工单，重定义roleCode:角色编号-工作编号
            roleCode = "1002";
        }

        // 2. 获取key
        String key = VMSystem.REGION_TASK_KEY_PREF + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "." + taskEntity.getRegionId() + "." + roleCode;
        // 3.存入redis score--: 1 ,-1
        redisTemplate.opsForZSet().incrementScore(key, taskEntity.getAssignorId(), score);
    }

    /**
     * 获取同一天内分配的工单最少的人
     *
     * @param regionId 区域id
     * @param isRepair 是否是维修工单
     * @return
     */
    @Override
    public Integer getTaskLastUser(String regionId, Boolean isRepair) {
        // 1. 默认是维修工单，指定员工编号为运维人员
        String roleCode = "1003";
        if (!isRepair) {
            // 如果不是维修工单，重新指定 员工编号为运营人员
            roleCode = "1002";
        }
        // 2. 获取key
        String key = VMSystem.REGION_TASK_KEY_PREF + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "." + regionId + "." + roleCode;
        // 3. 根据 key ,从redis 中拿数据 只取一个值 (key,0,1)-- (0,1)
        Set<Object> user = redisTemplate.opsForZSet().range(key, 0, 1);
        if (user == null || user.isEmpty()) {
            throw new LogicException("该区域暂时没有相关人员");
        }
//        return (Integer) user.stream().collect(Collectors.toList()).get(0); <==>    return (Integer) new ArrayList<>(user).get(0);
        // 返回用户id
        return (Integer) new ArrayList<>(user).get(0);
    }

    /**
     * 获取工单的统计情况
     *
     * @return
     */
    @Override
    public List<TaskReportInfo> getReportInfo(LocalDateTime start, LocalDateTime end) {

        // 运营工单总数
        CompletableFuture<Integer> supplyTotalFuture = CompletableFuture.supplyAsync(() -> this.taskCount(start, end, false, null));

        // 运营工单完成总数
        CompletableFuture<Integer> completeSupplyTotal = CompletableFuture.supplyAsync(() -> this.taskCount(start, end, false, VMSystem.TASK_STATUS_FINISH));

        // 运营工单拒绝总数
        CompletableFuture<Integer> cancelSupplyTotal = CompletableFuture.supplyAsync(() -> this.taskCount(start, end, false, VMSystem.TASK_STATUS_CANCEL));

        // 运营人员总数
        CompletableFuture<Integer> operatorCountFuture = CompletableFuture.supplyAsync(() -> userService.getOperatorCount());


        //运维工单总数
        CompletableFuture<Integer> repairTotalFuture = CompletableFuture.supplyAsync(() -> this.taskCount(start, end, true, null));

        // 运维工单完成总数
        CompletableFuture<Integer> completeRepairTotal = CompletableFuture.supplyAsync(() -> this.taskCount(start, end, true, VMSystem.TASK_STATUS_FINISH));

        // 运维工单拒绝总数
        CompletableFuture<Integer> cancelRepairTotal = CompletableFuture.supplyAsync(() -> this.taskCount(start, end, true, VMSystem.TASK_STATUS_CANCEL));

        // 运维人员总数
        CompletableFuture<Integer> repairCountFuture = CompletableFuture.supplyAsync(() -> userService.getRepairerCount());

        // 并行处理
        CompletableFuture.allOf(
                supplyTotalFuture, cancelSupplyTotal,
                completeSupplyTotal, operatorCountFuture,
                repairTotalFuture, completeRepairTotal,
                cancelRepairTotal, repairCountFuture).join();

        // 获取结果集封装对象
        List<TaskReportInfo> list = Lists.newLinkedList();
        TaskReportInfo supply = new TaskReportInfo(); // 运营
        TaskReportInfo repair = new TaskReportInfo(); // 运维

        try {
            // 运营结果封装
            supply.setTotal(supplyTotalFuture.get());
            supply.setCompleteTotal(completeSupplyTotal.get());
            supply.setRefuseTotal(cancelSupplyTotal.get());
            supply.setIsRepair(false);
            supply.setWorkCount(operatorCountFuture.get());
            list.add(supply);

            // 运维结果封装
            repair.setTotal(repairTotalFuture.get());
            repair.setCompleteTotal(completeRepairTotal.get());
            repair.setRefuseTotal(cancelRepairTotal.get());
            repair.setIsRepair(true);
            repair.setWorkCount(repairCountFuture.get());
            list.add(repair);
        } catch (Exception e) {
            log.error("构建工单统计数据失败", e);
        }
        return list;
    }

    /**
     * 统计工单数量
     *
     * @param start
     * @param end
     * @param isRepair   是否是运维工单
     * @param taskStatus
     * @return
     */
    private int taskCount(LocalDateTime start, LocalDateTime end, Boolean isRepair, Integer taskStatus) {
        LambdaQueryWrapper<TaskEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.ge(TaskEntity::getCreateTime, start).le(TaskEntity::getUpdateTime, end);

        if (taskStatus != null) {
            queryWrapper.eq(TaskEntity::getTaskStatus, taskStatus); // 跳过工单状态 为 null 的工单
        }

        if (isRepair) {
            queryWrapper.ne(TaskEntity::getProductTypeId, VMSystem.TASK_TYPE_SUPPLY); // 是维修工单--查询所有 不是 补货的工单
        } else {
            queryWrapper.eq(TaskEntity::getProductTypeId, VMSystem.TASK_TYPE_SUPPLY);
        }

        return this.count(queryWrapper); // 返回查出的数量
    }

    /**
     * 获取用户工作量详情
     *
     * @param userId
     * @param start
     * @param end
     * @return
     */
    @Override
    public UserWork getUserWork(Integer userId, LocalDateTime start, LocalDateTime end) {
        UserWork userWork = new UserWork();
        userWork.setUserId(userId); // 封装用户id
        UserViewModel user = userService.getUser(userId);
        userWork.setUserName(user.getUserName());
        userWork.setRoleName(user.getRoleName());
        userWork.setMobile(user.getMobile());
        // 并行处理--提高程序吞吐量

        // 当前用户本月总工单数
        CompletableFuture<Integer> totalTask = CompletableFuture.supplyAsync(
                () -> this.getCountByUserId(userId, null, start, end)
        ).whenCompleteAsync((res, err) -> {
            if (err != null) {
                userWork.setTotal(0);
                log.error("---user work error---", err);
            } else {
                userWork.setTotal(res);
            }
        });
        //当前用户本月完成工单
        CompletableFuture<Integer> completableTask = CompletableFuture.supplyAsync(
                        () -> this.getCountByUserId(userId, VMSystem.TASK_STATUS_FINISH, start, end))
                .whenCompleteAsync((res, err) -> {
                    if (err != null) {
                        userWork.setWorkCount(0);
                        log.error("---user work error---", err);
                    } else {
                        userWork.setWorkCount(res);
                    }
                });

        // 当前用户的进行中工单
        CompletableFuture<Integer> progressTask = CompletableFuture.supplyAsync(
                        () -> this.getCountByUserId(userId, VMSystem.TASK_STATUS_PROGRESS, start, end))
                .whenCompleteAsync((res, err) -> {
                    if (err != null) {
                        userWork.setProgressTotal(0);
                        log.error("---user progress error---", err);
                    } else {
                        userWork.setProgressTotal(res);
                    }
                });

        // 当前用户 本月 取消工单
        CompletableFuture<Integer> cancelTask = CompletableFuture.supplyAsync(
                () -> this.getCountByUserId(userId, VMSystem.TASK_STATUS_CANCEL, start, end)
        ).whenCompleteAsync((res, err) -> {
            if (err != null) {
                userWork.setCancelCount(0);
                log.error("---user cancel error---", err);
            } else {
                userWork.setCancelCount(res);
            }
        });

        // 并行处理
        CompletableFuture.allOf(completableTask, progressTask, cancelTask, totalTask).join();
        return userWork;
    }

    //根据工单状态，获取用户(当月)工单数
    private Integer getCountByUserId(Integer userId, Integer taskStatus, LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<TaskEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.ge(TaskEntity::getCreateTime, start).le(TaskEntity::getUpdateTime, end);
        if (taskStatus != null) {
            queryWrapper.eq(TaskEntity::getTaskStatus, taskStatus);
        }
        if (userId != null) {
            queryWrapper.eq(TaskEntity::getAssignorId, userId);
        }
        return this.count(queryWrapper);
    }

    /**
     * 获取排名前10的工作量
     *
     * @param start
     * @param end
     * @return
     */
    @Override
    public List<UserWork> getUserWorkTop10(LocalDate start, LocalDate end, Boolean isRepair, Long regionId) {
        QueryWrapper<TaskEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("count(assignor_id) as assignor_id", "user_name").lambda()
                .ge(TaskEntity::getCreateTime, start).le(TaskEntity::getUserId, end)
                .eq(TaskEntity::getTaskStatus, VMSystem.TASK_STATUS_FINISH)
                .groupBy(TaskEntity::getUserName).orderByDesc(TaskEntity::getAssignorId)
                .last("limit 10");
        if (regionId > 0) {
            queryWrapper.lambda().eq(TaskEntity::getRegionId,regionId);
        }
        if(isRepair){
            queryWrapper.lambda().ne(TaskEntity::getProductTypeId,VMSystem.TASK_TYPE_SUPPLY);
        }else {
            queryWrapper.lambda().eq(TaskEntity::getProductTypeId,VMSystem.TASK_TYPE_SUPPLY);
        }
        List<UserWork> userWorkList = this.list(queryWrapper).stream().map(task -> {
            UserWork userWork = new UserWork();
            userWork.setUserName(task.getUserName());
            userWork.setWorkCount(task.getAssignorId());
            return userWork;
        }).collect(Collectors.toList());
        return userWorkList;
    }
}

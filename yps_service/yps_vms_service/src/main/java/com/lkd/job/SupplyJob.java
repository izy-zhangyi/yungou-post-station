package com.yps.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yps.common.VMSystem;
import com.yps.entity.VendingMachineEntity;
import com.yps.feignService.TaskService;
import com.yps.service.VendingMachineService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import com.xxl.job.core.util.ShardingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 自动补货任务生成 Job
 */
@Component
@Slf4j
public class SupplyJob {

    @Resource
    private VendingMachineService vendingMachineService;

    @Resource
    private TaskService taskService; // 注入工单微服务，从中获取警戒值百分比

    /**
     * 售货机扫描任务
     * 扫描所有运营状态的售货机
     */
    @XxlJob("supplyJobHandler")
    public ReturnT<String> supplyJobHandler(String param) throws Exception {
        //获取分片总数和当前分片索引
        ShardingUtil.ShardingVO shardingVo = ShardingUtil.getShardingVo();
        int total = shardingVo.getTotal(); // 分片总数
        int index = shardingVo.getIndex(); // 当前分片索引
        log.info("分片参数  当前分片索引{}   总分片数{}", index, total);
        XxlJobLogger.log("分片参数  当前分片索引:" + index + "  总分片数:" + total);
        Integer percent = taskService.getSupplyAlertValue();// 获取警戒值百分比

        // 查询所有运营状态的售货机
        LambdaQueryWrapper<VendingMachineEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VendingMachineEntity::getVmStatus, VMSystem.VM_STATUS_RUNNING)
                .apply("mod(id," + total + ")=" + index);
        List<VendingMachineEntity> vmList = vendingMachineService.list(queryWrapper);

        // 遍历所有运营状态的售货机
        vmList.forEach(vm -> {
            XxlJobLogger.log("扫描售货机：" + vm.getInnerCode());
            // 扫描售货机货道逻辑
            int count = vendingMachineService.inventory(percent, vm);//售货机缺货货道数量
            if (count > 0) {
                XxlJobLogger.log("扫描售货机：" + vm.getInnerCode() + "缺货货道数量：" + count);
                // 发送补货消息
                vendingMachineService.sendSupplyTask(vm);
            }
        });
        return ReturnT.SUCCESS;
    }

}

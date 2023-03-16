package com.yps.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yps.annotations.ProcessType;
import com.yps.common.VMSystem;
import com.yps.contract.SupplyCfg;
import com.yps.exception.LogicException;
import com.yps.feignService.VMService;
import com.yps.http.viewModel.TaskDetailsViewModel;
import com.yps.http.viewModel.TaskViewModel;
import com.yps.service.TaskService;
import com.yps.utils.JsonUtil;
import com.yps.viewmodel.VendingMachineViewModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@ProcessType("supplyTask")
public class SupplyTaskHandler implements MsgHandler {
    @Resource
    private VMService vmService;
    @Resource
    private TaskService taskService;

    @Override
    public void process(String jsonMsg) throws IOException {
        try {

            // 解析协议
            SupplyCfg supplyCfg = JsonUtil.getByJson(jsonMsg, SupplyCfg.class);
            if (supplyCfg == null) {
                return;
            }
            //获取售货机信息
            VendingMachineViewModel vmInfo = vmService.getVMInfo(supplyCfg.getInnerCode());

            // 根据售货机所在的区域，找出同天内工单最少的人员
            Integer userId = taskService.getTaskLastUser(String.valueOf(vmInfo.getRegionId()), false);

            // 创建补货工单
            TaskViewModel taskViewModel = new TaskViewModel();
            taskViewModel.setAssignorId(userId);

            taskViewModel.setCreateType(0);
            taskViewModel.setProductType(VMSystem.TASK_TYPE_SUPPLY);
            taskViewModel.setInnerCode(supplyCfg.getInnerCode());
            taskViewModel.setDesc("自动补货工单");

            // 填充补货详情信息
            taskViewModel.setDetails(supplyCfg.getSupplyData().stream().map(c -> {
                // 获取补货详情封装对象
                TaskDetailsViewModel taskDetailsViewModel = new TaskDetailsViewModel();
                taskDetailsViewModel.setChannelCode(c.getChannelId()); // 货道编号
                taskDetailsViewModel.setSkuId(c.getSkuId()); // 商品id
                taskDetailsViewModel.setSkuName(c.getSkuName()); // 商品名称
                taskDetailsViewModel.setSkuImage(c.getSkuImage()); // 商品图片
                taskDetailsViewModel.setExpectCapacity(c.getCapacity()); // 期望补货数量
                return taskDetailsViewModel;
            }).collect(Collectors.toList()));

            // 创建工单
            taskService.createTask(taskViewModel);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("创建自动补货工单出错" + e.getMessage());
        }
    }
}

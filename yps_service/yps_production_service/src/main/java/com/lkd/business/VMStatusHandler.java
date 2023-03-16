package com.yps.business;

import com.yps.annotations.ProcessType;
import com.yps.common.VMSystem;
import com.yps.contract.VmStatusContract;
import com.yps.entity.TaskEntity;
import com.yps.feignService.VMService;
import com.yps.http.viewModel.TaskViewModel;
import com.yps.service.TaskService;
import com.yps.utils.JsonUtil;
import com.yps.viewmodel.VendingMachineViewModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 售货机状态消息的接收处理类，来处理设备消息
 */
@Component
@Slf4j
@ProcessType("vmStatus")
public class VMStatusHandler implements MsgHandler {

    @Resource
    private TaskService taskService;

    @Resource
    private VMService vmService;

    @Override
    public void process(String jsonMsg) throws IOException {
        // 1. 解析协议 --(消息，封装协议类型) -- 集合数据
        VmStatusContract vmStatusContract = JsonUtil.getByJson(jsonMsg, VmStatusContract.class);
        if (vmStatusContract == null
                || vmStatusContract.getStatusInfo() == null
                || vmStatusContract.getStatusInfo().size() <= 0) {
            return;
        }

        // 判断协议中，状态要是有一个false，则直接创建维修工单
        if (vmStatusContract.getStatusInfo().stream().anyMatch(b -> !b.isStatus())) {
            try {
                // 1. 获取售货机对象
                VendingMachineViewModel vmInfo = vmService.getVMInfo(vmStatusContract.getInnerCode());
                // 2. 获取该售货机所在区域内，同天工单量最少的员工
                Integer userId = taskService.getTaskLastUser(String.valueOf(vmInfo.getRegionId()), true);
                // 3. 创建维修工单，指派工单量最少的员工执行
                TaskViewModel taskViewModel = new TaskViewModel();
                taskViewModel.setAssignorId(userId); // 执行人id
                taskViewModel.setInnerCode(vmInfo.getInnerCode());// 售货机编号
                taskViewModel.setProductType(VMSystem.TASK_TYPE_REPAIR); // 维修工单
                taskViewModel.setCreateType(0); // 自动创建工单
                taskViewModel.setDesc("自动创建维修工单");
                // 创建工单
                taskService.createTask(taskViewModel);
            } catch (Exception e) {
                e.printStackTrace();
                log.error("创建自动维修工单失败，msg is:{}", jsonMsg);
            }

        }
    }
}

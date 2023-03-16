package com.yps.business.msgHandler;

import com.google.common.base.Strings;
import com.yps.annotations.ProcessType;
import com.yps.business.MsgHandler;
import com.yps.common.VMSystem;
import com.yps.contract.TaskCompleteContract;
import com.yps.service.VendingMachineService;
import com.yps.utils.JsonUtil;
import com.yps.viewmodel.VMDistance;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.ui.context.ThemeSource;

import javax.annotation.Resource;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.Objects;

/**
 * 工单完成处理
 * ### 消息分发处理架构
 * 基于我们现有的架构，每一个需要处理消息的类只需要添加@ProcessType注解，
 * 并实现MsgHandler接口即可。@ProcessType是一个自定义注解，用于指定消息协议名称
 */
@Component
@ProcessType("taskCompleted") // 通过 processType 注解 去指定要处理的协议 -- 在此为完成工单的协议
public class TaskCompletedMsgHandler implements MsgHandler {

    @Resource
    private VendingMachineService vendingMachineService; // 注入售货机服务

    @Override
    public void process(String jsonMsg) throws IOException {
        // process 可以自动的传递消息的具体内容
        // 1. 将拿到的消息，进行类型转换-- json -- 对象
        TaskCompleteContract taskCompleteContract = JsonUtil.getByJson(jsonMsg, TaskCompleteContract.class);

        // 数据校验
        if(taskCompleteContract == null || Strings.isNullOrEmpty(taskCompleteContract.getInnerCode())){
            return;
        }

        // 如果是投放工单，将售货机修改为运营状态
        if(Objects.deepEquals(taskCompleteContract.getTaskType(), VMSystem.TASK_TYPE_DEPLOY)){
            // 根据 售货机编号 修改售货机状态
            vendingMachineService.updateStatus(taskCompleteContract.getInnerCode(),VMSystem.VM_STATUS_RUNNING);
            // TODO: 保存设备的坐标---保存设备的坐标（数据库+es）
            VMDistance vmDistance = new VMDistance(); // 设备位置信息对象
            BeanUtils.copyProperties(taskCompleteContract,vmDistance);
            vendingMachineService.setVmDistance(vmDistance);// 调用接口中的方法，保存设备的坐标
        }
        // 如果是撤机工单， 将售货机修改为撤机状态
        if(Objects.deepEquals(taskCompleteContract.getTaskType(),VMSystem.TASK_TYPE_REVOKE)){
            // 根据 售货机编号 修改售货机状态
            vendingMachineService.updateStatus(taskCompleteContract.getInnerCode(),VMSystem.VM_STATUS_REVOKE);
        }
    }
}

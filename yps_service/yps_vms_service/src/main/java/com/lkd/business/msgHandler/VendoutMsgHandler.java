package com.yps.business.msgHandler;

import com.google.common.base.Strings;
import com.yps.annotations.ProcessType;
import com.yps.business.MsgHandler;
import com.yps.common.VMSystem;
import com.yps.config.ConsulConfig;
import com.yps.contract.VendoutResp;
import com.yps.service.VendingMachineService;
import com.yps.utils.DistributedLock;
import com.yps.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 处理出货结果
 */
@Component
@ProcessType(value = "vendoutResp")
public class VendoutMsgHandler implements MsgHandler {
    @Autowired
    private VendingMachineService vmService;
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private ConsulConfig consulConfig;

    @Override
    public void process(String jsonMsg) throws IOException {
        boolean success = JsonUtil.getNodeByName("success", jsonMsg).asBoolean();
        if (!success) return;
        VendoutResp vendoutResp = JsonUtil.getByJson(jsonMsg, VendoutResp.class);
        if (Strings.isNullOrEmpty(vendoutResp.getInnerCode())) return;

        //解锁售货机状态
        DistributedLock lock = new DistributedLock(consulConfig.getConsulRegisterHost(), consulConfig.getConsulRegisterPort());
        // 获取锁的id
        String sessionId = redisTemplate.boundValueOps(VMSystem.VM_LOCK_KEY_PREF + vendoutResp.getInnerCode()).get();
        lock.releaseLock(sessionId); // 释放锁
        vmService.vendOutResult(vendoutResp);
    }
}

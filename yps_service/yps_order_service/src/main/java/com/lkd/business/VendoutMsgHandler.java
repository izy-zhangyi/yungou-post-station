package com.yps.business;

import com.yps.annotations.ProcessType;
import com.yps.common.VMSystem;
import com.yps.config.ConsulConfig;
import com.yps.contract.VendoutReq;
import com.yps.contract.VendoutResp;
import com.yps.redis.RedisUtils;
import com.yps.service.OrderService;
import com.yps.utils.DistributedLock;
import com.yps.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 处理出货结果
 */
@Component
@ProcessType(value = "vendoutResp")
public class VendoutMsgHandler implements MsgHandler{

    @Autowired
    private OrderService orderService;
    @Override
    public void process(String jsonMsg) throws IOException {
        VendoutResp vendoutResp = JsonUtil.getByJson(jsonMsg, VendoutResp.class);
        //处理出货结果
        orderService.vendoutResult(vendoutResp);
    }
}

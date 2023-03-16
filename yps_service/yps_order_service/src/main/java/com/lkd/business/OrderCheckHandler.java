package com.yps.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.api.R;
import com.google.common.base.Strings;
import com.yps.annotations.ProcessType;
import com.yps.common.VMSystem;
import com.yps.contract.server.OrderCheck;
import com.yps.entity.OrderEntity;
import com.yps.service.OrderService;
import com.yps.utils.JsonUtil;
import org.checkerframework.checker.index.qual.LTEqLengthOf;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Objects;

/**
 * 超时订单处理
 * //将订单放到延迟队列中，10分钟后检查支付状态
 *         OrderCheck orderCheck = new OrderCheck();
 *         orderCheck.setOrderNo(orderEntity.getOrderNo());
 *         try {
 *             mqttProducer.send("$delayed/600/"+OrderConfig.ORDER_DELAY_CHECK_TOPIC,2,orderCheck);
 */
@Component
@ProcessType("orderCheck")
public class OrderCheckHandler implements MsgHandler {
    @Resource
    private OrderService orderService;

    @Override
    public void process(String jsonMsg) throws IOException {
        OrderCheck orderCheck = JsonUtil.getByJson(jsonMsg, OrderCheck.class); // 解析协议
        if (orderCheck == null || Strings.isNullOrEmpty(orderCheck.getOrderNo())) {
            return;
        }

        // 查询数据
        LambdaQueryWrapper<OrderEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderEntity::getOrderNo, orderCheck.getOrderNo())
                .eq(OrderEntity::getStatus, VMSystem.ORDER_STATUS_CREATE);
        OrderEntity orderEntity = orderService.getOne(queryWrapper);

        if(orderEntity == null || !Objects.deepEquals(orderEntity.getStatus(),VMSystem.ORDER_STATUS_CREATE)){
           //查出的订单实体为 null,--- 订单成功支付，直接返回
            return;
        }

        // 查出相应的订单--该订单创建10分钟之后还是没有付款--修改订单状态--- 10 分钟之后 查到了该创建订单-代表该订单已经是失效状态--修改订单状态
        LambdaUpdateWrapper<OrderEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(OrderEntity::getOrderNo,orderCheck.getOrderNo())
                .set(OrderEntity::getStatus,VMSystem.ORDER_STATUS_INVALID);
        orderService.update(updateWrapper);
    }
}

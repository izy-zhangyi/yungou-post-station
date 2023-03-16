package com.yps.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yps.common.VMSystem;
import com.yps.entity.OrderCollectEntity;
import com.yps.entity.OrderEntity;
import com.yps.feignService.UserService;
import com.yps.feignService.VMService;
import com.yps.service.OrderCollectService;
import com.yps.service.OrderService;
import com.yps.viewmodel.PartnerViewModel;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 订单汇总
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCollectJob {

    private final OrderService orderService;
    private final OrderCollectService orderCollectService;
    private final UserService userService;
    private final VMService vmService;


    @XxlJob("orderCollectJobHandler")
    public ReturnT<String> collectTask(String param) {
        LocalDate yesterday = LocalDate.now().plusDays(-1); // 前一天的时间
        var queryWrapper = new QueryWrapper<OrderEntity>();
        // 根据字段聚合select(聚合字段)
        queryWrapper.select(
                "owner_id", "node_id", "IFNULL(sum(amount),0) as amount", "IFNULL(sum(bill),0) as bill", "IFNULL(count(1),0) as price")
                // 数据库条件查询
                .lambda()
                .ge(OrderEntity::getCreateTime, yesterday)
                .lt(OrderEntity::getCreateTime, LocalDate.now())
                .eq(OrderEntity::getPayStatus, VMSystem.PAY_STATUS_PAYED)
                .groupBy(OrderEntity::getOwnerId, OrderEntity::getNodeId);
        //List<OrderEntity> list = orderService.list(queryWrapper);
        //list.stream().forEach();
        orderService.list(queryWrapper).forEach(order -> {
            OrderCollectEntity orderCollectEntity = new OrderCollectEntity(); // 获取订单统计实体类对象
            orderCollectEntity.setDate(yesterday); // 订单统计时间
            orderCollectEntity.setOwnerId(order.getOwnerId()); // 合作商id
            PartnerViewModel partner = userService.getPartner(order.getOwnerId());
            orderCollectEntity.setOwnerName(partner.getName()); // 合作商name、
            orderCollectEntity.setNodeId(order.getNodeId()); // 点位id
            orderCollectEntity.setNodeName(vmService.getNodeName(order.getNodeId())); // 点位name

            orderCollectEntity.setRatio(partner.getRatio()); // 分成比例
            orderCollectEntity.setTotalBill(order.getBill()); // 分成
            orderCollectEntity.setOrderCount(order.getPrice()); // 订单数量（借用了 price 字段）
            orderCollectEntity.setOrderTotalMoney(order.getAmount()); // 金额
            orderCollectService.save(orderCollectEntity); // 保存数据
        });
        return ReturnT.SUCCESS;
    }
}

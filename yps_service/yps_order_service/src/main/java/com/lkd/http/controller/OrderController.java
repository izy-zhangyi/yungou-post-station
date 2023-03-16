package com.yps.http.controller;

import com.yps.service.OrderService;
import com.yps.viewmodel.OrderViewModel;
import com.yps.viewmodel.Pager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    /**
     * 取消订单
     *
     * @param orderNo
     * @return
     */
    @GetMapping("/cancel/{orderNo}")
    public Boolean cancel(@PathVariable String orderNo) {
        return orderService.cancel(orderNo);
    }

    /**
     * 查询订单
     *
     * @param pageIndex
     * @param pageSize
     * @return
     */
    @GetMapping("/search")
    public Pager<OrderViewModel> search(
            @RequestParam(value = "pageIndex", required = false, defaultValue = "1") Integer pageIndex,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize,
            @RequestParam(value = "orderNo", required = false, defaultValue = "") String orderNo,
            @RequestParam(value = "openId", required = false, defaultValue = "") String openId,
            @RequestParam(value = "startDate", required = false, defaultValue = "") String startDate,
            @RequestParam(value = "endDate", required = false, defaultValue = "") String endData) {
        return orderService.search(pageIndex, pageSize, orderNo, openId, startDate, endData);
    }

    /**
     * 获取商圈下3个月内销量前10商品
     *
     * @param businessId
     * @return
     */
    @GetMapping("/businessTop10/{businessId}")
    public List<Long> getBusinessTop10Sku(@PathVariable("businessId") Integer businessId) {
        return orderService.getTop10Sku(businessId);
    }
}

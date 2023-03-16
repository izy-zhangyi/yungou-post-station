package com.yps.feignService;

import com.yps.feignService.fallback.OrderServiceFallBack;
import com.yps.viewmodel.OrderViewModel;
import com.yps.viewmodel.Pager;
import com.yps.viewmodel.RequestPay;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(value = "order-service", fallbackFactory = OrderServiceFallBack.class)
public interface OrderService {
    // 远程调用方法
    @PostMapping("/wxpay/requestPay")
    public String requestPay(@RequestBody RequestPay requestPay);

    @GetMapping("/order/cancel/{orderNo}")
    Boolean cancel(@PathVariable String orderNo);

    @GetMapping("/order/search")
    Pager<OrderViewModel> search(
            @RequestParam(value = "pageIndex", required = false, defaultValue = "1") Integer pageIndex,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize,
            @RequestParam(value = "orderNo", required = false, defaultValue = "") String orderNo,
            @RequestParam(value = "openId", required = false, defaultValue = "") String openId,
            @RequestParam(value = "startDate", required = false, defaultValue = "") String startDate,
            @RequestParam(value = "endDate", required = false, defaultValue = "") String endDate);
}

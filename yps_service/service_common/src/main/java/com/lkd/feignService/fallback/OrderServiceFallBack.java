package com.yps.feignService.fallback;

import com.yps.feignService.OrderService;
import com.yps.viewmodel.OrderViewModel;
import com.yps.viewmodel.Pager;
import com.yps.viewmodel.RequestPay;
import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 服务降级类  OrderServiceFallbackFactory
 * 用于处理远程调用失败
 */
@Component
@Slf4j
public class OrderServiceFallBack implements FallbackFactory<OrderService> {
    @Override
    public OrderService create(Throwable throwable) {
        log.error("订单服务调用失败", throwable);
        return new OrderService() {
            @Override
            public String requestPay(RequestPay requestPay) {
                return null;
            }

            @Override
            public Boolean cancel(String orderNo) {
                return null;
            }

            @Override
            public Pager<OrderViewModel> search(Integer pageIndex, Integer pageSize, String orderNo, String openId, String startDate, String endDate) {
                return Pager.buildEmpty();
            }
        };
    }
}

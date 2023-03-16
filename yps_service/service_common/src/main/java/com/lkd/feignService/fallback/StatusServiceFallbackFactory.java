package com.yps.feignService.fallback;

import com.yps.feignService.StatusService;
import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StatusServiceFallbackFactory implements FallbackFactory<StatusService> {
    @Override
    public StatusService create(Throwable throwable) {
        log.error("状态服务调用失败",throwable);
        return new StatusService() {
            @Override
            public Boolean getVMStatus(String innerCode) {
                return false;
            }
        };
    }
}

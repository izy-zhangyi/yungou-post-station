package com.yps.feignService;

import com.yps.feignService.fallback.StatusServiceFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "task-service",fallbackFactory = StatusServiceFallbackFactory.class)
public interface StatusService {
    @GetMapping("/status/vmStatus/{innerCode}")
    Boolean getVMStatus(@PathVariable("innerCode") String innerCode);
}

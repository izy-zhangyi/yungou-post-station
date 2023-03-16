package com.yps.feignService;

import com.yps.feignService.fallback.TaskServiceFallbackFactory;
import com.yps.viewmodel.UserWork;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "task-service",fallbackFactory = TaskServiceFallbackFactory.class)
public interface TaskService {
    @GetMapping("/task/supplyAlertValue")
    Integer getSupplyAlertValue();

    @GetMapping("/task/userWork")
    UserWork getUserWork(@RequestParam Integer userId, @RequestParam String start, @RequestParam String end);

}

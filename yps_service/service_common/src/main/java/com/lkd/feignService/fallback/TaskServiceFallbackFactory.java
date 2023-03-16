package com.yps.feignService.fallback;

import com.yps.feignService.TaskService;
import com.yps.viewmodel.UserWork;
import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TaskServiceFallbackFactory implements FallbackFactory<TaskService> {
    @Override
    public TaskService create(Throwable throwable) {
        log.error("调用工单服务失败",throwable);

        return new TaskService() {
            @Override
            public Integer getSupplyAlertValue() {
                return 0;
            }

            @Override
            public UserWork getUserWork(Integer userId, String start, String end) {
                UserWork userWork = new UserWork();
                userWork.setUserId(userId);
                userWork.setWorkCount(0);
                userWork.setCancelCount(0);
                userWork.setProgressTotal(0);
                userWork.setTotal(0);
                return userWork;
            }

        };
    }
}

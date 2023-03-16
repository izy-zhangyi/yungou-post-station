package com.yps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yps.entity.TaskDetailsEntity;

import java.util.List;

public interface TaskDetailsService extends IService<TaskDetailsEntity>{
    /**
     * 获取工单详情
     * @param taskId
     * @return
     */
    List<TaskDetailsEntity> getByTaskId(long taskId);
}

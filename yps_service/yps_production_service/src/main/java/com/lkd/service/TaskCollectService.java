package com.yps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yps.entity.TaskCollectEntity;

import java.time.LocalDate;
import java.util.List;

public interface TaskCollectService extends IService<TaskCollectEntity>{
    /**
     * 获取工单报表
     * @param start
     * @param end
     * @return
     */
    List<TaskCollectEntity> getTaskReport(LocalDate start, LocalDate end);
}

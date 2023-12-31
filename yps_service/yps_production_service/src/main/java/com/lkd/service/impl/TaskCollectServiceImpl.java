package com.yps.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yps.dao.TaskCollectDao;
import com.yps.entity.TaskCollectEntity;
import com.yps.service.TaskCollectService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class TaskCollectServiceImpl extends ServiceImpl<TaskCollectDao, TaskCollectEntity> implements TaskCollectService {
    @Override
    public List<TaskCollectEntity> getTaskReport(LocalDate start, LocalDate end) {
        QueryWrapper<TaskCollectEntity> qw = new QueryWrapper<>();
        qw
                .lambda()
                .ge(TaskCollectEntity::getCollectDate,start)
                .le(TaskCollectEntity::getCollectDate,end)
                .orderByAsc(TaskCollectEntity::getCollectDate);

        return this.list(qw);
    }
}

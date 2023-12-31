package com.yps.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yps.dao.TaskDetailsDao;
import com.yps.entity.TaskDetailsEntity;
import com.yps.service.TaskDetailsService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskDetailsServiceImpl extends ServiceImpl<TaskDetailsDao,TaskDetailsEntity> implements TaskDetailsService{
    @Override
    public List<TaskDetailsEntity> getByTaskId(long taskId) {

        QueryWrapper<TaskDetailsEntity> qw = new QueryWrapper<>();
        qw.lambda()
                .eq(TaskDetailsEntity::getTaskId,taskId);

        return this.list(qw);
    }
}

package com.yps.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yps.dao.TaskStatusTypeDao;
import com.yps.entity.TaskStatusTypeEntity;
import com.yps.service.TaskStatusTypeService;
import org.springframework.stereotype.Service;

@Service
public class TaskStatusTypeServiceImpl extends ServiceImpl<TaskStatusTypeDao,TaskStatusTypeEntity> implements TaskStatusTypeService{
}
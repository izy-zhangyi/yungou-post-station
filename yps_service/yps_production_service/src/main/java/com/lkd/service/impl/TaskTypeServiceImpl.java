package com.yps.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yps.dao.TaskTypeDao;
import com.yps.entity.TaskTypeEntity;
import com.yps.service.TaskTypeService;
import org.springframework.stereotype.Service;

@Service
public class TaskTypeServiceImpl extends ServiceImpl<TaskTypeDao,TaskTypeEntity> implements TaskTypeService{
}

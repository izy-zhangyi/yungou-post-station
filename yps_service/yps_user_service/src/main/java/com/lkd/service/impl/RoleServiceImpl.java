package com.yps.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yps.dao.RoleDao;
import com.yps.entity.RoleEntity;
import com.yps.service.RoleService;
import org.springframework.stereotype.Service;

@Service
public class RoleServiceImpl extends ServiceImpl<RoleDao,RoleEntity> implements RoleService{
}

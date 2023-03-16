package com.yps.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yps.dao.BusinessTypeDao;
import com.yps.entity.BusinessTypeEntity;
import com.yps.service.BusinessTypeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BusinessTypeServiceImpl extends ServiceImpl<BusinessTypeDao, BusinessTypeEntity> implements BusinessTypeService {
}

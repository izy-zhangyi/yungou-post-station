package com.yps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yps.entity.StatusTypeEntity;

public interface StatusTypeService extends IService<StatusTypeEntity> {
    String getByCode(String code);
}

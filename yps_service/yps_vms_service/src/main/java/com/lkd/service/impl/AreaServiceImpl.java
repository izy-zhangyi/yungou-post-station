package com.yps.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yps.dao.AreaDao;
import com.yps.entity.AreaEntity;
import com.yps.service.AreaService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AreaServiceImpl extends ServiceImpl<AreaDao,AreaEntity> implements AreaService{
    @Override
    public List<AreaEntity> getAllRootAreaList() {
        return this.getAllChildren(0);
    }

    @Override
    public List<AreaEntity> getAllChildren(int parentId) {
        QueryWrapper<AreaEntity> qw = new QueryWrapper<>();
        qw.lambda().eq(AreaEntity::getParentId,parentId);

        return this.list(qw);
    }
}

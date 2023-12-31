package com.yps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yps.entity.VendoutRunningEntity;
import com.yps.viewmodel.Pager;

import java.util.List;
import java.util.Map;

public interface VendoutRunningService extends IService<VendoutRunningEntity> {
    /**
     * 条件查询
     * @param searchMap
     * @return
     */
    List<VendoutRunningEntity> findList(Map searchMap);

    /**
     * 分页查询
     * @param pageIndex
     * @param pageSize
     * @param searchMap
     * @return
     */
    Pager<VendoutRunningEntity> findPage(long pageIndex, long pageSize, Map searchMap);
}

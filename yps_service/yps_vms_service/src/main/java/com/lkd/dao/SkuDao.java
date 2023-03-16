package com.yps.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yps.entity.SkuEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SkuDao extends BaseMapper<SkuEntity> {
    @Results(id = "skuMap",
            value = {@Result(column = "sku_id",property = "skuId",id = true),
                    @Result(column = "class_id",property = "classId"),
                    @Result(column = "class_id",property = "skuClass",one = @One(select = "com.yps.dao.SkuClassDao.selectById")),
            })
    @Select("select * from tb_sku where sku_id=#{skuId} limit 1")
    SkuEntity getById(long skuId);
}

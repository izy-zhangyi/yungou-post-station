package com.yps.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yps.entity.RegionEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RegionDao extends BaseMapper<RegionEntity> {
    @Results(id = "regionMap",value = {
            @Result(property = "id",column = "id"),
            @Result(property = "nodeCount",column = "id",one = @One(select = "com.yps.dao.NodeDao.getCountByRegion"))
    })
    @Select("select * from tb_region where id=#{id}")
    RegionEntity getById(Long id);
}

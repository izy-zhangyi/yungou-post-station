package com.yps.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yps.entity.JobEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobDao extends BaseMapper<JobEntity>{
}

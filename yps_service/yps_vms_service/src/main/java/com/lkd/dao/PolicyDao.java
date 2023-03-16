package com.yps.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yps.entity.PolicyEntity;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface PolicyDao extends BaseMapper<PolicyEntity> {
}

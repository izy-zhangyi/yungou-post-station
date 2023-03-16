package com.yps.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yps.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface OrderDao extends BaseMapper<OrderEntity> {
}

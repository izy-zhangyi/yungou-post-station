package com.yps.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yps.config.RedisDefinetion;
import com.yps.dao.StatusTypeDao;
import com.yps.entity.StatusTypeEntity;
import com.yps.service.StatusTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class StatusTypeServiceImpl extends ServiceImpl<StatusTypeDao,StatusTypeEntity> implements StatusTypeService{
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;


    @Override
    public String getByCode(String code) {
        List<Object> statusTypeEntityList = redisTemplate.opsForList().range(RedisDefinetion.STATUS_TYPE_CODE_PREFIX,0,-1);
        AtomicReference<String> desc = new AtomicReference<>("");
        statusTypeEntityList.forEach(s->{
            StatusTypeEntity statusTypeEntity = (StatusTypeEntity)s;
            if(statusTypeEntity.getStatusCode().equals(code)){
                desc.set(statusTypeEntity.getDescr());
            }
        });

        return desc.get();
    }

}

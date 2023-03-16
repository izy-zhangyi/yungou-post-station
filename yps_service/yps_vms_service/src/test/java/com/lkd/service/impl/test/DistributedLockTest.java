package com.yps.service.impl.test;

import com.yps.config.ConsulConfig;
import com.yps.utils.DistributedLock;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

@SpringBootTest
@RunWith(SpringRunner.class)
public class DistributedLockTest {

    @Resource
    private ConsulConfig consulConfig;

    /**
     * DistributedLock 是分布式锁处理类，
     * 是封装了基于consul的分布式锁，getLock用于获取锁，releaseLock用于解锁。
     */
    @Test
    public void testGetLock(){
        DistributedLock lock = new DistributedLock(consulConfig.getConsulRegisterHost(), consulConfig.getConsulRegisterPort());
        DistributedLock.LockContext lockLock = lock.getLock("aaa", 30);
        System.out.println(lockLock.getSession());
        System.out.println(lockLock.isGetLock());
    }

    @Test
    public void releaseLock(){
        DistributedLock lock = new DistributedLock(consulConfig.getConsulRegisterHost(), consulConfig.getConsulRegisterPort());
        lock.releaseLock("e1ed1f59-be21-1689-75bf-d0b224f6cec1");
    }
}

package com.yps.job;

import com.yps.common.VMSystem;
import com.yps.entity.UserEntity;
import com.yps.service.UserService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
public class UserJob {
    @Resource
    private UserService userService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

/*    // 简单
    @XxlJob("testHandler")
    public ReturnT<String> testHandler(String param) throws Exception {
        log.info("立可得集成 xxl-job");
        return ReturnT.SUCCESS;
    }*/

    /**
     * 每日工单量初始化
     *
     * @param param
     * @return
     * @throws Exception
     */
    @XxlJob("workCountInitJobHandler")
    public ReturnT<String> workCountInitJobHandler(String param) throws Exception {
        // 1. 获取 该售货机所在区域内的 所有的的 人员
        List<UserEntity> userList = userService.list();

        // 2. 过滤管理员，只留运营，运维人员
        userList.stream().filter(user -> user.getRoleId() != 1).forEach(user -> {
            // 1. 设置key: 固定前缀+第二天时间+区域id+人员编码-: 1002 运营 1003 运维
            String key = VMSystem.REGION_TASK_KEY_PREF
                    + LocalDate.now().plusDays(0).format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "."
                    + user.getRegionId() + "." + user.getRoleCode();

            // 2. 发送数据到redis中 -- (指定的大key,指定的userid，填充的初始数据)
            redisTemplate.opsForZSet().add(key,user.getId(),0);
            // 2.1 指定key的过期时间
            redisTemplate.expire(key,Duration.ofDays(2));
        });
        return ReturnT.SUCCESS;
    }
}

package com.yps.service.impl;

import com.yps.common.VMSystem;
import com.yps.http.viewModel.LoginReq;
import com.yps.http.viewModel.LoginResp;
import com.yps.service.UserService;
import com.yps.utils.BCrypt;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.Duration;

@RunWith(SpringRunner.class)
@SpringBootTest
public class UserServiceImplTest {
    @Autowired
    private UserService userService;
    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    @Test
    public void login() throws IOException {
        redisTemplate.opsForValue().set("13900000000","11111", Duration.ofMinutes(5));
        LoginReq loginReq = new LoginReq();
        loginReq.setLoginType(VMSystem.LOGIN_EMP);
        loginReq.setMobile("13900000000");
        loginReq.setCode("11111");
        LoginResp resp = userService.login(loginReq);

        System.out.println(resp);
    }

    @Test
    public void generatePwd(){
        String pwd = BCrypt.hashpw("admin",BCrypt.gensalt());
        System.out.println(pwd);
        //$2a$10$xykah91CBBmrNTljPMZakOVjoiKltyzxUbsJHU1QuDPptma4NCcG6
    }
}
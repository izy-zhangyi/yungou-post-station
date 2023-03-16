package com.yps.http.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.api.R;
import com.google.common.base.Strings;
import com.yps.entity.VendingMachineEntity;
import com.yps.service.VendingMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/acl")
@Slf4j
public class AclController {

    private final VendingMachineService vendingMachineService;

    /**
     * 连接控制
     *
     * @param clientid
     * @param username
     * @param password
     * @return
     */
    @PostMapping("/auth")
    public ResponseEntity<?> auth(@RequestParam(value = "clientid", required = false, defaultValue = "") String clientid,
                                  @RequestParam(value = "username", required = false, defaultValue = "") String username,
                                  @RequestParam(value = "password", required = false, defaultValue = "") String password) {

        log.info("客户端连接认证" + clientid);
        if (Strings.isNullOrEmpty(clientid)) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }

        if (clientid.startsWith("mqtt")) { // 测试连接
            return new ResponseEntity<>(null, HttpStatus.OK);
        }
        if (clientid.startsWith("monitor")) { // 服务器连接
            return new ResponseEntity<>(null, HttpStatus.OK);
        }
        VendingMachineEntity vendingMachine = getVendingMachineEntity(clientid);
        if (vendingMachine == null) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(null, HttpStatus.OK);
    }

    /**
     * 超级用户认证
     *
     * @param clientid
     * @param username
     * @return
     */
    @PostMapping("/superuser")
    public ResponseEntity<?> supperUser(@RequestParam(value = "clientid", required = false, defaultValue = "") String clientid,
                                        @RequestParam(value = "username", required = false, defaultValue = "") String username) {

        log.info("超级用户认证：{}", clientid);
        if (clientid.startsWith("monitor")) {
            log.info(clientid + " is superuser！！！");
            return new ResponseEntity<>(null, HttpStatus.OK);
        }
        if (clientid.startsWith("mqtt")) {
            log.info(clientid + " is superuser！！！");
            return new ResponseEntity<>(null, HttpStatus.OK);
        }
        log.info(clientid + "不是超级用户！！！");
        return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
    }

    /**
     * 发布订阅控制
     *
     * @param access   动作2：publish、1：subscribe
     * @param topic
     * @param clientId
     * @return
     */
    @PostMapping("/pubsub")
    public ResponseEntity<?> pubsub(@RequestParam(value = "access", defaultValue = "") int access,
                                    @RequestParam(value = "topic", defaultValue = "") String topic,
                                    @RequestParam(value = "clientid", defaultValue = "") String clientId) {

        log.info("acl 发布订阅 clientId:{},access:{},topic:{}", clientId, access, topic);
        if (Strings.isNullOrEmpty(clientId)) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        VendingMachineEntity vendingMachine = getVendingMachineEntity(clientId);
        if(vendingMachine == null){
            return new ResponseEntity<>(null,HttpStatus.BAD_REQUEST);
        }

        // 订阅
        if(access == 1 && topic.equals("vm/"+vendingMachine.getInnerCode())){
            return new ResponseEntity<>(null, HttpStatus.OK);
        }
        // 发布
        if(access ==2&& topic.equals("server/"+vendingMachine.getInnerCode())){
            return new ResponseEntity<>(null,HttpStatus.OK);
        }

        log.error(clientId+"不能发布订阅消息");
        return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
    }

    private VendingMachineEntity getVendingMachineEntity(String clientId) {
        LambdaQueryWrapper<VendingMachineEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VendingMachineEntity::getClientId, clientId);
        return vendingMachineService.getOne(queryWrapper);
    }
}
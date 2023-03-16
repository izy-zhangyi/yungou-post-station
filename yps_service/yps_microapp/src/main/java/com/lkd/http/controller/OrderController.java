package com.yps.http.controller;

import com.google.common.base.Strings;
import com.yps.common.VMSystem;
import com.yps.config.ConsulConfig;
import com.yps.exception.LogicException;
import com.yps.feignService.OrderService;
import com.yps.feignService.VMService;
import com.yps.service.WxService;
import com.yps.utils.DistributedLock;
import com.yps.viewmodel.OrderViewModel;
import com.yps.viewmodel.Pager;
import com.yps.viewmodel.RequestPay;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.Duration;

@RestController
@RequestMapping("/order")
@Slf4j
public class OrderController {

    @Autowired
    private WxService wxService;

    @Resource
    private OrderService orderService;

    @Resource
    private VMService vmService;
    @Resource
    private ConsulConfig consulConfig;
    @Resource
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 获取openId
     *
     * @param jsCode
     * @return
     */
    @GetMapping("/openid/{jsCode}")
    public String getOpenid(@PathVariable String jsCode) {
        return wxService.getOpenId(jsCode);
    }

    /**
     * 小程序请求支付
     *
     * @param requestPay 请求支付对象-- 封装了前端传回来的一些数据
     * @return
     */
    @PostMapping("/requestPay")
    public String requestPay(@RequestBody RequestPay requestPay) {

        if (!vmService.hasCapacity(requestPay.getInnerCode(), requestPay.getInnerCode())) {
            throw new LogicException("库存不足！");
        }

        // 如果openId为空，则根据jsCode生成
        if (Strings.isNullOrEmpty(requestPay.getOpenId())) {
//            String jsCode = requestPay.getJsCode(); // 从支付请求中获取 前端返回的 JSON 数据
//            wxService.getOpenId(requestPay.getJsCode()); // 调用微信服务接口，从 JSON 中获取openId
            requestPay.setOpenId(wxService.getOpenId(requestPay.getJsCode())); // 填充openid
        }

        // 加锁--分布式锁，机器同一时间只能处理一次出货 -- 获取锁对象
        DistributedLock lock = new DistributedLock(consulConfig.getConsulRegisterHost(), consulConfig.getConsulRegisterPort());
        DistributedLock.LockContext lockLock = lock.getLock(requestPay.getInnerCode(), 60); // 加锁 锁id-售货机id，60s超时自动释放锁
        if (!lockLock.isGetLock()) {
            throw new LogicException("机器出货中请稍后再试"); // 获取得到的锁为 false
        }
        // 将获取到的锁存入到 redis 中 boundValueOps(key).set(value)
        redisTemplate.boundValueOps(VMSystem.VM_LOCK_KEY_PREF + requestPay.getInnerCode())
                .set(lockLock.getSession(), Duration.ofSeconds(60));

        String responseData = orderService.requestPay(requestPay); // 获取成功的响应结果
        if (Strings.isNullOrEmpty(responseData)) {
            // 如果响应的数据为空
            throw new LogicException("微信支付接口调用失败！！！");
        }
        // 返回响应数据
        return responseData;
    }

    /**
     * 取消订单
     *
     * @param innerCode
     */
    @GetMapping("/cancel/{innerCode}/{orderNo}")
    public void cancel(@PathVariable("innerCode") String innerCode, @PathVariable("orderNo") String orderNo) {
        // 取消订单--释放锁
        DistributedLock lock = new DistributedLock(consulConfig.getConsulRegisterHost(), consulConfig.getConsulRegisterPort());
        String sessionId = redisTemplate.boundValueOps(VMSystem.VM_LOCK_KEY_PREF + innerCode).get();
        if (Strings.isNullOrEmpty(sessionId)) {
            return;
        }

        try {
            lock.releaseLock(sessionId);
            orderService.cancel(orderNo);
        } catch (Exception e) {
            log.error("取消订单出错", e);
        }
    }

    /**
     * 订单搜索
     *
     * @param pageIndex
     * @param pageSize
     * @param orderNo
     * @param openId
     * @param startDate
     * @param endDate
     * @return
     */
    @GetMapping("/search")
    public Pager<OrderViewModel> search(
            @RequestParam(value = "pageIndex", required = false, defaultValue = "1") Integer pageIndex,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize,
            @RequestParam(value = "orderNo", required = false, defaultValue = "") String orderNo,
            @RequestParam(value = "openId", required = false, defaultValue = "") String openId,
            @RequestParam(value = "startDate", required = false, defaultValue = "") String startDate,
            @RequestParam(value = "endDate", required = false, defaultValue = "") String endDate) {
        return orderService.search(pageIndex, pageSize, orderNo, openId, startDate, endDate);
    }
}
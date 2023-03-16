package com.yps.http.controller;

import com.yps.entity.OrderEntity;
import com.yps.service.OrderService;
import com.yps.service.WXPayService;
import com.yps.utils.ConvertUtils;
import com.yps.viewmodel.CreateOrder;;
import com.yps.viewmodel.OrderViewModel;
import com.yps.viewmodel.RequestPay;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 微信支付
 */
@RestController
@RequestMapping("/wxpay")
@Slf4j
public class WxPayController {
    @Resource
    private OrderService orderService;
    @Resource
    private WXPayService wxPayService;

    @PostMapping("/requestPay")
    public String requestPay(@RequestBody RequestPay requestPay){
        CreateOrder order = new CreateOrder(); // 1. 订单封装实体对象

        BeanUtils.copyProperties(requestPay,order);
        order.setPayType("2"); // 支付方式 1-支付宝支付  2-微信支付
        //OrderEntity orderEntity = orderService.createOrder(order); // 创建订单
        // todo 调用发起支付请求
        //return wxPayService.requestPay(orderEntity.getOrderNo());
        return  wxPayService.requestPay(orderService.createOrder(order).getOrderNo());
    }

    /**
     * 微信支付回调接口
     * @param request
     * @return
     */

    @RequestMapping("/payNotify")
    @ResponseBody
    public void payNotify(HttpServletRequest request, HttpServletResponse response){
        // 1.输入流转换成 xml 字符串
        try {
            String xml = ConvertUtils.convertToString(request.getInputStream());
            wxPayService.notifyPay(xml);

            // 2.支付成功--给微信一个成功的响应
            response.setContentType("text/xml");
            String data =  "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";
           response.getWriter().write(data); // 在前端页面上显示--写到前端页面上
        } catch (Exception e) {
            log.error("支付回调处理失败",e);
        }
    }
}

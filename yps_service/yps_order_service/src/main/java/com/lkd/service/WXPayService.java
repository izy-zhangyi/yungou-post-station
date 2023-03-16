package com.yps.service;

public interface WXPayService {
    /**
     * 调用统一下单接口发起支付
     * @param orderNo 订单编号
     * @return
     */
    String requestPay(String orderNo);

    /**
     * 微信回调之后的处理
     * @param notifyResult 通知结果
     */
    void notifyPay(String notifyResult);
}

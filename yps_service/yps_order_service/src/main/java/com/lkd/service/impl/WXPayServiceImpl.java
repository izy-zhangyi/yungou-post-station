package com.yps.service.impl;

import com.github.wxpay.sdk.WXPayRequest;
import com.github.wxpay.sdk.WXPayUtil;
import com.github.wxpay.sdk.WXPayXmlUtil;
import com.github.wxpay.sdk.WxPaySdkConfig;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.yps.common.VMSystem;
import com.yps.conf.WXConfig;
import com.yps.entity.OrderEntity;
import com.yps.exception.LogicException;
import com.yps.service.OrderService;
import com.yps.service.WXPayService;
import com.yps.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class WXPayServiceImpl implements WXPayService {
    @Resource
    private OrderService orderService;
    @Resource
    private WxPaySdkConfig wxPaySdkConfig;
    @Resource
    private WXConfig wxConfig;

    @Override
    public String requestPay(String orderNo) {
        OrderEntity orderEntity = orderService.getByOrderNo(orderNo); // 1.获取封装 订单实体对象

        try {
            String nonceStr = WXPayUtil.generateNonceStr(); // 获取随机字符串
            // 1. 封装请求参数
            Map<String, String> map = new HashMap<>();
            map.put("appid", wxPaySdkConfig.getAppID()); // 小程序id
            map.put("mch_id", wxPaySdkConfig.getMchID()); // 商户id
            map.put("nonce_str", nonceStr); //随机字符串
            map.put("body", orderEntity.getSkuName()); //商品描述
            map.put("out_trade_no", orderNo); // 订单号
            map.put("total_fee", orderEntity.getAmount() + " "); //金额
            map.put("spbill_create_ip", "127.0.0.1"); //终端地址
            map.put("notify_url", wxConfig.getNotifyUrl()); // 回调地址
            map.put("trade_type", "JSAPI"); // 交易类型
            map.put("openid", orderEntity.getOpenId()); // 用户id

            /**
             * 至关重要的签名，在封装参数的最后一步进行，根据微信官方提供的工具类WXPayUtil可以实现；
             * 调用微信接口，需要传xml格式的参数，可以使用**WXPayUtil.generateSignedXml(map, partnerKey)
             * 方法将HashMap类型的参数转换为xml类型，顺便将传入的参数按，秘钥加密封装进一个签名，
             * 调用该方法得出的参数就是带有签名的xml类型参数了。
             */
            String xmlParam = WXPayUtil.generateSignedXml(map, wxPaySdkConfig.getKey());

            // 2. 发起请求 -- 获取微信支付请求对象--传入参数--wx支付Sdk配置
            WXPayRequest wxPayRequest = new WXPayRequest(wxPaySdkConfig);
            // 2.2 获取带证书的请求结果
            // (url:固定-官方已给出，uuid:可以不填，data:封装的请求参数按照密钥加密获取到的新的对象，自动报告:boolean:false)
            String xmlResult = wxPayRequest.requestWithCert("/pay/unifiedorder", null, xmlParam, false);

            // 3.解析请求结果
            Map<String, String> mapResult = WXPayUtil.xmlToMap(xmlResult);
            String returnCode = mapResult.get("return_code"); // 获取返回状态码
            //返回移动端需要的封装参数
            Map<String, String> response = new HashMap<>();
            if (Objects.deepEquals(returnCode, "SUCCESS")) {
                // 业务结果
                String prepayId = mapResult.get("prepay_id"); // 获取预付订单信息
                if (Strings.isNullOrEmpty(prepayId)) {
                    log.error("prepay_id is null:{}", "当前订单可能已经被支付");
                    throw new LogicException("当前订单可能已经被支付");
                }
                response.put("appid", wxConfig.getAppId());
                response.put("package", "prepay_id=" + prepayId); //预支付交易会话标识
                //response.put("mch_id", wxConfig.getMchId());// 商户号
                response.put("nonce_str", WXPayUtil.generateNonceStr()); // 随机字符串
                response.put("sign", "MD5"); // 签名
                //要将返回的时间戳转化成字符串，不然小程序端调用wx.requestPayment方法会报签名错误
                response.put("timeStamp", System.currentTimeMillis() + " ");

                // 再次签名，--这个签名 主要 用于 小程序端调用 wx.requestPayment方法
                String signedXml = WXPayUtil.generateSignedXml(response, wxConfig.getPartnerKey());
                response.put("paySign", signedXml);
                response.put("appid", "");
                response.put("orderNo", orderNo);
                // 返回结果给前端小程序
                return JsonUtil.serialize(response);

            } else {
                log.error("调用微信统一下单接口失败:{}", response);
                return null;
            }
        } catch (Exception e) {
            log.error("调用微信统一下单接口失败", e);
            return "";
        }
    }

    @Override
    public void notifyPay(String notifyResult) {

        try {
            // 1.解析结果
            Map<String, String> map = WXPayUtil.xmlToMap(notifyResult);

            // 2.校验结果中的签名
            boolean signatureValid = WXPayUtil.isSignatureValid(map, wxConfig.getPartnerKey());
            if (signatureValid) {
                // 签名校验通过-- 返回的结果状态码校验
                if (Objects.deepEquals("SUCCESS", map.get("result_code"))) {
                    // 从结果中获取订单号
                    String tradeNo = map.get("out_trade_no"); // 对外贸易编号
                    OrderEntity orderEntity = orderService.getByOrderNo(tradeNo); // 获取订单实体类对象
                    orderEntity.setStatus(VMSystem.ORDER_STATUS_PAYED); // 订单状态--支付完成
                    orderEntity.setPayStatus(VMSystem.PAY_STATUS_PAYED); // 支付状态--支付完成
                    // 修改数据库数据
                    orderService.updateById(orderEntity);
                    //todo :支付完成通知出货 --发送消息到emq
                    orderService.payComplete(orderEntity.getOrderNo());
                }
                log.error("支付回调出错:" + notifyResult);
            }
            log.error("支付回调验签失败:" + notifyResult);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

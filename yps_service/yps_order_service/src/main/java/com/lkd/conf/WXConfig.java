package com.yps.conf;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 读取小程序配置
 */
@Component
@ConfigurationProperties("wxpay")
@Data
public class WXConfig {
    private String appId; // 小程序id
    private String appSecret; // 密钥
    private String mchId; //商户号
    private String partnerKey; //商户号秘钥
    private String notifyUrl = ""; // 微信支付成功之后的回调地址
}

package com.yps.config;

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
}

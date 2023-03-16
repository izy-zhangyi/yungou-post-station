package com.github.wxpay.sdk;

import com.yps.conf.WXConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.InputStream;

@Component
public class WxPaySdkConfig extends WXPayConfig {
    @Resource
    private WXConfig wxConfig;

    @Override
    public String getAppID() {
        return wxConfig.getAppId();
    }

    @Override
    public String getMchID() {
        return wxConfig.getMchId();
    }

    @Override
    public String getKey() {
        return wxConfig.getPartnerKey();
    }

    @Override
    InputStream getCertStream() {
        return null;
    }

    @Override
    IWXPayDomain getWXPayDomain() {
        return new IWXPayDomain() {
            @Override
            public void report(String s, long l, Exception e) {

            }

            @Override
            public DomainInfo getDomain(WXPayConfig wxPayConfig) {
                return new DomainInfo("api.mch.weixin.qq.com", true);
            }
        };
    }
}

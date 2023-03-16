package com.yps.service.impl;

import com.google.common.base.Strings;
import com.yps.config.WXConfig;
import com.yps.service.WxService;
import com.yps.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class WxServiceImpl implements WxService {

    @Autowired
    private WXConfig wxConfig;

    /**
     * openId是用户在当前公众号（小程序）下的唯一标识（‘身份证’），就是说通过这个openId，就能区分在这个公众号（小程序）下具体是哪个用户。
     *
     * 官方提供了http接口地址为：
     * https://api.weixin.qq.com/sns/jscode2session?appid=APPID&secret=SECRET&js_code=
     * JSCODE&grant_type=authorization_code
     *
     * 这是一个 HTTPS 接口，开发者服务器使用**登录凭证 code获取 session_key 和 openid。
     * 其中 session_key 是对用户数据进行[加密签名](https://www.w3cschool.cn/weixinapp/weixinapp-signature.html)的密钥。
     * 为了自身应用安全，**session_key 不应该在网络上传输。
     * @param jsCode
     * @return
     */
    @Override
    public String getOpenId(String jsCode) {
        // 1.获取 openid url
        String getOpenIdUrl = "https://api.weixin.qq.com/sns/jscode2session?appid="+wxConfig.getAppId()
                +"&secret="+wxConfig.getAppSecret()+"&js_code="+jsCode+"&grant_type=authorization_code";
        RestTemplate restTemplate = new RestTemplate(); // 请求接口的类
        // 获取请求结果-- 从请求接口对象中，调用getForObject方法(openIdUrl,要转换到的类型) 获取请求结果
        String respResult = restTemplate.getForObject(getOpenIdUrl,String.class);

        log.info("weixin pay result:"+respResult);
        if( Strings.isNullOrEmpty(respResult)) return "";
        try{
            // 请求路径不为空 -- 从 JSON 字符串中获取 错误码
            String errorCode = JsonUtil.getValueByNodeName("errcode",respResult) ;
            if(!Strings.isNullOrEmpty(errorCode)){
                // 将错误码类型转为 int 类型
                int errorCodeInt = Integer.valueOf(errorCode).intValue();
                if(errorCodeInt != 0) return "";
            }

            // 返回读取到的openid-用户身份id
            return JsonUtil.getValueByNodeName("openid",respResult);
        }catch (Exception ex){
            log.error("获取openId失败",ex);
            return "";
        }
    }
}

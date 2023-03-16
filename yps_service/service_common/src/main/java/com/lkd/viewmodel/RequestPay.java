package com.yps.viewmodel;

import lombok.Data;

@Data
public class RequestPay {
    private String innerCode; // 售货机编号
    private String openId; // 用户openId
    private String jsCode; //小程序端返回信息
    private String skuId; // 商品id
}

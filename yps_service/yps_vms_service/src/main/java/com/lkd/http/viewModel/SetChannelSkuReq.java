package com.yps.http.viewModel;

import lombok.Data;

@Data
public class SetChannelSkuReq{
    private String innerCode;
    private String channelCode;
    private String skuId;
}

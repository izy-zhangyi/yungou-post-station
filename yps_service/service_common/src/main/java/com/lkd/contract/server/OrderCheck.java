package com.yps.contract.server;

import com.yps.contract.AbstractContract;
import lombok.Data;

import java.io.Serializable;

@Data
public class OrderCheck extends AbstractContract implements Serializable {
    public OrderCheck(){
        this.setMsgType("orderCheck");
    }
    private String orderNo;
}

package com.yps.contract;

import lombok.Data;

@Data
public class VmStatusResp extends BaseContract{
    public VmStatusResp() {
        this.setMsgType("vmStatusResp");
    }
}

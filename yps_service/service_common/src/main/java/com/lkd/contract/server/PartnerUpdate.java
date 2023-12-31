package com.yps.contract.server;

import com.yps.contract.AbstractContract;
import lombok.Data;

import java.io.Serializable;

@Data
public class PartnerUpdate extends AbstractContract implements Serializable {
    public PartnerUpdate() {
        this.setMsgType("partnerUpdate");
    }

    private Integer id;
    private String name;
}

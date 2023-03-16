package com.yps.viewmodel;

import lombok.Data;

import java.io.Serializable;

@Data
public class VmSearch implements Serializable {
    /**
     * 经度
     */
    private Double lon;

    /**
     * 纬度
     */
    private Double lat;

    /**
     * 搜索范围
     */
    private Integer distance;
}

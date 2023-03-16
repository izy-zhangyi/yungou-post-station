package com.yps.viewmodel;

import lombok.Data;

import java.io.Serializable;

/**
 * 设备位置信息
 */
@Data
public class VMDistance implements Serializable {
    /**
     * 设配编号
     */
    private String innerCode;

    /**
     * 详细地址(不需要前端传入)
     */
    private String addr;

    /**
     * 点位名称(不需要前端传入)
     */
    private String nodeName;

    /**
     * 经度
     */
    private Double lon;

    /**
     * 纬度
     */
    private Double lat;
}

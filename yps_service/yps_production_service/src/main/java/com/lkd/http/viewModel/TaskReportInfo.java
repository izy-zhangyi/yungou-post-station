package com.yps.http.viewModel;

import lombok.Data;

import java.io.Serializable;

/**
 * 工单基本统计情况
 */
@Data
public class TaskReportInfo implements Serializable {

    /**
     * 工单总数
     */
    private Integer total;

    /**
     * 完成总数
     */
    private Integer completeTotal;

    /**
     * 拒绝总数
     */
    private Integer refuseTotal;

    /**
     * 进行中总数
     */
    private Integer progressTotal;

    /**
     * 工作人数
     */
    private Integer workCount;

    /**
     * 是否是运维工单统计
     */
    private Boolean isRepair;

    /**
     * 日期
     */
    private String date;
}

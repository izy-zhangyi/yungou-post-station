package com.yps.viewmodel;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户工作量统计
 */
@Data
public class UserWork implements Serializable {

    /**
     * 用户id
     */
    private Integer userId;

    /**
     * 人员名称
     */
    private String userName;

    /**
     *  联系电话
     */
    private String mobile;

    /**
     * 角色
     */
    private String roleName;

    /**
     * 完成工单数
     */
    private Integer workCount;

    /**
     * 当日进行中的工单
     */
    private Integer progressTotal;

    /**
     * 拒绝工单数
     */
    private Integer cancelCount;

    /**
     * 工单总数
     */
    private Integer total;
}

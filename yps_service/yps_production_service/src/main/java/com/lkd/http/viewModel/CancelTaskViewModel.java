package com.yps.http.viewModel;

import lombok.Data;

import java.io.Serializable;

@Data
public class CancelTaskViewModel implements Serializable {
    /**
     * 拒绝/取消理由
     */
    private String desc;

}

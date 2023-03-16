package com.yps.http.viewModel;

import lombok.Data;

/**
 * 销售情况(按周统计)
 */
@Data
public class SalesInfo {
    /**
     * 当时数据
     */
    private Long currentAmount = 0L;
    /**
     * 同比增长
     */
    private Integer percent = 0;
}

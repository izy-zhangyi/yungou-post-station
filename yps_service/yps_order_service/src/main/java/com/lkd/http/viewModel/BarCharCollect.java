package com.yps.http.viewModel;

import com.google.common.collect.Lists;
import lombok.Data;

import java.util.List;

/**
 * 柱状图
 */
@Data
public class BarCharCollect {
    /**
     *  x 轴
     */
    private List<String> xAxis = Lists.newArrayList();

    /**
     * y 轴
     */
    private List<Integer> yAxis = Lists.newArrayList();
}

package com.yps.service;

import com.yps.entity.OrderCollectEntity;
import com.yps.http.viewModel.BarCharCollect;
import com.yps.viewmodel.Pager;

import java.time.LocalDate;
import java.util.List;

public interface ReportService {

    /**
     * 获取合作商分账汇总信息
     *
     * @param pageIndex
     * @param pageSize
     * @param name
     * @param startTime
     * @param endTime
     * @return
     */
    Pager<OrderCollectEntity> getPartnerCollect(Long pageIndex, Long pageSize, String name, LocalDate startTime, LocalDate endTime);

    /**
     * 获取合作商前12条点位分账数据
     *
     * @param partnerId
     * @return
     */
    List<OrderCollectEntity> getTop12(Integer partnerId);

    /**
     * 合作商点位分账搜索
     *
     * @param partnerId
     * @param nodeName
     * @param start
     * @param end
     * @return
     */
    Pager<OrderCollectEntity> search(Long pageIndex, Long pageSize, Integer partnerId, String nodeName, LocalDate start, LocalDate end);

    /**
     * 获取分账数据列表
     *
     * @param partnerId
     * @param nodeName
     * @param start
     * @param end
     * @return
     */
    List<OrderCollectEntity> getList(Integer partnerId, String nodeName, LocalDate start, LocalDate end);

    /**
     * 获取一定日期内合作商的收益统计
     *
     * @param partnerId
     * @param start
     * @param end
     * @return
     */
    BarCharCollect getCollect(Integer partnerId, LocalDate start, LocalDate end);

    /**
     * 获取一定日期内的销售额统计
     *
     * @param start
     * @param end
     * @return
     */
    BarCharCollect getAmountCollect(LocalDate start, LocalDate end, Integer collectType);

    /**
     * 获取地区销量统计 --ey: 北京2街道，北京6街道 --区域 --2
     * @param start
     * @param end
     * @return
     */
    BarCharCollect getCollectByRegion(LocalDate start, LocalDate end);
}

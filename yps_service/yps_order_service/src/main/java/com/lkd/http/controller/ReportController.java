package com.yps.http.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.api.R;
import com.yps.entity.OrderCollectEntity;
import com.yps.http.viewModel.BarCharCollect;
import com.yps.http.viewModel.BillExportDataVO;
import com.yps.service.ReportService;
import com.yps.viewmodel.Pager;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/report")
public class ReportController {

    private final ReportService reportService;

    /**
     * 获取一定日期范围之内的合作商分成汇总数据
     *
     * @param pageIndex
     * @param pageSize
     * @param partnerName
     * @param start
     * @param end
     * @return
     */
    @GetMapping("/partnerCollect")
    public Pager<OrderCollectEntity> getPartnerCollect(
            @RequestParam(value = "pageIndex", required = false, defaultValue = "1") Long pageIndex,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Long pageSize,
            @RequestParam(value = "partnerName", required = false, defaultValue = "") String partnerName,
            @RequestParam(value = "start", required = true, defaultValue = "") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam(value = "end", required = true, defaultValue = "") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end
    ) {
        return reportService.getPartnerCollect(pageIndex, pageSize, partnerName, start, end);
    }

    /**
     * 获取最近12条分账信息
     *
     * @param partnerId
     * @return
     */
    @GetMapping("/top12Collect/{partnerId}")
    public List<OrderCollectEntity> getTop12Collect(@PathVariable("partnerId") Integer partnerId) {
        return reportService.getTop12(partnerId);
    }

    /**
     * 合作商搜索分账信息
     *
     * @param partnerId
     * @param pageIndex
     * @param pageSize
     * @param nodeName
     * @param start
     * @param end
     * @return
     */
    @GetMapping("/search/{partnerId}")
    public Pager<OrderCollectEntity> search(
            @PathVariable Integer partnerId,
            @RequestParam(value = "pageIndex", required = false, defaultValue = "1") Long pageIndex,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Long pageSize,
            @RequestParam(value = "nodeName", required = false, defaultValue = "") String nodeName,
            @RequestParam(value = "start", required = true, defaultValue = "") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam(value = "end", required = true, defaultValue = "") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return reportService.search(pageIndex, pageSize, partnerId, nodeName, start, end);
    }

    /**
     * 数据导出
     *
     * @param partnerId
     * @param start
     * @param end
     */
    @GetMapping("/export/{partnerId}/{start}/{end}")
    public void export(HttpServletResponse response,
                       @PathVariable Integer partnerId,
                       @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
                       @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end,
                       @RequestParam(value = "nodeName", required = false, defaultValue = "") String nodeName) throws IOException {

        // 1. 获取需要导出得数据列表
        List<BillExportDataVO> exportDataVos = reportService.getList(partnerId, nodeName, start, end).stream().map(item -> {
            BillExportDataVO billExportDataVO = new BillExportDataVO(); //票据导出数据VO
            //atZone指定时区  atTime(0,0) 0h0m0s
            billExportDataVO.setDate(Date.from(item.getDate().atTime(0, 0).atZone(ZoneId.systemDefault()).toInstant()));
            billExportDataVO.setAmount(item.getTotalBill());
            billExportDataVO.setNodeName(item.getNodeName());
            billExportDataVO.setOrderCount(item.getOrderCount());
            return billExportDataVO;
        }).collect(Collectors.toList());

        // 2. 使用 easyExcel 导出数据
        response.setContentType("application/vnd.ms-excel"); // 类型为excel类型
        response.setCharacterEncoding("utf-8");
        /**
         * 在 HTTP 场景中，第一个参数或者是 inline（默认值，表示回复中的消息体会以页面的一部分或者整个页面的形式展示），
         * 或者是 attachment（意味着消息体应该被下载到本地；大多数浏览器会呈现一个“保存为”的对话框，
         * 将 filename 的值预填为下载后的文件名，假如它存在的话）
         */
        response.setHeader("content-disposition", "attachment;filename=bill.xlsx");
        // write(从响应数据中获取输出流，转换实体类).sheet(工作表名称).doWrite(需要下载得数据集合);
        EasyExcel.write(response.getOutputStream(), BillExportDataVO.class).sheet("分账数据").doWrite(exportDataVos);
    }

    /**
     * 获取合作商一定日期范围的收益情况
     *
     * @param partnerId
     * @param start
     * @param end
     * @return
     */
    @GetMapping("/collectReport/{partnerId}/{start}/{end}")
    public BarCharCollect getCollectReport(@PathVariable Integer partnerId,
                                           @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
                                           @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return reportService.getCollect(partnerId, start, end);
    }

    /**
     * 获取销售额统计
     *
     * @param collectType
     * @param start
     * @param end
     * @return
     */
    @GetMapping("/amountCollect/{collectType}/{start}/{end}")
    public BarCharCollect getAmountCollect(@PathVariable Integer collectType,
                                           @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
                                           @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return reportService.getAmountCollect(start, end, collectType);
    }

    /**
     * 根据地区汇总销售额数据
     *
     * @param start
     * @param end
     * @return
     */
    @GetMapping("/regionCollect/{start}/{end}")
    public BarCharCollect getRegionCollect(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return reportService.getCollectByRegion(start, end);
    }
}
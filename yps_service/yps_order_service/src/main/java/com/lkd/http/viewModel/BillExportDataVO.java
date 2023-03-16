package com.yps.http.viewModel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

import java.util.Date;

/**
 * 定义到导出的数据对象类
 * 账单导出数据对象
 */
@Data
@ContentRowHeight(15) // 内容行高
@HeadRowHeight(20) // 头高度
@ColumnWidth(25) // 列宽
public class BillExportDataVO {

    @DateTimeFormat("yyyyy-MM-dd")
    @ExcelProperty(value = "分账日期", index = 0)
    private Date date;

    @ExcelProperty(value = "分账点位", index = 1)
    private String nodeName; // 分账点位

    @ExcelProperty(value = "订单数", index = 2)
    private Integer orderCount; //订单数

    @ExcelProperty(value = "分账金额", index = 3)
    private Integer amount; // 分成金额


}

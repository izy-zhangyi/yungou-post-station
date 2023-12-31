package com.yps.http.viewModel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
public class SkuImport implements Serializable {

    @ExcelProperty("商品名称")
    private String skuName;

    @ExcelProperty("图片url")
    private String skuImage;

    @ExcelProperty("价格")
    private Integer price;

    @ExcelProperty("类别")
    private Integer classId;

    @ExcelProperty("净含量")
    private String unit;

    @ExcelProperty("品牌")
    private String brandName;

}

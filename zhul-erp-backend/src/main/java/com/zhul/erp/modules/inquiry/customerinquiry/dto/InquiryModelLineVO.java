package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

/** 询盘列表「查看」弹窗里的一行型号 */
@Data
public class InquiryModelLineVO {
    private String brand;
    /** 型号：已确认的取确认后的型号，否则取原始型号 */
    private String model;
    /** 一级品类；关联不上品类库时为原文 */
    private String category;
    private Integer quantity;
    private String unit;
}

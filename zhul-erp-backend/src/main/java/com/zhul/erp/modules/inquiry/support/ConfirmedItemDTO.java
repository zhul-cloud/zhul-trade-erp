package com.zhul.erp.modules.inquiry.support;

import lombok.Data;

/** 拆单确认后、用于创建 inquiry_order_item 的已核实明细（customerinquiry -> inquiryorder 的交接结构）。 */
@Data
public class ConfirmedItemDTO {
    private String originalModel;
    private String confirmedModel;
    private Integer confidence;
    private String correctionNote;
    private String description;
    private Integer quantity;
    private String unit;
    private String deliveryRequirement;
    private String remark;
}

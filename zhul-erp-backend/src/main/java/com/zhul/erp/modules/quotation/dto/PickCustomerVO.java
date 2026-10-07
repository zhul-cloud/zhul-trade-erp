package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

/** 挑选型号报价：可选客户 */
@Data
public class PickCustomerVO {
    private Long customerId;
    private String customerName;
    private String country;
    /** 可报价 / 询价中 / 已报价的询盘数 */
    private Long inquiryCount;
}

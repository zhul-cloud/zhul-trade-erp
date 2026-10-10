package com.zhul.erp.modules.sales.dto;

import lombok.Data;

/** 有可开 PI 报价单的客户 */
@Data
public class PiCandidateCustomerVO {
    private Long customerId;
    private String customerName;
    private String country;
    private Long quotationCount;
}

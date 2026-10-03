package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.time.LocalDate;

/** 新建询盘时选中客户后展示的新老客户判断 */
@Data
public class CustomerTypeVO {
    /** 见 InquiryConstants.CUSTOMER_* */
    private Integer customerType;
    private Integer wonCount;
    private LocalDate lastWonDate;
}

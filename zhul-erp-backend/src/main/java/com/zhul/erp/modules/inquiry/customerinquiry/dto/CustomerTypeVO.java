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
    /** 客户档案的来源渠道，新建询盘单时默认填入来源 */
    private Integer sourceChannel;
}

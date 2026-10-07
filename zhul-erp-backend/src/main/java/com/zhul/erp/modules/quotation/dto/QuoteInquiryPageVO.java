package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

import java.util.List;

/** 待报价询盘：分页结果 + 顶部计数 */
@Data
public class QuoteInquiryPageVO {
    private Long total;
    private List<QuoteInquiryVO> records;
    private Long readyCount;
    private Long sourcingCount;
}

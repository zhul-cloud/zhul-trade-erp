package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

/** 按询盘报价：待报价询盘列表查询 */
@Data
public class QuoteInquiryQuery {
    /** 客户名称、询盘编号 */
    private String keyword;
    private Boolean readyOnly;
    private Boolean dueToday;
    private Integer page = 1;
    private Integer pageSize = 20;
}

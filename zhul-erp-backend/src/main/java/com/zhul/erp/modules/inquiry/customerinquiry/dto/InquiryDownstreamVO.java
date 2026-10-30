package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

/** 引用询盘单的下游单据：有任一条时询盘不能删除 */
@Data
public class InquiryDownstreamVO {
    /** TASK-询价任务、QUOTATION-报价单、PI、ORDER-销售订单 */
    private String kind;
    private Long id;
    private String no;
    private String statusName;
}

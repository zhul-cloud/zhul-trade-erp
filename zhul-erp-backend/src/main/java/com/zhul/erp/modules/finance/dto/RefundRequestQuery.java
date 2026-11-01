package com.zhul.erp.modules.finance.dto;

import lombok.Data;

/** 退款申请分页 */
@Data
public class RefundRequestQuery {
    private Integer page;
    private Integer pageSize;
    /** 1-待审批、2-待财务处理、3-已执行、4-已驳回、5-已撤回；为空为全部 */
    private Integer status;
    /** 申请号、售后单号、订单号 */
    private String keyword;
}

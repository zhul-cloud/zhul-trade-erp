package com.zhul.erp.modules.aftersales.dto;

import lombok.Data;

/** 退货入库分页 */
@Data
public class ReturnQuery {
    private Integer page;
    private Integer pageSize;
    /** 1-待入库、2-待处理、3-已处理、4-已作废；为空为全部 */
    private Integer status;
    /** 退货单号、售后单号、订单号、快递单号 */
    private String keyword;
}

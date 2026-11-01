package com.zhul.erp.modules.aftersales.dto;

import lombok.Data;

/** 售后管理分页 */
@Data
public class AfterSalesQuery {
    private Integer page;
    private Integer pageSize;
    /** 1-处理中、2-待客户确认、3-已结案、4-已撤销；为空为全部 */
    private Integer status;
    /** 售后单号、订单号、型号 */
    private String keyword;
    private Long customerId;
    private String levelCode;
    private Long ownerId;
}

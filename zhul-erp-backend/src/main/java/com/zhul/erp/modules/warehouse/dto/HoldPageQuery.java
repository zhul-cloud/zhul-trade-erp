package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;

/** 闲置库存列表的查询 */
@Data
public class HoldPageQuery {
    private Integer page;
    private Integer pageSize;
    /** 型号、来源采购订单号 */
    private String keyword;
    private Integer status;
    /** 来源（1-到货差异、2-订单部分取消）；为空不限 */
    private Integer sourceType;
}

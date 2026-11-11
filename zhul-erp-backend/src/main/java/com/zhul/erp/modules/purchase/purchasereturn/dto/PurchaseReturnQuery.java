package com.zhul.erp.modules.purchase.purchasereturn.dto;

import lombok.Data;

/** 采购退货页查询 */
@Data
public class PurchaseReturnQuery {
    private Integer status;
    private Long supplierId;
    private Integer sourceType;
    private Long ownerId;
    private String keyword;
    private Integer page;
    private Integer pageSize;
}

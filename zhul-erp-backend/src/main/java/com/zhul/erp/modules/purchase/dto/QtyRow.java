package com.zhul.erp.modules.purchase.dto;

import lombok.Data;

/** 采购单行数量汇总：草稿中、已下单 */
@Data
public class QtyRow {
    private Long id;
    private Integer draftQty;
    private Integer orderedQty;
}

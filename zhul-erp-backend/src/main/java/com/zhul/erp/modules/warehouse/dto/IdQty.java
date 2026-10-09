package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;

/** 按 ID 汇总的数量 */
@Data
public class IdQty {
    private Long id;
    private Integer qty;
}

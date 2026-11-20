package com.zhul.erp.modules.inventory.dto;

import lombok.Data;

/** 库存台账：月份 yyyy-MM（默认本月）、仓库（空为全部仓库合计）、关键词（型号、品牌） */
@Data
public class LedgerQuery {
    private String month;
    private Long warehouseId;
    private String keyword;
    private Integer page;
    private Integer pageSize;
}

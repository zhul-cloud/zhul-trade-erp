package com.zhul.erp.modules.inventory.dto;

import lombok.Data;

/** 库存查询：关键词（型号、品牌）、仓库（ID；forwarders 为全部货代仓）、只看可用、只看有不良 */
@Data
public class StockQuery {
    private String keyword;
    private Long warehouseId;
    /** own-公司仓库、forwarders-全部货代仓；与 warehouseId 二选一 */
    private String warehouseScope;
    private Boolean onlyAvailable;
    private Boolean onlyDefective;
    private Integer page;
    private Integer pageSize;
}

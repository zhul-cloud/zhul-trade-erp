package com.zhul.erp.modules.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存类目账（spec warehouse/inventory「库存台账」）：按一级品类汇总明细账。
 * 每行按 期初金额 + 本月入库 − 本月出库 + 在库成本调整 = 期末金额 排；合计行即总账。
 * 本月销售成本只在全部仓库时有值（销售成本不分仓库），不进算式。
 */
@Data
public class CategoryLedgerVO {
    private String month;
    private Long warehouseId;
    private List<Row> rows;
    private Row total;

    @Data
    public static class Row {
        /** 一级品类 ID，0 为未分类；合计行为空 */
        private Long categoryId;
        private String categoryName;
        private Integer productCount;
        private BigDecimal openingAmount;
        private BigDecimal inAmount;
        private BigDecimal outAmount;
        private BigDecimal adjustAmount;
        private BigDecimal closingAmount;
        /** 本月销售成本；按仓库查看时为空 */
        private BigDecimal cogsAmount;
        private String currencyCode = "CNY";
    }
}

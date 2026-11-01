package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 部分取消的影响（确认前展示）：合计变化、多收、每个型号对采购与库存的处理 */
@Data
public class PartialCancelPreviewVO {
    private String currencyCode;
    private BigDecimal itemAmountBefore;
    private BigDecimal itemAmountAfter;
    private BigDecimal discountBefore;
    private BigDecimal discountAfter;
    private BigDecimal totalBefore;
    private BigDecimal totalAfter;
    /** 已到账（含手续费差额） */
    private BigDecimal received;
    /** 取消后多出的已到账（进入待处理多收），不小于 0 */
    private BigDecimal overpaidAfter;
    private List<Line> items;

    @Data
    public static class Line {
        private Long itemId;
        private String model;
        private Integer quantity;
        private Integer quantityBefore;
        private Integer quantityAfter;
        private BigDecimal amount;
        /** 是否有采购需求（系统外采购的只改订单） */
        private Boolean tracked;
        private Integer unplaced;
        private List<PoQty> drafts;
        private Integer held;
        private List<PoQty> ordered;
        private Integer untracked;
    }

    @Data
    public static class PoQty {
        private Long poId;
        private String poNo;
        private Integer quantity;
    }
}

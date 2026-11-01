package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 按型号退回前的预览 */
@Data
public class ReturnPreviewVO {
    /** 任务里还有待审核的回价：不能退回 */
    private Boolean pendingReview;
    private List<Row> rows;
    /** 用了这些型号价格的草稿报价单 */
    private List<WithdrawPreviewVO.DraftQuotation> drafts;

    @Data
    public static class Row {
        private Long itemId;
        private String model;
        private Integer quantity;
        private Integer quoteStatus;
        /** 待询价：一定交出 */
        private Boolean must;
        /** 已报给客户：不能交出 */
        private Boolean locked;
        private String lockedQuotationNo;
        private String checkedChannels;
        /** 当前采购成本价，兼职采购为空 */
        private BigDecimal unitPriceCny;
        private String currencyCode;
    }
}

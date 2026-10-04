package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 审核通过：逐型号选推荐、作废记录 */
@Data
public class ReviewApproveRequest {
    @NotEmpty(message = "请选择要审核的型号")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "型号不能为空")
        private Long itemId;
        @NotNull(message = "回价人不能为空")
        private Long quotedBy;
        /** 推荐报价；只有无货结果时为空 */
        private Long recommendedQuoteId;
        @Valid
        private List<VoidQuote> voids;
    }

    @Data
    public static class VoidQuote {
        @NotNull(message = "请选择要作废的记录")
        private Long quoteId;
        @Size(max = 200, message = "作废原因不能超过 200 字")
        private String reason;
    }
}

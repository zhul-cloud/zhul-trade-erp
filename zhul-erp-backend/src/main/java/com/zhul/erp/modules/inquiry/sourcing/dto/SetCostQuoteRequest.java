package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

/** 采购负责人指定采购成本价；quoteId 为空表示恢复按推荐报价自动取 */
@Data
public class SetCostQuoteRequest {
    private Long quoteId;
}

package com.zhul.erp.modules.supply.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 汇入信号时从来源单据读出的一行 */
@Data
public class SourceRow {
    private Long sourceId;
    private Long docId;
    private String docNo;
    private Long brandId;
    private String brandRaw;
    private Long categoryId;
    private Long productId;
    private String modelRaw;
    private String modelKey;
    private Integer quantity;
    private String reasonCode;
    private String reasonText;
    private LocalDateTime occurredAt;
    private Boolean cancelled;
}

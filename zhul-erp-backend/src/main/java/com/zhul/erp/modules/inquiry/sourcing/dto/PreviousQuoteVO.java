package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 接手人看到的上一轮记录；兼职采购看不到价格（unitPriceCny 为空） */
@Data
public class PreviousQuoteVO {
    private String quotedByName;
    /** 改派前只保存、没有提交的记录 */
    private Boolean draft;
    private Boolean noStock;
    /** 内部无货原因（字典值）；没有填时为空 */
    private Integer noStockReason;
    /** 内部无货原因名称；没有填时为空串 */
    private String noStockReasonName;
    private Integer channel;
    private String shopName;
    private String note;
    private BigDecimal unitPriceCny;
    private String currencyCode;
}

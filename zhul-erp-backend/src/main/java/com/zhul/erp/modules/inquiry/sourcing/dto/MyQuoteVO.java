package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 本人在某型号上的一条询价记录 */
@Data
public class MyQuoteVO {
    private Long id;
    private Integer channel;
    private String shopName;
    private Long supplierId;
    private BigDecimal unitPrice;
    private Boolean taxIncluded;
    private java.math.BigDecimal taxRate;
    private Integer itemCondition;
    private Integer leadTime;
    private String note;
    private Boolean recommended;
    private Boolean noStock;
    /** 1-草稿、2-已提交、3-待审核、4-已作废 */
    private Integer status;
    /** 作废原因（status=4）或退回原因（被退回的草稿） */
    private String reviewNote;
    private LocalDateTime quotedAt;
}

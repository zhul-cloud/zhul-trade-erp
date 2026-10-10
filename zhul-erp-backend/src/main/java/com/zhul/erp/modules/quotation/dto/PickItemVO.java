package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 可挑选的型号 */
@Data
public class PickItemVO {
    private Long itemId;
    private Integer lineNo;
    private String model;
    private String brand;
    private String category;
    private Integer quantity;
    /** 采购成本价（CNY），无货或还在询价时为空 */
    private BigDecimal costPrice;
    private Boolean noStock;
    /** 能否选：有采购成本价或无货 */
    private Boolean pickable;
    /** 不能选的原因 */
    private String disabledReason;
    /** 已出现在已发送（及之后）的报价单中 */
    private Boolean quoted;
    /** 已在该客户另一张草稿报价单中时的编号 */
    private String draftQuotationNo;
}

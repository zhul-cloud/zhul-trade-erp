package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 报价单型号行（系统内查看，含成本与利润） */
@Data
public class QuotationItemVO {
    private Long id;
    private Integer lineNo;
    private Long customerInquiryId;
    private String inquiryCode;
    private Long inquiryItemId;
    private String model;
    private String brand;
    private String category;
    private String description;
    /** 英文描述（给客户看的单据用；系统内显示中文 description） */
    private String descriptionEn;
    private Integer itemCondition;
    private String conditionName;
    private Integer leadTime;
    private String leadTimeName;
    private String warranty;
    private Integer quantity;
    private Boolean noStock;
    /** 无货行：无货且没有填售价，不报价、不计入合计 */
    private Boolean noStockLine;
    /** 替代型号（无货行） */
    private String replacementModel;
    /** 发给客户的单据上的品牌（英文名；品牌资料里匹配不到时与原文相同） */
    private String brandEn;
    private BigDecimal costPrice;
    private BigDecimal costPriceForeign;
    private Integer pricingMode;
    private BigDecimal marginRate;
    private BigDecimal markupAmount;
    private BigDecimal suggestedMargin;
    private String suggestBasis;
    private BigDecimal floorMargin;
    private Boolean belowFloor;
    /** 建议主管核价提示文字 */
    private List<String> hints;
    private BigDecimal unitPrice;
    private BigDecimal unitPriceCny;
    private BigDecimal amount;
    private BigDecimal amountCny;
    private BigDecimal netProfit;
    private BigDecimal netProfitCny;
    /** 已进入有效销售订单 */
    private Boolean won;
}

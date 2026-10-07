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
    private Integer itemCondition;
    private String conditionName;
    private Integer leadTime;
    private String leadTimeName;
    private String warranty;
    private Integer quantity;
    private Boolean noStock;
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

package com.zhul.erp.modules.sales.dto;

import lombok.Data;
import java.math.BigDecimal;

/** PI 型号行（系统内查看，含成本与利润） */
@Data
public class PiItemVO {
    private Long id;
    private Integer lineNo;
    private Long quotationId;
    private String quotationNo;
    private Long quotationItemId;
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
    private BigDecimal quotedPrice;
    private BigDecimal unitPrice;
    private BigDecimal unitPriceCny;
    private BigDecimal amount;
    private BigDecimal amountCny;
    private BigDecimal costPrice;
    private BigDecimal floorMargin;
    private BigDecimal marginRate;
    private Boolean belowFloor;
    private BigDecimal netProfit;
    private BigDecimal netProfitCny;
    private String hsCode;
    private String originCountry;
    private String remark;
}

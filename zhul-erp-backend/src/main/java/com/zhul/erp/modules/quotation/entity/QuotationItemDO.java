package com.zhul.erp.modules.quotation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 报价单型号行：型号信息、采购成本价与建议规则都存快照 */
@Data
@TableName("quotation_item")
public class QuotationItemDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long quotationId;
    private Integer versionNo;
    /** 1-当前版本 */
    private Integer isCurrent;
    private Integer lineNo;
    private Long customerInquiryId;
    private Long inquiryItemId;
    private Long costQuoteId;
    private String model;
    private String brand;
    private String brandKey;
    private String category;
    private String description;
    /** 英文描述（给客户看的单据用；系统内显示中文 description） */
    private String descriptionEn;
    private Integer itemCondition;
    private Integer leadTime;
    private String warranty;
    private Integer quantity;
    private Integer noStock;
    /** 替代型号（无货行：询价时标停产记录的替代型号） */
    private String replacementModel;
    private BigDecimal costPrice;
    /** 定价方式，见 QuotationPricing.MODE_* */
    private Integer pricingMode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal marginRate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal markupAmount;
    private BigDecimal suggestedMargin;
    private String suggestBasis;
    private BigDecimal floorMargin;
    private String hints;
    private BigDecimal unitPrice;
    private BigDecimal unitPriceCny;
    private BigDecimal amount;
    private BigDecimal amountCny;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal netProfit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal netProfitCny;
    /** 是否已进入有效销售订单（0-否、1-是），由销售订单的创建与取消维护 */
    private Integer won;
    private LocalDateTime deletedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}

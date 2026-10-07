package com.zhul.erp.modules.sales.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** PI 型号行（挂在版本上），逐级记录来源报价行与询盘型号 */
@Data
@TableName("proforma_invoice_item")
public class PiItemDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long piId;
    private Long versionId;
    private Integer lineNo;
    private Long quotationId;
    private Long quotationItemId;
    private Long customerInquiryId;
    private Long inquiryItemId;
    private String model;
    private String brand;
    private String category;
    private String description;
    private Integer itemCondition;
    private Integer leadTime;
    private String warranty;
    private Integer quantity;
    private BigDecimal quotedPrice;
    private BigDecimal unitPrice;
    private BigDecimal unitPriceCny;
    private BigDecimal amount;
    private BigDecimal amountCny;
    private BigDecimal costPrice;
    private BigDecimal floorMargin;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal marginRate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal netProfit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal netProfitCny;
    private String hsCode;
    private String originCountry;
    private String remark;
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

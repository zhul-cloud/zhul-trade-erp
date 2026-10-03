package com.zhul.erp.modules.inquiry.sourcing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 询价记录：一条询价结果，已提交的即为历史询价的一行 */
@Data
@TableName("sourcing_quote")
public class SourcingQuoteDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long inquiryItemId;
    private Long taskId;
    private Long customerInquiryId;
    private String brand;
    private String brandKey;
    private String model;
    private String modelKey;
    private Integer channel;
    private String shopName;
    /** 渠道为供应商时关联的供应商 */
    private Long supplierId;
    private Integer noStock;
    private String currencyCode;
    private BigDecimal unitPrice;
    private BigDecimal exchangeRate;
    private BigDecimal unitPriceCny;
    private Integer taxIncluded;
    /** 税率百分比，含税时有值 */
    private BigDecimal taxRate;
    private Integer itemCondition;
    private Integer leadTime;
    private String note;
    /** 该采购对该型号的推荐报价 */
    private Integer recommended;
    private Long quotedBy;
    private LocalDateTime quotedAt;
    private Integer status;
    private Integer entryMode;
    private Long importId;
    /** 提交批次：同一次写入的记录相同 */
    private String submitBatch;
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

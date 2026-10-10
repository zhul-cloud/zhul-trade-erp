package com.zhul.erp.modules.sales.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 销售订单型号行 */
@Data
@TableName("sales_order_item")
public class SalesOrderItemDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long soId;
    private Integer lineNo;
    private Long piItemId;
    private Long quotationId;
    private Long quotationItemId;
    private Long customerInquiryId;
    private Long inquiryItemId;
    private String model;
    private String brand;
    private String category;
    private String description;
    /** 英文描述（给客户看的单据用；系统内显示中文 description） */
    private String descriptionEn;
    private Integer itemCondition;
    private Integer leadTime;
    /** 1-现货、2-期货 */
    private Integer stockType;
    /** 字典 sales_order_status 的 item_code */
    private String progressCode;
    /** 为空表示未指定 */
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long purchaserId;
    private String warranty;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private BigDecimal amountCny;
    private BigDecimal costPrice;
    private BigDecimal marginRate;
    private BigDecimal netProfit;
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

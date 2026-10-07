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

/** PI 版本快照：每个版本保存完整内容，发送后只读 */
@Data
@TableName("proforma_invoice_version")
public class PiVersionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long piId;
    private Integer versionNo;
    private Integer status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String buyerJson;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String consigneeJson;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long buyerPartyId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long consigneePartyId;
    private String deliveryTime;
    private String paymentTerm;
    private String incoterm;
    private String incotermPlace;
    private String portOfShipment;
    private String remark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer bankAccountId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bankAccountJson;
    private Integer discountType;
    private BigDecimal discountValue;
    private BigDecimal discountAmount;
    private BigDecimal discountAmountCny;
    private BigDecimal itemAmount;
    private BigDecimal feeAmount;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountCny;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal netProfit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal netProfitCny;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal marginRate;
    private LocalDateTime sentAt;
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

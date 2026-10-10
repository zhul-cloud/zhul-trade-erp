package com.zhul.erp.modules.quotation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 报价单版本的表头快照：已发送的版本只读；修改中的新版本在这里编辑表头 */
@Data
@TableName("quotation_version")
public class QuotationVersionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long quotationId;
    private Integer versionNo;
    /** 1-编辑中、2-已发送、3-已放弃，见 QuotationConstants.VERSION_* */
    private Integer status;
    private BigDecimal exchangeRate;
    private LocalDateTime rateTime;
    private String incoterm;
    private String incotermPlace;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate validUntil;
    private String remark;
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

package com.zhul.erp.modules.finance.entity;

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

/** 报销明细：一个来源同时只能在一张有效报销单里（release_seq = 0） */
@Data
@TableName("fin_reimbursement_line")
public class ReimbursementLineDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long reimbursementId;
    /** 1-采购货款垫付、2-国内快递运费、3-退货运费、4-费用单、5-收到退款 */
    private Integer sourceType;
    private Long sourceId;
    private String sourceNo;
    private LocalDate occurDate;
    private String description;
    private BigDecimal amount;
    private Long releaseSeq;
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

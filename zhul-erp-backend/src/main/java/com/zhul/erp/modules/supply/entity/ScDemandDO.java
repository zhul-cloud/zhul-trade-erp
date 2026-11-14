package com.zhul.erp.modules.supply.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 需求对象，码值见 V1.5.34__supply_chain_phase1.sql 字段注释 */
@Data
@TableName("sc_demand")
public class ScDemandDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long brandId;
    private Long categoryId;
    private String modelKey;
    private String model;
    private String scopeKey;
    private Integer status;
    private String pauseReason;
    private Integer demandLevel;
    private Integer suggestedPriority;
    private String priorityRule;
    private Integer priority;
    private Integer priorityManual;
    private String priorityReason;
    private Integer cntInquiry;
    private Integer cntOrder;
    private Integer cntStockout;
    private Integer cntLost;
    private Integer cntLostSupply;
    private Integer cntSearch;
    private Integer cntResearch;
    private LocalDateTime lastSignalAt;
    private Integer coverageStatus;
    private Integer suggestedCoverage;
    private Integer coveragePending;
    private String coverageBasis;
    private String coverageNote;
    private Integer evidenceType;
    private Long evidenceId;
    private LocalDate evidenceDate;
    private LocalDateTime enteredMatrixAt;
    private Long ownerId;
    private String nextAction;
    private LocalDate nextActionDate;
    private Integer nextActionSource;
    private Integer reminderType;
    private Integer deferCount;
    private Long mergedIntoId;
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

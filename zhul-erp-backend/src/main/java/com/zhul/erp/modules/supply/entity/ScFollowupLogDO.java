package com.zhul.erp.modules.supply.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 供应链处理记录，码值见 V1.5.34__supply_chain_phase1.sql 字段注释 */
@Data
@TableName("sc_followup_log")
public class ScFollowupLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Integer recordType;
    private Integer sourceType;
    private Long sourceId;
    private String sourceDocNo;
    /** 对象类型（1-型号、2-候选供应商） */
    private Integer objectType;
    private String content;
    private String demandIds;
    private String candidateIds;
    private String statusChanges;
    private Long gapProjectId;
    private String nextAction;
    private LocalDate nextActionDate;
    private Long operatorId;
    private String idempotencyKey;
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

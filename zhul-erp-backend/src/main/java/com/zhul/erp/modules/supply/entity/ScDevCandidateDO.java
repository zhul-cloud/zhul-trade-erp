package com.zhul.erp.modules.supply.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 开发任务的候选供应商，码值见 V1.5.35__supply_chain_phase2.sql 字段注释 */
@Data
@TableName("sc_dev_candidate")
public class ScDevCandidateDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long projectId;
    private Long supplierId;
    private String linkKey;
    private String name;
    private String contactName;
    private String contactInfo;
    private Integer source;
    private Integer stage;
    private String abandonReason;
    private String note;
    private Integer evidenceType;
    private Long evidenceId;
    private LocalDate evidenceDate;
    private LocalDate quotedAt;
    private String nextAction;
    private LocalDate nextActionDate;
    private Long ownerId;
    private String lastAction;
    private LocalDateTime lastActionAt;
    private Long lastActionBy;
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

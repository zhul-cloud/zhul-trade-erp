package com.zhul.erp.modules.supply.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 需求信号，码值见 V1.5.34__supply_chain_phase1.sql 字段注释 */
@Data
@TableName("sc_demand_signal")
public class ScDemandSignalDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Integer sourceType;
    private Long sourceId;
    private Long sourceDocId;
    private String sourceDocNo;
    private Long demandId;
    private Long brandId;
    private String brandRaw;
    private Long categoryId;
    private String modelRaw;
    private String modelKey;
    private Integer quantity;
    private String reasonCode;
    private String reasonText;
    private String content;
    private String platform;
    private Integer occurCount;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String linkUrl;
    private LocalDateTime occurredAt;
    private Integer sourceCancelled;
    private Long operatorId;
    private String ignoreReason;
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

package com.zhul.erp.modules.supply.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 供应链专项，码值见 V1.5.34__supply_chain_phase1.sql 字段注释 */
@Data
@TableName("sc_gap_project")
public class ScGapProjectDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String name;
    private Long brandId;
    private Long categoryId;
    private String goal;
    private Long ownerId;
    private LocalDate dueDate;
    private Integer status;
    private String activeScope;
    private String resultNote;
    private LocalDateTime closedAt;
    private Long closedBy;
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

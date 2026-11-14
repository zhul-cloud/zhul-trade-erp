package com.zhul.erp.modules.supply.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 主动拓展范围，码值见 V1.5.35__supply_chain_phase2.sql 字段注释 */
@Data
@TableName("sc_expansion_scope")
public class ScExpansionScopeDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long brandId;
    private Long categoryId;
    private Integer heat;
    private String reason;
    private Long ownerId;
    private String activeScope;
    private String removeReason;
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

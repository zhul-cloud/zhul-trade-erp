package com.zhul.erp.modules.supply.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 覆盖渠道，码值见 V1.5.34__supply_chain_phase1.sql 字段注释 */
@Data
@TableName("sc_coverage_channel")
public class ScCoverageChannelDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long demandId;
    private Long supplierId;
    private Integer role;
    private Integer sortOrder;
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

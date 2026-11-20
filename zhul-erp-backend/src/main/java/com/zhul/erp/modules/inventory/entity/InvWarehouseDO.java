package com.zhul.erp.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 仓库（自有仓、货代仓） */
@Data
@TableName("inv_warehouse")
public class InvWarehouseDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String code;
    private String name;
    /** 1-自有仓、2-货代仓 */
    private Integer type;
    private Long forwarderId;
    /** 0-停用、1-启用 */
    private Integer status;
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

package com.zhul.erp.modules.logistics.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 销售出库合并打包组：同一客户、同一货代的几张销售出库单共用一批箱子（openspec outbound-merge-pack） */
@Data
@TableName("outbound_pack")
public class OutboundPackDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long customerId;
    private Long forwarderId;
    /** 1-有效、2-已拆开 */
    private Integer status;
    private Long packedBy;
    private LocalDateTime packedAt;
    private String splitReason;
    private Long splitBy;
    private LocalDateTime splitAt;
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

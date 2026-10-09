package com.zhul.erp.modules.logistics.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 出库单的箱子 */
@Data
@TableName("outbound_box")
public class OutboundBoxDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long outboundId;
    private Integer boxNo;
    private Integer length;
    private Integer width;
    private Integer height;
    private BigDecimal grossWeight;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal netWeight;
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

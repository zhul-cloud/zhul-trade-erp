package com.zhul.erp.modules.logistics.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 国内快递 */
@Data
@TableName("courier_waybill")
public class CourierWaybillDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String carrier;
    private String trackingNo;
    private LocalDate sentDate;
    private BigDecimal freight;
    private Long payerId;
    private Long forwarderId;
    /** 1-有效、2-已撤销 */
    private Integer status;
    private String undoReason;
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

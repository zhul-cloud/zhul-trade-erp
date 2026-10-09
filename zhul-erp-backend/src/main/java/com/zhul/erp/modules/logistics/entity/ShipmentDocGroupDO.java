package com.zhul.erp.modules.logistics.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 单证组 */
@Data
@TableName("shipment_doc_group")
public class ShipmentDocGroupDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long logisticsId;
    /** 逗号分隔的订单 ID */
    private String soIds;
    private String ciNo;
    private String plNo;
    private String paymentRef;
    /** 1-有效、2-已作废 */
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

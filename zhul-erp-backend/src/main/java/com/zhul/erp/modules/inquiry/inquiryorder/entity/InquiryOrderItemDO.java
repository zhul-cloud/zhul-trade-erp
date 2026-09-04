package com.zhul.erp.modules.inquiry.inquiryorder.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inquiry_order_item")
public class InquiryOrderItemDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String itemCode;
    private Long inquiryOrderId;
    private String brand;
    private String category;
    private String originalModel;
    private String confirmedModel;
    /** 置信度，见 ConfidenceLevel；手动创建固定为 CONFIRMED */
    private Integer confidence;
    private String correctionNote;
    private String description;
    private Integer quantity;
    private String unit;
    private String deliveryRequirement;
    private String remark;
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

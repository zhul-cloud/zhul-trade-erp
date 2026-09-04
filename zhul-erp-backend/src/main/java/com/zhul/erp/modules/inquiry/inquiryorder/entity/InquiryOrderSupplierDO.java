package com.zhul.erp.modules.inquiry.inquiryorder.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 询盘单-报价来源关联。见 design.md 决策11：source_type 区分"正式供应商"
 * 与"电商询价渠道"（淘宝/1688/闲鱼等），后者不落地 supplier 主数据。
 */
@Data
@TableName("inquiry_order_supplier")
public class InquiryOrderSupplierDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long inquiryOrderId;
    /** 报价来源类型，见 InquirySourceType */
    private Integer sourceType;
    /** source_type=正式供应商 时必填 */
    private Long supplierId;
    /** source_type=电商询价渠道 时必填，见 ChannelPlatform */
    private Integer channelPlatform;
    private String channelName;
    private String channelLink;
    private LocalDate sentDate;
    private LocalDate replyDeadline;
    /** 状态，见 SupplierAssociationStatus */
    private Integer status;
    private String quoteFileUrl;
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

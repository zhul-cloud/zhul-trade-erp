package com.zhul.erp.modules.inquiry.customerinquiry.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 询盘单附件（非公开存储） */
@Data
@TableName("customer_inquiry_attachment")
public class CustomerInquiryAttachmentDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long customerInquiryId;
    /** 追加批次ID，0 为录入询盘单时的附件 */
    private Long appendId;
    private String fileName;
    private String fileKey;
    /** 存储方式（LOCAL-服务器目录、OSS-对象存储） */
    private String storage;
    private String contentType;
    private Long fileSize;
    private Integer sort;
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

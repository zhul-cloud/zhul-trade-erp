package com.zhul.erp.modules.inquiry.customerinquiry.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("customer_inquiry")
public class CustomerInquiryDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String inquiryCode;
    private Long customerId;
    /** 询盘来源，见 CustomerInquirySource */
    private Integer source;
    private String rawContent;
    private String rawAttachmentUrl;
    private LocalDate inquiryDate;
    private LocalDate expectedReplyDate;
    /** 状态，见 CustomerInquiryStatus */
    private Integer status;
    private Integer totalOrderCount;
    private Integer totalItemCount;
    private Integer pendingVerifyCount;
    private Long ownerId;
    private Long aiTaskId;
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

package com.zhul.erp.modules.inquiry.customerinquiry.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 询盘单追加型号批次（spec inquiry/inquiry-intake「追加型号」） */
@Data
@TableName("customer_inquiry_append")
public class CustomerInquiryAppendDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long customerInquiryId;
    private String rawContent;
    private Long aiTaskId;
    /** 1-解析中、2-待确认、3-解析失败、4-已确认、5-已放弃 */
    private Integer status;
    /** 1-AI、2-手动 */
    private Integer parseMode;
    private String parseError;
    private Integer itemCount;
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

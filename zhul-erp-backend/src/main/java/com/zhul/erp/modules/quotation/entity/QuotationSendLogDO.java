package com.zhul.erp.modules.quotation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 报价单发送记录 */
@Data
@TableName("quotation_send_log")
public class QuotationSendLogDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long quotationId;
    private Integer versionNo;
    /** 发送方式，见 QuotationConstants.CHANNEL_* */
    private Integer channel;
    private Long sentBy;
    private LocalDateTime sentAt;
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

package com.zhul.erp.modules.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商机需求附件；文件在私有目录，fileKey 不对外返回 */
@Data
@TableName("opportunity_attachment")
public class OpportunityAttachmentDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long opportunityId;
    private String fileName;
    private String fileKey;
    private Long fileSize;
    private String contentType;
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

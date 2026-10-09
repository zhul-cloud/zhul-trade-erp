package com.zhul.erp.modules.attachment.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 业务附件：发货单、入库单、拍摄任务的图片与视频 */
@Data
@TableName("biz_attachment")
public class BizAttachmentDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    /** SHIPMENT / RECEIPT / SHOOT */
    private String ownerType;
    /** 0 表示已上传、还没挂到单据上 */
    private Long ownerId;
    /** 1-图片、2-视频 */
    private Integer kind;
    /** LOCAL / OSS */
    private String storage;
    private String fileKey;
    private String url;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private Long uploadedBy;
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

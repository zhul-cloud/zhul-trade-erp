package com.zhul.erp.modules.attachment.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 业务附件的型号标注：供方发货、验收入库（含不良品）、打包发货的图片与视频涉及哪些型号 */
@Data
@TableName("biz_attachment_model")
public class BizAttachmentModelDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long attachmentId;
    /** 型号归并键：小写（品牌 + 空格 + 型号） */
    private String assetKey;
    private String brand;
    private String model;
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

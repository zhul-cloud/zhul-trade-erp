package com.zhul.erp.modules.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 型号素材：拍摄任务上传，归到品牌 + 型号名下 */
@Data
@TableName("media_asset")
public class MediaAssetDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String assetKey;
    private String brand;
    private String model;
    private Long attachmentId;
    /** 1-拆箱视频、2-验货视频、3-实物图 */
    private Integer mediaType;
    private Long taskId;
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

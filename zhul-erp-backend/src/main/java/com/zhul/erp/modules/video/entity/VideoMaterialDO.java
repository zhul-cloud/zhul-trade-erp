package com.zhul.erp.modules.video.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 视频任务的图片素材：顺序与是否选用 */
@Data
@TableName("product_video_material")
public class VideoMaterialDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long taskId;
    private Long attachmentId;
    private Integer sort;
    /** 0-不用于视频、1-选用 */
    private Integer used;
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

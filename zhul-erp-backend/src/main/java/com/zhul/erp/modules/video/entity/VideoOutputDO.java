package com.zhul.erp.modules.video.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 视频任务的生成结果：每次生成竖屏、横屏各一行 */
@Data
@TableName("product_video_output")
public class VideoOutputDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long taskId;
    private Integer version;
    /** 9:16 或 16:9 */
    private String aspect;
    private Long videoAttachmentId;
    private Long coverAttachmentId;
    private Integer durationSec;
    /** 1-待审核、2-已通过、3-已替换、4-已退回 */
    private Integer status;
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

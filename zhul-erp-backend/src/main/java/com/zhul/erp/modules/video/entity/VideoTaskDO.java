package com.zhul.erp.modules.video.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 视频任务：一家公司一个商品一个任务 */
@Data
@TableName("product_video_task")
public class VideoTaskDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long productId;
    private String brand;
    private String model;
    /** 型号归并键，关联素材库 */
    private String assetKey;
    /** 见 VideoConstants.STATUS_* */
    private Integer status;
    /** 分镜脚本 JSON，见 VideoScript */
    private String script;
    /** 0-无、1-AI、2-手工修改 */
    private Integer scriptSource;
    /** 0-未生成、1-生成中、2-已生成、3-生成失败 */
    private Integer scriptStatus;
    private Long scriptAiTaskId;
    private String scriptError;
    private LocalDateTime queuedAt;
    private LocalDateTime nextTryAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String failReason;
    private String reviewBy;
    private LocalDateTime reviewTime;
    private String rejectReason;
    /** 最近一次生成的版本号，0 表示还没生成过 */
    private Integer currentVersion;
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

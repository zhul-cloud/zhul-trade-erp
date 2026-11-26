package com.zhul.erp.modules.video.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 视频任务列表的一行 */
@Data
public class VideoTaskVO {
    private Long id;
    private Long productId;
    private String brand;
    private String model;
    private String productName;
    private String category;
    private Integer status;
    private String statusName;
    /** 排队中：在全服务器队列里的位置（从 1 起）；其他状态为空 */
    private Integer queuePosition;
    /** 排队中：按平均耗时估算的预计开始时间 */
    private LocalDateTime expectedAt;
    private String failReason;
    private String rejectReason;
    private Integer usedCount;
    private Integer imageCount;
    /** 已通过的竖屏视频与封面（列表缩略图，点击播放） */
    private Long videoAttachmentId;
    private Long coverAttachmentId;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}

package com.zhul.erp.modules.video.dto;

import lombok.Data;

/** 视频任务列表：页签 all、pending、queued（含生成中）、review、approved、rejected、failed */
@Data
public class VideoTaskQuery {
    private String tab;
    private Long brandId;
    private Long categoryId;
    private String keyword;
    private Integer page;
    private Integer pageSize;
}

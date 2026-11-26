package com.zhul.erp.modules.video.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 分镜脚本：英文标题 + 4–8 个镜头，每个镜头一张图片和一句字幕（两个画幅共用） */
@Data
public class VideoScript {
    private String title;
    private List<Shot> shots = new ArrayList<>();

    @Data
    public static class Shot {
        private Long attachmentId;
        private String caption;
    }
}

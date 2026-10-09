package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/** 拍摄任务详情 */
@Data
public class ShootVO {
    private ShootListVO task;
    /** 本任务的素材（复用时为被复用任务的素材） */
    private List<Media> media;
    /** 同型号已有的素材（其他任务拍的） */
    private List<Media> sameModel;

    @Data
    public static class Media {
        private Long id;
        private Long attachmentId;
        /** 1-拆箱视频、2-验货视频、3-实物图 */
        private Integer mediaType;
        private Integer kind;
        private String fileName;
        private Long fileSize;
        private String contentType;
        private LocalDateTime createTime;
    }
}

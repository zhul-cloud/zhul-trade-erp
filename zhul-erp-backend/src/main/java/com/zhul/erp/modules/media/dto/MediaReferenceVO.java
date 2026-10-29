package com.zhul.erp.modules.media.dto;

import lombok.Data;

import java.util.List;

/** 参考素材：找到的那一层与型号（按热度排序） */
@Data
public class MediaReferenceVO {
    /** 1 同系列、2 同品牌同品类、3 同品牌；0 没找到（不回退到其他品牌） */
    private Integer level;
    private String levelName;
    private List<Model> models;

    @Data
    public static class Model {
        private Long productId;
        private String brand;
        private String model;
        private String productName;
        private Long inquiryCount;
        private Long dealCount;
    }
}

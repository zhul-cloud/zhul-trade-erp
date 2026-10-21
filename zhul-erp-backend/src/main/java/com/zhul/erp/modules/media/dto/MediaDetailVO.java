package com.zhul.erp.modules.media.dto;

import lombok.Data;

import java.util.List;

/** 一个型号的素材：按分组（业务流程顺序）列出，每组按类别 */
@Data
public class MediaDetailVO {
    private String brand;
    private String model;
    private Long productId;
    private String productName;
    /** 有可发客户的图片或视频；没有时页面提供「参考素材」 */
    private Boolean hasSendable;
    private List<Group> groups;

    @Data
    public static class Group {
        private String name;
        private List<MediaItemVO> items;
    }
}

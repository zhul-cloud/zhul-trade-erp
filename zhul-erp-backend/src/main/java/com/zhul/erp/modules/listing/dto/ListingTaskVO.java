package com.zhul.erp.modules.listing.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 发品任务（列表行与详情页头） */
@Data
public class ListingTaskVO {
    private Long id;
    private Long productId;
    private String brand;
    private String model;
    private String productName;
    private String category;
    private String platform;
    private String platformLabel;
    /** 1-待发布、2-已发布、3-已取消 */
    private Integer status;
    private String statusName;
    private String cancelReason;
    /** 三个提醒：素材待补、内容有更新、待替换素材 */
    private Boolean needMedia;
    private Boolean contentUpdated;
    private Boolean needReplace;
    /** 本型号用于发品的图片数、视频数 */
    private Integer imageCount;
    private Integer videoCount;
    /** 参考素材的型号，为空表示用本型号素材 */
    private String refBrand;
    private String refModel;
    private String refBy;
    private LocalDateTime refTime;
    private String listingUrl;
    private String platformItemId;
    private String note;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private String syncedBy;
    private LocalDateTime syncedAt;
    private String replacedBy;
    private LocalDateTime replacedAt;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}

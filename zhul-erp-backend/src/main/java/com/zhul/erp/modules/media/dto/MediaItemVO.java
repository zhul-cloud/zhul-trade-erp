package com.zhul.erp.modules.media.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 素材库里的一条素材。本公司的素材有 itemId；共享商品库的官方素材用 officialTable + officialId 标识
 * （本公司改过标记时也有 itemId）。
 */
@Data
public class MediaItemVO {
    private Long itemId;
    private String officialTable;
    private Long officialId;
    private Integer category;
    private String categoryName;
    /** 1-图片、2-视频、3-文件 */
    private Integer kind;
    private String fileName;
    private Long fileSize;
    private String contentType;
    /** 视频处理状态：0-无需处理、1-压缩中、2-已完成、3-失败 */
    private Integer processingStatus;
    private Boolean canSend;
    private Boolean forListing;
    /** 这个类别能否标「用于发品」 */
    private Boolean listingAllowed;
    /** 来源：单据类型（SHIPMENT、RECEIPT、RECEIPT_DEFECT、SHOOT、PACKING、SOURCING_QUOTE、NETWORK、INQUIRY、PRODUCT）、ID、编号 */
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private String uploaderName;
    private LocalDateTime uploadedAt;
    private String note;
    /** 官方素材的固定地址（公开文件、外部链接） */
    private String publicUrl;
}

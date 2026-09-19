package com.zhul.erp.modules.product.dto;

import lombok.Data;

@Data
public class MediaVO {
    private Long id;
    private Long productId;
    /** 1-图片、2-视频 */
    private Integer mediaType;
    private String fileUrl;
    /** 1-平台上传、2-外部链接 */
    private Integer storageType;
    private String coverUrl;
    private String title;
    private Long fileSize;
    private Integer isMain;
    private String source;
    private Integer sortOrder;
}

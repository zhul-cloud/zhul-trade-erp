package com.zhul.erp.modules.media.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** 素材库型号列表 */
@Data
public class MediaModelVO {
    private String brand;
    private String model;
    /** 共享商品库里的商品名称，没有为空 */
    private String productName;
    private Long productId;
    /** 分组名 → 素材数（本公司的素材，不含官方资料） */
    private Map<String, Integer> groupCounts;
    private Boolean hasMarketing;
    private Integer sendableCount;
    private Integer listingCount;
    private LocalDateTime lastAddedAt;
}

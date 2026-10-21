package com.zhul.erp.modules.media.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 素材库型号列表的一行（数据库汇总结果） */
@Data
public class MediaModelRow {
    private String assetKey;
    private String brand;
    private String model;
    private LocalDateTime lastAddedAt;
    private Integer marketingCount;
    private Integer sendableCount;
    private Integer listingCount;
}

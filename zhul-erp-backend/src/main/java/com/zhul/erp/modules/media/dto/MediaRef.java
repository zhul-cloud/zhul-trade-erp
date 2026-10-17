package com.zhul.erp.modules.media.dto;

import lombok.Data;

/** 指向一条素材：本公司素材用 itemId，官方素材用 officialTable + officialId（另带型号，用于本公司的标记） */
@Data
public class MediaRef {
    private Long itemId;
    private String officialTable;
    private Long officialId;
    private String brand;
    private String model;
}

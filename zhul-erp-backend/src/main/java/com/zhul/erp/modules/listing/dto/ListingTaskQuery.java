package com.zhul.erp.modules.listing.dto;

import lombok.Data;

/** 发品任务列表查询 */
@Data
public class ListingTaskQuery {
    /** 页签：pending、updated、replace、published、cancelled、all；为空按 pending */
    private String tab;
    /** 平台编码 */
    private String platform;
    private Long brandId;
    private Long categoryId;
    /** 型号、名称 */
    private String keyword;
    private Integer page;
    private Integer pageSize;
}

package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待确认品牌汇总（跨租户）：只有名称和计数，不含租户、供应商信息。
 * 来源包括供应商主营产品里手填的品牌，以及询盘、销售订单、采购订单里认不出的品牌写法（openspec brand-unify）。
 */
@Data
public class PendingBrandVO {
    /** 比较键，确认操作时回传 */
    private String pendingKey;
    /** 名称（同一比较键下取最早出现的写法） */
    private String name;
    private Integer supplierCount;
    /** 出现在询盘型号、销售订单、采购订单里的行数 */
    private Integer inquiryCount;
    private Integer orderCount;
    private Integer purchaseCount;
    private LocalDateTime firstSeen;
    /** 建议：similar（相近品牌）、notBrand（不是品牌名）；没有建议时为空 */
    private String suggestKind;
    private String suggestText;
    private Long suggestBrandId;
    private String suggestBrandName;
}

package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** 内容任务列表行；各平台写入时间为空表示本公司该平台（独立站按语言）营销内容未写入 */
@Data
public class ContentTaskVO {
    private Long productId;
    private Long taskId;
    private String brandName;
    private String categoryName;
    private String mpn;
    private String productName;
    /** 1-资料待补、2-资料待核、3-营销待生成、4-已完成 */
    private Integer status;
    private LocalDateTime enAt;
    private String enBy;
    private LocalDateTime ruAt;
    private String ruBy;
    private LocalDateTime alibabaAt;
    private String alibabaBy;
    private LocalDateTime micAt;
    private String micBy;
    private LocalDateTime socialAt;
    private String socialBy;
    /** 本公司标了不发布的平台：平台编码 → 标记 */
    private Map<String, MarketingSkipVO> skips = new HashMap<>();
    /** 本公司最近下载营销包时间 */
    private LocalDateTime downloadedAt;
    /** 资料（所有公司共享）：最近导入、核对、下载资料包 */
    private LocalDateTime factImportedAt;
    private String factImportedBy;
    private LocalDateTime factVerifiedAt;
    private String factVerifiedBy;
    private LocalDateTime factDownloadedAt;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}

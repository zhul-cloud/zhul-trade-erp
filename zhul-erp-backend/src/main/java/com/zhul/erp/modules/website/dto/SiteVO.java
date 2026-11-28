package com.zhul.erp.modules.website.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 站点（列表与详情共用）；凭据只有是否已配置与末四位 */
@Data
public class SiteVO {
    private Long id;
    private String name;
    private String url;
    private List<SiteLanguage> languages;
    private String pathTemplate;
    private Integer publishMode;
    private String publishModeName;
    private String repoUrl;
    private String repoBranch;
    private String deployHost;
    private Integer deployPort;
    private String deployUser;
    private String deployPath;
    private SiteScope productScope;
    private List<Long> nonGenuineBrandIds;
    private BigDecimal priceFactor;
    private String currencyCode;
    private Boolean needPurge;
    private String purgeNote;
    private Integer takeoverStatus;
    private String takeoverStatusName;
    private String takeoverBy;
    private LocalDateTime takeoverAt;
    private Integer status;
    private SecretVO repoToken;
    private SecretVO deployKey;
    private SecretVO indexNowKey;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
    /** 商品数、最近发布、未确认差异（只在列表、详情返回） */
    private SiteSummary summary;
}

package com.zhul.erp.modules.website.dto;

import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;

/** 新建、修改站点。三个凭据留空表示不改；校验在服务层（WebsiteSiteService.validate） */
@Data
public class SiteRequest {
    private String name;
    private String url;
    private List<SiteLanguage> languages;
    private String pathTemplate;
    /** 1-自动发布、2-只复制 */
    private Integer publishMode;
    private String repoUrl;
    private String repoBranch;
    private String deployHost;
    private Integer deployPort;
    private String deployUser;
    private String deployPath;
    private SiteScope productScope;
    private List<Long> nonGenuineBrandIds;
    private BigDecimal priceFactor;
    private Boolean needPurge;
    private String purgeNote;
    @ToString.Exclude
    private String repoToken;
    @ToString.Exclude
    private String deployKey;
    @ToString.Exclude
    private String indexNowKey;
    /** 只用于 IndexNow 密钥：true 表示删除已配置的密钥（仓库令牌、部署私钥是自动发布必填，不能删除） */
    private Boolean clearIndexNowKey;
}

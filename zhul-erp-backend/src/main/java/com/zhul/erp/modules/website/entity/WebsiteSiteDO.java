package com.zhul.erp.modules.website.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 独立站站点；JSON 列以字符串保存，由服务层解析 */
@Data
@TableName("website_site")
public class WebsiteSiteDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String name;
    private String url;
    /** [{"code":"en","prefix":""}] */
    private String languages;
    private String pathTemplate;
    /** 见 WebsiteConstants.MODE_* */
    private Integer publishMode;
    private String repoUrl;
    private String repoBranch;
    private String deployHost;
    private Integer deployPort;
    private String deployUser;
    private String deployPath;
    /** {"brandIds":[],"categoryIds":[]} */
    private String productScope;
    /** 品牌 id 数组 */
    private String nonGenuineBrands;
    private BigDecimal priceFactor;
    private String currencyCode;
    private Integer needPurge;
    private String purgeNote;
    /** 见 WebsiteConstants.TAKEOVER_* */
    private Integer takeoverStatus;
    private String takeoverBy;
    private LocalDateTime takeoverAt;
    private Integer status;
    private LocalDateTime deletedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}

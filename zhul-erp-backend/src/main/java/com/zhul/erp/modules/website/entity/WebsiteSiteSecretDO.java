package com.zhul.erp.modules.website.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

/** 站点凭据：密文（AesUtils）与末四位，明文不落库 */
@Data
@TableName("website_site_secret")
public class WebsiteSiteSecretDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long siteId;
    /** 见 WebsiteConstants.SECRET_* */
    private String secretType;
    @ToString.Exclude
    private String cipherText;
    private String last4;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}

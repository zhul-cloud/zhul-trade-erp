package com.zhul.erp.modules.product.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品平台营销内容（按公司；阿里国际站、中国制造网、社媒，每个平台只用自己的列） */
@Data
@TableName("product_platform_content")
public class ProductPlatformContentDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long productId;
    /** 平台编码，见 MarketingPlatform */
    private String platform;
    private String lang;
    private String title;
    /** 备选标题 JSON：[{title, angle}] */
    private String titleAlternates;
    /** 卖点，每行一条 */
    private String sellingPoints;
    /** 产品属性 JSON：[{name, value}] */
    private String attributes;
    private String detail;
    /** 平台 FAQ JSON：[{question, answer}] */
    private String faqs;
    /** 询盘引导语 */
    private String cta;
    private String linkedinPost;
    private String facebookPost;
    private String youtubeDescription;
    /** 社媒帖子 JSON：[{channel, label, text}]（v2；为空时显示旧的三列） */
    private String posts;
    /** 视频方案 JSON：[{label, markdown}] */
    private String videos;
    private Long importId;
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

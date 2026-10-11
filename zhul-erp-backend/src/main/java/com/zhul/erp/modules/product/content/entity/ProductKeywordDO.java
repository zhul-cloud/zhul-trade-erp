package com.zhul.erp.modules.product.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品关键词总库（按公司、按型号一份） */
@Data
@TableName("product_keyword")
public class ProductKeywordDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long productId;
    private String lang;
    private String keyword;
    /** 小写、去首尾空格，总库内唯一 */
    private String keywordKey;
    /** 1-核心、2-主要、3-长尾 */
    private Integer tier;
    private String category;
    private String intent;
    private String basis;
    private Integer sortOrder;
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

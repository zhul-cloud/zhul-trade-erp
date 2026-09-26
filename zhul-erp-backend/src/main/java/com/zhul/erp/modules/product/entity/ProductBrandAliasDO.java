package com.zhul.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 品牌别名（平台级，tenant_id 恒为 0）。别名是品牌的一个属性：编辑品牌时整体替换该品牌的别名列表，
 * 与修改品牌简介等字段同性质，所以没有软删除字段。
 */
@Data
@TableName("product_brand_alias")
public class ProductBrandAliasDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long brandId;
    /** 别名原文 */
    private String alias;
    /** 比较键：去首尾空格、转小写 */
    private String aliasKey;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}

package com.zhul.erp.modules.product.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品资料待确认项（共享；每次导入 v2 资料包整份替换） */
@Data
@TableName("product_fact_note")
public class ProductFactNoteDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long productId;
    /** 1-关键未知字段、2-冲突与待确认 */
    private Integer kind;
    private String item;
    private String statusText;
    private String impact;
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

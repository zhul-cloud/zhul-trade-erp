package com.zhul.erp.modules.masterdata.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商主营产品：一行 = 供应商 × 品牌（正式品牌或待确认品牌名）× 一个细分品类；categoryId 为空表示该品牌全部品类。
 */
@Data
@TableName("supplier_product_scope")
public class SupplierProductScopeDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long supplierId;
    /** 正式品牌 ID；为空表示待确认品牌 */
    private Long brandId;
    private String pendingBrandName;
    /** 待确认品牌名比较键：去首尾空格、转小写 */
    private String pendingKey;
    /** 细分品类 ID；为空表示该品牌全部品类 */
    private Long categoryId;
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

package com.zhul.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品主数据表（平台共享，Part Number实体核心表） */
@Data
@TableName("product")
public class ProductDO {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID（0=平台级共享，本模块所有数据均为0） */
    private Integer tenantId;
    /** 原始型号（制造商资料/询盘原文，仅去首尾空格，其余不清洗；被引用后不可修改） */
    private String mpnRaw;
    /** 归一化型号（NFKC后转小写，去掉所有非字母数字字符），用于去重与检索；系统生成，禁止手工编辑 */
    private String mpnNormalized;
    /** 展示型号，页面标题使用；默认等于mpn_raw */
    private String mpnDisplay;
    /** 品牌ID，关联product_brand.id；被引用后不可修改 */
    private Long brandId;
    /** 品类ID，关联product_category.id */
    private Long categoryId;
    /** 系列ID，关联product_series.id；必须属于brand_id对应品牌；未分配时为空 */
    private Long seriesId;
    /** 产品名称，如SITOP Power Supply */
    private String productName;
    /** 简介，需能追溯到官方资料或询盘单，不得凭空扩写 */
    private String shortDescription;
    /** 一句话核心规格摘要，列表页展示 */
    private String specSummary;
    /** 生命周期（1-在产Active、2-现行Current、3-旧款Legacy、4-已停产Discontinued、5-停产无替代Obsolete、6-未知Unknown）；是否有替代型号查product_relationship */
    private Integer lifecycleStatus;
    /** 生命周期判断依据；lifecycle_status为4或5时必填 */
    private String lifecycleSource;
    /** 状态（0-禁用、1-启用） */
    private Integer status;
    /** 软删除时间，NULL表示未删除 */
    private LocalDateTime deletedAt;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 创建人 */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    /** 更新人 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}

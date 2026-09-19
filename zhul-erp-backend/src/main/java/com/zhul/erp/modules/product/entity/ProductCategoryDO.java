package com.zhul.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 品类主数据表 */
@Data
@TableName("product_category")
public class ProductCategoryDO {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID（0=平台级共享，本模块所有数据均为0） */
    private Integer tenantId;
    /** 品类编码，小写蛇形，如controllers/servo；独立站URL直接沿用，有商品后不可修改 */
    private String categoryCode;
    /** 品类名称，如PLC & Controllers */
    private String categoryName;
    /** 上级品类ID，关联product_category.id；当前只有一级品类，预留，暂不使用 */
    private Long parentId;
    /** 排序 */
    private Integer sortOrder;
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

package com.zhul.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品应用场景表 */
@Data
@TableName("product_application")
public class ProductApplicationDO {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID（0=平台级共享，本模块所有数据均为0） */
    private Integer tenantId;
    /** 商品ID，关联product.id */
    private Long productId;
    /** 应用场景标题，如Water & pump stations */
    private String title;
    /** 场景说明，必须基于真实产品用途，不得为SEO编造 */
    private String description;
    /** 图标，直接存emoji（迁移自独立站）或图标标识；展示方式由消费方决定 */
    private String icon;
    /** 是否已人工核实（0-未核实、1-已核实）；仅为标记，不影响读取 */
    private Integer verified;
    /** 排序 */
    private Integer sortOrder;
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

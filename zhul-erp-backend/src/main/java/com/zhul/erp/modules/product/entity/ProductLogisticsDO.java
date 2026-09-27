package com.zhul.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商品物流信息表（一对一） */
@Data
@TableName("product_logistics")
public class ProductLogisticsDO {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID（0=平台级共享，本模块所有数据均为0） */
    private Integer tenantId;
    /** 商品ID，关联product.id；一个商品最多一行 */
    private Long productId;
    /** 净重（kg）；未维护为NULL，不是0 */
    private BigDecimal netWeightKg;
    /** 毛重（kg，含包装）；填写时不得小于净重 */
    private BigDecimal grossWeightKg;
    /** 单品长（mm） */
    private BigDecimal lengthMm;
    /** 单品宽（mm） */
    private BigDecimal widthMm;
    /** 单品高（mm） */
    private BigDecimal heightMm;
    /** 包装类型，如 盒装/箱装/托盘 */
    private String packageType;
    /** 包装长（mm） */
    private BigDecimal packageLengthMm;
    /** 包装宽（mm） */
    private BigDecimal packageWidthMm;
    /** 包装高（mm） */
    private BigDecimal packageHeightMm;
    /** 每个包装内的件数 */
    private Integer packageQuantity;
    /** 是否危险品或含电池等限运品（0-否、1-是） */
    private Integer isDangerous;
    /** 运输备注，如 需防潮、含锂电池 */
    private String shippingNote;
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

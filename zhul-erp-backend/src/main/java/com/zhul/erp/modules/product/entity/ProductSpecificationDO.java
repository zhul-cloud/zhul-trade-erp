package com.zhul.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品规格参数表 */
@Data
@TableName("product_specification")
public class ProductSpecificationDO {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID（0=平台级共享，本模块所有数据均为0） */
    private Integer tenantId;
    /** 商品ID，关联product.id */
    private Long productId;
    /** 规格编码，小写蛇形，如rated_voltage；同一商品内唯一 */
    private String specKey;
    /** 规格显示名称，如Rated Voltage */
    private String specLabel;
    /** 规格值（展示文本） */
    private String specValue;
    /** 单位，如V DC；与规格值分开存储 */
    private String specUnit;
    /** 数据来源，如Manufacturer Datasheet/询盘单 */
    private String source;
    /** 是否已核实（0-未核实、1-已核实） */
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

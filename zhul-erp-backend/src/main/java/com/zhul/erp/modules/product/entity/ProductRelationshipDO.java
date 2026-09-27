package com.zhul.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品型号关系表（替代/兼容/交叉引用） */
@Data
@TableName("product_relationship")
public class ProductRelationshipDO {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID（0=平台级共享，本模块所有数据均为0） */
    private Integer tenantId;
    /** 商品ID（关系挂在哪个商品下），关联product.id */
    private Long productId;
    /** 关联型号原文，可以是目录里没有的旧型号/停产型号 */
    private String relatedMpn;
    /** 关联商品ID，关联product.id；仅当关联型号在目录内时有值 */
    private Long relatedProductId;
    /** 关系类型（1-官方直接替代、2-厂商后续型号、3-功能性替代、4-兼容、5-交叉引用、6-同系列）；3/4/5/6为对称类型，1/2非对称 */
    private Integer relationshipType;
    /** 置信度（1-已验证、2-高、3-中、4-低、5-未知）；4/5时下游不得展示为"推荐替代"类强断言 */
    private Integer confidence;
    /** 关系说明文案，措辞需按relationship_type区分 */
    private String note;
    /** 核实人；confidence=1时必填 */
    private String verifiedBy;
    /** 核实时间 */
    private LocalDateTime verifiedAt;
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

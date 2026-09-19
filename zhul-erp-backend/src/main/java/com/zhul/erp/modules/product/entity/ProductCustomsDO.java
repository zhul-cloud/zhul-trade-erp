package com.zhul.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商品海关信息表（一对一） */
@Data
@TableName("product_customs")
public class ProductCustomsDO {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID（0=平台级共享，本模块所有数据均为0） */
    private Integer tenantId;
    /** 商品ID，关联product.id；一个商品最多一行 */
    private Long productId;
    /** HS编码，仅数字（去掉点和空格后6~10位）；一个商品一个，空表示未维护 */
    private String hsCode;
    /** 申报品名（中文） */
    private String customsNameCn;
    /** 申报品名（英文） */
    private String customsNameEn;
    /** 默认原产国，ISO 3166-1 alpha-2大写（如DE/CN）；同一型号不同批次可能不同，实际以货物单据为准 */
    private String originCountry;
    /** 申报要素 */
    private String declarationElements;
    /** 监管条件代码，如A/B；无则为空 */
    private String supervisionConditions;
    /** 出口退税率（%，0~100）；政策会调整，以最近一次维护为准；未维护为NULL */
    private BigDecimal exportRebateRate;
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

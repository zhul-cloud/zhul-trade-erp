package com.zhul.erp.modules.product.merge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品合并记录（平台级，spec product/product「商品合并」） */
@Data
@TableName("product_merge")
public class ProductMergeDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID（0-平台） */
    private Integer tenantId;
    /** 被并入的商品 */
    private Long fromProductId;
    /** 主商品 */
    private Long toProductId;
    /** 被并入商品的品牌 / 型号快照 */
    private String fromLabel;
    /** 各明细表改指向的条数（JSON） */
    private String affected;
    /** 合并原因 */
    private String reason;
    /** 操作人 */
    private String operatorId;
    /** 操作人所在租户 */
    private Integer operatorTenantId;
    /** 软删除时间 */
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

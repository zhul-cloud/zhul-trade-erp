package com.zhul.erp.modules.product.candidate.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品候选（平台级，tenant_id 恒为 0）：同一品牌键 + 归一化型号一行 */
@Data
@TableName("product_candidate")
public class ProductCandidateDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long brandId;
    private String brandText;
    private String brandKey;
    private String mpnRaw;
    private String mpnNormalized;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long categoryId;
    private String categoryText;
    private String productName;
    private String description;
    private String descriptionEn;
    /** 1-待审核、2-已建档、3-已并入、4-已驳回 */
    private Integer status;
    /** 1-询盘出现、2-采购问到有货、3-已成交 */
    private Integer level;
    private Integer sourceCount;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long productId;
    private Integer rejectReason;
    private String rejectNote;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long reviewedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime reviewedAt;
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

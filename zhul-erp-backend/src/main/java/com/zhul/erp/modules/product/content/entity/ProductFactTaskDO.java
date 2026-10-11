package com.zhul.erp.modules.product.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品资料任务（平台级，每个商品一行；没有记录的商品视为资料待补） */
@Data
@TableName("product_fact_task")
public class ProductFactTaskDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long productId;
    private LocalDateTime importedAt;
    private String importedBy;
    /** 为空或早于导入时间表示资料待核 */
    private LocalDateTime verifiedAt;
    private String verifiedBy;
    private LocalDateTime downloadedAt;
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

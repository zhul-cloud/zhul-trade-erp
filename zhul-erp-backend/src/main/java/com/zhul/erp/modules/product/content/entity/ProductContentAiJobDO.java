package com.zhul.erp.modules.product.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品内容 AI 生成记录（spec product/product-content「AI 生成资料与营销内容」）：每个商品每次一条，按公司 */
@Data
@TableName("product_content_ai_job")
public class ProductContentAiJobDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long productId;
    /** 1-排队中、2-生成中、3-待确认、4-已导入、5-失败、6-已取消 */
    private Integer status;
    private Long aiTaskId;
    private String factsFileName;
    private String factsMarkdown;
    private String marketingFileName;
    private String marketingMarkdown;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime factsImportedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime marketingImportedAt;
    private Integer selfScore;
    private String summary;
    private String failReason;
    private String skillVersion;
    private Long requestedBy;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
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

package com.zhul.erp.modules.product.candidate.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 待关联单据的标记：按「租户 + 归并键」记 AI 建议与「标为不是商品」（openspec unlinked-docs-enhance） */
@Data
@TableName("doc_unlinked_mark")
public class DocUnlinkedMarkDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String groupKey;
    /** 是否标为不是商品（0-否、1-是） */
    private Integer ignored;
    private String ignoredBy;
    private LocalDateTime ignoredAt;
    /** AI 判断（0-未识别、1-商品型号、2-费用或非商品、3-看不出） */
    private Integer aiKind;
    private String aiBrand;
    private String aiModel;
    private Long aiTaskId;
    private LocalDateTime aiAt;
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

package com.zhul.erp.modules.document.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 单据模版：每租户每类一条，记默认版本；租户没有自己的行时沿用平台（tenant_id=0）的内置版本 */
@Data
@TableName("document_template")
public class DocumentTemplateDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer tenantId;
    private Integer docType;
    /** 默认版本；为空表示沿用平台内置版本 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long defaultVersionId;
    /** 该租户是否停用了平台内置版本（0-否、1-是） */
    private Integer builtinDisabled;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}

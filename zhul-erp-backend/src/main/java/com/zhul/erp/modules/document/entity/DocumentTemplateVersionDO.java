package com.zhul.erp.modules.document.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 单据模版版本：保存后内容不可修改，需要调整时上传新版本 */
@Data
@TableName("document_template_version")
public class DocumentTemplateVersionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Integer docType;
    private Integer versionNo;
    private String note;
    /** 私有存储 key；平台内置为 classpath: 路径 */
    private String fileKey;
    private String fileName;
    /** 文字报价模版正文 */
    private String content;
    private Long uploadedBy;
    private Integer builtin;
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

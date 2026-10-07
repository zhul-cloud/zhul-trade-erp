package com.zhul.erp.modules.document.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 单据模版版本 */
@Data
public class TemplateVersionVO {
    private Long id;
    private Integer docType;
    private Integer versionNo;
    private String note;
    private String fileName;
    /** 文字报价模版正文 */
    private String content;
    private Boolean builtin;
    private Boolean enabled;
    private Boolean isDefault;
    private String uploadedByName;
    private LocalDateTime createTime;
    /** 上传时的提示（不影响保存），如没有页码 */
    private List<String> warnings;
}

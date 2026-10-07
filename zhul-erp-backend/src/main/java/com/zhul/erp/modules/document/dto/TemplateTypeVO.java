package com.zhul.erp.modules.document.dto;

import lombok.Data;

/** 单据模版类型概况 */
@Data
public class TemplateTypeVO {
    private Integer docType;
    private String name;
    /** 是否已实现生成（CI / PL 随发货模块上线） */
    private Boolean generatable;
    private Integer defaultVersionNo;
    private Integer versionCount;
}

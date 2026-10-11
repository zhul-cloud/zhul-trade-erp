package com.zhul.erp.modules.product.dto;

import lombok.Data;

@Data
public class SpecificationVO {
    private Long id;
    private String specKey;
    private String specLabel;
    private String specValue;
    private String specUnit;
    /** 条件/范围、证据状态、证据来源说明（v2 资料包导入；手工编辑时原样带回） */
    private String conditionText;
    private String evidence;
    private String sourceNote;
    private String source;
    private Integer verified;
    private Integer sortOrder;
}

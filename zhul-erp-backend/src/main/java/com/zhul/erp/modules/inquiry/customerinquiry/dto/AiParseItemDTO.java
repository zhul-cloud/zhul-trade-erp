package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.util.List;

/** AI 解析输出中的一个型号；新增字段缺失时按默认值处理 */
@Data
public class AiParseItemDTO {
    private String originalModel;
    private String confirmedModel;
    private Integer confidence;
    private String correctionNote;
    private String description;
    /** 英文描述（给客户看的单据用；系统内显示中文 description） */
    private String descriptionEn;
    private Integer quantity;
    private String unit;
    private String remark;
    /** 1-在产、2-停产、3-待查 */
    private Integer lifecycle;
    private String replacementModel;
    /** 1-简单、2-中等、3-困难 */
    private Integer difficulty;
    private String inquiryScript;
    private List<String> searchKeywords;
}

package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

/** ai_task.output（skill_id=inquiry-parse-and-split）里单条型号明细的结构。 */
@Data
public class AiParseItemDTO {
    private String originalModel;
    private String confirmedModel;
    /** 见 ConfidenceLevel */
    private Integer confidence;
    private String correctionNote;
    private String description;
    private Integer quantity;
    private String unit;
    private String remark;
}

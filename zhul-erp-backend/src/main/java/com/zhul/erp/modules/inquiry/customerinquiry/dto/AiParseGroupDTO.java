package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** ai_task.output 里按品牌+品类拆分出的一个分组。 */
@Data
public class AiParseGroupDTO {
    private String brand;
    private String category;
    private String inquiryTemplate;
    private String emailTemplateCn;
    private String emailTemplateEn;
    private List<AiParseItemDTO> items = new ArrayList<>();
}

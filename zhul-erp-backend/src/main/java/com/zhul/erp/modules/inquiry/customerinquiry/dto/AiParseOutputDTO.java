package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** skill_id=inquiry-parse-and-split 的 ai_task.output 顶层结构。 */
@Data
public class AiParseOutputDTO {
    private List<AiParseGroupDTO> groups = new ArrayList<>();
}

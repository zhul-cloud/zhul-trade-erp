package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** AI 解析输出 */
@Data
public class AiParseOutputDTO {
    private List<AiParseGroupDTO> groups = new ArrayList<>();
}

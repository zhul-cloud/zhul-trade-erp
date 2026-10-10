package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** AI 解析输出中的一个品牌 + 品类分组 */
@Data
public class AiParseGroupDTO {
    private String brand;
    private String category;
    /** 分组级询价话术，型号没有单独话术时使用 */
    private String inquiryTemplate;
    private List<AiParseItemDTO> items = new ArrayList<>();
}

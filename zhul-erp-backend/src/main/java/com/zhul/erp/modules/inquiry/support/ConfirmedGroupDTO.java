package com.zhul.erp.modules.inquiry.support;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 拆单确认后、用于创建一张 inquiry_order 的已核实分组（customerinquiry -> inquiryorder 的交接结构）。 */
@Data
public class ConfirmedGroupDTO {
    private String brand;
    private String category;
    private String inquiryTemplate;
    private String emailTemplateCn;
    private String emailTemplateEn;
    private List<ConfirmedItemDTO> items = new ArrayList<>();
}

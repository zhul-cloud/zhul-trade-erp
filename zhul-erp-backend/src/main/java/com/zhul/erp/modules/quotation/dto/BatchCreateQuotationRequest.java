package com.zhul.erp.modules.quotation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 按询盘批量生成报价单：每个询盘各生成一张，必须是同一客户 */
@Data
public class BatchCreateQuotationRequest {
    @NotEmpty(message = "请选择要报价的询盘")
    @Size(max = 20, message = "一次最多生成 20 张，请分批生成")
    private List<Long> inquiryIds;
}

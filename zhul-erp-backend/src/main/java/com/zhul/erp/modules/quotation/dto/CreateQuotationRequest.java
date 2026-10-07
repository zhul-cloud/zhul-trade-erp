package com.zhul.erp.modules.quotation.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 新建报价单：按询盘（inquiryIds，带入全部已有采购成本价的型号）或挑选型号（itemIds），二选一 */
@Data
public class CreateQuotationRequest {
    @Size(max = 50, message = "一次最多合并 50 个询盘")
    private List<Long> inquiryIds;
    @Size(max = 300, message = "一张报价单最多 300 个型号")
    private List<Long> itemIds;
}

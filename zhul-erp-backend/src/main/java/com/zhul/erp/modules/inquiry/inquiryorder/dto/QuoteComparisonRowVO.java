package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class QuoteComparisonRowVO {
    private Long inquiryOrderItemId;
    private String confirmedModel;
    /** key = inquiryOrderSupplierId，与 QuoteComparisonVO#columns 顺序对应 */
    private Map<Long, QuoteComparisonCellVO> cellsBySupplierId = new LinkedHashMap<>();
}

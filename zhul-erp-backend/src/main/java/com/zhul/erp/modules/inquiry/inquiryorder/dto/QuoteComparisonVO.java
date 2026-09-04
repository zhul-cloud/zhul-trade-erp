package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 报价对比表格（PRD 6.5.1）：行=型号，列=各已关联报价来源。 */
@Data
public class QuoteComparisonVO {
    private List<InquiryOrderSupplierVO> columns = new ArrayList<>();
    private List<QuoteComparisonRowVO> rows = new ArrayList<>();
}

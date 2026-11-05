package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 批量生成结果：生成的报价单与跳过的询盘 */
@Data
public class BatchCreateQuotationVO {
    private List<Created> created = new ArrayList<>(20);
    private List<Skipped> skipped = new ArrayList<>(20);

    @Data
    public static class Created {
        private Long quotationId;
        private String quotationNo;
        private Long inquiryId;
        private String inquiryCode;
        private Integer itemCount;
        private BigDecimal totalAmount;
        private String currencyCode;
        /** 还在询价、没有带入的型号数 */
        private Integer pendingCount;
    }

    @Data
    public static class Skipped {
        private Long inquiryId;
        private String inquiryCode;
        private String reason;
    }
}

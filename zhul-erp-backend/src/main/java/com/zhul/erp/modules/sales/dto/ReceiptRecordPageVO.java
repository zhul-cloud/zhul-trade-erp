package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 收款记录：分页列表与按筛选结果的汇总（线上、线下、合计） */
@Data
public class ReceiptRecordPageVO {
    private Long total;
    private List<ReceiptRowVO> records;
    private List<Summary> summary;

    @Data
    public static class Summary {
        /** 1-线下、2-线上、0-合计 */
        private Integer channel;
        private Long count;
        private List<Money> amounts;
        private List<Money> fees;
        private BigDecimal netAmountCny;
    }

    @Data
    public static class Money {
        private String currencyCode;
        private BigDecimal amount;
    }
}

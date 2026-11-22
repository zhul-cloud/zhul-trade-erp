package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 业务凭证月份详情：核对、凭证列表与分录 */
@Data
public class VoucherMonthDetailVO {
    private String month;
    private String currencyCode;
    private Boolean generated;
    private String generatedByName;
    private LocalDateTime generatedAt;
    private Integer exportCount;
    private String exportedByName;
    private LocalDateTime exportedAt;
    private Recon recon;
    /** 各类别凭证张数（键为类别） */
    private Map<Integer, Integer> categoryCounts;
    private BigDecimal debitTotal;
    /** 当月凭证用到、还没设置科目编码的业务项名称 */
    private List<String> missingItems;
    /** 库存月结的本月销售成本（不扣退货入库），与退货入库凭证金额，用于说明差异 */
    private BigDecimal periodCogsAmount;
    private BigDecimal returnInAmount;
    private List<Voucher> vouchers;

    /** 期初 + 凭证里库存商品净额 + 不生成凭证的变动 = 期末 */
    @Data
    public static class Recon {
        private BigDecimal openingAmount;
        private BigDecimal stockNetAmount;
        private BigDecimal noVoucherAmount;
        private BigDecimal closingAmount;
        private BigDecimal diff;
        private List<NoVoucherItem> noVoucherItems;
    }

    @Data
    public static class NoVoucherItem {
        private String bizType;
        private String name;
        private BigDecimal amount;
    }

    @Data
    public static class Voucher {
        private Long id;
        private LocalDate voucherDate;
        private Integer category;
        private String categoryName;
        private String sourceDocType;
        private Long sourceDocId;
        private String sourceDocNo;
        private String bizType;
        private String summary;
        /** 摘要拆开：动作、单号、往来单位，前端把单号做成链接 */
        private String action;
        private String partnerText;
        private BigDecimal debitTotal;
        private List<Entry> entries;
    }

    @Data
    public static class Entry {
        private String itemKey;
        private String itemName;
        private String accountCode;
        private String accountName;
        private Long partnerId;
        private String partnerName;
        private BigDecimal debit;
        private BigDecimal credit;
    }
}

package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 应收管理（spec finance/accounts-receivable）：有效销售订单里还没收齐的款。未收 = 订单合计 − 已到账 − 手续费差额（与订单详情同口径）；
 * 折人民币 = 未收 × 订单汇率，HALF_UP 保留 2 位。
 */
@Data
public class ReceivableVO {
    private Stats stats = new Stats();
    /** 有未收的订单：逾期的在前（逾期越久越前），再按约定付款日从近到远，没约定的在最后 */
    private List<Row> rows = new ArrayList<>();

    @Data
    public static class Stats {
        private BigDecimal totalCny = BigDecimal.ZERO;
        private int customers;
        private int orders;
        private BigDecimal overdueCny = BigDecimal.ZERO;
        private int overdueOrders;
        /** 7 天内到期（今天到 7 天后，不含已逾期） */
        private BigDecimal weekCny = BigDecimal.ZERO;
        private int weekOrders;
        /** 本月到期（今天到月底，不含已逾期） */
        private BigDecimal monthCny = BigDecimal.ZERO;
        private int monthOrders;
    }

    @Data
    public static class Row {
        private Long orderId;
        private String soNo;
        private Long piId;
        /** 来源单号：形式发票号，手动建的订单为空 */
        private String piNo;
        private LocalDate salesDate;
        private Long customerId;
        private String customerName;
        private String customerCountry;
        private String customerContact;
        private Long ownerId;
        private String ownerName;
        private String currencyCode;
        private BigDecimal totalAmount;
        private BigDecimal receivedAmount;
        private BigDecimal remainingAmount;
        private BigDecimal remainingCny;
        private LocalDate dueDate;
        /** 逾期天数，没逾期为空 */
        private Long overdueDays;
        /** 距到期天数（含今天到期为 0），已逾期或没约定为空 */
        private Long dueInDays;
    }
}

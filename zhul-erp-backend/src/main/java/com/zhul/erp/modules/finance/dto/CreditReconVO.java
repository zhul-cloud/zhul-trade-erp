package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 余额月度核对表：期初 + 转入 − 抵扣 − 提现 = 期末，原币与人民币各一套 */
@Data
public class CreditReconVO {
    private String month;
    private String currencyCode;
    private List<Row> rows;
    private Row total;
    /** 合计行是否平衡（期初 + 转入 − 抵扣 − 提现 = 期末） */
    private Boolean balanced;

    @Data
    public static class Row {
        private Long customerId;
        private String customerName;
        private Integer businessUnitId;
        private String businessUnitName;
        private Pair opening;
        private Pair refundIn;
        private Pair collectionIn;
        private Pair overpaidIn;
        private Pair adjust;
        /** 抵扣 = 本月占用 − 本月释放（正数表示用掉） */
        private Pair deduct;
        /** 提现（正数表示转出） */
        private Pair withdraw;
        private Pair closing;
    }

    @Data
    public static class Pair {
        private BigDecimal amount;
        private BigDecimal amountCny;

        public static Pair of(BigDecimal amount, BigDecimal amountCny) {
            Pair p = new Pair();
            p.setAmount(amount);
            p.setAmountCny(amountCny);
            return p;
        }
    }
}

package com.zhul.erp.modules.finance.support;

import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.masterdata.support.PaymentTerms;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 付款计划（纯函数，spec finance/accounts-payable「付款计划与到期」）：
 * 各期金额 = 应付金额 × 比例，HALF_UP 两位小数，尾差给最后一期；
 * 到期日按时点推算（下单后取下单日期、发货前取预计发货日期、入库后取全部入库日期 + N 天，还没全部入库为空）；
 * 净已付按到期先后（待入库排后、同日按期次）依次填满各期。
 */
public final class InstallmentPlanner {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private InstallmentPlanner() {
    }

    /** 一期付款条件：比例（百分比）、时点、入库后天数 */
    public record Term(BigDecimal percent, int trigger, int days) {
    }

    /** 推算日期用的事实：下单日期、预计发货日期、全部入库日期（没全部入库为空）、对账确认日期 */
    public record Dates(LocalDate orderDate, LocalDate expectedShipDate, LocalDate receivedDate, LocalDate confirmedDate) {
    }

    /** 一期：paid 与 status 由 {@link #allocate} 填 */
    public record Inst(int seq, BigDecimal ratio, int trigger, int days, BigDecimal amount, LocalDate dueDate,
                       BigDecimal paid, int status) {
        Inst withPaid(BigDecimal p) {
            int st = p.signum() <= 0 ? FinanceConstants.STATUS_UNPAID
                    : p.compareTo(amount) >= 0 ? FinanceConstants.STATUS_PAID : FinanceConstants.STATUS_PARTIAL;
            return new Inst(seq, ratio, trigger, days, amount, dueDate, p, st);
        }

        public BigDecimal unpaid() {
            return amount.subtract(paid).max(BigDecimal.ZERO);
        }
    }

    /** 按付款条件分期；没有付款条件时整笔为一期「下单后」 */
    public static List<Inst> plan(BigDecimal amount, List<Term> terms, Dates dates) {
        List<Term> list = terms == null || terms.isEmpty()
                ? List.of(new Term(HUNDRED, PaymentTerms.ON_ORDER, 0)) : terms;
        List<Inst> out = new ArrayList<>(list.size());
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < list.size(); i++) {
            Term t = list.get(i);
            BigDecimal part = i == list.size() - 1 ? amount.subtract(assigned)
                    : amount.multiply(t.percent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            assigned = assigned.add(part);
            out.add(new Inst(i + 1, t.percent(), t.trigger(), t.days(), part, dueDate(t, dates), BigDecimal.ZERO,
                    FinanceConstants.STATUS_UNPAID));
        }
        return out;
    }

    /** 货代应付：一期，对账确认当天到期 */
    public static List<Inst> single(BigDecimal amount, LocalDate dueDate) {
        return List.of(new Inst(1, HUNDRED, FinanceConstants.TRIGGER_STATEMENT, 0, amount, dueDate, BigDecimal.ZERO,
                FinanceConstants.STATUS_UNPAID));
    }

    static LocalDate dueDate(Term t, Dates d) {
        return switch (t.trigger()) {
            case PaymentTerms.ON_ORDER -> d.orderDate();
            case PaymentTerms.BEFORE_SHIPMENT -> d.expectedShipDate() != null ? d.expectedShipDate() : d.orderDate();
            case PaymentTerms.AFTER_RECEIPT -> d.receivedDate() == null ? null : d.receivedDate().plusDays(t.days());
            default -> d.confirmedDate();
        };
    }

    /** 核销顺序：有到期日的按日期，待入库的排后，同日按期次 */
    public static final Comparator<Inst> ORDER = Comparator
            .comparing((Inst i) -> i.dueDate() == null ? LocalDate.MAX : i.dueDate())
            .thenComparingInt(Inst::seq);

    /** 把净已付（不超过应付）按核销顺序填满各期；返回仍按期次排序 */
    public static List<Inst> allocate(List<Inst> insts, BigDecimal paid) {
        BigDecimal left = paid == null ? BigDecimal.ZERO : paid.max(BigDecimal.ZERO);
        List<Inst> ordered = new ArrayList<>(insts);
        ordered.sort(ORDER);
        List<Inst> filled = new ArrayList<>(insts.size());
        for (Inst i : ordered) {
            BigDecimal p = left.min(i.amount()).max(BigDecimal.ZERO);
            left = left.subtract(p);
            filled.add(i.withPaid(p));
        }
        filled.sort(Comparator.comparingInt(Inst::seq));
        return filled;
    }

    /** 最早一期有未付金额的到期日；只剩待入库的未付期次时为空 */
    public static LocalDate nextDue(List<Inst> insts) {
        return insts.stream().filter(i -> i.unpaid().signum() > 0 && i.dueDate() != null)
                .map(Inst::dueDate).min(Comparator.naturalOrder()).orElse(null);
    }
}

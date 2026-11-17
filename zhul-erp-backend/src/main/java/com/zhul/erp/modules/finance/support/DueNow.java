package com.zhul.erp.modules.finance.support;

import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.entity.PayableInstallmentDO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * 当期应付（spec finance/supplier-payment「登记付款」）：登记付款、批量付款的默认金额。
 * 已逾期、今天到期、{@link FinanceConstants#DUE_SOON_DAYS} 天内到期的各期未付之和；没有时取核销顺序中第一个有未付的期；
 * 没有付款计划时为全部未付。金额为原币，不超过应付单未付。
 */
public final class DueNow {

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");

    /** amount：默认付款金额；note：由哪几期组成，没有付款计划时为空 */
    public record Result(BigDecimal amount, String note) {
    }

    private DueNow() {
    }

    /**
     * @param unpaid      应付单未付（已关闭、已付清为 0）
     * @param insts       付款计划（按期号）
     * @param triggerName 期次的时点名称，如「下单后」「入库后 30 天」
     */
    public static Result of(BigDecimal unpaid, List<PayableInstallmentDO> insts, LocalDate today,
                            Function<PayableInstallmentDO, String> triggerName) {
        if (unpaid == null || unpaid.signum() <= 0) {
            return new Result(BigDecimal.ZERO, "");
        }
        List<PayableInstallmentDO> open = insts == null ? List.of() : insts.stream()
                .filter(i -> left(i).signum() > 0)
                // 核销顺序：有到期日的按到期日，待入库（没有到期日）排在后面，同一天按期号
                .sorted(Comparator.comparing((PayableInstallmentDO i) -> i.getDueDate() == null ? LocalDate.MAX : i.getDueDate())
                        .thenComparing(PayableInstallmentDO::getSeq))
                .toList();
        if (open.isEmpty()) {
            return new Result(unpaid, "");
        }
        LocalDate limit = today.plusDays(FinanceConstants.DUE_SOON_DAYS);
        List<PayableInstallmentDO> due = new ArrayList<>(open.stream()
                .filter(i -> i.getDueDate() != null && !i.getDueDate().isAfter(limit)).toList());
        if (due.isEmpty()) {
            due.add(open.get(0));
        }
        BigDecimal sum = due.stream().map(DueNow::left).reduce(BigDecimal.ZERO, BigDecimal::add).min(unpaid);
        List<String> parts = due.stream().map(i -> describe(i, triggerName.apply(i))).toList();
        return new Result(sum, String.join("；", parts));
    }

    private static BigDecimal left(PayableInstallmentDO i) {
        BigDecimal paid = i.getPaidAmount() == null ? BigDecimal.ZERO : i.getPaidAmount();
        return i.getAmount().subtract(paid).max(BigDecimal.ZERO);
    }

    /** 第 1 期 30% 下单后，11-10 到期；没有到期日的为「待入库」（对账确认的为「待对账」） */
    static String describe(PayableInstallmentDO i, String trigger) {
        String ratio = i.getRatio() == null ? "" : " " + i.getRatio().stripTrailingZeros().toPlainString() + "%";
        String when = i.getDueDate() != null ? i.getDueDate().format(MD) + " 到期"
                : Integer.valueOf(FinanceConstants.TRIGGER_STATEMENT).equals(i.getTriggerType()) ? "待对账" : "待入库";
        return "第 " + i.getSeq() + " 期" + ratio + " " + trigger + "，" + when;
    }
}

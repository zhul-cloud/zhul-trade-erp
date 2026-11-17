package com.zhul.erp.modules.finance;

import com.zhul.erp.modules.finance.entity.PayableInstallmentDO;
import com.zhul.erp.modules.finance.support.DueNow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** spec finance/supplier-payment「登记付款」：付款金额默认为当期应付 */
class DueNowTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    private static PayableInstallmentDO inst(int seq, String ratio, int trigger, String amount, String paid, LocalDate due) {
        PayableInstallmentDO i = new PayableInstallmentDO();
        i.setSeq(seq);
        i.setRatio(new BigDecimal(ratio));
        i.setTriggerType(trigger);
        i.setAmount(new BigDecimal(amount));
        i.setPaidAmount(new BigDecimal(paid));
        i.setDueDate(due);
        return i;
    }

    private static DueNow.Result of(String unpaid, List<PayableInstallmentDO> insts, LocalDate today) {
        return DueNow.of(new BigDecimal(unpaid), insts, today,
                i -> switch (i.getTriggerType()) {
                    case 1 -> "下单后";
                    case 2 -> "发货前";
                    case 5 -> "对账确认";
                    default -> "入库后 30 天";
                });
    }

    @Test
    void onlyDepositDue() {
        List<PayableInstallmentDO> plan = List.of(inst(1, "30.0000", 1, "3000.00", "0", LocalDate.of(2026, 11, 10)),
                inst(2, "70.0000", 2, "7000.00", "0", LocalDate.of(2026, 12, 5)));
        DueNow.Result r = of("10000.00", plan, TODAY);
        assertThat(r.amount()).isEqualByComparingTo("3000.00");
        assertThat(r.note()).isEqualTo("第 1 期 30% 下单后，11-10 到期");
    }

    @Test
    void balanceIncludedWhenShipDateWithinSevenDays() {
        List<PayableInstallmentDO> plan = List.of(inst(1, "30.0000", 1, "3000.00", "3000.00", LocalDate.of(2026, 11, 10)),
                inst(2, "70.0000", 2, "7000.00", "0", LocalDate.of(2026, 12, 5)));
        DueNow.Result r = of("7000.00", plan, LocalDate.of(2026, 11, 30));
        assertThat(r.amount()).isEqualByComparingTo("7000.00");
        assertThat(r.note()).isEqualTo("第 2 期 70% 发货前，12-05 到期");
    }

    @Test
    void severalDueAreSummedInSettlementOrder() {
        List<PayableInstallmentDO> plan = List.of(inst(1, "30.0000", 1, "300.00", "100.00", LocalDate.of(2026, 11, 1)),
                inst(2, "30.0000", 2, "300.00", "0", LocalDate.of(2026, 11, 24)),
                inst(3, "40.0000", 3, "400.00", "0", null));
        DueNow.Result r = of("900.00", plan, TODAY);
        assertThat(r.amount()).isEqualByComparingTo("500.00");
        assertThat(r.note()).isEqualTo("第 1 期 30% 下单后，11-01 到期；第 2 期 30% 发货前，11-24 到期");
    }

    @Test
    void nothingDueTakesEarliestInstallment() {
        DueNow.Result r = of("5000.00", List.of(inst(1, "100.0000", 3, "5000.00", "0", null)), TODAY);
        assertThat(r.amount()).isEqualByComparingTo("5000.00");
        assertThat(r.note()).isEqualTo("第 1 期 100% 入库后 30 天，待入库");
        // 有到期日的排在待入库前面
        DueNow.Result r2 = of("1000.00", List.of(inst(1, "50.0000", 3, "500.00", "0", null),
                inst(2, "50.0000", 2, "500.00", "0", LocalDate.of(2027, 1, 20))), TODAY);
        assertThat(r2.amount()).isEqualByComparingTo("500.00");
        assertThat(r2.note()).isEqualTo("第 2 期 50% 发货前，01-20 到期");
        assertThat(of("200.00", List.of(inst(1, "100.0000", 5, "200.00", "0", null)), TODAY).note())
                .isEqualTo("第 1 期 100% 对账确认，待对账");
    }

    @Test
    void noPlanPaidOrCappedByUnpaid() {
        assertThat(of("1234.50", List.of(), TODAY)).isEqualTo(new DueNow.Result(new BigDecimal("1234.50"), ""));
        assertThat(of("0", List.of(inst(1, "100.0000", 1, "100.00", "0", TODAY)), TODAY).amount()).isEqualByComparingTo("0");
        // 应付单未付少于期次未付（如欠款抵扣冲减）：不超过未付
        assertThat(of("80.00", List.of(inst(1, "100.0000", 1, "100.00", "0", TODAY)), TODAY).amount())
                .isEqualByComparingTo("80.00");
    }
}

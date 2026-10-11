package com.zhul.erp.modules.finance;

import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.support.InstallmentPlanner;
import com.zhul.erp.modules.finance.support.InstallmentPlanner.Dates;
import com.zhul.erp.modules.finance.support.InstallmentPlanner.Inst;
import com.zhul.erp.modules.finance.support.InstallmentPlanner.Term;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** spec finance/accounts-payable「付款计划与到期」 */
class InstallmentPlannerTest {

    private static final LocalDate ORDER = LocalDate.of(2026, 10, 12);
    private static final LocalDate SHIP = LocalDate.of(2026, 10, 15);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static Term t(String percent, int trigger, int days) {
        return new Term(d(percent), trigger, days);
    }

    @Test
    void splitWithTailOnLastInstallment() {
        List<Inst> plan = InstallmentPlanner.plan(d("100.00"), List.of(t("33", 1, 0), t("33", 2, 0), t("34", 3, 0)),
                new Dates(ORDER, SHIP, null, null));
        assertThat(plan).extracting(Inst::amount).containsExactly(d("33.00"), d("33.00"), d("34.00"));
        assertThat(plan).extracting(Inst::dueDate).containsExactly(ORDER, SHIP, null);
    }

    @Test
    void roundingHalfUpAndSumEqualsAmount() {
        List<Inst> plan = InstallmentPlanner.plan(d("54270.00"), List.of(t("30", 1, 0), t("70", 3, 0)), new Dates(ORDER, SHIP, null, null));
        assertThat(plan).extracting(Inst::amount).containsExactly(d("16281.00"), d("37989.00"));
        List<Inst> odd = InstallmentPlanner.plan(d("0.05"), List.of(t("50", 1, 0), t("50", 1, 0)), new Dates(ORDER, SHIP, null, null));
        assertThat(odd).extracting(Inst::amount).containsExactly(d("0.03"), d("0.02"));
    }

    @Test
    void dueDateAfterReceiptPlusDays() {
        LocalDate received = LocalDate.of(2026, 10, 20);
        List<Inst> plan = InstallmentPlanner.plan(d("1000"), List.of(t("100", 3, 30)), new Dates(ORDER, SHIP, received, null));
        assertThat(plan.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 11, 19));
    }

    @Test
    void noTermsMeansWholeAmountOnOrder() {
        List<Inst> plan = InstallmentPlanner.plan(d("10.00"), List.of(), new Dates(ORDER, null, null, null));
        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).dueDate()).isEqualTo(ORDER);
    }

    @Test
    void allocatePaidByDueDateWaitingLast() {
        // 第 1 期待入库，第 2 期下单后：先付第 2 期
        List<Inst> plan = InstallmentPlanner.plan(d("100.00"), List.of(t("70", 3, 0), t("30", 1, 0)), new Dates(ORDER, SHIP, null, null));
        List<Inst> filled = InstallmentPlanner.allocate(plan, d("50.00"));
        assertThat(filled.get(1).paid()).isEqualByComparingTo("30.00");
        assertThat(filled.get(1).status()).isEqualTo(FinanceConstants.STATUS_PAID);
        assertThat(filled.get(0).paid()).isEqualByComparingTo("20.00");
        assertThat(filled.get(0).status()).isEqualTo(FinanceConstants.STATUS_PARTIAL);
        assertThat(InstallmentPlanner.nextDue(filled)).isNull();
    }

    @Test
    void nextDueIsEarliestUnpaid() {
        List<Inst> plan = InstallmentPlanner.plan(d("100.00"), List.of(t("30", 1, 0), t("70", 2, 0)), new Dates(ORDER, SHIP, null, null));
        assertThat(InstallmentPlanner.nextDue(InstallmentPlanner.allocate(plan, BigDecimal.ZERO))).isEqualTo(ORDER);
        assertThat(InstallmentPlanner.nextDue(InstallmentPlanner.allocate(plan, d("30.00")))).isEqualTo(SHIP);
        assertThat(InstallmentPlanner.nextDue(InstallmentPlanner.allocate(plan, d("100.00")))).isNull();
    }

    @Test
    void zeroAndNegativePaid() {
        List<Inst> plan = InstallmentPlanner.single(d("3250.00"), ORDER);
        assertThat(InstallmentPlanner.allocate(plan, d("-5")).get(0).paid()).isEqualByComparingTo("0");
        assertThat(InstallmentPlanner.allocate(plan, null).get(0).status()).isEqualTo(FinanceConstants.STATUS_UNPAID);
        assertThat(InstallmentPlanner.allocate(plan, d("99999999.99")).get(0).paid()).isEqualByComparingTo("3250.00");
    }
}

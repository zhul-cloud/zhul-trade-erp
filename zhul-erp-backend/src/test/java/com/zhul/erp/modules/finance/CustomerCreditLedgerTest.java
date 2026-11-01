package com.zhul.erp.modules.finance;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.entity.CustomerCreditEntryDO;
import com.zhul.erp.modules.finance.service.CustomerCreditLedger;
import com.zhul.erp.modules.finance.service.CustomerCreditLedger.Account;
import com.zhul.erp.modules.finance.service.CustomerCreditLedger.Source;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** spec finance/customer-credit「余额流水」「余额按事业部隔离」：先进先出取价值、释放、冲销、余额不足、账户隔离 */
class CustomerCreditLedgerTest extends SalesContractSupport {

    @Autowired
    private CustomerCreditLedger ledger;

    private Account usd;

    @BeforeEach
    void account() {
        TenantContext.setTenantId(0);
        usd = new Account(customer("ACROBOT Automacao", "Brazil"), "USD", null);
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static void eq(String expected, BigDecimal actual) {
        assertEquals(0, d(expected).compareTo(actual), "期望 " + expected + "，实际 " + actual);
    }

    private CustomerCreditEntryDO in(Account a, String amount, String cny) {
        return ledger.credit(a, FinanceConstants.CREDIT_REFUND_IN, d(amount), d(cny), new Source("REFUND", 1L, "TK1"), "");
    }

    private CustomerCreditEntryDO occupy(Account a, String amount, String piNo) {
        return ledger.consume(a, FinanceConstants.CREDIT_OCCUPY, d(amount), new Source("PI", 9L, piNo), "");
    }

    @Test
    void fifoTakesOldestLockedValue_andPartialRemainderKeepsValue() {
        in(usd, "100", "710");
        in(usd, "100", "690");
        CustomerCreditEntryDO out = occupy(usd, "150", "FWPI20261205001");
        eq("-150", out.getAmount());
        eq("-1055.00", out.getAmountCny());
        eq("50", ledger.balance(usd).amount());
        eq("345.00", ledger.balance(usd).amountCny());
        // 剩下的 50 用完时取剩余价值，没有尾差
        eq("-345.00", occupy(usd, "50", "FWPI2").getAmountCny());
        eq("0", ledger.balance(usd).amount());
        eq("0", ledger.balance(usd).amountCny());
    }

    @Test
    void thirdsOfOddValue_sumExactly() {
        in(usd, "3", "10.00");
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < 3; i++) {
            total = total.add(occupy(usd, "1", "P" + i).getAmountCny());
        }
        eq("-10.00", total);
    }

    @Test
    void notEnough_reportsAvailableAndOccupier() {
        in(usd, "200", "1420");
        occupy(usd, "150", "FWPI20261205001");
        BizException e = assertThrows(BizException.class, () -> occupy(usd, "80", "FWPI2"));
        assertEquals("可用余额只有 USD 50.00，其余被 FWPI20261205001 等占用", e.getMessage());
        assertThrows(BizException.class, () -> occupy(usd, "0", "FWPI3"));
        assertThrows(BizException.class, () -> in(usd, "-1", "-7"));
    }

    @Test
    void releaseRestoresSameLotsAndValue() {
        in(usd, "100", "690");
        in(usd, "200", "1420");
        CustomerCreditEntryDO out = occupy(usd, "150", "FWPI1");
        assertEquals(2, ledger.release(out, FinanceConstants.CREDIT_RELEASE, "PI 关闭").size(), "消耗了两笔转入，释放两条");
        eq("300", ledger.balance(usd).amount());
        eq("2110.00", ledger.balance(usd).amountCny());
        // 再用时顺序与价值不变
        eq("-1045.00", occupy(usd, "150", "FWPI2").getAmountCny());
        // 已释放的不重复释放
        assertTrue(ledger.release(out, FinanceConstants.CREDIT_RELEASE, "").isEmpty());
    }

    @Test
    void reverseOnlyWhenUnused_evenAfterRelease() {
        CustomerCreditEntryDO refundIn = in(usd, "200", "1420");
        CustomerCreditEntryDO out = occupy(usd, "150", "FWPI20261205001");
        BizException e = assertThrows(BizException.class, () -> ledger.reverse(refundIn, "作废退款单"));
        assertEquals("余额已被 FWPI20261205001 使用，请先处理该单据", e.getMessage());
        ledger.release(out, FinanceConstants.CREDIT_RELEASE, "");
        ledger.reverse(refundIn, "作废退款单");
        eq("0", ledger.balance(usd).amount());
        eq("0", ledger.balance(usd).amountCny());
    }

    @Test
    void accountsAreIsolatedByCurrencyAndBusinessUnit() {
        in(usd, "100", "710");
        Account eur = new Account(usd.customerId(), "EUR", null);
        Account bu = new Account(usd.customerId(), "USD", 777);
        in(eur, "50", "390");
        eq("0", ledger.balance(bu).amount());
        assertThrows(BizException.class, () -> occupy(bu, "10", "P"));
        eq("100", ledger.balance(usd).amount());
        eq("50", ledger.balance(eur).amount());
    }

    @Test
    void hugeAmountsKeepPrecision() {
        in(usd, "1000000000000000.00", "7100000000000000.00");
        eq("-2366666666666666.64", occupy(usd, "333333333333333.33", "P").getAmountCny());
    }
}

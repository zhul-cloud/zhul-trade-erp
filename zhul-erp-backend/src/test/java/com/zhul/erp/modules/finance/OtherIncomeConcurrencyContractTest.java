package com.zhul.erp.modules.finance;

import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.SaveOtherIncomeRequest;
import com.zhul.erp.modules.finance.service.OtherIncomeService;
import com.zhul.erp.modules.system.service.DictItemService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * audit BUS-012：编辑已读到有效记录、还没保存时另一请求把它作废，编辑不能把记录写回有效。
 * 用 SpyBean 把编辑卡在「读完记录、校验类别」处，再执行作废，复现两个请求交错。
 */
class OtherIncomeConcurrencyContractTest extends IntegrationTestBase {

    private static final int TENANT = 99000096;

    @Autowired private OtherIncomeService service;
    @SpyBean private DictItemService dictItemService;

    private long id;

    @BeforeEach
    void data() {
        cleanup();
        loginAsAdmin("it_income_admin");
        jdbc.update("insert into fin_other_income (tenant_id, income_no, income_date, category, amount, remark, status, void_reason) "
                + "values (?, 'ITOI-RACE', ?, 'REBATE', 100.00, '返点', ?, '')", TENANT, LocalDate.now(), FinanceConstants.RECORD_VALID);
        id = jdbc.queryForObject("select id from fin_other_income where income_no = 'ITOI-RACE'", Long.class);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from fin_other_income where tenant_id = ?", TENANT);
    }

    @Test
    void voidDuringEdit_staysVoid() throws Exception {
        CountDownLatch editRead = new CountDownLatch(1);
        CountDownLatch resumeEdit = new CountDownLatch(1);
        AtomicReference<Thread> editThread = new AtomicReference<>();
        doAnswer(inv -> {
            if (Thread.currentThread() == editThread.get()) {
                editRead.countDown();
                resumeEdit.await(10, TimeUnit.SECONDS);
            }
            return inv.callRealMethod();
        }).when(dictItemService).listByDictType(eq(FinanceConstants.DICT_INCOME_CATEGORY));

        SecurityContext auth = SecurityContextHolder.getContext();
        SaveOtherIncomeRequest req = new SaveOtherIncomeRequest();
        req.setIncomeDate(LocalDate.now());
        req.setCategory("REBATE");
        req.setAmount(new BigDecimal("100"));
        req.setRemark("返点（改）");

        CompletableFuture<Void> edit = CompletableFuture.runAsync(() -> as(auth, () -> {
            editThread.set(Thread.currentThread());
            service.update(id, req);
        }));
        assertTrue(editRead.await(10, TimeUnit.SECONDS), "编辑应已读到记录");
        CompletableFuture<Void> voiding = CompletableFuture.runAsync(() -> as(auth, () -> service.voidIncome(id, "重复登记")));
        // 修复前作废不受阻、先提交；修复后作废等编辑的行锁，这里等一会儿再放行编辑
        try {
            voiding.get(1500, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException expected) {
            // 作废在等编辑释放行锁
        }
        resumeEdit.countDown();
        edit.get(15, TimeUnit.SECONDS);
        voiding.get(15, TimeUnit.SECONDS);

        assertEquals(FinanceConstants.RECORD_VOID,
                jdbc.queryForObject("select status from fin_other_income where id = ?", Integer.class, id), "最终仍是作废");
        assertEquals("重复登记", jdbc.queryForObject("select void_reason from fin_other_income where id = ?", String.class, id));
    }

    private static void as(SecurityContext auth, Runnable action) {
        SecurityContextHolder.setContext(auth);
        TenantContext.setTenantId(TENANT);
        try {
            action.run();
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}

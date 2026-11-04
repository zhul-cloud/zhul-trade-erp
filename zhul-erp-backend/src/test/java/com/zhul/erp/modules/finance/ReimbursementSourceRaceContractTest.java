package com.zhul.erp.modules.finance;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.SubmitReimbursementRequest;
import com.zhul.erp.modules.finance.service.ExpenseService;
import com.zhul.erp.modules.finance.service.ReimbursementService;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * audit BUS-011：报销提交已读到有效费用、还没写报销明细时，另一请求作废这张费用单。
 * 两者只能成功一个：不能出现「费用已作废，却在待付款报销单里」。用 SpyBean 让提交停在取报销编号处。
 */
class ReimbursementSourceRaceContractTest extends IntegrationTestBase {

    private static final int TENANT = 99000097;
    private static final long ME = 99000002L;

    @Autowired private ReimbursementService reimbursements;
    @Autowired private ExpenseService expenses;
    @SpyBean private DocumentNumberService numbers;

    private long expense;

    @BeforeEach
    void data() {
        cleanup();
        testUserTenant = TENANT;
        loginAsAdmin("it_bx_race");
        jdbc.update("insert into fin_expense (tenant_id, expense_no, name, occur_date, payee_type, payee_name, currency_code, amount, "
                + "amount_cny, method, advance_user_id, status, void_reason) values (?, 'ITFE-RACE', '补发运费', ?, 4, '顺丰', 'CNY', "
                + "100.00, 100.00, ?, ?, ?, '')", TENANT, LocalDate.now(), FinanceConstants.METHOD_ADVANCE, ME, FinanceConstants.FE_VALID);
        expense = jdbc.queryForObject("select id from fin_expense where expense_no = 'ITFE-RACE'", Long.class);
        jdbc.update("insert into biz_attachment (tenant_id, owner_type, owner_id, kind, storage, file_key, url, file_name, file_size, "
                + "content_type, processing_status, uploaded_by) values (?, 'EXPENSE', ?, 1, 'LOCAL', 'it/bx/a.png', '', 'a.png', 10, "
                + "'image/png', 0, ?)", TENANT, expense, ME);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from fin_reimbursement_line where tenant_id = ?", TENANT);
        jdbc.update("delete from fin_reimbursement where tenant_id = ?", TENANT);
        jdbc.update("delete from biz_attachment where tenant_id = ?", TENANT);
        jdbc.update("delete from fin_expense where tenant_id = ?", TENANT);
    }

    @Test
    void voidExpenseDuringSubmit_onlyOneSucceeds() throws Exception {
        CountDownLatch submitRead = new CountDownLatch(1);
        CountDownLatch resumeSubmit = new CountDownLatch(1);
        AtomicReference<Thread> submitThread = new AtomicReference<>();
        doAnswer(inv -> {
            if (Thread.currentThread() == submitThread.get()) {
                submitRead.countDown();
                resumeSubmit.await(10, TimeUnit.SECONDS);
            }
            return inv.callRealMethod();
        }).when(numbers).next(eq(DocumentType.BX));

        SecurityContext auth = SecurityContextHolder.getContext();
        SubmitReimbursementRequest req = new SubmitReimbursementRequest();
        SubmitReimbursementRequest.Item item = new SubmitReimbursementRequest.Item();
        item.setSourceType(FinanceConstants.ITEM_EXPENSE);
        item.setSourceId(expense);
        req.setItems(List.of(item));

        CompletableFuture<Void> submit = CompletableFuture.runAsync(() -> as(auth, () -> {
            submitThread.set(Thread.currentThread());
            reimbursements.submit(req);
        }));
        assertTrue(submitRead.await(10, TimeUnit.SECONDS), "提交应已读到待报销费用");
        CompletableFuture<Void> voiding = CompletableFuture.runAsync(() -> as(auth, () -> expenses.voidExpense(expense, "录错了")));
        try {
            voiding.get(1500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException waitingOrRejected) {
            // 修复后作废在等提交释放行锁，放行提交后再看结果
        }
        resumeSubmit.countDown();
        submit.get(15, TimeUnit.SECONDS);
        ExecutionException rejected = null;
        try {
            voiding.get(15, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            rejected = e;
        }

        int status = jdbc.queryForObject("select status from fin_expense where id = ?", Integer.class, expense);
        int lines = jdbc.queryForObject("select count(*) from fin_reimbursement_line where tenant_id = ? and source_type = ? "
                + "and source_id = ? and deleted_at is null", Integer.class, TENANT, FinanceConstants.ITEM_EXPENSE, expense);
        assertEquals(1, lines, "报销单已提交");
        assertEquals(FinanceConstants.FE_VALID, status, "已进入报销单的费用不能被作废");
        assertTrue(rejected != null, "作废应被拒绝");
        assertInstanceOf(BizException.class, rejected.getCause());
        assertEquals("已在报销单里，不能作废", rejected.getCause().getMessage());
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

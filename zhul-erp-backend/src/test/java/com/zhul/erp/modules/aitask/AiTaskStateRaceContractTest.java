package com.zhul.erp.modules.aitask;

import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.dto.AiCallConfig;
import com.zhul.erp.modules.aitask.dto.AiOrchestratorSubmitResult;
import com.zhul.erp.modules.aitask.dto.AiTaskCallbackRequest;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.repository.AiTaskMapper;
import com.zhul.erp.modules.aitask.service.AiConfigService;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.modules.aitask.service.AiTaskResultDispatcher;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.aitask.service.impl.AiTaskTimeoutWatchdog;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * audit AI-001：受理结果、回调与超时扫描交错时，结束的任务不被改回，任务与询盘状态一致。
 * 迟到的结果按用户确认的规则丢弃（2026-11-04）；重复回调只通知业务一次。
 */
class AiTaskStateRaceContractTest extends IntegrationTestBase {

    private static final int TENANT = 99000094;
    private static final String NOOP_SKILL = "it-state-race";
    private static final String PARSE_SKILL = "inquiry-parse-and-split";
    private static final String WATCHDOG_THREAD = "it-ai-watchdog";

    @Autowired private AiTaskService aiTaskService;
    @Autowired private AiTaskTimeoutWatchdog watchdog;
    @MockBean private AiOrchestratorClient orchestratorClient;
    @MockBean private AiConfigService aiConfigService;
    @Autowired private AiTaskMapper aiTaskMapper;
    @SpyBean private AiTaskResultDispatcher resultDispatcher;

    @BeforeEach
    void data() {
        cleanup();
        jdbc.update("insert into tenant (id, name, status) values (?, 'AI 状态测试租户', 1)", TENANT);
        when(aiConfigService.resolveForCurrentTenant()).thenReturn(AiCallConfig.builder().mode("api").provider("claude")
                .apiKey("sk-test").parseModel("m").translateModel("m").parseEffort("low").translateEffort("low")
                .maxWebSearches(1).build());
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        jdbc.update("delete from customer_inquiry where tenant_id = ?", TENANT);
        jdbc.update("delete from ai_task where tenant_id = ?", TENANT);
        jdbc.update("delete from tenant where id = ?", TENANT);
    }

    /** 插入一个 30 分钟前创建、处理中的任务（超过默认 20 分钟的超时） */
    private long staleTask(String skill) {
        jdbc.update("insert into ai_task (tenant_id, skill_id, status, create_time) values (?, ?, 2, now() - interval 30 minute)",
                TENANT, skill);
        return jdbc.queryForObject("select max(id) from ai_task where tenant_id = ?", Long.class, TENANT);
    }

    private int status(long task) {
        return jdbc.queryForObject("select status from ai_task where id = ?", Integer.class, task);
    }

    private static AiTaskCallbackRequest success() {
        AiTaskCallbackRequest req = new AiTaskCallbackRequest();
        req.setStatus("success");
        req.setOutput(Map.of("ok", true));
        return req;
    }

    /** 通知业务的次数，按状态统计 */
    private long dispatched(long task, int status) {
        ArgumentCaptor<AiTaskDO> captor = ArgumentCaptor.forClass(AiTaskDO.class);
        verify(resultDispatcher, atLeast(0)).dispatch(captor.capture());
        return captor.getAllValues().stream().filter(t -> t.getId() == task && t.getStatus() == status).count();
    }

    @Test
    void acceptedAfterCallback_doesNotReopenTask() {
        CompletableFuture<AiOrchestratorSubmitResult> accepted = new CompletableFuture<>();
        when(orchestratorClient.submit(anyString(), anyLong(), anyString(), any())).thenReturn(accepted);
        TenantContext.setTenantId(TENANT);
        long task = aiTaskService.createAndSubmit(NOOP_SKILL, "{}", 1L).getId();
        TenantContext.clear();

        aiTaskService.handleCallback(task, success());
        assertEquals(AiTaskStatus.COMPLETED, status(task));

        accepted.complete(AiOrchestratorSubmitResult.accepted("job-1"));
        assertEquals(AiTaskStatus.COMPLETED, status(task), "回调已先完成，晚到的受理结果不能把任务改回处理中");
    }

    @Test
    void callbackWhileWatchdogHoldsStaleSnapshot_watchdogDoesNotOverwrite() throws Exception {
        long task = staleTask(NOOP_SKILL);
        CountDownLatch selected = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // MyBatis 的 mapper 是代理，SpyBean 包不住：用委托给真实 mapper 的对象，在查完、更新前停住
        AiTaskMapper paused = mock(AiTaskMapper.class, AdditionalAnswers.delegatesTo(aiTaskMapper));
        AiTaskTimeoutWatchdog scanner = new AiTaskTimeoutWatchdog(paused, resultDispatcher);
        ReflectionTestUtils.setField(scanner, "timeoutMinutes", 20);
        doAnswer(inv -> {
            Object rows = aiTaskMapper.selectList(inv.getArgument(0));
            if (WATCHDOG_THREAD.equals(Thread.currentThread().getName())) {
                selected.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
            }
            return rows;
        }).when(paused).selectList(any());
        clearInvocations(resultDispatcher);

        Thread scan = new Thread(scanner::checkTimeouts, WATCHDOG_THREAD);
        scan.start();
        assertTrue(selected.await(10, TimeUnit.SECONDS), "超时扫描已查到处理中的任务");
        aiTaskService.handleCallback(task, success());
        release.countDown();
        scan.join(10_000);

        assertEquals(AiTaskStatus.COMPLETED, status(task), "扫描按旧快照不能把已完成的任务写成失败");
        assertEquals(0, dispatched(task, AiTaskStatus.FAILED), "没有抢到更新的扫描不通知业务失败");
    }

    @Test
    void lateSuccessAfterTimeout_isDiscardedAndInquiryStaysConsistent() {
        long task = staleTask(PARSE_SKILL);
        jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, inquiry_date, quote_deadline, status, ai_task_id) "
                + "values (?, 'ITRACE-1', curdate(), curdate(), 2, ?)", TENANT, task);

        watchdog.checkTimeouts();
        assertEquals(AiTaskStatus.FAILED, status(task));

        aiTaskService.handleCallback(task, success());

        assertEquals(AiTaskStatus.FAILED, status(task), "超时后迟到的成功结果丢弃，任务保持失败");
        assertEquals(4, jdbc.queryForObject("select status from customer_inquiry where ai_task_id = ?", Integer.class, task),
                "询盘保持解析失败，与任务一致");
    }

    @Test
    void duplicateCallback_dispatchesOnce() {
        long task = staleTask(NOOP_SKILL);
        clearInvocations(resultDispatcher);

        aiTaskService.handleCallback(task, success());
        aiTaskService.handleCallback(task, success());

        assertEquals(AiTaskStatus.COMPLETED, status(task));
        assertEquals(1, dispatched(task, AiTaskStatus.COMPLETED), "编排服务重投同一结果时只通知业务一次");
    }

    @Test
    void rejectedAfterCallback_doesNotFailCompletedTask() {
        CompletableFuture<AiOrchestratorSubmitResult> rejected = new CompletableFuture<>();
        when(orchestratorClient.submit(anyString(), anyLong(), anyString(), any())).thenReturn(rejected);
        TenantContext.setTenantId(TENANT);
        long task = aiTaskService.createAndSubmit(NOOP_SKILL, "{}", 1L).getId();
        TenantContext.clear();
        aiTaskService.handleCallback(task, success());
        clearInvocations(resultDispatcher);

        rejected.complete(AiOrchestratorSubmitResult.rejected("连接超时"));

        assertEquals(AiTaskStatus.COMPLETED, status(task), "已完成的任务不被提交失败改写");
        assertEquals(0, dispatched(task, AiTaskStatus.FAILED));
        assertEquals(List.of(), jdbc.queryForList("select id from ai_task where id = ? and error_message <> ''", Long.class, task));
    }

    private static AiTaskCallbackRequest progress(int done, int total) {
        AiTaskCallbackRequest req = new AiTaskCallbackRequest();
        req.setStatus("progress");
        req.setOutput(Map.of("done", done, "total", total));
        return req;
    }

    /** 插入一个处理中的任务：创建于 createdAgo 分钟前，最近一次进度报告在 progressAgo 分钟前（null 表示没有报告过） */
    private long runningTask(int createdAgo, Integer progressAgo) {
        jdbc.update("insert into ai_task (tenant_id, skill_id, status, create_time, last_progress_at, progress_done, progress_total) "
                        + "values (?, ?, 2, now() - interval ? minute, "
                        + "if(? is null, null, now() - interval ? minute), 1, 9)",
                TENANT, NOOP_SKILL, createdAgo, progressAgo, progressAgo == null ? 0 : progressAgo);
        return jdbc.queryForObject("select max(id) from ai_task where tenant_id = ?", Long.class, TENANT);
    }

    @Test
    void progressWhileRunning_updatesProgressOnly() {
        long task = runningTask(5, null);
        clearInvocations(resultDispatcher);

        aiTaskService.handleCallback(task, progress(3, 9));

        Map<String, Object> row = jdbc.queryForMap(
                "select status, progress_done, progress_total, last_progress_at from ai_task where id = ?", task);
        assertEquals(AiTaskStatus.PROCESSING, ((Number) row.get("status")).intValue(), "进度报告不改变状态");
        assertEquals(3, ((Number) row.get("progress_done")).intValue());
        assertEquals(9, ((Number) row.get("progress_total")).intValue());
        assertTrue(row.get("last_progress_at") != null);
        assertEquals(0, dispatched(task, AiTaskStatus.COMPLETED) + dispatched(task, AiTaskStatus.FAILED), "进度不通知业务");
    }

    @Test
    void progressAfterFinish_isIgnored() {
        long task = runningTask(5, null);
        aiTaskService.handleCallback(task, success());

        aiTaskService.handleCallback(task, progress(8, 9));

        assertEquals(AiTaskStatus.COMPLETED, status(task));
        assertEquals(1, jdbc.queryForObject("select progress_done from ai_task where id = ?", Integer.class, task),
                "任务结束后到达的进度报告忽略");
    }

    @Test
    void timeout_countsFromLatestProgress_andHasMaxDuration() {
        long reporting = runningTask(50, 3);
        long silent = runningTask(50, 25);
        long noProgress = runningTask(30, null);
        long overMax = runningTask(181, 1);
        clearInvocations(resultDispatcher);

        watchdog.checkTimeouts();

        assertEquals(AiTaskStatus.PROCESSING, status(reporting), "运行 50 分钟但 3 分钟前还在报告进度，不算超时");
        assertEquals(AiTaskStatus.FAILED, status(silent), "最近一次进度在 25 分钟前，超过 20 分钟判超时");
        assertEquals(AiTaskStatus.FAILED, status(noProgress), "没有任何进度报告时从创建时间算起");
        assertEquals(AiTaskStatus.FAILED, status(overMax), "超过最长处理时长 180 分钟，即使还在报告进度也判超时");
        assertTrue(jdbc.queryForObject("select error_message from ai_task where id = ?", String.class, overMax)
                .contains("最长处理时长180分钟"));
        for (long task : List.of(silent, noProgress, overMax)) {
            assertEquals(1, dispatched(task, AiTaskStatus.FAILED), "超时的任务都会分发失败结果");
        }
        assertEquals(0, dispatched(reporting, AiTaskStatus.FAILED));
    }
}

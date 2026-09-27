package com.zhul.erp.modules.aitask.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.dto.AiOrchestratorSubmitResult;
import com.zhul.erp.modules.aitask.dto.AiTaskCallbackRequest;
import com.zhul.erp.modules.aitask.dto.AiTaskVO;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.repository.AiTaskMapper;
import com.zhul.erp.modules.aitask.service.impl.AiTaskServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对应 specs/ai-platform/task-orchestration/spec.md：
 * - 创建通用 AI 任务
 * - 提交任务给 AI 编排服务不阻塞等待
 * - 接收 webhook 回调更新任务状态
 * - 按 skillId 路由处理结果
 */
@ExtendWith(MockitoExtension.class)
class AiTaskServiceImplTest {

    @Mock
    private AiTaskMapper aiTaskMapper;
    @Mock
    private AiOrchestratorClient orchestratorClient;
    @Mock
    private AiTaskResultDispatcher resultDispatcher;

    private AiTaskServiceImpl aiTaskService;

    @BeforeEach
    void setUp() {
        aiTaskService = new AiTaskServiceImpl(aiTaskMapper, orchestratorClient, resultDispatcher, new ObjectMapper());
        TenantContext.setTenantId(1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_createsTaskWithQueuedStatus() {
        AiTaskDO created = aiTaskService.create("inquiry-parse-and-split", "{\"raw\":\"text\"}", 1000L);

        assertThat(created.getStatus()).isEqualTo(AiTaskStatus.QUEUED);
        assertThat(created.getSkillId()).isEqualTo("inquiry-parse-and-split");
        assertThat(created.getRequestedBy()).isEqualTo(1000L);
        verify(aiTaskMapper, times(1)).insert(any(AiTaskDO.class));
    }

    @Test
    void createAndSubmit_doesNotBlockOnOrchestratorCall() {
        // 模拟 MyBatis-Plus insert() 真实行为：insert 后把生成的主键回填到实体上
        doAnswer(invocation -> {
            AiTaskDO arg = invocation.getArgument(0);
            arg.setId(42L);
            return 1;
        }).when(aiTaskMapper).insert(any(AiTaskDO.class));

        CompletableFuture<AiOrchestratorSubmitResult> pending = new CompletableFuture<>();
        when(orchestratorClient.submit(eq("inquiry-parse-and-split"), eq(42L), eq("{}"))).thenReturn(pending);

        long start = System.nanoTime();
        AiTaskVO result = aiTaskService.createAndSubmit("inquiry-parse-and-split", "{}", 1000L);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(result).isNotNull();
        assertThat(elapsedMs).isLessThan(500);
        // 手动完成 future，模拟 AI 编排服务稍后受理成功，任务应转为处理中
        pending.complete(AiOrchestratorSubmitResult.accepted("job-1"));
        ArgumentCaptor<AiTaskDO> captor = ArgumentCaptor.forClass(AiTaskDO.class);
        verify(aiTaskMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AiTaskStatus.PROCESSING);
    }

    @Test
    void createAndSubmit_withActiveTransaction_deferSubmitUntilAfterCommit() {
        // 复现真实事故：create() 插入的 ai_task 还在事务里没提交，submit() 却立刻发起了
        // 异步 HTTP 请求；如果 AI 编排服务响应快到回调能在事务提交前打回来（真实场景：
        // 客户询盘走图片/Excel录入、rawContent为空，编排服务同步立刻拒绝），
        // handleCallback() 在另一个数据库连接里查不到这条未提交的记录，回调直接丢失，
        // 只能等 AiTaskTimeoutWatchdog 几十分钟后兜底、报出跟真实原因无关的超时提示。
        // 修复：submit() 必须注册在事务提交之后才真正调用编排服务。
        doAnswer(invocation -> {
            AiTaskDO arg = invocation.getArgument(0);
            arg.setId(44L);
            return 1;
        }).when(aiTaskMapper).insert(any(AiTaskDO.class));
        when(orchestratorClient.submit(any(), any(), any())).thenReturn(new CompletableFuture<>());

        TransactionSynchronizationManager.initSynchronization();
        try {
            aiTaskService.createAndSubmit("inquiry-parse-and-split", "{}", 1000L);

            // 事务还没提交——不应该已经调用编排服务
            verify(orchestratorClient, times(0)).submit(any(), any(), any());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // 事务提交后，才真正发起提交
        verify(orchestratorClient, times(1)).submit(eq("inquiry-parse-and-split"), eq(44L), eq("{}"));
    }

    @Test
    void createAndSubmit_whenOrchestratorRejects_marksFailedAndDispatches() {
        // 复现真实踩过的坑：提交阶段就失败（比如连不上 AI 编排服务）时，之前只更新了
        // ai_task 自身状态，没有像 handleCallback() 一样调用 resultDispatcher，导致
        // customer_inquiry 等发起方永远收不到失败通知、一直卡在"解析中"。
        doAnswer(invocation -> {
            AiTaskDO arg = invocation.getArgument(0);
            arg.setId(43L);
            return 1;
        }).when(aiTaskMapper).insert(any(AiTaskDO.class));

        CompletableFuture<AiOrchestratorSubmitResult> pending = new CompletableFuture<>();
        when(orchestratorClient.submit(eq("inquiry-parse-and-split"), eq(43L), eq("{}"))).thenReturn(pending);

        aiTaskService.createAndSubmit("inquiry-parse-and-split", "{}", 1000L);
        pending.complete(AiOrchestratorSubmitResult.rejected("java.net.ConnectException"));

        ArgumentCaptor<AiTaskDO> updateCaptor = ArgumentCaptor.forClass(AiTaskDO.class);
        verify(aiTaskMapper, times(1)).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getStatus()).isEqualTo(AiTaskStatus.FAILED);
        assertThat(updateCaptor.getValue().getErrorMessage()).isEqualTo("java.net.ConnectException");

        ArgumentCaptor<AiTaskDO> dispatchCaptor = ArgumentCaptor.forClass(AiTaskDO.class);
        verify(resultDispatcher, times(1)).dispatch(dispatchCaptor.capture());
        assertThat(dispatchCaptor.getValue().getId()).isEqualTo(43L);
        assertThat(dispatchCaptor.getValue().getStatus()).isEqualTo(AiTaskStatus.FAILED);
        assertThat(dispatchCaptor.getValue().getSkillId()).isEqualTo("inquiry-parse-and-split");
    }

    @Test
    void handleCallback_success_marksCompletedAndDispatches() {
        AiTaskDO existing = new AiTaskDO();
        existing.setId(1L);
        existing.setSkillId("inquiry-parse-and-split");
        when(aiTaskMapper.selectById(1L)).thenReturn(existing);

        AiTaskCallbackRequest req = new AiTaskCallbackRequest();
        req.setStatus("success");
        req.setOutput(java.util.Map.of("groups", java.util.List.of()));

        aiTaskService.handleCallback(1L, req);

        ArgumentCaptor<AiTaskDO> captor = ArgumentCaptor.forClass(AiTaskDO.class);
        verify(aiTaskMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AiTaskStatus.COMPLETED);
        assertThat(captor.getValue().getOutput()).contains("groups");

        ArgumentCaptor<AiTaskDO> dispatched = ArgumentCaptor.forClass(AiTaskDO.class);
        verify(resultDispatcher, times(1)).dispatch(dispatched.capture());
        assertThat(dispatched.getValue().getStatus()).isEqualTo(AiTaskStatus.COMPLETED);
        assertThat(dispatched.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    void handleCallback_failed_marksFailedWithErrorMessageAndDispatches() {
        AiTaskDO existing = new AiTaskDO();
        existing.setId(2L);
        existing.setSkillId("inquiry-parse-and-split");
        when(aiTaskMapper.selectById(2L)).thenReturn(existing);

        AiTaskCallbackRequest req = new AiTaskCallbackRequest();
        req.setStatus("failed");
        req.setError("联网搜索超时");

        aiTaskService.handleCallback(2L, req);

        ArgumentCaptor<AiTaskDO> captor = ArgumentCaptor.forClass(AiTaskDO.class);
        verify(aiTaskMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AiTaskStatus.FAILED);
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("联网搜索超时");
        verify(resultDispatcher, times(1)).dispatch(any(AiTaskDO.class));
    }

    @Test
    void handleCallback_taskNotFound_throwsBizException() {
        when(aiTaskMapper.selectById(999L)).thenReturn(null);
        AiTaskCallbackRequest req = new AiTaskCallbackRequest();
        req.setStatus("success");

        assertThrows(BizException.class, () -> aiTaskService.handleCallback(999L, req));
    }

    @Test
    void getById_whenFound_returnsVo() {
        AiTaskDO task = new AiTaskDO();
        task.setId(5L);
        task.setSkillId("inquiry-parse-and-split");
        task.setStatus(AiTaskStatus.COMPLETED);
        when(aiTaskMapper.selectById(5L)).thenReturn(task);

        AiTaskVO vo = aiTaskService.getById(5L);

        assertThat(vo.getSkillId()).isEqualTo("inquiry-parse-and-split");
    }

    @Test
    void getById_whenNotFound_throwsBizException() {
        when(aiTaskMapper.selectById(999L)).thenReturn(null);

        assertThrows(BizException.class, () -> aiTaskService.getById(999L));
    }
}

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

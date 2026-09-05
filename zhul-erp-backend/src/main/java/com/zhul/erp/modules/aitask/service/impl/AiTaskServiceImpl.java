package com.zhul.erp.modules.aitask.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.dto.AiTaskCallbackRequest;
import com.zhul.erp.modules.aitask.dto.AiTaskVO;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.repository.AiTaskMapper;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.modules.aitask.service.AiTaskResultDispatcher;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Slf4j
@Service
public class AiTaskServiceImpl implements AiTaskService {

    private final AiTaskMapper aiTaskMapper;
    private final AiOrchestratorClient orchestratorClient;
    // @Lazy 打破 AiTaskServiceImpl -> AiTaskResultDispatcher -> InquiryParseResultHandler
    // -> CustomerInquiryServiceImpl -> AiTaskService 的 Bean 循环依赖；dispatch() 只在收到
    // 回调时才需要真正用到这个 Bean，延迟解析不影响运行时行为。
    // 手写构造函数（而不是 @RequiredArgsConstructor）：Lombok 默认不会把 @Lazy 这类第三方
    // 注解从字段复制到生成的构造函数参数上，那样 Spring 看不到 @Lazy，循环依赖检测仍会报错。
    private final AiTaskResultDispatcher resultDispatcher;
    private final ObjectMapper objectMapper;

    public AiTaskServiceImpl(AiTaskMapper aiTaskMapper,
                              AiOrchestratorClient orchestratorClient,
                              @Lazy AiTaskResultDispatcher resultDispatcher,
                              ObjectMapper objectMapper) {
        this.aiTaskMapper = aiTaskMapper;
        this.orchestratorClient = orchestratorClient;
        this.resultDispatcher = resultDispatcher;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskVO createAndSubmit(String skillId, String inputJson, Long requestedBy) {
        AiTaskDO task = create(skillId, inputJson, requestedBy);
        submit(task, inputJson);
        return toVo(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDO create(String skillId, String inputJson, Long requestedBy) {
        AiTaskDO task = new AiTaskDO();
        task.setTenantId(currentTenantId());
        task.setSkillId(skillId);
        task.setStatus(AiTaskStatus.QUEUED);
        task.setRequestedBy(requestedBy);
        aiTaskMapper.insert(task);
        return task;
    }

    /**
     * 提交给 AI 编排服务；不等待处理完成，受理结果通过回调更新任务状态。
     * inputJson 只用于本次提交请求体，不落库——ai_task 表按 design.md/PRD 的字段定义
     * 只存 output（处理结果），不存 input，输入数据的留存由发起方（如 customer_inquiry
     * 的 raw_content）自行负责。
     *
     * <p>真实踩过的坑：这里发起的是异步 HTTP 调用，而 create() 插入的 ai_task 这时候
     * 还在 createAndSubmit() 所在的事务里、尚未提交。如果 AI 编排服务响应快到回调能在
     * 事务提交前打回来（比如编排服务同步校验后立刻判定失败，不需要真正跑一次 AI），
     * handleCallback() 用另一个数据库连接查 {@code aiTaskMapper.selectById(taskId)}
     * 会因为看不到未提交的行而报"AI任务不存在"，这条回调就白白丢了，任务只能等
     * AiTaskTimeoutWatchdog 在几十分钟后兜底、报出一个跟真实失败原因无关的超时提示。
     * 用 Claude Code 联网搜索走真实流程时几十秒到几分钟起步，不会撞见这个时间窗口；
     * 但客户询盘走图片/Excel录入时 rawContent 为空、编排服务会立刻同步拒绝，稳定复现。
     * 所以提交动作必须等当前事务真正提交之后才发起，不能指望"响应总是比事务提交慢"。
     */
    private void submit(AiTaskDO task, String inputJson) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSubmit(task, inputJson);
                }
            });
        } else {
            // 理论上 createAndSubmit() 总是带着 @Transactional 被 Spring 代理调用，
            // 这个分支只在没有事务上下文时（比如单测直接 new 出来调用）兜底，保持行为一致。
            doSubmit(task, inputJson);
        }
    }

    private void doSubmit(AiTaskDO task, String inputJson) {
        orchestratorClient.submit(task.getSkillId(), task.getId(), inputJson)
                .thenAccept(result -> {
                    LocalDateTime now = LocalDateTime.now();
                    if (result.isAccepted()) {
                        markStatus(task.getId(), AiTaskStatus.PROCESSING, null, null, now, null);
                    } else {
                        // 提交阶段就失败（如连不上AI编排服务）——这不是走 webhook 回调的路径，
                        // 之前这里只更新了 ai_task 自身状态，没有像 handleCallback() 一样调用
                        // resultDispatcher，导致发起方（如 customer_inquiry）永远收不到失败通知、
                        // 一直卡在"解析中"。这里补上同样的分发，让提交失败和回调失败走统一路径。
                        markStatus(task.getId(), AiTaskStatus.FAILED, null, result.getErrorMessage(), null, now);
                        AiTaskDO failed = new AiTaskDO();
                        failed.setId(task.getId());
                        failed.setSkillId(task.getSkillId());
                        failed.setStatus(AiTaskStatus.FAILED);
                        failed.setErrorMessage(result.getErrorMessage());
                        failed.setCompletedAt(now);
                        resultDispatcher.dispatch(failed);
                    }
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(Long taskId, AiTaskCallbackRequest req) {
        AiTaskDO task = aiTaskMapper.selectById(taskId);
        if (task == null || task.getDeletedAt() != null) {
            throw new BizException("AI任务不存在: " + taskId);
        }
        LocalDateTime now = LocalDateTime.now();
        if (req.isSuccess()) {
            String outputJson = writeJsonSafely(req.getOutput());
            markStatus(taskId, AiTaskStatus.COMPLETED, outputJson, null, null, now);
            task.setStatus(AiTaskStatus.COMPLETED);
            task.setOutput(outputJson);
        } else {
            markStatus(taskId, AiTaskStatus.FAILED, null, req.getError(), null, now);
            task.setStatus(AiTaskStatus.FAILED);
            task.setErrorMessage(req.getError());
        }
        task.setCompletedAt(now);
        resultDispatcher.dispatch(task);
    }

    /**
     * 单行状态更新，直接走 Mapper 而不复用其他 @Transactional service 方法——
     * 这个方法本身会被 submit() 里的异步回调线程调用，若通过 this.xxx() 调用会绕过
     * Spring AOP 代理导致 @Transactional 失效（自调用问题），单条 UPDATE 语句本身
     * 已经是原子操作，不需要额外声明事务。
     */
    private void markStatus(Long taskId, int status, String output, String errorMessage,
                              LocalDateTime startedAt, LocalDateTime completedAt) {
        AiTaskDO update = new AiTaskDO();
        update.setId(taskId);
        update.setStatus(status);
        if (output != null) {
            update.setOutput(output);
        }
        if (errorMessage != null) {
            update.setErrorMessage(errorMessage);
        }
        if (startedAt != null) {
            update.setStartedAt(startedAt);
        }
        if (completedAt != null) {
            update.setCompletedAt(completedAt);
        }
        aiTaskMapper.updateById(update);
    }

    private String writeJsonSafely(Object output) {
        if (output == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            log.warn("序列化AI任务回调output失败", e);
            return String.valueOf(output);
        }
    }

    private int currentTenantId() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : 0;
    }

    @Override
    public AiTaskVO getById(Long id) {
        AiTaskDO task = aiTaskMapper.selectById(id);
        if (task == null || task.getDeletedAt() != null) {
            throw new BizException("AI任务不存在: " + id);
        }
        return toVo(task);
    }

    private AiTaskVO toVo(AiTaskDO task) {
        AiTaskVO vo = new AiTaskVO();
        vo.setId(task.getId());
        vo.setSkillId(task.getSkillId());
        vo.setStatus(task.getStatus());
        vo.setOutput(task.getOutput());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setRequestedBy(task.getRequestedBy());
        vo.setStartedAt(task.getStartedAt());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setCreateTime(task.getCreateTime());
        return vo;
    }
}

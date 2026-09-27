package com.zhul.erp.modules.aitask.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.repository.AiTaskMapper;
import com.zhul.erp.modules.aitask.service.AiTaskResultDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定期扫描长时间停留在"排队中/处理中"的 AI 任务并标记为失败，转人工兜底。
 * 对应 PRD 第7节"AI 处理超时（无回调）"的处理方式："前端轮询兜底或设置超时阈值，
 * 超时后允许业务员手动标记'处理超时'转人工"——这里用后端定时任务代替"手动标记"，
 * 效果一致：超时后统一走已有的失败分发路径（{@link AiTaskResultDispatcher}），
 * 询盘解析场景下客户询盘会进入"解析失败"态，业务员可以点"手动创建询盘单"转人工，
 * 而不是让记录永远卡在"解析中"。
 *
 * <p>这里不做自动重试（不会重新调用 AI 编排服务）——沿用 PRD 已经定好的"超时转人工"
 * 设计，而不是引入一套新的自动重试语义（重试次数、退避策略等 PRD 未定义过）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskTimeoutWatchdog {

    private final AiTaskMapper aiTaskMapper;
    private final AiTaskResultDispatcher resultDispatcher;

    @Value("${zhul.ai-task.timeout-minutes:20}")
    private int timeoutMinutes;

    @Scheduled(fixedDelayString = "${zhul.ai-task.timeout-check-interval-ms:60000}")
    public void checkTimeouts() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<AiTaskDO> staleTasks = aiTaskMapper.selectList(new LambdaQueryWrapper<AiTaskDO>()
                .in(AiTaskDO::getStatus, AiTaskStatus.QUEUED, AiTaskStatus.PROCESSING)
                .lt(AiTaskDO::getCreateTime, deadline)
                .isNull(AiTaskDO::getDeletedAt));

        for (AiTaskDO task : staleTasks) {
            String message = "处理超时：超过" + timeoutMinutes + "分钟未收到AI编排服务的处理结果";
            log.warn("ai_task id={} skillId={} 处理超时，标记为失败并转人工：{}", task.getId(), task.getSkillId(), message);

            LocalDateTime now = LocalDateTime.now();
            AiTaskDO update = new AiTaskDO();
            update.setId(task.getId());
            update.setStatus(AiTaskStatus.FAILED);
            update.setErrorMessage(message);
            update.setCompletedAt(now);
            aiTaskMapper.updateById(update);

            AiTaskDO failed = new AiTaskDO();
            failed.setId(task.getId());
            failed.setSkillId(task.getSkillId());
            failed.setStatus(AiTaskStatus.FAILED);
            failed.setErrorMessage(message);
            failed.setCompletedAt(now);
            TenantContext.setTenantId(task.getTenantId());
            try {
                resultDispatcher.dispatch(failed);
            } finally {
                TenantContext.clear();
            }
        }
    }
}

package com.zhul.erp.modules.aitask.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractLambdaWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
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
 * 询盘解析场景下询盘单会进入"解析失败"态，业务员可以点"手动创建询盘单"转人工，
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

    /** 仍在报告进度的任务最长处理多久（分钟），从创建时算起（询盘大附件分批解析） */
    @Value("${zhul.ai-task.max-minutes:180}")
    private int maxMinutes = 180;

    @Scheduled(fixedDelayString = "${zhul.ai-task.timeout-check-interval-ms:60000}")
    public void checkTimeouts() {
        // 跨租户的后台任务：按各行自己的租户处理，不由租户拦截器追加当前租户条件
        TenantScope.platform(() -> {
            LocalDateTime scanTime = LocalDateTime.now();
            LocalDateTime idleDeadline = scanTime.minusMinutes(timeoutMinutes);
            LocalDateTime maxDeadline = scanTime.minusMinutes(maxMinutes);
            List<AiTaskDO> staleTasks = aiTaskMapper.selectList(new LambdaQueryWrapper<AiTaskDO>()
                    .in(AiTaskDO::getStatus, AiTaskStatus.QUEUED, AiTaskStatus.PROCESSING)
                    .isNull(AiTaskDO::getDeletedAt)
                    .and(w -> stale(w, idleDeadline, maxDeadline)));

            for (AiTaskDO task : staleTasks) {
                boolean overMax = task.getCreateTime() != null && task.getCreateTime().isBefore(maxDeadline);
                String message = overMax
                        ? "处理超时：超过最长处理时长" + maxMinutes + "分钟仍未完成"
                        : "处理超时：超过" + timeoutMinutes + "分钟未收到AI编排服务的处理结果或进度";
                log.warn("ai_task id={} skillId={} 处理超时，标记为失败并转人工：{}", task.getId(), task.getSkillId(), message);

                LocalDateTime now = LocalDateTime.now();
                AiTaskDO update = new AiTaskDO();
                update.setStatus(AiTaskStatus.FAILED);
                update.setErrorMessage(message);
                update.setCompletedAt(now);
                // 查到之后回调可能已先落库：只在任务仍未结束时改为失败，抢到才通知业务（audit AI-001）
                // 查询后又收到进度报告的任务不算超时：更新时再按同样的条件判断一次
                int changed = aiTaskMapper.update(update, new LambdaUpdateWrapper<AiTaskDO>()
                        .eq(AiTaskDO::getId, task.getId())
                        .in(AiTaskDO::getStatus, AiTaskStatus.OPEN)
                        .and(w -> stale(w, idleDeadline, maxDeadline)));
                if (changed == 0) {
                    log.info("ai_task id={} 在超时扫描期间已结束，跳过", task.getId());
                    continue;
                }

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
        });
    }

    /**
     * 超时条件：从最近一次进度报告（没有时为创建时间）起超过 timeoutMinutes 没有消息，
     * 或者从创建起超过最长处理时长 maxMinutes（spec「任务失败的统一处理」）
     */
    private static <W extends AbstractLambdaWrapper<AiTaskDO, W>> void stale(W w, LocalDateTime idleDeadline,
                                                                          LocalDateTime maxDeadline) {
        w.nested(n -> n.isNull(AiTaskDO::getLastProgressAt).lt(AiTaskDO::getCreateTime, idleDeadline))
                .or().lt(AiTaskDO::getLastProgressAt, idleDeadline)
                .or().lt(AiTaskDO::getCreateTime, maxDeadline);
    }
}

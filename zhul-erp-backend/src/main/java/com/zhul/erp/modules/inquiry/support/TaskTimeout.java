package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/** 询价任务超时判断：询价中，且首次分配起超过时限（普通 / 紧急，单位小时）。每次请求读一次设置 */
public record TaskTimeout(int normalHours, int urgentHours) {

    public static TaskTimeout of(SourcingSettings settings, int tenantId) {
        return new TaskTimeout(settings.timeoutHours(tenantId, false), settings.timeoutHours(tenantId, true));
    }

    public int hoursFor(SourcingTaskDO task) {
        return Objects.equals(task.getUrgent(), 1) ? urgentHours : normalHours;
    }

    public boolean isTimeout(SourcingTaskDO task, LocalDateTime now) {
        Long remaining = remainingMinutes(task, now);
        return remaining != null && remaining < 0;
    }

    /** 距超时还剩的分钟数；未在询价中或未分配时为空 */
    public Long remainingMinutes(SourcingTaskDO task, LocalDateTime now) {
        if (task.getStatus() == null || task.getStatus() != InquiryConstants.TASK_SOURCING || task.getFirstAssignedAt() == null) {
            return null;
        }
        return Duration.between(now, task.getFirstAssignedAt().plusHours(hoursFor(task))).toMinutes();
    }
}

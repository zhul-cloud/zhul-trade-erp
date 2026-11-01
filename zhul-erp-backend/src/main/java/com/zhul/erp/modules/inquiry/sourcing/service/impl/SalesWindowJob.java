package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskMapper;
import com.zhul.erp.modules.inquiry.sourcing.service.SourcingTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 超时流转：业务员到期没处理的询价任务进入待分配。每个任务独立事务，一个失败不影响其他任务 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesWindowJob {

    private final SourcingTaskMapper taskMapper;
    private final SourcingTaskService taskService;

    @Scheduled(fixedDelayString = "${zhul.inquiry.sales-window-check-interval-ms:60000}")
    public void moveDueTasks() {
        for (SourcingTaskDO task : taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                .select(SourcingTaskDO::getId, SourcingTaskDO::getTenantId)
                .eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_SALES)
                .le(SourcingTaskDO::getSalesDeadline, LocalDateTime.now())
                .isNull(SourcingTaskDO::getDeletedAt))) {
            TenantContext.setTenantId(task.getTenantId());
            try {
                taskService.moveToPoolIfDue(task.getId());
            } catch (RuntimeException e) {
                log.error("询价任务超时流转失败，taskId={}", task.getId(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}

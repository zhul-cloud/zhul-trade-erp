package com.zhul.erp.modules.inquiry.sourcing.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskAssigneeDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskAssigneeMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskMapper;
import com.zhul.erp.modules.system.notification.constants.NotificationNode;
import com.zhul.erp.modules.system.notification.support.NotificationTodoChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** 询价待办是否已处理：待分配的任务已分出去或取消；分给我的任务已回价、我已退回或被改派走 */
@Component
@RequiredArgsConstructor
public class SourcingNotificationChecker implements NotificationTodoChecker {

    private final SourcingTaskMapper taskMapper;
    private final SourcingTaskAssigneeMapper assigneeMapper;

    @Override
    public Set<NotificationNode> nodes() {
        return Set.of(NotificationNode.SOURCING_POOL, NotificationNode.SOURCING_ASSIGNED);
    }

    @Override
    public Set<Long> open(NotificationNode node, Long recipientId, Collection<Long> bizIds) {
        if (node == NotificationNode.SOURCING_POOL) {
            return ids(taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                    .select(SourcingTaskDO::getId)
                    .in(SourcingTaskDO::getId, bizIds)
                    .eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_UNASSIGNED)
                    .isNull(SourcingTaskDO::getDeletedAt)));
        }
        Set<Long> sourcing = ids(taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                .select(SourcingTaskDO::getId)
                .in(SourcingTaskDO::getId, bizIds)
                .eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_SOURCING)
                .isNull(SourcingTaskDO::getDeletedAt)));
        if (sourcing.isEmpty()) {
            return Set.of();
        }
        return assigneeMapper.selectList(new LambdaQueryWrapper<SourcingTaskAssigneeDO>()
                        .select(SourcingTaskAssigneeDO::getTaskId)
                        .in(SourcingTaskAssigneeDO::getTaskId, sourcing)
                        .eq(SourcingTaskAssigneeDO::getAssigneeId, recipientId)
                        .eq(SourcingTaskAssigneeDO::getActive, 1))
                .stream().map(SourcingTaskAssigneeDO::getTaskId).collect(Collectors.toSet());
    }

    private static Set<Long> ids(java.util.List<SourcingTaskDO> rows) {
        return rows.stream().map(SourcingTaskDO::getId).collect(Collectors.toSet());
    }
}

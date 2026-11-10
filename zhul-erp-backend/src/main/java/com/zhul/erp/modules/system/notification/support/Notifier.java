package com.zhul.erp.modules.system.notification.support;

import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.system.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务模块发通知的入口：事务里先登记，事务提交后统一发出；业务回滚时不发。
 * 发送失败只记日志，不影响业务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Notifier {

    private static final Object KEY = new Object();

    private final NotificationService service;
    private final CurrentUserResolver currentUser;

    public void send(Notice notice) {
        if (notice == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deliver(List.of(notice), currentUser.resolve(), TenantContext.getTenantId());
            return;
        }
        @SuppressWarnings("unchecked")
        List<Notice> pending = (List<Notice>) TransactionSynchronizationManager.getResource(KEY);
        if (pending == null) {
            List<Notice> list = new ArrayList<>(8);
            TransactionSynchronizationManager.bindResource(KEY, list);
            Long actor = currentUser.resolve();
            Integer tenantId = TenantContext.getTenantId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(KEY);
                    if (status == STATUS_COMMITTED) {
                        deliver(list, actor, tenantId);
                    }
                }
            });
            pending = list;
        }
        pending.add(notice);
    }

    private void deliver(List<Notice> notices, Long actor, Integer tenantId) {
        if (tenantId == null) {
            log.warn("没有租户上下文，通知未发出，节点={}", notices.stream().map(n -> n.node().name()).toList());
            return;
        }
        try {
            TenantContext.runAs(tenantId, () -> service.deliver(notices, actor));
        } catch (RuntimeException e) {
            log.error("通知发送失败，节点={}", notices.stream().map(n -> n.node().name()).toList(), e);
        }
    }
}

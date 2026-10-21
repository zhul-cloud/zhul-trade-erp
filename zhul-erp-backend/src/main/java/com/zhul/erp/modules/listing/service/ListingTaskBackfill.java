package com.zhul.erp.modules.listing.service;

import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.listing.repository.ListingTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 发品任务补齐（openspec listing-tasks）：启动时逐个公司对齐一次。
 * 上线时为已满足条件的商品补生成任务；之后平台账号改了默认平台设置、或个别事件处理失败时，也在下次启动时补上。
 * 对齐是幂等的，重复启动不会重复生成；每个公司一个事务，失败只记日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListingTaskBackfill implements ApplicationRunner {

    private final ListingTaskMapper taskMapper;
    private final ListingTaskSync sync;
    private final TransactionTemplate tx;

    @Override
    public void run(ApplicationArguments args) {
        int changed = 0;
        for (Integer tenantId : taskMapper.tenantsWithContent()) {
            try {
                TenantContext.setTenantId(tenantId);
                Integer n = tx.execute(s -> sync.syncAll(tenantId));
                changed += n == null ? 0 : n;
            } catch (RuntimeException e) {
                log.warn("发品任务补齐失败，下次启动重试，tenantId={}", tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
        if (changed > 0) {
            log.info("发品任务补齐完成：新建、取消或恢复 {} 个任务", changed);
        }
    }
}

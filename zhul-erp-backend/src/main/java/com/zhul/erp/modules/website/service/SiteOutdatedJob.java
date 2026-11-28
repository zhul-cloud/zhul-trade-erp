package com.zhul.erp.modules.website.service;

import com.zhul.erp.framework.tenant.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天 06:00 重算全部独立站已发布任务的「内容有更新」（询价回价、汇率变化不实时推送，靠这里兜住） */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteOutdatedJob {

    private final SiteOutdatedService outdated;

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Shanghai")
    public void run() {
        int n = TenantScope.platform(outdated::recomputeAll);
        log.info("独立站「内容有更新」每日重算完成，任务数={}", n);
    }
}

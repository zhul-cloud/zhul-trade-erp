package com.zhul.erp.modules.website.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.entity.WebsiteReleaseDO;
import com.zhul.erp.modules.website.repository.WebsiteReleaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布单定时任务（design 第 9 条）：每 30 秒先跑看门狗（执行中超过 40 分钟标为失败），再给有排队发布单的站点派发。
 * 定时任务没有登录上下文：平台范围查询，再按发布单所属公司 runAs 处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteReleaseJob {

    private final WebsiteReleaseMapper releaseMapper;
    private final SiteReleaseDispatcher dispatcher;
    private final SiteReleaseCallback callback;
    private final SitePublisherClient client;

    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void tick() {
        if (!client.enabled()) {
            return;
        }
        TenantScope.platform(() -> {
            watchdog(LocalDateTime.now());
            dispatchQueued();
        });
    }

    void watchdog(LocalDateTime now) {
        List<WebsiteReleaseDO> stale = releaseMapper.selectList(new LambdaQueryWrapper<WebsiteReleaseDO>()
                .eq(WebsiteReleaseDO::getStatus, WebsiteConstants.RELEASE_RUNNING)
                .lt(WebsiteReleaseDO::getStartedAt, now.minusMinutes(WebsiteConstants.RELEASE_TIMEOUT_MINUTES)));
        for (WebsiteReleaseDO r : stale) {
            TenantContext.runAs(r.getTenantId(), () -> callback.finishFailed(r.getId(), r.getJobNo(),
                    callback.runningStep(r.getId(), r.getJobNo()),
                    "超过 " + WebsiteConstants.RELEASE_TIMEOUT_MINUTES + " 分钟没有完成，已标为失败"));
            log.warn("独立站发布单超时，releaseNo={}", r.getReleaseNo());
        }
    }

    void dispatchQueued() {
        Map<Long, Integer> siteTenants = new LinkedHashMap<>();
        for (WebsiteReleaseDO r : releaseMapper.selectList(new LambdaQueryWrapper<WebsiteReleaseDO>()
                .select(WebsiteReleaseDO::getSiteId, WebsiteReleaseDO::getTenantId)
                .eq(WebsiteReleaseDO::getStatus, WebsiteConstants.RELEASE_QUEUED)
                .isNull(WebsiteReleaseDO::getDeletedAt).orderByAsc(WebsiteReleaseDO::getId))) {
            siteTenants.putIfAbsent(r.getSiteId(), r.getTenantId());
        }
        siteTenants.forEach((siteId, tenantId) -> {
            try {
                TenantContext.runAs(tenantId, () -> dispatcher.dispatch(siteId));
            } catch (RuntimeException e) {
                log.error("派发独立站发布单失败，siteId={}", siteId, e);
            }
        });
    }
}

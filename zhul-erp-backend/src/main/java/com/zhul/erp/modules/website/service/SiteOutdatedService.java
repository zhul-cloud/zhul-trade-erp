package com.zhul.erp.modules.website.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.listing.constants.ListingConstants;
import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import com.zhul.erp.modules.listing.repository.ListingTaskMapper;
import com.zhul.erp.modules.product.content.support.MarketingPlatform;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.entity.WebsiteProductDO;
import com.zhul.erp.modules.website.entity.WebsiteSiteDO;
import com.zhul.erp.modules.website.repository.WebsiteSiteMapper;
import com.zhul.erp.modules.website.support.SiteContentChanged;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 「内容有更新」的判断（design 第 6 条）：已发布、已上线待确认的独立站任务，按当前数据重新生成站点数据，
 * 与上次发布快照逐字段比较（与预览同一套比较），有差异就标 site_outdated = 1，没有就清掉。
 * 触发：营销内容写入、商品资料修改、素材标记变更、站点设置修改（事务提交后）；每天 06:00 全部重算一次，
 * 兜住询价回价、汇率这类不推送的变化。
 * 事务边界：一个任务一个事务（只改这个任务的标记和站点商品的计算售价），某个任务失败不影响其他任务。
 */
@Slf4j
@Service
public class SiteOutdatedService {

    private static final List<Integer> LIVE = List.of(ListingConstants.STATUS_PUBLISHED, ListingConstants.STATUS_LIVE_UNCONFIRMED);

    private final ListingTaskMapper taskMapper;
    private final WebsiteSiteMapper siteMapper;
    private final SiteProductService siteProducts;
    private final SiteDataService siteData;
    private final SitePreviewService previews;
    private final TransactionTemplate tx;

    public SiteOutdatedService(ListingTaskMapper taskMapper, WebsiteSiteMapper siteMapper, SiteProductService siteProducts,
                               SiteDataService siteData, SitePreviewService previews, PlatformTransactionManager txManager) {
        this.taskMapper = taskMapper;
        this.siteMapper = siteMapper;
        this.siteProducts = siteProducts;
        this.siteData = siteData;
        this.previews = previews;
        // 提交后的监听里仍绑定着已结束的事务，必须另起新事务，否则写入不会提交
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 写入事务提交后重算；失败只记日志，第二天由定时任务补上 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChanged(SiteContentChanged e) {
        try {
            if (e.siteId() != null) {
                each(base().eq(ListingTaskDO::getSiteId, e.siteId()), false);
            } else if (!e.assetKeys().isEmpty()) {
                each(base().in(ListingTaskDO::getAssetKey, e.assetKeys()), false);
            } else if (!e.productIds().isEmpty()) {
                // 商品库共享：各公司这个商品的独立站任务都要重算
                each(base().in(ListingTaskDO::getProductId, e.productIds()), true);
            }
        } catch (RuntimeException ex) {
            log.warn("重算独立站「内容有更新」失败，等定时任务补上：{}", ex.getMessage());
        }
    }

    /** 全部公司重算（定时任务，平台范围调用） */
    public int recomputeAll() {
        int[] n = {0};
        each(base(), true, t -> n[0]++);
        return n[0];
    }

    /**
     * 重算一个任务，返回是否有更新（需在任务所属公司的租户上下文里调用）。
     * 不改任务的更新时间、更新人：这是系统判断，不是人改了任务。
     */
    public boolean recompute(Long taskId) {
        Boolean r = tx.execute(s -> {
            ListingTaskDO t = taskMapper.selectById(taskId);
            if (t == null || t.getDeletedAt() != null || !LIVE.contains(t.getStatus())) {
                return null;
            }
            WebsiteSiteDO site = siteMapper.selectById(t.getSiteId());
            if (site == null || !Objects.equals(site.getPublishMode(), WebsiteConstants.MODE_AUTO)) {
                return null;
            }
            WebsiteProductDO wp = siteProducts.ensure(site, t);
            if (!StringUtils.hasText(wp.getLastSnapshot())) {
                return null;
            }
            Map<String, Object> data = siteData.build(site, t, wp, null, false).data();
            data.remove("release");
            boolean outdated = !previews.changes(wp.getLastSnapshot(), data).isEmpty();
            mark(taskMapper, t, outdated);
            return outdated;
        });
        return Boolean.TRUE.equals(r);
    }

    /** 写回标记；预览算出差异时也调用（与预览同一事务） */
    static void mark(ListingTaskMapper taskMapper, ListingTaskDO t, boolean outdated) {
        int v = outdated ? 1 : 0;
        if (!LIVE.contains(t.getStatus()) || Objects.equals(t.getSiteOutdated(), v)) {
            return;
        }
        taskMapper.update(null, new LambdaUpdateWrapper<ListingTaskDO>().eq(ListingTaskDO::getId, t.getId())
                .set(ListingTaskDO::getSiteOutdated, v));
        t.setSiteOutdated(v);
    }

    private static LambdaQueryWrapper<ListingTaskDO> base() {
        return new LambdaQueryWrapper<ListingTaskDO>()
                .select(ListingTaskDO::getId, ListingTaskDO::getTenantId)
                .eq(ListingTaskDO::getPlatform, MarketingPlatform.SITE.code())
                .gt(ListingTaskDO::getSiteId, 0L)
                .in(ListingTaskDO::getStatus, LIVE)
                .isNull(ListingTaskDO::getDeletedAt)
                .orderByAsc(ListingTaskDO::getId);
    }

    private void each(LambdaQueryWrapper<ListingTaskDO> q, boolean allTenants) {
        each(q, allTenants, t -> { });
    }

    private void each(LambdaQueryWrapper<ListingTaskDO> q, boolean allTenants, Consumer<ListingTaskDO> after) {
        List<ListingTaskDO> tasks = allTenants ? TenantScope.platform(() -> taskMapper.selectList(q)) : taskMapper.selectList(q);
        for (ListingTaskDO t : tasks) {
            try {
                if (allTenants) {
                    TenantContext.runAs(t.getTenantId(), () -> recompute(t.getId()));
                } else {
                    recompute(t.getId());
                }
                after.accept(t);
            } catch (RuntimeException ex) {
                log.warn("重算独立站「内容有更新」失败，taskId={}：{}", t.getId(), ex.getMessage());
            }
        }
    }
}

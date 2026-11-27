package com.zhul.erp.modules.listing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.listing.constants.ListingConstants;
import com.zhul.erp.modules.listing.dto.ListingTaskDetailVO;
import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import com.zhul.erp.modules.listing.repository.ListingTaskMapper;
import com.zhul.erp.modules.product.content.support.MarketingPlatform;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.entity.WebsiteProductDO;
import com.zhul.erp.modules.website.entity.WebsiteSiteDO;
import com.zhul.erp.modules.website.repository.WebsiteProductMapper;
import com.zhul.erp.modules.website.repository.WebsiteSiteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 「自动发布」站点的独立站任务状态（spec listing-task「独立站一键发布的任务状态」）：
 * <pre>
 * 待发布 / 已发布 / 已上线待确认 ─发起发布→ 发布中
 * 发布中 ─发布单完成→ 已上线待确认（系统写入上线链接、发布单号、发布时间）
 * 发布中 ─发布单失败→ 回到发起前的状态
 * 已上线待确认 ─确认上线→ 已发布（站点要求时必须勾选「已清缓存」）
 * </pre>
 * 已发布、已上线待确认的任务可以再发布（同步更新）。只复制的站点仍用手工「已发布」。
 * 事务边界：每次流转在调用方的事务里加行锁执行（发布单的发起、完成、失败与任务状态同一事务）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteTaskFlow {

    private static final Set<Integer> CAN_BEGIN = Set.of(ListingConstants.STATUS_PENDING, ListingConstants.STATUS_PUBLISHED,
            ListingConstants.STATUS_LIVE_UNCONFIRMED);

    private final ListingTaskMapper taskMapper;
    private final WebsiteSiteMapper siteMapper;
    private final WebsiteProductMapper websiteProductMapper;
    private final ObjectMapper objectMapper;

    /** 独立站任务所在站点是「自动发布」 */
    public boolean autoPublish(ListingTaskDO t) {
        WebsiteSiteDO s = site(t);
        return s != null && Objects.equals(s.getPublishMode(), WebsiteConstants.MODE_AUTO);
    }

    /** 发起发布：返回发起前的状态（失败时退回） */
    @Transactional(rollbackFor = Exception.class)
    public int begin(Long taskId, Long releaseId) {
        ListingTaskDO t = lock(taskId);
        requireAuto(t);
        if (!CAN_BEGIN.contains(t.getStatus())) {
            throw new BizException(t.getStatus() == ListingConstants.STATUS_PUBLISHING ? "任务正在发布中" : "任务已取消，不能发布");
        }
        update(taskId, w -> w.set(ListingTaskDO::getStatus, ListingConstants.STATUS_PUBLISHING)
                .set(ListingTaskDO::getReleaseId, releaseId));
        return t.getStatus();
    }

    /** 发布单完成：发布中 → 已上线待确认；listingUrl 为第一种语言的上线链接 */
    @Transactional(rollbackFor = Exception.class)
    public void succeed(Long taskId, Long releaseId, String listingUrl, String publishedBy) {
        ListingTaskDO t = lock(taskId);
        requirePublishing(t, releaseId);
        LocalDateTime now = LocalDateTime.now();
        update(taskId, w -> w.set(ListingTaskDO::getStatus, ListingConstants.STATUS_LIVE_UNCONFIRMED)
                .set(ListingTaskDO::getListingUrl, listingUrl == null ? "" : listingUrl)
                .set(ListingTaskDO::getPublishedBy, publishedBy)
                .set(ListingTaskDO::getPublishedAt, now)
                // 刚按当前数据发布，快照就是最新的
                .set(ListingTaskDO::getSiteOutdated, 0));
    }

    /** 发布单失败：发布中 → 发起前的状态（发布单号保留，详情据此显示失败原因） */
    @Transactional(rollbackFor = Exception.class)
    public void fail(Long taskId, Long releaseId, int previousStatus) {
        ListingTaskDO t = lock(taskId);
        requirePublishing(t, releaseId);
        if (!CAN_BEGIN.contains(previousStatus)) {
            throw new IllegalArgumentException("发起前的状态不对：" + previousStatus);
        }
        update(taskId, w -> w.set(ListingTaskDO::getStatus, previousStatus));
    }

    /** 确认上线：已上线待确认 → 已发布；以当前营销内容的写入时间作为「内容有更新」的新基准 */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long taskId, boolean purged) {
        ListingTaskDO t = lock(taskId);
        if (!Objects.equals(t.getStatus(), ListingConstants.STATUS_LIVE_UNCONFIRMED)) {
            throw new BizException("只有已上线待确认的任务可以确认上线");
        }
        WebsiteSiteDO s = site(t);
        if (s != null && Objects.equals(s.getNeedPurge(), 1) && !purged) {
            throw new BizException("请先按说明清理站点缓存，再勾选『已清缓存』");
        }
        Map<String, Object> flags = taskMapper.flags(taskId);
        Object contentAt = flags == null ? null : flags.get("current_content_at");
        String user = SecurityUtils.getCurrentUsername();
        update(taskId, w -> w.set(ListingTaskDO::getStatus, ListingConstants.STATUS_PUBLISHED)
                .set(ListingTaskDO::getConfirmedBy, user)
                .set(ListingTaskDO::getConfirmedAt, LocalDateTime.now())
                .set(ListingTaskDO::getSiteOutdated, 0)
                .set(ListingTaskDO::getContentAt, ListingTaskService.time(contentAt)));
        log.info("确认独立站上线，taskId={}，已清缓存={}", taskId, purged);
    }

    /** 任务详情里的站点信息 */
    public ListingTaskDetailVO.SiteInfo siteInfo(ListingTaskDO t) {
        WebsiteSiteDO s = site(t);
        if (s == null) {
            return null;
        }
        ListingTaskDetailVO.SiteInfo info = new ListingTaskDetailVO.SiteInfo();
        info.setSiteId(s.getId());
        info.setName(s.getName());
        info.setAutoPublish(Objects.equals(s.getPublishMode(), WebsiteConstants.MODE_AUTO));
        info.setNeedPurge(Objects.equals(s.getNeedPurge(), 1));
        info.setPurgeNote(info.isNeedPurge() ? s.getPurgeNote() : null);
        WebsiteProductDO wp = websiteProductMapper.selectOne(new LambdaQueryWrapper<WebsiteProductDO>()
                .eq(WebsiteProductDO::getSiteId, s.getId()).eq(WebsiteProductDO::getProductId, t.getProductId())
                .isNull(WebsiteProductDO::getDeletedAt));
        if (wp != null && StringUtils.hasText(wp.getLiveUrls())) {
            try {
                info.setLiveUrls(objectMapper.readValue(wp.getLiveUrls(), new TypeReference<LinkedHashMap<String, String>>() { }));
            } catch (JsonProcessingException e) {
                log.warn("上线链接读取失败，websiteProductId={}", wp.getId());
            }
        }
        return info;
    }

    // ================================================================ 工具

    private WebsiteSiteDO site(ListingTaskDO t) {
        if (!MarketingPlatform.SITE.code().equals(t.getPlatform()) || t.getSiteId() == null || t.getSiteId() == 0) {
            return null;
        }
        return siteMapper.selectById(t.getSiteId());
    }

    private void requireAuto(ListingTaskDO t) {
        if (!autoPublish(t)) {
            throw new BizException("只有自动发布站点的独立站任务可以一键发布");
        }
    }

    private static void requirePublishing(ListingTaskDO t, Long releaseId) {
        if (!Objects.equals(t.getStatus(), ListingConstants.STATUS_PUBLISHING) || !Objects.equals(t.getReleaseId(), releaseId)) {
            throw new BizException("任务不在这张发布单的发布中");
        }
    }

    private ListingTaskDO lock(Long id) {
        if (taskMapper.lock(id, TenantContext.getTenantId()) == null) {
            throw new BizException("发品任务不存在");
        }
        return taskMapper.selectById(id);
    }

    private void update(Long id, java.util.function.UnaryOperator<LambdaUpdateWrapper<ListingTaskDO>> f) {
        taskMapper.update(null, f.apply(new LambdaUpdateWrapper<ListingTaskDO>().eq(ListingTaskDO::getId, id))
                .set(ListingTaskDO::getUpdateTime, LocalDateTime.now())
                .set(ListingTaskDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
    }
}

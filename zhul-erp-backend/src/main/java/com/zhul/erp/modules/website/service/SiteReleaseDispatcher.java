package com.zhul.erp.modules.website.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import com.zhul.erp.modules.listing.repository.ListingTaskMapper;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.entity.WebsiteProductDO;
import com.zhul.erp.modules.website.entity.WebsiteReleaseDO;
import com.zhul.erp.modules.website.entity.WebsiteReleaseItemDO;
import com.zhul.erp.modules.website.entity.WebsiteSiteDO;
import com.zhul.erp.modules.website.entity.WebsiteSiteJobDO;
import com.zhul.erp.modules.website.repository.WebsiteProductMapper;
import com.zhul.erp.modules.website.repository.WebsiteReleaseItemMapper;
import com.zhul.erp.modules.website.repository.WebsiteReleaseMapper;
import com.zhul.erp.modules.website.repository.WebsiteSiteJobMapper;
import com.zhul.erp.modules.website.repository.WebsiteSiteMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 发布单派发（design 第 9 条）：同一站点同一时间只执行一张，后来的排队按先后执行。
 * 串行靠站点行锁（SELECT … FOR UPDATE）：认领（排队中 → 执行中）在一个短事务里完成，提交发布服务在事务外，
 * 这样回调到达时发布单已经提交，可以按任务号找到。项目没有引入 Redisson，行锁在多实例下同样有效。
 */
@Slf4j
@Service
public class SiteReleaseDispatcher {

    private final WebsiteSiteService sites;
    private final WebsiteSiteMapper siteMapper;
    private final WebsiteReleaseMapper releaseMapper;
    private final WebsiteReleaseItemMapper itemMapper;
    private final WebsiteProductMapper websiteProductMapper;
    private final WebsiteSiteJobMapper jobMapper;
    private final ListingTaskMapper taskMapper;
    private final ProductBrandMapper brandMapper;
    private final SiteMediaService siteMedia;
    private final SitePublisherClient client;
    private final SiteReleaseCallback callback;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final String callbackBase;

    public SiteReleaseDispatcher(WebsiteSiteService sites, WebsiteSiteMapper siteMapper, WebsiteReleaseMapper releaseMapper,
                                 WebsiteReleaseItemMapper itemMapper, WebsiteProductMapper websiteProductMapper,
                                 WebsiteSiteJobMapper jobMapper, ListingTaskMapper taskMapper, ProductBrandMapper brandMapper,
                                 SiteMediaService siteMedia, SitePublisherClient client, SiteReleaseCallback callback,
                                 TransactionTemplate tx, ObjectMapper objectMapper,
                                 @Value("${zhul.ai-task.callback-base-url}") String callbackBase) {
        this.sites = sites;
        this.siteMapper = siteMapper;
        this.releaseMapper = releaseMapper;
        this.itemMapper = itemMapper;
        this.websiteProductMapper = websiteProductMapper;
        this.jobMapper = jobMapper;
        this.taskMapper = taskMapper;
        this.brandMapper = brandMapper;
        this.siteMedia = siteMedia;
        this.client = client;
        this.callback = callback;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.callbackBase = callbackBase.replaceAll("/+$", "");
    }

    /** 这个站点没有执行中的发布单时，派发最早排队的一张（需在所属公司的租户上下文里调用） */
    public void dispatch(Long siteId) {
        WebsiteReleaseDO r = tx.execute(s -> claim(siteId));
        if (r == null) {
            return;
        }
        try {
            SitePublisherClient.Reply reply = client.submitJob(request(r));
            if (reply.status() == 202) {
                log.info("独立站发布单开始执行，releaseNo={}，jobNo={}", r.getReleaseNo(), r.getJobNo());
                return;
            }
            if (reply.status() == 409) {
                // 发布服务里这个站点还有任务（如导入、比对）：退回排队，下次再派
                requeue(r);
                return;
            }
            callback.finishFailed(r.getId(), r.getJobNo(), "submit", "发布服务没有接受任务：" + reply.detail());
        } catch (BizException | IllegalStateException e) {
            log.warn("独立站发布单提交失败，releaseNo={}：{}", r.getReleaseNo(), e.getMessage());
            callback.finishFailed(r.getId(), r.getJobNo(), "submit", e.getMessage());
        }
    }

    /** 认领：锁站点行，没有执行中的就把最早排队的改为执行中，任务号为「发布单号-第几次」 */
    WebsiteReleaseDO claim(Long siteId) {
        if (siteMapper.lock(siteId, TenantContext.getTenantId()) == null) {
            return null;
        }
        Long running = releaseMapper.selectCount(new LambdaQueryWrapper<WebsiteReleaseDO>()
                .eq(WebsiteReleaseDO::getSiteId, siteId).eq(WebsiteReleaseDO::getStatus, WebsiteConstants.RELEASE_RUNNING));
        if (running > 0) {
            return null;
        }
        WebsiteReleaseDO next = releaseMapper.selectOne(new LambdaQueryWrapper<WebsiteReleaseDO>()
                .eq(WebsiteReleaseDO::getSiteId, siteId).eq(WebsiteReleaseDO::getStatus, WebsiteConstants.RELEASE_QUEUED)
                .isNull(WebsiteReleaseDO::getDeletedAt).orderByAsc(WebsiteReleaseDO::getId).last("LIMIT 1"));
        if (next == null) {
            return null;
        }
        next.setStatus(WebsiteConstants.RELEASE_RUNNING);
        next.setJobNo(next.getReleaseNo() + "-" + (attemptOf(next.getJobNo()) + 1));
        next.setStartedAt(LocalDateTime.now());
        next.setEndedAt(null);
        releaseMapper.updateById(next);
        return next;
    }

    private void requeue(WebsiteReleaseDO r) {
        tx.executeWithoutResult(s -> {
            WebsiteReleaseDO cur = releaseMapper.selectById(r.getId());
            if (Objects.equals(cur.getStatus(), WebsiteConstants.RELEASE_RUNNING) && Objects.equals(cur.getJobNo(), r.getJobNo())) {
                cur.setStatus(WebsiteConstants.RELEASE_QUEUED);
                // 任务号退回上一次，下次认领时重新加 1
                int attempt = attemptOf(r.getJobNo()) - 1;
                cur.setJobNo(attempt <= 0 ? "" : cur.getReleaseNo() + "-" + attempt);
                releaseMapper.updateById(cur);
            }
        });
    }

    /** 从部署一步重试：上次部署失败、有提交号 */
    static boolean fromDeploy(WebsiteReleaseDO r) {
        return WebsiteConstants.STEP_DEPLOY.equals(r.getFailedStep()) && StringUtils.hasText(r.getCommitSha());
    }

    static int attemptOf(String jobNo) {
        if (!StringUtils.hasText(jobNo) || jobNo.lastIndexOf('-') < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(jobNo.substring(jobNo.lastIndexOf('-') + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ================================================================ 请求

    Map<String, Object> request(WebsiteReleaseDO r) {
        WebsiteSiteDO site = sites.get(r.getSiteId());
        List<WebsiteReleaseItemDO> items = itemMapper.selectList(new LambdaQueryWrapper<WebsiteReleaseItemDO>()
                .eq(WebsiteReleaseItemDO::getReleaseId, r.getId()).orderByAsc(WebsiteReleaseItemDO::getId));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", r.getJobNo());
        body.put("type", "publish");
        body.put("callbackUrl", callbackBase + "/api/v1/website-publisher/callback");
        body.put("site", sites.publisherSite(site));
        Map<String, String> secrets = sites.publisherSecrets(site.getId());
        body.put("secrets", secrets);
        body.put("release", r.getReleaseNo());
        List<Map<String, Object>> checks = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        Map<Long, Map<String, Object>> released = new HashMap<>();
        for (WebsiteReleaseItemDO i : items) {
            Map<String, Object> data = readMap(i.getSnapshot());
            data.put("release", r.getReleaseNo());
            released.put(i.getWebsiteProductId(), data);
            for (String url : readMap(i.getLiveUrls()).values().stream().map(String::valueOf).toList()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("url", url);
                c.put("images", data.get("photos") instanceof List<?> l ? l.size() : 0);
                c.put("video", data.get("videos") instanceof List<?> l && !l.isEmpty());
                c.put("release", r.getReleaseNo());
                checks.add(c);
                urls.add(url);
            }
        }
        body.put("checks", checks);
        if (secrets.containsKey("indexNowKey")) {
            body.put("indexNowUrls", urls);
        }
        if (fromDeploy(r)) {
            body.put("startFrom", WebsiteConstants.STEP_DEPLOY);
            body.put("commit", r.getCommitSha());
            return body;
        }
        copyMedia(site, items);
        Map<String, Object> siteInfo = new LinkedHashMap<>();
        siteInfo.put("code", sites.publisherSite(site).get("code"));
        siteInfo.put("nonGenuineBrands", brandNames(sites.nonGenuineOf(site)));
        Map<String, Object> siteData = new LinkedHashMap<>();
        siteData.put("schemaVersion", 1);
        siteData.put("site", siteInfo);
        siteData.put("generatedAt", Instant.now().toString());
        siteData.put("release", r.getReleaseNo());
        siteData.put("products", catalog(site.getId(), released));
        body.put("siteData", siteData);
        return body;
    }

    /**
     * 全量的站点商品（站点数据约定：站点上有、文件里没有的商品会让构建失败）：
     * 这次发布的用新数据；发布过的用上次发布快照（保留原来的发布单标记）；导入后还没发布的用站点原有内容；
     * 最后加上最近一次导入时「只在站点上有」的商品，原样不动。
     */
    List<Map<String, Object>> catalog(Long siteId, Map<Long, Map<String, Object>> released) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WebsiteProductDO wp : websiteProductMapper.selectList(new LambdaQueryWrapper<WebsiteProductDO>()
                .eq(WebsiteProductDO::getSiteId, siteId).isNull(WebsiteProductDO::getDeletedAt)
                .orderByAsc(WebsiteProductDO::getId))) {
            Map<String, Object> data = released.get(wp.getId());
            if (data == null && StringUtils.hasText(wp.getLastSnapshot())) {
                data = readMap(wp.getLastSnapshot());
            }
            if (data == null && wp.getImportedAt() != null && StringUtils.hasText(wp.getOriginalContent())) {
                data = readMap(wp.getOriginalContent());
            }
            if (data != null) {
                out.add(data);
            }
        }
        out.addAll(onlyOnSite(siteId));
        return out;
    }

    /** 最近一次导入（已完成或已确认）里没有匹配到商品档案的站点商品 */
    List<Map<String, Object>> onlyOnSite(Long siteId) {
        WebsiteSiteJobDO job = jobMapper.selectOne(new LambdaQueryWrapper<WebsiteSiteJobDO>()
                .eq(WebsiteSiteJobDO::getSiteId, siteId).eq(WebsiteSiteJobDO::getJobType, "import")
                .in(WebsiteSiteJobDO::getStatus, 2, 4).orderByDesc(WebsiteSiteJobDO::getId).last("LIMIT 1"));
        if (job == null || !StringUtils.hasText(job.getResult())) {
            return List.of();
        }
        try {
            JsonNode unmatched = objectMapper.readTree(job.getResult()).path("unmatched");
            if (!unmatched.isArray()) {
                return List.of();
            }
            return objectMapper.convertValue(unmatched, new TypeReference<List<Map<String, Object>>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("导入结果读取失败", e);
        }
    }

    /** 发布时才把私有素材复制到公开桶（已存在跳过），链接与预览时算的一致 */
    private void copyMedia(WebsiteSiteDO site, List<WebsiteReleaseItemDO> items) {
        for (WebsiteReleaseItemDO i : items) {
            ListingTaskDO task = taskMapper.selectById(i.getListingTaskId());
            WebsiteProductDO wp = websiteProductMapper.selectById(i.getWebsiteProductId());
            if (task != null && wp != null) {
                siteMedia.of(site.getId(), wp.getSlug(), task, "", true);
            }
        }
    }

    private List<String> brandNames(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return brandMapper.selectBatchIds(ids).stream().map(ProductBrandDO::getBrandName).toList();
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("发布单数据读取失败", e);
        }
    }
}

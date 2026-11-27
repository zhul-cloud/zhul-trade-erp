package com.zhul.erp.modules.website.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.listing.constants.ListingConstants;
import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import com.zhul.erp.modules.listing.repository.ListingTaskMapper;
import com.zhul.erp.modules.listing.service.ListingTaskService;
import com.zhul.erp.modules.media.service.MediaIndexer;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.content.support.MarketingPlatform;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.dto.SiteJobVO;
import com.zhul.erp.modules.website.entity.WebsiteProductDO;
import com.zhul.erp.modules.website.entity.WebsiteReleaseDO;
import com.zhul.erp.modules.website.entity.WebsiteSiteDO;
import com.zhul.erp.modules.website.entity.WebsiteSiteJobDO;
import com.zhul.erp.modules.website.repository.WebsiteProductMapper;
import com.zhul.erp.modules.website.repository.WebsiteReleaseMapper;
import com.zhul.erp.modules.website.repository.WebsiteSiteJobMapper;
import com.zhul.erp.modules.website.repository.WebsiteSiteMapper;
import com.zhul.erp.modules.website.support.SiteEntry;
import com.zhul.erp.modules.website.dto.SiteLanguage;
import com.zhul.erp.modules.website.support.SiteStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 导入站点现有商品与一致性比对（spec product/website-import，design 第 7 条）。两者都由发布服务异步执行：
 * 发起时记一条站点任务（执行中）并提交，回调完成后把结果存进任务（已完成），由管理员确认：
 * 导入确认后写入站点商品和发品任务；比对的差异逐条确认接受后，可以「开始以 ERP 为准」。
 * 事务边界：发起时写任务行与提交分开（提交在事务外）；回调、确认导入、确认差异、开始以 ERP 为准各一个事务。
 */
@Slf4j
@Service
public class SiteImportService {

    public static final String IMPORT = "import";
    public static final String COMPARE = "compare";
    private static final int JOB_RUNNING = 1;
    private static final int JOB_DONE = 2;
    private static final int JOB_FAILED = 3;
    private static final int JOB_CONFIRMED = 4;
    private static final String[] STATUS_NAMES = {"", "执行中", "已完成", "失败", "已确认"};
    private static final int MAX_REASON = 200;

    private final WebsiteSiteService sites;
    private final WebsiteSiteMapper siteMapper;
    private final WebsiteSiteJobMapper jobMapper;
    private final WebsiteProductMapper websiteProductMapper;
    private final WebsiteReleaseMapper releaseMapper;
    private final ListingTaskMapper taskMapper;
    private final ProductMapper productMapper;
    private final ProductBrandMapper brandMapper;
    private final SiteDataService siteData;
    private final SitePreviewService previews;
    private final SiteReleaseDispatcher dispatcher;
    private final SitePublisherClient client;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;

    public SiteImportService(WebsiteSiteService sites, WebsiteSiteMapper siteMapper, WebsiteSiteJobMapper jobMapper,
                             WebsiteProductMapper websiteProductMapper, WebsiteReleaseMapper releaseMapper,
                             ListingTaskMapper taskMapper, ProductMapper productMapper, ProductBrandMapper brandMapper,
                             SiteDataService siteData, SitePreviewService previews, SiteReleaseDispatcher dispatcher,
                             SitePublisherClient client, TransactionTemplate tx, ObjectMapper objectMapper) {
        this.sites = sites;
        this.siteMapper = siteMapper;
        this.jobMapper = jobMapper;
        this.websiteProductMapper = websiteProductMapper;
        this.releaseMapper = releaseMapper;
        this.taskMapper = taskMapper;
        this.productMapper = productMapper;
        this.brandMapper = brandMapper;
        this.siteData = siteData;
        this.previews = previews;
        this.dispatcher = dispatcher;
        this.client = client;
        this.tx = tx;
        this.objectMapper = objectMapper;
    }

    // ================================================================ 发起

    /** 导入现有商品 */
    public SiteJobVO startImport(Long siteId) {
        WebsiteSiteDO site = requireTakeover(siteId);
        return submit(site, IMPORT, null);
    }

    /** 一致性比对：用 ERP 现在会生成的站点数据构建，与仓库现有数据逐个字段比较 */
    public SiteJobVO startCompare(Long siteId) {
        WebsiteSiteDO site = requireTakeover(siteId);
        return submit(site, COMPARE, site);
    }

    private SiteJobVO submit(WebsiteSiteDO site, String type, WebsiteSiteDO compareSite) {
        WebsiteSiteJobDO job = tx.execute(s -> {
            siteMapper.lock(site.getId(), TenantContext.getTenantId());
            if (jobMapper.selectCount(new LambdaQueryWrapper<WebsiteSiteJobDO>().eq(WebsiteSiteJobDO::getSiteId, site.getId())
                    .eq(WebsiteSiteJobDO::getStatus, JOB_RUNNING)) > 0) {
                throw new BizException("这个站点有导入或比对正在执行，请稍后再试");
            }
            if (releaseMapper.selectCount(new LambdaQueryWrapper<WebsiteReleaseDO>().eq(WebsiteReleaseDO::getSiteId, site.getId())
                    .in(WebsiteReleaseDO::getStatus, WebsiteConstants.RELEASE_QUEUED, WebsiteConstants.RELEASE_RUNNING)) > 0) {
                throw new BizException("这个站点有发布单正在执行，请稍后再试");
            }
            WebsiteSiteJobDO j = new WebsiteSiteJobDO();
            j.setTenantId(site.getTenantId());
            j.setSiteId(site.getId());
            j.setJobType(type);
            j.setJobNo(type.toUpperCase(Locale.ROOT) + "-" + site.getId() + "-" + System.currentTimeMillis());
            j.setStatus(JOB_RUNNING);
            j.setError("");
            j.setStartedAt(LocalDateTime.now());
            jobMapper.insert(j);
            return j;
        });
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getJobNo());
        body.put("type", type);
        body.put("callbackUrl", dispatcher.callbackUrl());
        body.put("site", sites.publisherSite(site));
        body.put("secrets", sites.publisherSecrets(site.getId()));
        try {
            if (compareSite != null) {
                body.put("siteData", dispatcher.siteData(site, job.getJobNo(), erpCatalog(site)));
            }
            SitePublisherClient.Reply reply = client.submitJob(body);
            if (reply.status() != 202) {
                String msg = reply.status() == 409 ? "站点正在执行其他任务，请稍后再试" : "发布服务没有接受任务：" + reply.detail();
                finish(job.getId(), JOB_FAILED, null, msg);
                throw new BizException(msg);
            }
        } catch (IllegalStateException e) {
            finish(job.getId(), JOB_FAILED, null, e.getMessage());
            throw new BizException(e.getMessage());
        }
        log.info("站点{}开始，siteId={}，jobNo={}", IMPORT.equals(type) ? "导入" : "比对", site.getId(), job.getJobNo());
        return toVO(jobMapper.selectById(job.getId()));
    }

    /** ERP 现在会生成的全部站点商品（比对用）：每个站点商品按当前数据重新生成 */
    private Map<Long, Map<String, Object>> erpCatalog(WebsiteSiteDO site) {
        Map<Long, Map<String, Object>> out = new HashMap<>();
        for (WebsiteProductDO wp : websiteProductMapper.selectList(new LambdaQueryWrapper<WebsiteProductDO>()
                .eq(WebsiteProductDO::getSiteId, site.getId()).isNull(WebsiteProductDO::getDeletedAt))) {
            ListingTaskDO t = taskMapper.selectById(wp.getListingTaskId());
            if (t != null && t.getDeletedAt() == null) {
                out.put(wp.getId(), siteData.build(site, t, wp, null, false).data());
            }
        }
        return out;
    }

    // ================================================================ 回调

    /** 发布服务回调：不是导入、比对任务返回 false。步骤进度不记录，只处理结束 */
    public boolean handle(JsonNode body) {
        String jobId = body.path("jobId").asText("");
        WebsiteSiteJobDO found = StringUtils.hasText(jobId) ? TenantScope.platform(() -> jobMapper.selectOne(
                new LambdaQueryWrapper<WebsiteSiteJobDO>().eq(WebsiteSiteJobDO::getJobNo, jobId).last("LIMIT 1"))) : null;
        if (found == null) {
            return false;
        }
        if (!"done".equals(body.path("event").asText()) || !Objects.equals(found.getStatus(), JOB_RUNNING)) {
            return true;
        }
        TenantContext.runAs(found.getTenantId(), () -> {
            if (!"success".equals(body.path("status").asText())) {
                finish(found.getId(), JOB_FAILED, null, body.path("error").asText("执行失败"));
                log.warn("站点任务失败，jobNo={}", jobId);
                return;
            }
            JsonNode result = body.path("result");
            Object stored = IMPORT.equals(found.getJobType()) ? analyzeImport(found.getSiteId(), result) : compareResult(result);
            finish(found.getId(), JOB_DONE, stored, "");
            log.info("站点任务完成，jobNo={}", jobId);
        });
        return true;
    }

    /** 状态只从执行中改（重复回调、看门狗已结束的不再改） */
    private void finish(Long jobId, int status, Object result, String error) {
        LambdaUpdateWrapper<WebsiteSiteJobDO> w = new LambdaUpdateWrapper<WebsiteSiteJobDO>()
                .eq(WebsiteSiteJobDO::getId, jobId).eq(WebsiteSiteJobDO::getStatus, JOB_RUNNING)
                .set(WebsiteSiteJobDO::getStatus, status)
                .set(WebsiteSiteJobDO::getError, cut(error, 500))
                .set(WebsiteSiteJobDO::getEndedAt, LocalDateTime.now());
        if (result != null) {
            w.set(WebsiteSiteJobDO::getResult, json(result));
        }
        jobMapper.update(null, w);
    }

    /** 执行超过 40 分钟没有结束的导入、比对标为失败（定时任务，平台范围调用） */
    public void watchdog(LocalDateTime now) {
        for (WebsiteSiteJobDO j : jobMapper.selectList(new LambdaQueryWrapper<WebsiteSiteJobDO>()
                .eq(WebsiteSiteJobDO::getStatus, JOB_RUNNING)
                .lt(WebsiteSiteJobDO::getStartedAt, now.minusMinutes(WebsiteConstants.RELEASE_TIMEOUT_MINUTES)))) {
            TenantContext.runAs(j.getTenantId(), () -> finish(j.getId(), JOB_FAILED, null,
                    "超过 " + WebsiteConstants.RELEASE_TIMEOUT_MINUTES + " 分钟没有完成，已标为失败"));
        }
    }

    /**
     * 导入结果：按品牌加型号（去掉首尾空格、不区分大小写）匹配商品档案；已匹配的列出与 ERP 现有内容不同的字段；
     * 未匹配的原样保存（以 ERP 为准后作为「只在站点上有」的商品带上）；列出 ERP 没有对应位置的字段；
     * 已导入、但站点上已经没有的商品列出来提示。
     */
    Map<String, Object> analyzeImport(Long siteId, JsonNode result) {
        WebsiteSiteDO site = sites.get(siteId);
        List<Map<String, Object>> products = objectMapper.convertValue(result.path("exported").path("products"),
                new TypeReference<List<Map<String, Object>>>() { });
        if (products == null) {
            products = List.of();
        }
        Map<String, ProductDO> byKey = match(products);
        List<Map<String, Object>> matched = new ArrayList<>();
        List<Map<String, Object>> unmatched = new ArrayList<>();
        Set<String> siteOnlyFields = new LinkedHashSet<>();
        Set<Long> seen = new HashSet<>();
        for (Map<String, Object> sp : products) {
            sp.keySet().stream().filter(k -> !SiteEntry.IDENTITY.contains(k) && SiteEntry.groupOf(k) == null)
                    .forEach(siteOnlyFields::add);
            ProductDO p = byKey.get(key(str(sp.get("brand")), str(sp.get("model"))));
            if (p == null) {
                unmatched.add(sp);
                continue;
            }
            seen.add(p.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", p.getId());
            m.put("brand", sp.get("brand"));
            m.put("model", sp.get("model"));
            m.put("slug", sp.get("slug"));
            m.put("sellPrice", sp.get("sell_price"));
            m.put("photos", sp.get("photos") instanceof List<?> l ? l.size() : 0);
            m.put("videos", sp.get("videos") instanceof List<?> l ? l.size() : 0);
            m.put("diffFields", diffFields(site, p, sp));
            m.put("data", sp);
            matched.add(m);
        }
        List<Map<String, Object>> missing = new ArrayList<>();
        for (WebsiteProductDO wp : websiteProductMapper.selectList(new LambdaQueryWrapper<WebsiteProductDO>()
                .eq(WebsiteProductDO::getSiteId, siteId).isNotNull(WebsiteProductDO::getImportedAt)
                .isNull(WebsiteProductDO::getDeletedAt))) {
            if (!seen.contains(wp.getProductId())) {
                Map<String, Object> orig = siteData.originalOf(wp);
                missing.add(Map.of("productId", wp.getProductId(), "brand", str(orig.get("brand")), "model", str(orig.get("model"))));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("commit", result.path("commit").asText(""));
        out.put("warnings", objectMapper.convertValue(result.path("exportWarnings"), List.class));
        out.put("total", products.size());
        out.put("matched", matched);
        out.put("unmatched", unmatched);
        out.put("siteOnlyFields", siteOnlyFields);
        out.put("missing", missing);
        return out;
    }

    /** 与 ERP 现有内容不同的字段：按 ERP 现有资料生成一份（不当作导入过），与站点内容比较 */
    private List<String> diffFields(WebsiteSiteDO site, ProductDO p, Map<String, Object> sp) {
        ListingTaskDO t = new ListingTaskDO();
        t.setProductId(p.getId());
        t.setPlatform(MarketingPlatform.SITE.code());
        t.setSiteId(site.getId());
        t.setAssetKey(MediaIndexer.key(brandName(p), p.getMpnRaw()));
        t.setRefBrand("");
        t.setRefModel("");
        WebsiteProductDO wp = new WebsiteProductDO();
        wp.setSiteId(site.getId());
        wp.setProductId(p.getId());
        wp.setSlug(str(sp.get("slug")));
        Map<String, Object> erp = siteData.build(site, t, wp, null, false).data();
        erp.remove("release");
        // 站点独有字段 ERP 没有，不算差异
        sp.keySet().stream().filter(k -> !SiteEntry.IDENTITY.contains(k) && SiteEntry.groupOf(k) == null)
                .forEach(k -> erp.put(k, sp.get(k)));
        return previews.changes(json(sp), erp).stream().map(c -> c.field()).toList();
    }

    private Map<String, ProductDO> match(List<Map<String, Object>> products) {
        Map<String, List<Long>> brandIds = new HashMap<>();
        for (ProductBrandDO b : brandMapper.selectList(new LambdaQueryWrapper<ProductBrandDO>()
                .select(ProductBrandDO::getId, ProductBrandDO::getBrandName))) {
            brandIds.computeIfAbsent(norm(b.getBrandName()), k -> new ArrayList<>()).add(b.getId());
        }
        Map<String, Set<String>> modelsByBrand = new HashMap<>();
        for (Map<String, Object> sp : products) {
            modelsByBrand.computeIfAbsent(norm(str(sp.get("brand"))), k -> new LinkedHashSet<>()).add(str(sp.get("model")).trim());
        }
        Map<String, ProductDO> out = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : modelsByBrand.entrySet()) {
            List<Long> ids = brandIds.get(e.getKey());
            if (ids == null || e.getValue().isEmpty()) {
                continue;
            }
            for (ProductDO p : productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                    .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                    .eq(ProductDO::getArchiveStatus, ProductConstants.ARCHIVE_DONE)
                    .in(ProductDO::getBrandId, ids).in(ProductDO::getMpnRaw, e.getValue())
                    .isNull(ProductDO::getDeletedAt).orderByAsc(ProductDO::getId))) {
                out.putIfAbsent(e.getKey() + "\n" + norm(p.getMpnRaw()), p);
            }
        }
        return out;
    }

    private static Map<String, Object> compareResult(JsonNode result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("commit", result.path("commit").asText(""));
        out.put("same", result.path("same").asInt());
        out.put("differences", result.path("differences"));
        out.put("onlyOnSite", result.path("onlyOnSite"));
        out.put("onlyInErp", result.path("onlyInErp"));
        return out;
    }

    // ================================================================ 确认

    /**
     * 确认导入：已匹配的商品建立或覆盖站点商品（链接别名锁定、站点原有内容、导入时间、上线链接，并以站点内容作为上次发布快照），
     * 这个站点的发品任务（没有就创建）标为已发布。不改共享商品库、营销内容和素材库。
     */
    public SiteJobVO confirmImport(Long jobId) {
        tx.executeWithoutResult(s -> {
            WebsiteSiteJobDO j = job(jobId, IMPORT);
            WebsiteSiteDO site = requireTakeover(j.getSiteId());
            siteMapper.lock(site.getId(), TenantContext.getTenantId());
            j = job(jobId, IMPORT);
            if (!Objects.equals(j.getStatus(), JOB_DONE)) {
                throw new BizException(Objects.equals(j.getStatus(), JOB_CONFIRMED) ? "这次导入已经确认过" : "导入还没有完成");
            }
            WebsiteSiteJobDO latest = latest(site.getId(), IMPORT);
            if (latest != null && !Objects.equals(latest.getId(), j.getId())) {
                throw new BizException("有更新的导入结果，请确认最新的一次");
            }
            JsonNode result = tree(j.getResult());
            String user = SecurityUtils.getCurrentUsername();
            LocalDateTime now = LocalDateTime.now();
            int n = 0;
            for (JsonNode m : result.path("matched")) {
                ProductDO p = productMapper.selectById(m.path("productId").asLong());
                if (p == null || p.getDeletedAt() != null) {
                    continue;
                }
                importOne(site, p, objectMapper.convertValue(m.path("data"), new TypeReference<LinkedHashMap<String, Object>>() { }),
                        user, now);
                n++;
            }
            jobMapper.update(null, new LambdaUpdateWrapper<WebsiteSiteJobDO>().eq(WebsiteSiteJobDO::getId, jobId)
                    .set(WebsiteSiteJobDO::getStatus, JOB_CONFIRMED));
            log.info("确认导入站点商品，siteId={}，商品数={}", site.getId(), n);
        });
        return toVO(jobMapper.selectById(jobId));
    }

    private void importOne(WebsiteSiteDO site, ProductDO p, Map<String, Object> sp, String user, LocalDateTime now) {
        ListingTaskDO task = taskMapper.selectOne(new LambdaQueryWrapper<ListingTaskDO>()
                .eq(ListingTaskDO::getProductId, p.getId()).eq(ListingTaskDO::getPlatform, MarketingPlatform.SITE.code())
                .eq(ListingTaskDO::getSiteId, site.getId()).isNull(ListingTaskDO::getDeletedAt).last("LIMIT 1"));
        if (task == null) {
            task = new ListingTaskDO();
            task.setTenantId(site.getTenantId());
            task.setProductId(p.getId());
            task.setPlatform(MarketingPlatform.SITE.code());
            task.setSiteId(site.getId());
            task.setAssetKey(MediaIndexer.key(brandName(p), p.getMpnRaw()));
            task.setStatus(ListingConstants.STATUS_PUBLISHED);
            taskMapper.insert(task);
        }
        String slug = str(sp.get("slug")).trim();
        Map<String, String> urls = new LinkedHashMap<>();
        for (SiteLanguage l : sites.languagesOf(site)) {
            urls.put(l.code(), SitePreviewService.liveUrl(site, l, slug));
        }
        WebsiteProductDO wp = websiteProductMapper.selectOne(new LambdaQueryWrapper<WebsiteProductDO>()
                .eq(WebsiteProductDO::getSiteId, site.getId()).eq(WebsiteProductDO::getProductId, p.getId())
                .isNull(WebsiteProductDO::getDeletedAt));
        boolean isNew = wp == null;
        if (isNew) {
            wp = new WebsiteProductDO();
            wp.setTenantId(site.getTenantId());
            wp.setSiteId(site.getId());
            wp.setProductId(p.getId());
            wp.setLastReleaseId(0L);
        }
        wp.setListingTaskId(task.getId());
        wp.setSlug(slug);
        wp.setSlugLocked(1);
        String status = str(sp.get("status"));
        wp.setSiteStatus(SiteStatus.LEGACY.equals(status) ? status : "");
        Map<String, Object> priceSource = new LinkedHashMap<>();
        priceSource.put("type", "import");
        priceSource.put("price", sp.get("sell_price"));
        wp.setPriceSource(json(priceSource));
        wp.setOriginalContent(json(sp));
        // 站点上现在的内容就是「上次发布」：之后预览、「内容有更新」都和它比
        wp.setLastSnapshot(json(sp));
        wp.setImportedAt(now);
        wp.setLiveUrls(json(urls));
        if (isNew) {
            websiteProductMapper.insert(wp);
        } else {
            websiteProductMapper.updateById(wp);
        }
        Map<String, Object> flags = taskMapper.flags(task.getId());
        Object contentAt = flags == null ? null : flags.get("current_content_at");
        Long taskId = task.getId();
        taskMapper.update(null, new LambdaUpdateWrapper<ListingTaskDO>().eq(ListingTaskDO::getId, taskId)
                .set(ListingTaskDO::getStatus, ListingConstants.STATUS_PUBLISHED)
                .set(ListingTaskDO::getCancelReason, "")
                .set(ListingTaskDO::getListingUrl, urls.values().stream().findFirst().orElse(""))
                .set(ListingTaskDO::getPublishedBy, user)
                .set(ListingTaskDO::getPublishedAt, now)
                .set(ListingTaskDO::getContentAt, ListingTaskService.time(contentAt))
                .set(ListingTaskDO::getSiteOutdated, 0)
                .set(ListingTaskDO::getUpdateTime, now)
                .set(ListingTaskDO::getUpdateBy, user));
    }

    /** 确认接受比对差异：items 为空表示全部；必须写明原因 */
    public SiteJobVO accept(Long jobId, List<Map<String, String>> items, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty() || why.length() > MAX_REASON) {
            throw new BizException("请写明接受差异的原因（最多 200 个字）");
        }
        tx.executeWithoutResult(s -> {
            WebsiteSiteJobDO j = job(jobId, COMPARE);
            requireTakeover(j.getSiteId());
            siteMapper.lock(j.getSiteId(), TenantContext.getTenantId());
            j = job(jobId, COMPARE);
            if (!Objects.equals(j.getStatus(), JOB_DONE)) {
                throw new BizException("比对还没有完成");
            }
            Set<String> diffs = new LinkedHashSet<>();
            tree(j.getResult()).path("differences").forEach(d -> diffs.add(d.path("model").asText() + "\n" + d.path("field").asText()));
            List<Map<String, Object>> accepted = accepted(j);
            Set<String> done = new HashSet<>();
            accepted.forEach(a -> done.add(a.get("model") + "\n" + a.get("field")));
            List<String> pick = new ArrayList<>();
            if (items == null || items.isEmpty()) {
                pick.addAll(diffs);
            } else {
                for (Map<String, String> it : items) {
                    String k = it.get("model") + "\n" + it.get("field");
                    if (!diffs.contains(k)) {
                        throw new BizException("没有这条差异：" + it.get("model") + " · " + it.get("field"));
                    }
                    pick.add(k);
                }
            }
            String user = SecurityUtils.getCurrentUsername();
            String at = LocalDateTime.now().toString();
            for (String k : pick) {
                if (done.add(k)) {
                    String[] mf = k.split("\n", 2);
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("model", mf[0]);
                    a.put("field", mf[1]);
                    a.put("reason", why);
                    a.put("by", user);
                    a.put("at", at);
                    accepted.add(a);
                }
            }
            jobMapper.update(null, new LambdaUpdateWrapper<WebsiteSiteJobDO>().eq(WebsiteSiteJobDO::getId, jobId)
                    .set(WebsiteSiteJobDO::getAccepted, json(accepted)));
        });
        return toVO(jobMapper.selectById(jobId));
    }

    /**
     * 开始以 ERP 为准：导入过商品的站点，需要在最后一次导入确认之后做过比对，且差异都已确认接受；
     * 没有导入过商品（仓库里本来没有商品）的站点可以直接开始。之后不能退回接管中。
     */
    public void takeover(Long siteId) {
        tx.executeWithoutResult(s -> {
            WebsiteSiteDO site = requireTakeover(siteId);
            siteMapper.lock(siteId, TenantContext.getTenantId());
            boolean imported = websiteProductMapper.selectCount(new LambdaQueryWrapper<WebsiteProductDO>()
                    .eq(WebsiteProductDO::getSiteId, siteId).isNotNull(WebsiteProductDO::getImportedAt)
                    .isNull(WebsiteProductDO::getDeletedAt)) > 0;
            WebsiteSiteJobDO cmp = latest(siteId, COMPARE);
            if (imported) {
                WebsiteSiteJobDO imp = latestConfirmed(siteId);
                if (cmp == null || !Objects.equals(cmp.getStatus(), JOB_DONE)
                        || (imp != null && cmp.getId() < imp.getId())) {
                    throw new BizException("请先在导入之后做一次一致性比对");
                }
                int pending = pending(cmp);
                if (pending > 0) {
                    throw new BizException("还有 " + pending + " 条差异没有确认");
                }
            }
            if (cmp != null && Objects.equals(cmp.getStatus(), JOB_DONE)) {
                jobMapper.update(null, new LambdaUpdateWrapper<WebsiteSiteJobDO>().eq(WebsiteSiteJobDO::getId, cmp.getId())
                        .set(WebsiteSiteJobDO::getStatus, JOB_CONFIRMED));
            }
            siteMapper.update(null, new LambdaUpdateWrapper<WebsiteSiteDO>().eq(WebsiteSiteDO::getId, siteId)
                    .set(WebsiteSiteDO::getTakeoverStatus, WebsiteConstants.TAKEOVER_DONE)
                    .set(WebsiteSiteDO::getTakeoverBy, SecurityUtils.getCurrentUsername())
                    .set(WebsiteSiteDO::getTakeoverAt, LocalDateTime.now())
                    .set(WebsiteSiteDO::getUpdateTime, LocalDateTime.now())
                    .set(WebsiteSiteDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
            log.info("站点开始以 ERP 为准，siteId={}，导入过商品={}", siteId, imported);
        });
    }

    // ================================================================ 查询

    /** 站点最近的导入、比对任务（最近在前） */
    public List<SiteJobVO> list(Long siteId) {
        sites.get(siteId);
        return jobMapper.selectList(new LambdaQueryWrapper<WebsiteSiteJobDO>().eq(WebsiteSiteJobDO::getSiteId, siteId)
                        .orderByDesc(WebsiteSiteJobDO::getId).last("LIMIT 50"))
                .stream().map(j -> {
                    SiteJobVO v = toVO(j);
                    v.setResult(null);
                    return v;
                }).toList();
    }

    public SiteJobVO detail(Long jobId) {
        WebsiteSiteJobDO j = jobMapper.selectById(jobId);
        if (j == null) {
            throw new BizException("任务不存在");
        }
        sites.get(j.getSiteId());
        return toVO(j);
    }

    private SiteJobVO toVO(WebsiteSiteJobDO j) {
        SiteJobVO v = new SiteJobVO();
        v.setId(j.getId());
        v.setSiteId(j.getSiteId());
        v.setJobType(j.getJobType());
        v.setJobNo(j.getJobNo());
        v.setStatus(j.getStatus());
        v.setStatusName(STATUS_NAMES[j.getStatus()]);
        v.setError(j.getError());
        v.setStartedAt(j.getStartedAt());
        v.setEndedAt(j.getEndedAt());
        v.setCreateBy(j.getCreateBy());
        v.setCreateTime(j.getCreateTime());
        v.setResult(tree(j.getResult()));
        v.setAccepted(tree(j.getAccepted()));
        if (COMPARE.equals(j.getJobType())) {
            v.setPending(pending(j));
        }
        return v;
    }

    /** 没有确认接受的差异条数 */
    private int pending(WebsiteSiteJobDO j) {
        Set<String> done = new HashSet<>();
        accepted(j).forEach(a -> done.add(a.get("model") + "\n" + a.get("field")));
        int n = 0;
        for (JsonNode d : tree(j.getResult()).path("differences")) {
            if (!done.contains(d.path("model").asText() + "\n" + d.path("field").asText())) {
                n++;
            }
        }
        return n;
    }

    // ================================================================ 工具

    private WebsiteSiteDO requireTakeover(Long siteId) {
        WebsiteSiteDO site = sites.get(siteId);
        if (!Objects.equals(site.getPublishMode(), WebsiteConstants.MODE_AUTO)) {
            throw new BizException("只复制的站点不需要导入和比对");
        }
        if (Objects.equals(site.getTakeoverStatus(), WebsiteConstants.TAKEOVER_DONE)) {
            throw new BizException("站点已经以 ERP 为准，不能再导入或比对");
        }
        return site;
    }

    private WebsiteSiteJobDO job(Long id, String type) {
        WebsiteSiteJobDO j = id == null ? null : jobMapper.selectById(id);
        if (j == null || !type.equals(j.getJobType())) {
            throw new BizException("任务不存在");
        }
        return j;
    }

    private WebsiteSiteJobDO latest(Long siteId, String type) {
        return jobMapper.selectOne(new LambdaQueryWrapper<WebsiteSiteJobDO>().eq(WebsiteSiteJobDO::getSiteId, siteId)
                .eq(WebsiteSiteJobDO::getJobType, type).in(WebsiteSiteJobDO::getStatus, JOB_DONE, JOB_CONFIRMED)
                .orderByDesc(WebsiteSiteJobDO::getId).last("LIMIT 1"));
    }

    private WebsiteSiteJobDO latestConfirmed(Long siteId) {
        return jobMapper.selectOne(new LambdaQueryWrapper<WebsiteSiteJobDO>().eq(WebsiteSiteJobDO::getSiteId, siteId)
                .eq(WebsiteSiteJobDO::getJobType, IMPORT).eq(WebsiteSiteJobDO::getStatus, JOB_CONFIRMED)
                .orderByDesc(WebsiteSiteJobDO::getId).last("LIMIT 1"));
    }

    private List<Map<String, Object>> accepted(WebsiteSiteJobDO j) {
        if (!StringUtils.hasText(j.getAccepted())) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(j.getAccepted(), new TypeReference<List<Map<String, Object>>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("差异确认记录读取失败", e);
        }
    }

    private String brandName(ProductDO p) {
        ProductBrandDO b = p.getBrandId() == null ? null : brandMapper.selectById(p.getBrandId());
        return b == null ? "" : b.getBrandName();
    }

    private static String key(String brand, String model) {
        return norm(brand) + "\n" + norm(model);
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String cut(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) : s);
    }

    private JsonNode tree(String json) {
        try {
            return StringUtils.hasText(json) ? objectMapper.readTree(json) : objectMapper.createObjectNode();
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    private String json(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("站点任务序列化失败", e);
        }
    }
}

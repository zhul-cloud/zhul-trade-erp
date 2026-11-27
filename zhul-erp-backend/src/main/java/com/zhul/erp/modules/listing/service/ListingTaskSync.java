package com.zhul.erp.modules.listing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.listing.constants.ListingConstants;
import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import com.zhul.erp.modules.listing.repository.ListingTaskMapper;
import com.zhul.erp.modules.media.service.MediaIndexer;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.content.entity.ProductContentTaskDO;
import com.zhul.erp.modules.product.content.entity.ProductFactTaskDO;
import com.zhul.erp.modules.product.content.entity.ProductMarketingSkipDO;
import com.zhul.erp.modules.product.content.repository.ProductContentTaskMapper;
import com.zhul.erp.modules.product.content.repository.ProductFactTaskMapper;
import com.zhul.erp.modules.product.content.repository.ProductMarketingSkipMapper;
import com.zhul.erp.modules.product.content.service.FactTaskService;
import com.zhul.erp.modules.product.content.service.PlatformSettingService;
import com.zhul.erp.modules.product.content.support.MarketingChangedEvent;
import com.zhul.erp.modules.product.content.support.MarketingPlatform;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.dto.SiteScope;
import com.zhul.erp.modules.website.entity.WebsiteSiteDO;
import com.zhul.erp.modules.website.repository.WebsiteSiteMapper;
import com.zhul.erp.modules.website.support.SiteScopeMatcher;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 发品任务的生成与取消（spec product/listing-task「发品任务的生成」「取消与恢复」）。
 * 幂等：按当前的资料状态、平台设置、不发布标记和营销写入，逐个「商品 × 平台」对齐任务；补生成、平时的事件都走这里。
 * 独立站按站点区分（openspec website-publish）：每个站点一个任务，另看站点是否启用、商品是否在站点的商品范围内；
 * 公司还没有站点时与原来一样，按平台生成一个独立站任务（site_id 为 0），建好第一个站点后挂到这个站点上。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingTaskSync {

    private final ListingTaskMapper taskMapper;
    private final ProductContentTaskMapper contentTaskMapper;
    private final ProductFactTaskMapper factTaskMapper;
    private final ProductMarketingSkipMapper skipMapper;
    private final ProductMapper productMapper;
    private final ProductBrandMapper brandMapper;
    private final PlatformSettingService platformSettings;
    private final WebsiteSiteMapper siteMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ObjectMapper objectMapper;

    /** 营销变化：在发布方的事务里同步处理，失败一起回滚 */
    @EventListener
    public void on(MarketingChangedEvent e) {
        if (e.tenantId() == null) {
            if (e.productIds() == null || e.productIds().isEmpty()) {
                return;
            }
            // 资料核对是共享操作：显式跨公司，查出有这个商品营销任务的公司，按各自的租户逐个处理
            for (Integer tenantId : TenantScope.platform(() -> taskMapper.tenantsOf(e.productIds()))) {
                TenantContext.runAs(tenantId, () -> sync(tenantId, e.productIds()));
            }
            return;
        }
        if (e.productIds() == null) {
            syncAll(e.tenantId());
        } else {
            sync(e.tenantId(), e.productIds());
        }
    }

    /** 这家公司全部有营销写入的商品，按批处理 */
    public int syncAll(int tenantId) {
        List<Long> ids = taskMapper.writtenProducts(tenantId);
        int changed = 0;
        for (int i = 0; i < ids.size(); i += ListingConstants.SYNC_BATCH) {
            changed += sync(tenantId, ids.subList(i, Math.min(ids.size(), i + ListingConstants.SYNC_BATCH)));
        }
        return changed;
    }

    /** 对齐这家公司这些商品的发品任务，返回新建、取消或恢复的任务数 */
    public int sync(int tenantId, Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return 0;
        }
        Set<MarketingPlatform> enabled = platformSettings.enabled(tenantId);
        Map<Long, ProductDO> products = productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                        .eq(ProductDO::getArchiveStatus, ProductConstants.ARCHIVE_DONE)
                        .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .in(ProductDO::getId, productIds)
                        .eq(ProductDO::getStatus, 1)
                        .isNull(ProductDO::getDeletedAt))
                .stream().collect(Collectors.toMap(ProductDO::getId, Function.identity()));
        if (products.isEmpty()) {
            return 0;
        }
        Map<Long, String> brandNames = brandNames(products.values());
        Map<Long, ProductContentTaskDO> contents = contentTaskMapper.selectList(new LambdaQueryWrapper<ProductContentTaskDO>()
                        .eq(ProductContentTaskDO::getTenantId, tenantId)
                        .in(ProductContentTaskDO::getProductId, products.keySet())
                        .isNull(ProductContentTaskDO::getDeletedAt))
                .stream().collect(Collectors.toMap(ProductContentTaskDO::getProductId, Function.identity(), (a, b) -> a));
        Map<Long, ProductFactTaskDO> facts = factTaskMapper.selectList(new LambdaQueryWrapper<ProductFactTaskDO>()
                        .eq(ProductFactTaskDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .in(ProductFactTaskDO::getProductId, products.keySet())
                        .isNull(ProductFactTaskDO::getDeletedAt))
                .stream().collect(Collectors.toMap(ProductFactTaskDO::getProductId, Function.identity(), (a, b) -> a));
        Set<String> skipped = new HashSet<>();
        skipMapper.selectList(new LambdaQueryWrapper<ProductMarketingSkipDO>()
                        .eq(ProductMarketingSkipDO::getTenantId, tenantId)
                        .in(ProductMarketingSkipDO::getProductId, products.keySet())
                        .isNull(ProductMarketingSkipDO::getDeletedAt))
                .forEach(s -> skipped.add(s.getProductId() + "|" + s.getPlatform()));
        Map<String, ListingTaskDO> existing = new HashMap<>();
        taskMapper.selectList(new LambdaQueryWrapper<ListingTaskDO>()
                        .eq(ListingTaskDO::getTenantId, tenantId)
                        .in(ListingTaskDO::getProductId, products.keySet())
                        .isNull(ListingTaskDO::getDeletedAt))
                .forEach(t -> existing.put(key(t.getProductId(), t.getPlatform(), t.getSiteId()), t));
        List<Site> sites = sites(tenantId);
        Map<Long, Long> parents = sites.isEmpty() ? Map.of() : categoryParents(products.values());

        int changed = 0;
        for (ProductDO p : products.values()) {
            String assetKey = MediaIndexer.key(brandNames.get(p.getBrandId()), p.getMpnRaw());
            ProductContentTaskDO content = contents.get(p.getId());
            boolean verified = FactTaskService.verified(facts.get(p.getId()));
            for (MarketingPlatform platform : MarketingPlatform.values()) {
                String skipKey = p.getId() + "|" + platform.code();
                String platformCancel = !enabled.contains(platform) ? ListingConstants.CANCEL_DISABLED
                        : skipped.contains(skipKey) ? ListingConstants.CANCEL_SKIPPED : null;
                boolean written = platform.written(content);
                if (platform != MarketingPlatform.SITE) {
                    changed += align(existing.get(key(p.getId(), platform.code(), 0L)), tenantId, p.getId(), platform, 0L,
                            platformCancel, verified, written, assetKey);
                    continue;
                }
                ListingTaskDO legacy = existing.get(key(p.getId(), platform.code(), 0L));
                if (sites.isEmpty()) {
                    // 还没有站点：与原来一样按平台生成一个任务（site_id 为 0），手工发布；建好第一个站点后挂到这个站点上
                    changed += align(legacy, tenantId, p.getId(), platform, 0L, platformCancel, verified, written, assetKey);
                    continue;
                }
                Site first = sites.get(0);
                if (legacy != null && !existing.containsKey(key(p.getId(), platform.code(), first.id()))) {
                    adopt(legacy, first.id());
                    existing.put(key(p.getId(), platform.code(), first.id()), legacy);
                }
                for (Site site : sites) {
                    String cancel = platformCancel != null ? platformCancel
                            : !site.enabled() ? ListingConstants.CANCEL_SITE_DISABLED
                            : !SiteScopeMatcher.matches(site.scope(), p.getBrandId(), p.getCategoryId(), parents.get(p.getCategoryId()))
                            ? ListingConstants.CANCEL_OUT_OF_SCOPE : null;
                    changed += align(existing.get(key(p.getId(), platform.code(), site.id())), tenantId, p.getId(), platform, site.id(),
                            cancel, verified, written, assetKey);
                }
            }
        }
        return changed;
    }

    /** 对齐一个任务：没有就按条件新建；有就按取消原因取消（只取消待发布的）或恢复；返回状态变化数 */
    private int align(ListingTaskDO task, int tenantId, Long productId, MarketingPlatform platform, Long siteId,
                      String cancel, boolean verified, boolean written, String assetKey) {
        if (task == null) {
            return cancel == null && verified && written ? create(tenantId, productId, platform, siteId, assetKey) : 0;
        }
        int status = task.getStatus();
        if (cancel != null) {
            if (status == ListingConstants.STATUS_PENDING) {
                return update(task, ListingConstants.STATUS_CANCELLED, cancel, assetKey);
            }
            if (status == ListingConstants.STATUS_CANCELLED && !Objects.equals(cancel, task.getCancelReason())) {
                update(task, status, cancel, assetKey);
            }
        } else if (status == ListingConstants.STATUS_CANCELLED && written) {
            // 任务生成过，说明当时已满足条件；之后资料回到待核也保持任务，所以恢复只看内容还在
            return update(task, ListingConstants.STATUS_PENDING, "", assetKey);
        } else if (!Objects.equals(task.getAssetKey(), assetKey)) {
            update(task, status, task.getCancelReason(), assetKey);
        }
        return 0;
    }

    /** 公司建好第一个站点前生成的独立站任务，挂到第一个站点上 */
    private void adopt(ListingTaskDO task, Long siteId) {
        taskMapper.update(null, new LambdaUpdateWrapper<ListingTaskDO>()
                .eq(ListingTaskDO::getId, task.getId())
                .eq(ListingTaskDO::getSiteId, 0L)
                .set(ListingTaskDO::getSiteId, siteId)
                .set(ListingTaskDO::getUpdateTime, LocalDateTime.now())
                .set(ListingTaskDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
        task.setSiteId(siteId);
        log.info("旧独立站任务挂到站点，taskId={}，siteId={}", task.getId(), siteId);
    }

    private static String key(Long productId, String platform, Long siteId) {
        return productId + "|" + platform + "|" + (siteId == null ? 0L : siteId);
    }

    private record Site(Long id, boolean enabled, SiteScope scope) {
    }

    /** 本公司未删除的站点，按 id 升序（第一个站点接收旧任务）；只复制的站点也生成任务，由人工标记已发布 */
    private List<Site> sites(int tenantId) {
        List<Site> out = new ArrayList<>();
        for (WebsiteSiteDO s : siteMapper.selectList(new LambdaQueryWrapper<WebsiteSiteDO>()
                .eq(WebsiteSiteDO::getTenantId, tenantId).isNull(WebsiteSiteDO::getDeletedAt))) {
            out.add(new Site(s.getId(), Objects.equals(s.getStatus(), WebsiteConstants.STATUS_ENABLED), scope(s.getProductScope())));
        }
        out.sort(Comparator.comparing(Site::id));
        return out;
    }

    private SiteScope scope(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SiteScope.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("站点商品范围解析失败", e);
        }
    }

    private Map<Long, Long> categoryParents(Collection<ProductDO> products) {
        List<Long> ids = products.stream().map(ProductDO::getCategoryId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> out = new HashMap<>(ids.size() * 2);
        for (ProductCategoryDO c : categoryMapper.selectBatchIds(ids)) {
            if (c.getParentId() != null) {
                out.put(c.getId(), c.getParentId());
            }
        }
        return out;
    }

    private int create(int tenantId, Long productId, MarketingPlatform platform, Long siteId, String assetKey) {
        ListingTaskDO row = new ListingTaskDO();
        row.setTenantId(tenantId);
        row.setProductId(productId);
        row.setPlatform(platform.code());
        row.setSiteId(siteId);
        row.setAssetKey(assetKey);
        row.setStatus(ListingConstants.STATUS_PENDING);
        try {
            taskMapper.insert(row);
            return 1;
        } catch (DuplicateKeyException e) {
            // 并发时另一个请求先生成了
            return 0;
        }
    }

    private int update(ListingTaskDO task, int status, String reason, String assetKey) {
        taskMapper.update(null, new LambdaUpdateWrapper<ListingTaskDO>()
                .eq(ListingTaskDO::getId, task.getId())
                .set(ListingTaskDO::getStatus, status)
                .set(ListingTaskDO::getCancelReason, reason)
                .set(ListingTaskDO::getAssetKey, assetKey)
                .set(ListingTaskDO::getUpdateTime, LocalDateTime.now())
                .set(ListingTaskDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
        return status == task.getStatus() ? 0 : 1;
    }

    private Map<Long, String> brandNames(Collection<ProductDO> products) {
        List<Long> ids = products.stream().map(ProductDO::getBrandId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return brandMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ProductBrandDO::getId, ProductBrandDO::getBrandName));
    }
}

package com.zhul.erp.modules.listing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.framework.security.SecurityUtils;
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
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
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

    /** 营销变化：在发布方的事务里同步处理，失败一起回滚 */
    @EventListener
    public void on(MarketingChangedEvent e) {
        if (e.tenantId() == null) {
            if (e.productIds() == null || e.productIds().isEmpty()) {
                return;
            }
            // 资料核对是共享操作：显式跨公司，查出有这个商品营销任务的公司逐个处理
            for (Integer tenantId : taskMapper.tenantsOf(e.productIds())) {
                sync(tenantId, e.productIds());
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
                .forEach(t -> existing.put(t.getProductId() + "|" + t.getPlatform(), t));

        int changed = 0;
        for (ProductDO p : products.values()) {
            String assetKey = MediaIndexer.key(brandNames.get(p.getBrandId()), p.getMpnRaw());
            ProductContentTaskDO content = contents.get(p.getId());
            boolean verified = FactTaskService.verified(facts.get(p.getId()));
            for (MarketingPlatform platform : MarketingPlatform.values()) {
                String k = p.getId() + "|" + platform.code();
                ListingTaskDO task = existing.get(k);
                String cancel = !enabled.contains(platform) ? ListingConstants.CANCEL_DISABLED
                        : skipped.contains(k) ? ListingConstants.CANCEL_SKIPPED : null;
                if (task == null) {
                    if (cancel == null && verified && platform.written(content)) {
                        changed += create(tenantId, p.getId(), platform, assetKey);
                    }
                    continue;
                }
                int status = task.getStatus();
                if (cancel != null) {
                    if (status == ListingConstants.STATUS_PENDING) {
                        changed += update(task, ListingConstants.STATUS_CANCELLED, cancel, assetKey);
                    }
                } else if (status == ListingConstants.STATUS_CANCELLED && platform.written(content)) {
                    // 任务生成过，说明当时已满足条件；之后资料回到待核也保持任务，所以恢复只看内容还在
                    changed += update(task, ListingConstants.STATUS_PENDING, "", assetKey);
                } else if (!Objects.equals(task.getAssetKey(), assetKey)) {
                    update(task, status, task.getCancelReason(), assetKey);
                }
            }
        }
        return changed;
    }

    private int create(int tenantId, Long productId, MarketingPlatform platform, String assetKey) {
        ListingTaskDO row = new ListingTaskDO();
        row.setTenantId(tenantId);
        row.setProductId(productId);
        row.setPlatform(platform.code());
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

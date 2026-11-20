package com.zhul.erp.modules.supply.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.support.ProductCategoryChangedEvent;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.repository.ScDemandMapper;
import com.zhul.erp.modules.supply.repository.SupplySourceMapper;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 需求型号品类按商品主数据补齐（spec supply-chain/demand-pool「需求型号品类按商品主数据补齐」）：
 * 当前租户品类为 0（未分类）、有型号的需求对象，找同品牌、同归一化型号的商品，商品品类是细分品类时补上。
 * 只补未分类的，已有品类的不改；补齐写需求对象操作日志，操作人记为系统。
 * <p>
 * 两个入口：每日任务对每个租户调用 {@link #fillAll()}（调用方负责租户上下文与事务）；
 * 商品品类改动的事务提交后，{@link #onProductCategoryChanged} 在当前租户下另起事务补齐对应型号，失败只记日志，第二天由每日任务补上。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemandCategorySync {

    /** 按型号批量查商品的每批型号数 */
    private static final int KEY_BATCH = 500;

    private final ScDemandMapper demandMapper;
    private final SupplySourceMapper sourceMapper;
    private final ProductMapper productMapper;
    private final SupplyLookups lookups;
    private final DocLogs docLogs;
    private final PlatformTransactionManager txManager;

    /** 补齐当前租户全部未分类的需求对象，返回补齐个数 */
    public int fillAll() {
        List<ScDemandDO> demands = demandMapper.selectList(new LambdaQueryWrapper<ScDemandDO>()
                .select(ScDemandDO::getId, ScDemandDO::getBrandId, ScDemandDO::getModelKey, ScDemandDO::getCategoryId)
                .eq(ScDemandDO::getCategoryId, 0L)
                .ne(ScDemandDO::getModelKey, "")
                .isNull(ScDemandDO::getDeletedAt));
        if (demands.isEmpty()) {
            return 0;
        }
        Set<Long> level2 = level2();
        List<String> keys = demands.stream().map(ScDemandDO::getModelKey).distinct().toList();
        Map<String, Long> categoryOf = new HashMap<>(keys.size() * 2);
        for (int i = 0; i < keys.size(); i += KEY_BATCH) {
            for (Map<String, Object> m : sourceMapper.productScopes(keys.subList(i, Math.min(keys.size(), i + KEY_BATCH)))) {
                Number brand = (Number) m.get("brandId");
                Number category = (Number) m.get("categoryId");
                if (brand != null && category != null && level2.contains(category.longValue())) {
                    categoryOf.put(key(brand.longValue(), (String) m.get("modelKey")), category.longValue());
                }
            }
        }
        return apply(demands, categoryOf);
    }

    /** 补齐当前租户里与这些商品同品牌同型号、未分类的需求对象 */
    public int fillForProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return 0;
        }
        Set<Long> level2 = level2();
        Map<String, Long> categoryOf = new HashMap<>(productIds.size() * 2);
        for (ProductDO p : productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getArchiveStatus, ProductConstants.ARCHIVE_DONE)
                .select(ProductDO::getBrandId, ProductDO::getMpnNormalized, ProductDO::getCategoryId)
                .in(ProductDO::getId, productIds)
                .isNull(ProductDO::getDeletedAt))) {
            if (p.getBrandId() != null && StringUtils.hasText(p.getMpnNormalized()) && level2.contains(p.getCategoryId())) {
                categoryOf.put(key(p.getBrandId(), p.getMpnNormalized()), p.getCategoryId());
            }
        }
        if (categoryOf.isEmpty()) {
            return 0;
        }
        Set<String> models = new HashSet<>();
        categoryOf.keySet().forEach(k -> models.add(k.substring(k.indexOf(':') + 1)));
        List<ScDemandDO> demands = demandMapper.selectList(new LambdaQueryWrapper<ScDemandDO>()
                .select(ScDemandDO::getId, ScDemandDO::getBrandId, ScDemandDO::getModelKey, ScDemandDO::getCategoryId)
                .eq(ScDemandDO::getCategoryId, 0L)
                .in(ScDemandDO::getModelKey, models)
                .isNull(ScDemandDO::getDeletedAt));
        return apply(demands, categoryOf);
    }

    /** 商品品类改动提交后，在当前租户下补齐；没有租户上下文（平台入口）时交给每日任务 */
    @TransactionalEventListener
    public void onProductCategoryChanged(ProductCategoryChangedEvent event) {
        Integer tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return;
        }
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            Integer filled = tx.execute(st -> fillForProducts(event.productIds()));
            if (filled != null && filled > 0) {
                log.info("按商品主数据补齐需求型号品类，tenantId={}, count={}", tenantId, filled);
            }
        } catch (RuntimeException e) {
            // 事务提交后的监听里抛出会让已成功的采纳请求报错，所以只记日志
            log.warn("按商品主数据补齐需求型号品类失败，等每日任务补上，tenantId={}", tenantId, e);
        }
    }

    private int apply(List<ScDemandDO> demands, Map<String, Long> categoryOf) {
        List<long[]> changed = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (ScDemandDO d : demands) {
            Long category = categoryOf.get(key(d.getBrandId() == null ? 0 : d.getBrandId(), d.getModelKey()));
            if (category == null) {
                continue;
            }
            int updated = demandMapper.update(null, new LambdaUpdateWrapper<ScDemandDO>()
                    .eq(ScDemandDO::getId, d.getId())
                    .eq(ScDemandDO::getCategoryId, 0L)
                    .set(ScDemandDO::getCategoryId, category)
                    .set(ScDemandDO::getUpdateTime, now));
            if (updated > 0) {
                changed.add(new long[]{d.getId(), category});
            }
        }
        if (!changed.isEmpty()) {
            Map<Long, String> names = lookups.categoryNames(changed.stream().map(c -> c[1]).toList());
            for (long[] c : changed) {
                docLogs.addBySystem(DocumentType.SC, c[0], "品类",
                        "未分类 → " + Objects.toString(names.get(c[1]), "") + "（按商品主数据）");
            }
        }
        return changed.size();
    }

    private Set<Long> level2() {
        Set<Long> out = new HashSet<>();
        lookups.categoryParents().forEach((id, parent) -> {
            if (parent != null) {
                out.add(id);
            }
        });
        return out;
    }

    private static String key(long brandId, String modelKey) {
        return brandId + ":" + modelKey;
    }
}

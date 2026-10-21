package com.zhul.erp.modules.product.content.service;

import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import com.zhul.erp.modules.product.content.support.MarketingChangedEvent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.modules.product.content.entity.ProductContentTaskDO;
import com.zhul.erp.modules.product.content.entity.ProductFactTaskDO;
import com.zhul.erp.modules.product.content.repository.ProductFactTaskMapper;
import com.zhul.erp.modules.product.content.support.MarketingPlatform;
import com.zhul.erp.modules.product.entity.ProductApplicationDO;
import com.zhul.erp.modules.product.entity.ProductSpecificationDO;
import com.zhul.erp.modules.product.repository.ProductApplicationMapper;
import com.zhul.erp.modules.product.repository.ProductSpecificationMapper;
import com.zhul.erp.modules.product.support.ProductFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

/**
 * 资料任务（spec product/product-content「内容任务」「资料核对」）：平台级，每个商品一行，所有公司共用。
 * 导入资料包记录导入时间；核对记录核对人并把中文规格、应用场景标为已核实；导入晚于核对即回到待核。
 */
@Service
@RequiredArgsConstructor
public class FactTaskService {

    private final ProductFactTaskMapper factTaskMapper;
    private final ApplicationEventPublisher events;
    private final ProductSpecificationMapper specificationMapper;
    private final ProductApplicationMapper applicationMapper;
    private final ProductFinder productFinder;

    /** 资料任务行，没有时为空 */
    public ProductFactTaskDO find(Long productId) {
        return factTaskMapper.selectOne(new LambdaQueryWrapper<ProductFactTaskDO>()
                .eq(ProductFactTaskDO::getProductId, productId)
                .isNull(ProductFactTaskDO::getDeletedAt));
    }

    /** 取该商品的资料任务行并加锁，没有时创建；必须在事务内调用 */
    public ProductFactTaskDO lockOrCreate(Long productId) {
        Long id = factTaskMapper.lockByProduct(productId);
        if (id == null) {
            ProductFactTaskDO row = new ProductFactTaskDO();
            row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            row.setProductId(productId);
            try {
                factTaskMapper.insert(row);
            } catch (DuplicateKeyException e) {
                // 并发时另一个请求先插入了，锁住它即可
            }
            id = factTaskMapper.lockByProduct(productId);
        }
        return factTaskMapper.selectById(id);
    }

    /** 确认资料包后记录导入（调用方已锁住资料任务行） */
    public void markImported(ProductFactTaskDO fact) {
        // 时间存到秒：同一秒内先核对再导入时，导入时间要晚于核对时间才能回到待核
        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (fact.getVerifiedAt() != null && !now.isAfter(fact.getVerifiedAt())) {
            now = fact.getVerifiedAt().plusSeconds(1);
        }
        fact.setImportedAt(now);
        fact.setImportedBy(SecurityUtils.getCurrentUsername());
        factTaskMapper.updateById(fact);
    }

    /** 资料已核：记录核对人与时间，未核实的中文规格、应用场景标为已核实 */
    @Transactional(rollbackFor = Exception.class)
    public void verify(Long productId) {
        productFinder.lockActive(productId);
        ProductFactTaskDO fact = lockOrCreate(productId);
        if (verified(fact)) {
            throw BizException.of(ProductErrorCodes.FACTS_ALREADY_VERIFIED, "资料已核对，之后没有再导入资料包");
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (fact.getImportedAt() != null && now.isBefore(fact.getImportedAt())) {
            now = fact.getImportedAt();
        }
        String user = SecurityUtils.getCurrentUsername();
        fact.setVerifiedAt(now);
        fact.setVerifiedBy(user);
        factTaskMapper.updateById(fact);
        specificationMapper.update(null, new LambdaUpdateWrapper<ProductSpecificationDO>()
                .eq(ProductSpecificationDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductSpecificationDO::getProductId, productId)
                .eq(ProductSpecificationDO::getLang, ContentConstants.FACTS_LANG)
                .eq(ProductSpecificationDO::getVerified, 0)
                .isNull(ProductSpecificationDO::getDeletedAt)
                .set(ProductSpecificationDO::getVerified, 1)
                .set(ProductSpecificationDO::getUpdateTime, now)
                .set(ProductSpecificationDO::getUpdateBy, user));
        applicationMapper.update(null, new LambdaUpdateWrapper<ProductApplicationDO>()
                .eq(ProductApplicationDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductApplicationDO::getProductId, productId)
                .eq(ProductApplicationDO::getLang, ContentConstants.FACTS_LANG)
                .eq(ProductApplicationDO::getVerified, 0)
                .isNull(ProductApplicationDO::getDeletedAt)
                .set(ProductApplicationDO::getVerified, 1)
                .set(ProductApplicationDO::getUpdateTime, now)
                .set(ProductApplicationDO::getUpdateBy, user));
        // 资料是共享的：所有有这个商品营销任务的公司都重算发品任务
        events.publishEvent(new MarketingChangedEvent(null, List.of(productId)));
    }

    /** 记录下载资料包时间（逐个商品独立，不锁商品） */
    public void markDownloaded(Collection<Long> productIds) {
        LocalDateTime now = LocalDateTime.now();
        for (Long productId : productIds) {
            int updated = factTaskMapper.update(null, new LambdaUpdateWrapper<ProductFactTaskDO>()
                    .eq(ProductFactTaskDO::getProductId, productId)
                    .set(ProductFactTaskDO::getDownloadedAt, now)
                    .set(ProductFactTaskDO::getUpdateTime, now)
                    .set(ProductFactTaskDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
            if (updated == 0) {
                ProductFactTaskDO row = new ProductFactTaskDO();
                row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
                row.setProductId(productId);
                row.setDownloadedAt(now);
                try {
                    factTaskMapper.insert(row);
                } catch (DuplicateKeyException e) {
                    // 并发插入，下载时间以另一个请求为准
                }
            }
        }
    }

    /** 已核对且之后没有再导入 */
    public static boolean verified(ProductFactTaskDO fact) {
        return fact != null && fact.getVerifiedAt() != null
                && (fact.getImportedAt() == null || !fact.getVerifiedAt().isBefore(fact.getImportedAt()));
    }

    /** 任务阶段，与 ProductContentTaskMapper.STAGE 同一口径：本公司启用且没标不发布的平台都写完才算完成 */
    public static int stage(ProductFactTaskDO fact, ProductContentTaskDO task, Set<MarketingPlatform> enabled,
                            Set<String> skipped) {
        if (fact == null || fact.getImportedAt() == null && fact.getVerifiedAt() == null) {
            return ContentConstants.STAGE_FACTS_PENDING;
        }
        if (!verified(fact)) {
            return ContentConstants.STAGE_FACTS_REVIEW;
        }
        for (MarketingPlatform p : enabled) {
            if (!skipped.contains(p.code()) && !p.written(task)) {
                return ContentConstants.STAGE_MARKETING;
            }
        }
        return ContentConstants.STAGE_DONE;
    }
}

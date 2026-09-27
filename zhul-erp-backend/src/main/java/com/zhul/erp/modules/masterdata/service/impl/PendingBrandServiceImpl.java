package com.zhul.erp.modules.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.modules.masterdata.dto.PendingBrandVO;
import com.zhul.erp.modules.masterdata.entity.SupplierProductScopeDO;
import com.zhul.erp.modules.masterdata.repository.SupplierProductScopeMapper;
import com.zhul.erp.modules.masterdata.service.PendingBrandService;
import com.zhul.erp.modules.product.dto.BrandVO;
import com.zhul.erp.modules.product.dto.SaveBrandRequest;
import com.zhul.erp.modules.product.service.BrandService;
import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 待确认品牌。汇总与关联都是有意的跨租户操作（平台账号统一整理品牌词表），只暴露名称和计数。
 */
@Service
@RequiredArgsConstructor
public class PendingBrandServiceImpl implements PendingBrandService {

    private final SupplierProductScopeMapper scopeMapper;
    private final BrandService brandService;
    private final PlatformScopeGuard platformScopeGuard;

    @Override
    public List<PendingBrandVO> list() {
        platformScopeGuard.requirePlatform();
        return scopeMapper.selectPendingBrands();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkAsAlias(String pendingKey, Long brandId) {
        platformScopeGuard.requirePlatform();
        if (brandId == null) {
            throw new BizException("请选择要归到的品牌");
        }
        PendingBrandVO pending = requirePending(pendingKey);
        brandService.addAlias(brandId, pending.getName());
        relink(pending.getPendingKey(), brandId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBrand(String pendingKey, SaveBrandRequest req) {
        platformScopeGuard.requirePlatform();
        PendingBrandVO pending = requirePending(pendingKey);
        SaveBrandRequest brandReq = req != null ? req : new SaveBrandRequest();
        if (!StringUtils.hasText(brandReq.getBrandName())) {
            brandReq.setBrandName(pending.getName());
        }
        BrandVO brand = brandService.create(brandReq);
        if (!BrandResolver.key(brand.getBrandName()).equals(pending.getPendingKey())) {
            // 新建时改了名字：原待确认名称作为别名保留，以后同样的写法能直接归到这个品牌
            brandService.addAlias(brand.getId(), pending.getName());
        }
        relink(pending.getPendingKey(), brand.getId());
        return brand.getId();
    }

    private PendingBrandVO requirePending(String pendingKey) {
        String key = BrandResolver.key(pendingKey);
        return scopeMapper.selectPendingBrands().stream()
                .filter(p -> p.getPendingKey().equals(key))
                .findFirst()
                .orElseThrow(() -> new BizException("待确认品牌不存在或已被处理"));
    }

    /** 关联到正式品牌后，同一供应商下的同品牌行合并：任一行为全部品类则只留一行全部品类，否则按品类去重 */
    private void relink(String pendingKey, Long brandId) {
        List<Long> supplierIds = scopeMapper.selectSupplierIdsByPendingKey(pendingKey);
        scopeMapper.linkPendingToBrand(pendingKey, brandId, SecurityUtils.getCurrentUsername());
        for (Long supplierId : supplierIds) {
            mergeBrand(supplierId, brandId);
        }
    }

    private void mergeBrand(Long supplierId, Long brandId) {
        List<SupplierProductScopeDO> rows = scopeMapper.selectList(new LambdaQueryWrapper<SupplierProductScopeDO>()
                .eq(SupplierProductScopeDO::getSupplierId, supplierId)
                .eq(SupplierProductScopeDO::getBrandId, brandId)
                .isNull(SupplierProductScopeDO::getDeletedAt)
                .orderByAsc(SupplierProductScopeDO::getId));
        if (rows.size() <= 1) {
            return;
        }
        boolean allCategories = rows.stream().anyMatch(r -> r.getCategoryId() == null);
        List<Long> toDelete = new ArrayList<>(rows.size());
        Set<Long> keptCategories = new HashSet<>();
        boolean keptAll = false;
        for (SupplierProductScopeDO r : rows) {
            boolean keep = allCategories
                    ? r.getCategoryId() == null && !keptAll
                    : keptCategories.add(r.getCategoryId());
            if (keep && r.getCategoryId() == null) {
                keptAll = true;
            }
            if (!keep) {
                toDelete.add(r.getId());
            }
        }
        if (!toDelete.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            scopeMapper.update(null, new LambdaUpdateWrapper<SupplierProductScopeDO>()
                    .set(SupplierProductScopeDO::getDeletedAt, now)
                    .set(SupplierProductScopeDO::getUpdateTime, now)
                    .set(SupplierProductScopeDO::getUpdateBy, SecurityUtils.getCurrentUsername())
                    .in(SupplierProductScopeDO::getId, toDelete));
        }
    }
}

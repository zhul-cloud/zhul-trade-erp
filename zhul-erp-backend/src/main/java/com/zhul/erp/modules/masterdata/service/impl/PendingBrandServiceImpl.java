package com.zhul.erp.modules.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.modules.masterdata.dto.PendingBrandVO;
import com.zhul.erp.modules.masterdata.entity.BrandTextIgnoredDO;
import com.zhul.erp.modules.masterdata.entity.SupplierProductScopeDO;
import com.zhul.erp.modules.masterdata.repository.BrandTextIgnoredMapper;
import com.zhul.erp.modules.masterdata.repository.SupplierProductScopeMapper;
import com.zhul.erp.modules.masterdata.service.PendingBrandService;
import com.zhul.erp.modules.masterdata.support.BrandDocTexts;
import com.zhul.erp.modules.masterdata.support.BrandSuggest;
import com.zhul.erp.modules.product.dto.BrandVO;
import com.zhul.erp.modules.product.dto.SaveBrandRequest;
import com.zhul.erp.modules.product.entity.ProductBrandAliasDO;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.repository.ProductBrandAliasMapper;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.service.BrandService;
import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 待确认品牌。汇总与关联都是有意的跨租户操作（平台账号统一整理品牌词表），只暴露名称和计数。
 * 除供应商主营产品里的待确认名称外，还汇总询盘、销售订单、采购订单里认不出的品牌写法；
 * 设为别名或新建品牌后，单据里这个写法统一成正式品牌名（openspec brand-unify）。
 */
@Service
@RequiredArgsConstructor
public class PendingBrandServiceImpl implements PendingBrandService {

    private static final int PLATFORM_TENANT_ID = 0;
    private static final int TEXT_MAX = 128;

    private final SupplierProductScopeMapper scopeMapper;
    private final BrandService brandService;
    private final PlatformScopeGuard platformScopeGuard;
    private final ProductBrandMapper brandMapper;
    private final ProductBrandAliasMapper aliasMapper;
    private final BrandTextIgnoredMapper ignoredMapper;
    private final BrandDocTexts docTexts;

    @Override
    public List<PendingBrandVO> list() {
        platformScopeGuard.requirePlatform();
        return new ArrayList<>(pending().values());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkAsAlias(String pendingKey, Long brandId) {
        linkAsAlias(List.of(pendingKey), brandId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int linkAsAlias(List<String> pendingKeys, Long brandId) {
        platformScopeGuard.requirePlatform();
        if (brandId == null) {
            throw new BizException("请选择要归到的品牌");
        }
        ProductBrandDO brand = brandMapper.selectById(brandId);
        if (brand == null || brand.getDeletedAt() != null) {
            throw new BizException("品牌不存在");
        }
        List<PendingBrandVO> picked = require(pending(), pendingKeys);
        for (PendingBrandVO p : picked) {
            brandService.addAlias(brandId, p.getName());
            relink(p.getPendingKey(), brandId);
        }
        docTexts.normalize(picked.stream().map(PendingBrandVO::getPendingKey).toList(), brand.getBrandName());
        return picked.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBrand(String pendingKey, SaveBrandRequest req) {
        platformScopeGuard.requirePlatform();
        PendingBrandVO pending = require(pending(), List.of(pendingKey)).get(0);
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
        docTexts.normalize(List.of(pending.getPendingKey()), brand.getBrandName());
        return brand.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int ignore(List<String> pendingKeys) {
        platformScopeGuard.requirePlatform();
        List<PendingBrandVO> picked = require(pending(), pendingKeys);
        for (PendingBrandVO p : picked) {
            BrandTextIgnoredDO x = new BrandTextIgnoredDO();
            x.setTenantId(PLATFORM_TENANT_ID);
            x.setTextKey(cut(p.getPendingKey()));
            x.setText(cut(p.getName()));
            ignoredMapper.insert(x);
        }
        return picked.size();
    }

    // ---------------------------------------------------------------- 汇总

    /**
     * 比较键 → 待确认品牌：供应商主营产品里的待确认名称，加上单据里品牌库认不出的写法；
     * 标为不是品牌的不列出。按出现次数倒序。
     */
    private Map<String, PendingBrandVO> pending() {
        Map<String, BrandSuggest.Entry> byKey = new HashMap<>();
        List<BrandSuggest.Entry> entries = brandEntries(byKey);
        Set<String> ignored = new HashSet<>();
        ignoredMapper.selectList(new LambdaQueryWrapper<BrandTextIgnoredDO>()
                        .eq(BrandTextIgnoredDO::getTenantId, PLATFORM_TENANT_ID))
                .forEach(x -> ignored.add(x.getTextKey()));

        Map<String, PendingBrandVO> out = new HashMap<>();
        for (PendingBrandVO p : scopeMapper.selectPendingBrands()) {
            if (!ignored.contains(p.getPendingKey())) {
                p.setInquiryCount(0);
                p.setOrderCount(0);
                p.setPurchaseCount(0);
                out.put(p.getPendingKey(), p);
            }
        }
        for (BrandDocTexts.Usage u : docTexts.usages().values()) {
            if (byKey.containsKey(u.key()) || ignored.contains(u.key())) {
                continue;
            }
            PendingBrandVO p = out.computeIfAbsent(u.key(), k -> {
                PendingBrandVO x = new PendingBrandVO();
                x.setPendingKey(k);
                x.setName(u.name());
                x.setSupplierCount(0);
                return x;
            });
            p.setInquiryCount(u.inquiry());
            p.setOrderCount(u.order());
            p.setPurchaseCount(u.purchase());
            if (u.firstSeen() != null && (p.getFirstSeen() == null || u.firstSeen().isBefore(p.getFirstSeen()))) {
                p.setFirstSeen(u.firstSeen());
            }
        }
        for (PendingBrandVO p : out.values()) {
            BrandSuggest.Suggestion s = BrandSuggest.of(p.getName(), byKey, entries);
            if (s != null) {
                p.setSuggestKind(s.kind());
                p.setSuggestText(s.text());
                p.setSuggestBrandId(s.brandId());
                p.setSuggestBrandName(s.brandName());
            }
        }
        Map<String, PendingBrandVO> sorted = new LinkedHashMap<>();
        out.values().stream()
                .sorted(Comparator.comparingInt(PendingBrandServiceImpl::uses).reversed()
                        .thenComparing(PendingBrandVO::getFirstSeen, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(p -> sorted.put(p.getPendingKey(), p));
        return sorted;
    }

    /** 品牌库里的全部写法（正式名与别名）；byKey 按比较键索引 */
    private List<BrandSuggest.Entry> brandEntries(Map<String, BrandSuggest.Entry> byKey) {
        Map<Long, ProductBrandDO> brands = new HashMap<>();
        brandMapper.selectList(new LambdaQueryWrapper<ProductBrandDO>()
                        .eq(ProductBrandDO::getTenantId, PLATFORM_TENANT_ID)
                        .isNull(ProductBrandDO::getDeletedAt))
                .forEach(b -> brands.put(b.getId(), b));
        List<BrandSuggest.Entry> entries = new ArrayList<>(brands.size() * 3);
        brands.values().forEach(b -> entries.add(new BrandSuggest.Entry(b.getId(), b.getBrandName(), b.getBrandName(), false)));
        aliasMapper.selectList(new LambdaQueryWrapper<ProductBrandAliasDO>()
                        .eq(ProductBrandAliasDO::getTenantId, PLATFORM_TENANT_ID))
                .forEach(a -> {
                    ProductBrandDO b = brands.get(a.getBrandId());
                    if (b != null) {
                        entries.add(new BrandSuggest.Entry(b.getId(), b.getBrandName(), a.getAlias(), true));
                    }
                });
        entries.forEach(e -> byKey.putIfAbsent(BrandResolver.key(e.text()), e));
        return entries;
    }

    private static int uses(PendingBrandVO p) {
        return p.getSupplierCount() + p.getInquiryCount() + p.getOrderCount() + p.getPurchaseCount();
    }

    private static List<PendingBrandVO> require(Map<String, PendingBrandVO> all, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new BizException("请选择待确认品牌");
        }
        List<PendingBrandVO> out = new ArrayList<>(keys.size());
        for (String k : keys) {
            PendingBrandVO p = all.get(BrandResolver.key(k));
            if (p == null) {
                throw new BizException("待确认品牌不存在或已被处理，请刷新后再试");
            }
            if (!out.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    private static String cut(String s) {
        return s.length() > TEXT_MAX ? s.substring(0, TEXT_MAX) : s;
    }

    // ---------------------------------------------------------------- 供应商主营产品

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

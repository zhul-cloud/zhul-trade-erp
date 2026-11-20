package com.zhul.erp.modules.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.dto.ProductMatchVO;
import com.zhul.erp.modules.product.dto.ProductOptionVO;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.service.ProductLookupService;
import com.zhul.erp.modules.product.support.LikeUtils;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.product.support.ProductOrderCodes;
import com.zhul.erp.modules.product.support.IndustryScope;
import com.zhul.erp.modules.product.support.ProductNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductLookupServiceImpl implements ProductLookupService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_CANDIDATES = 10;

    private final ProductMapper productMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductNames productNames;
    private final IndustryScope industryScope;
    private final ProductOrderCodes orderCodes;
    private final com.zhul.erp.modules.product.support.PlatformScopeGuard platformScopeGuard;

    @Override
    public List<ProductOptionVO> search(String keyword, Integer limit, Long brandId) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String trimmed = keyword.trim();
        String normalized = MpnNormalizer.normalize(trimmed);
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        LambdaQueryWrapper<ProductDO> wrapper = selectable()
                .eq(brandId != null, ProductDO::getBrandId, brandId)
                // 已建档的排在候选前面（只返回这两种，已建档的值更大）
                .orderByDesc(ProductDO::getArchiveStatus)
                .orderByAsc(ProductDO::getMpnNormalized)
                .orderByAsc(ProductDO::getId)
                .last("LIMIT " + size);
        List<Long> byCode = orderCodes.productsMatching(trimmed, MAX_LIMIT);
        wrapper.and(w -> {
            // 归一化后为空（如 "---"）时跳过型号分支，否则 LIKE '%' 会命中全部商品
            if (!normalized.isEmpty()) {
                w.likeRight(ProductDO::getMpnNormalized, normalized).or();
            }
            w.like(ProductDO::getProductName, LikeUtils.escape(trimmed));
            // 也能按订货号（物料号、料号）找
            if (!byCode.isEmpty()) {
                w.or().in(ProductDO::getId, byCode);
            }
        });
        return toOptions(productMapper.selectList(wrapper));
    }

    @Override
    public ProductMatchVO match(String brand, String mpn) {
        ProductMatchVO result = new ProductMatchVO();
        result.setCandidates(List.of());
        String normalized = MpnNormalizer.normalize(mpn);
        if (normalized.isEmpty()) {
            return result;
        }

        ProductDO exact = null;
        if (StringUtils.hasText(brand)) {
            // 品牌按唯一性规则比较（数据库排序规则忽略大小写，首尾空格在这里去掉）
            ProductBrandDO matchedBrand = brandMapper.selectOne(new LambdaQueryWrapper<ProductBrandDO>()
                    .eq(ProductBrandDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                    .isNull(ProductBrandDO::getDeletedAt)
                    .eq(ProductBrandDO::getBrandName, brand.trim())
                    .last("LIMIT 1"));
            if (matchedBrand != null) {
                // 候选商品对所有公司都参与精确匹配（同一型号只有一个商品 ID），已建档的优先
                exact = productMapper.selectOne(selectable(true)
                        .eq(ProductDO::getBrandId, matchedBrand.getId())
                        .eq(ProductDO::getMpnNormalized, normalized)
                        .orderByDesc(ProductDO::getArchiveStatus)
                        .last("LIMIT 1"));
            }
        }

        final Long exactId = exact == null ? null : exact.getId();
        // 归一化型号相同的排在前缀相同的之前：相同的字符串一定比以它为前缀的更长的字符串小
        List<ProductDO> candidates = productMapper.selectList(selectable()
                .likeRight(ProductDO::getMpnNormalized, normalized)
                .ne(exactId != null, ProductDO::getId, exactId)
                .orderByAsc(ProductDO::getMpnNormalized)
                .orderByAsc(ProductDO::getId)
                .last("LIMIT " + MAX_CANDIDATES));

        List<ProductDO> all = new ArrayList<>(candidates);
        if (exact != null) {
            all.add(exact);
        }
        List<ProductOptionVO> options = toOptions(all);
        if (exact != null && isCandidate(exact) && !candidateVisible(exact.getId())) {
            // 别家公司询到的候选：只给商品 ID 与型号，不带由别家询盘带来的名称、品类
            ProductOptionVO bare = options.get(options.size() - 1);
            bare.setProductName(null);
            bare.setCategoryName(null);
        }
        if (exact != null) {
            result.setExact(options.get(options.size() - 1));
            options = options.subList(0, options.size() - 1);
        }
        result.setCandidates(new ArrayList<>(options));
        return result;
    }

    @Override
    public ProductOptionVO selectable(Long id) {
        if (id == null) {
            return null;
        }
        ProductDO product = productMapper.selectOne(selectable().eq(ProductDO::getId, id));
        return product == null ? null : toOptions(List.of(product)).get(0);
    }

    @Override
    public java.util.Map<String, Long> matchAll(java.util.Collection<String[]> brandModels) {
        java.util.Map<String, String> brandsByKey = new java.util.HashMap<>();
        java.util.Set<String> mpns = new java.util.HashSet<>();
        for (String[] bm : brandModels) {
            if (StringUtils.hasText(bm[0]) && StringUtils.hasText(bm[1])) {
                brandsByKey.put(bm[0].trim().toLowerCase(java.util.Locale.ROOT), bm[0].trim());
                String n = MpnNormalizer.normalize(bm[1]);
                if (!n.isEmpty()) {
                    mpns.add(n);
                }
            }
        }
        if (brandsByKey.isEmpty() || mpns.isEmpty()) {
            return java.util.Map.of();
        }
        // 品牌名比较忽略大小写（数据库排序规则），结果按小写归并
        List<ProductBrandDO> brands = brandMapper.selectList(new LambdaQueryWrapper<ProductBrandDO>()
                .select(ProductBrandDO::getId, ProductBrandDO::getBrandName)
                .eq(ProductBrandDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductBrandDO::getDeletedAt)
                .in(ProductBrandDO::getBrandName, brandsByKey.values()));
        if (brands.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<Long, String> brandKeys = new java.util.HashMap<>();
        brands.forEach(b -> brandKeys.put(b.getId(), b.getBrandName().trim().toLowerCase(java.util.Locale.ROOT)));
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                        .select(ProductDO::getId, ProductDO::getBrandId, ProductDO::getMpnNormalized)
                        .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .isNull(ProductDO::getDeletedAt)
                        .in(ProductDO::getArchiveStatus, ProductConstants.ARCHIVE_DONE, ProductConstants.ARCHIVE_CANDIDATE)
                        .in(ProductDO::getBrandId, brandKeys.keySet())
                        .in(ProductDO::getMpnNormalized, mpns)
                        .orderByDesc(ProductDO::getArchiveStatus)
                        .orderByAsc(ProductDO::getId))
                .forEach(p -> result.putIfAbsent(brandKeys.get(p.getBrandId()) + "|" + p.getMpnNormalized(), p.getId()));
        return result;
    }

    /**
     * 只有启用且未删除的商品才能被业务单据选用：已建档的（租户只在本行业的商品里查找，按所属品类的一级品类），
     * 以及候选商品（spec product/product-lookup「选择器搜索」：只给在商品候选里有本公司来源的租户）。
     */
    private LambdaQueryWrapper<ProductDO> selectable() {
        return selectable(false);
    }

    /** anyCandidate 为 true 时候选商品不按来源公司过滤（只用于精确匹配） */
    private LambdaQueryWrapper<ProductDO> selectable(boolean anyCandidate) {
        LambdaQueryWrapper<ProductDO> wrapper = new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductDO::getDeletedAt)
                .eq(ProductDO::getStatus, ProductConstants.STATUS_ENABLED);
        java.util.Set<Long> visibleCategories = industryScope.visibleCategoryIds();
        boolean platform = platformScopeGuard.isPlatform();
        Integer tenant = com.zhul.erp.framework.tenant.TenantContext.getTenantId();
        wrapper.and(w -> {
            w.and(a -> {
                a.eq(ProductDO::getArchiveStatus, ProductConstants.ARCHIVE_DONE);
                if (visibleCategories != null) {
                    if (visibleCategories.isEmpty()) {
                        a.apply("1 = 0");
                    } else {
                        a.in(ProductDO::getCategoryId, visibleCategories);
                    }
                }
            });
            w.or(c -> {
                c.eq(ProductDO::getArchiveStatus, ProductConstants.ARCHIVE_CANDIDATE);
                if (!anyCandidate && !platform) {
                    c.apply(CANDIDATE_OF_TENANT, tenant == null ? -1 : tenant);
                }
            });
        });
        return wrapper;
    }

    /** 候选商品在商品候选里有这家公司的来源 */
    private static final String CANDIDATE_OF_TENANT = "EXISTS (SELECT 1 FROM product_candidate pc JOIN product_candidate_source ps"
            + " ON ps.candidate_id = pc.id AND ps.deleted_at IS NULL WHERE pc.product_id = product.id AND ps.tenant_id = {0})";

    private static boolean isCandidate(ProductDO p) {
        return java.util.Objects.equals(p.getArchiveStatus(), ProductConstants.ARCHIVE_CANDIDATE);
    }

    private boolean candidateVisible(Long productId) {
        return productMapper.selectCount(selectable().eq(ProductDO::getId, productId)) > 0;
    }

    private List<ProductOptionVO> toOptions(List<ProductDO> products) {
        ProductNames.Lookup lookup = productNames.load(products);
        List<ProductOptionVO> options = new ArrayList<>(products.size());
        for (ProductDO product : products) {
            ProductOptionVO vo = new ProductOptionVO();
            vo.setId(product.getId());
            vo.setMpnDisplay(product.getMpnDisplay());
            ProductBrandDO brand = lookup.brands().get(product.getBrandId());
            vo.setBrandName(brand == null ? null : brand.getBrandName());
            ProductCategoryDO category = lookup.categories().get(product.getCategoryId());
            vo.setCategoryName(category == null ? null : category.getCategoryName());
            vo.setProductName(product.getProductName());
            vo.setCandidate(isCandidate(product));
            options.add(vo);
        }
        return options;
    }
}

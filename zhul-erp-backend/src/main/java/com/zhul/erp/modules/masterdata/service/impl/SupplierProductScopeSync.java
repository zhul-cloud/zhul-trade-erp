package com.zhul.erp.modules.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.modules.masterdata.dto.SupplierProductScopeRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierProductScopeVO;
import com.zhul.erp.modules.masterdata.entity.SupplierProductScopeDO;
import com.zhul.erp.modules.masterdata.repository.SupplierProductScopeMapper;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.support.BrandResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商主营产品的归一、校验、整体替换、加载与筛选。整体替换：软删除旧行、插入新行（行本身没有需要保留的身份）。
 * 调用方负责事务。
 */
@Component
@RequiredArgsConstructor
public class SupplierProductScopeSync {

    public static final int MAX_BRANDS = 50;

    private final SupplierProductScopeMapper scopeMapper;
    private final BrandResolver brandResolver;
    private final ProductBrandMapper brandMapper;
    private final ProductCategoryMapper categoryMapper;

    /** 归一后的一个主营品牌 */
    public record Scope(Long brandId, String pendingName, Set<Long> categoryIds) {
    }

    /** 归一品牌、校验细分品类与重复品牌；返回按请求顺序的结果 */
    public List<Scope> normalize(List<SupplierProductScopeRequest> requests) {
        if (requests.size() > MAX_BRANDS) {
            throw new BizException("主营品牌最多 " + MAX_BRANDS + " 个");
        }
        Map<String, Scope> byKey = new LinkedHashMap<>();
        Set<Long> allCategoryIds = new HashSet<>();
        for (SupplierProductScopeRequest req : requests) {
            Long brandId = req.getBrandId();
            String name = req.getBrandName() == null ? "" : req.getBrandName().trim();
            if (brandId == null && name.isEmpty()) {
                throw new BizException("请选择或输入主营品牌");
            }
            if (brandId != null) {
                ProductBrandDO brand = brandMapper.selectById(brandId);
                if (brand == null || brand.getDeletedAt() != null) {
                    throw new BizException("品牌不存在，请重新选择");
                }
                name = brand.getBrandName();
            } else {
                brandId = brandResolver.resolve(name);
            }
            String key = brandId != null ? "b:" + brandId : "p:" + BrandResolver.key(name);
            if (byKey.containsKey(key)) {
                throw new BizException("主营品牌重复：" + name);
            }
            Set<Long> categoryIds = req.getCategoryIds() == null ? Set.of() : new LinkedHashSet<>(req.getCategoryIds());
            allCategoryIds.addAll(categoryIds);
            byKey.put(key, new Scope(brandId, brandId == null ? name : "", categoryIds));
        }
        if (!allCategoryIds.isEmpty()) {
            List<ProductCategoryDO> categories = categoryMapper.selectBatchIds(allCategoryIds);
            Set<Long> subIds = categories.stream()
                    .filter(c -> c.getDeletedAt() == null && c.getParentId() != null)
                    .map(ProductCategoryDO::getId).collect(Collectors.toSet());
            if (!subIds.containsAll(allCategoryIds)) {
                throw new BizException("主营品类只能选择细分品类");
            }
        }
        return new ArrayList<>(byKey.values());
    }

    /**
     * 把存量自由文本主营品牌（main_brands）转成主营产品：按逗号、中文逗号、顿号、斜杠、分号拆分，
     * 逐个按名称或别名归一，重复的只保留第一个，最多 50 个，均为全部品类。
     */
    public List<Scope> fromLegacy(String mainBrands) {
        Map<String, Scope> byKey = new LinkedHashMap<>();
        for (String part : mainBrands.split("[,，、/;；]")) {
            String name = part.trim();
            if (name.isEmpty() || name.length() > 64) {
                continue;
            }
            Long brandId = brandResolver.resolve(name);
            String key = brandId != null ? "b:" + brandId : "p:" + BrandResolver.key(name);
            byKey.putIfAbsent(key, new Scope(brandId, brandId == null ? name : "", Set.of()));
            if (byKey.size() >= MAX_BRANDS) {
                break;
            }
        }
        return new ArrayList<>(byKey.values());
    }

    /** 整体替换某供应商的主营产品 */
    public void replace(Integer tenantId, Long supplierId, List<Scope> scopes) {
        LocalDateTime now = LocalDateTime.now();
        scopeMapper.update(null, new LambdaUpdateWrapper<SupplierProductScopeDO>()
                .set(SupplierProductScopeDO::getDeletedAt, now)
                .set(SupplierProductScopeDO::getUpdateTime, now)
                .set(SupplierProductScopeDO::getUpdateBy, SecurityUtils.getCurrentUsername())
                .eq(SupplierProductScopeDO::getSupplierId, supplierId)
                .isNull(SupplierProductScopeDO::getDeletedAt));
        for (Scope s : scopes) {
            if (s.categoryIds().isEmpty()) {
                scopeMapper.insert(row(tenantId, supplierId, s, null));
            } else {
                for (Long categoryId : s.categoryIds()) {
                    scopeMapper.insert(row(tenantId, supplierId, s, categoryId));
                }
            }
        }
    }

    private static SupplierProductScopeDO row(Integer tenantId, Long supplierId, Scope s, Long categoryId) {
        SupplierProductScopeDO r = new SupplierProductScopeDO();
        r.setTenantId(tenantId);
        r.setSupplierId(supplierId);
        r.setBrandId(s.brandId());
        r.setPendingBrandName(s.brandId() == null ? s.pendingName() : "");
        r.setPendingKey(s.brandId() == null ? BrandResolver.key(s.pendingName()) : "");
        r.setCategoryId(categoryId);
        return r;
    }

    /** 批量加载供应商的主营产品：按品牌分组，保持录入顺序 */
    public Map<Long, List<SupplierProductScopeVO>> load(Collection<Long> supplierIds) {
        Map<Long, List<SupplierProductScopeVO>> result = new HashMap<>(supplierIds.size() * 2);
        if (supplierIds.isEmpty()) {
            return result;
        }
        List<SupplierProductScopeDO> rows = scopeMapper.selectList(new LambdaQueryWrapper<SupplierProductScopeDO>()
                .in(SupplierProductScopeDO::getSupplierId, supplierIds)
                .isNull(SupplierProductScopeDO::getDeletedAt)
                .orderByAsc(SupplierProductScopeDO::getId));
        if (rows.isEmpty()) {
            return result;
        }
        Set<Long> brandIds = rows.stream().map(SupplierProductScopeDO::getBrandId).filter(id -> id != null)
                .collect(Collectors.toSet());
        Set<Long> categoryIds = rows.stream().map(SupplierProductScopeDO::getCategoryId).filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> brandNames = brandIds.isEmpty() ? Map.of() : brandMapper.selectBatchIds(brandIds).stream()
                .collect(Collectors.toMap(ProductBrandDO::getId, ProductBrandDO::getBrandName));
        Map<Long, String> categoryNames = categoryIds.isEmpty() ? Map.of() : categoryMapper.selectBatchIds(categoryIds)
                .stream().collect(Collectors.toMap(ProductCategoryDO::getId,
                        c -> StringUtils.hasText(c.getCategoryNameZh()) ? c.getCategoryNameZh() : c.getCategoryName()));

        Map<Long, Map<String, SupplierProductScopeVO>> grouped = new LinkedHashMap<>();
        for (SupplierProductScopeDO r : rows) {
            String key = r.getBrandId() != null ? "b:" + r.getBrandId() : "p:" + r.getPendingKey();
            SupplierProductScopeVO vo = grouped.computeIfAbsent(r.getSupplierId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(key, k -> {
                        SupplierProductScopeVO v = new SupplierProductScopeVO();
                        v.setBrandId(r.getBrandId());
                        v.setBrandName(r.getBrandId() != null ? brandNames.getOrDefault(r.getBrandId(), "")
                                : r.getPendingBrandName());
                        v.setPending(r.getBrandId() == null);
                        v.setCategories(new ArrayList<>());
                        return v;
                    });
            if (r.getCategoryId() != null) {
                SupplierProductScopeVO.CategoryRef ref = new SupplierProductScopeVO.CategoryRef();
                ref.setId(r.getCategoryId());
                ref.setName(categoryNames.getOrDefault(r.getCategoryId(), ""));
                vo.getCategories().add(ref);
            }
        }
        grouped.forEach((supplierId, byBrand) -> result.put(supplierId, new ArrayList<>(byBrand.values())));
        return result;
    }

    /**
     * 按主营品牌 / 细分品类筛选出的供应商 ID（本租户）。有品牌时，「该品牌全部品类」也算命中该品牌下的任意细分品类；
     * 只按品类筛选时只命中明确选了该品类的供应商（全部品类不对应具体品类，算进去会让筛选失去意义）。
     */
    public Set<Long> supplierIdsMatching(Integer tenantId, Long brandId, Long categoryId) {
        LambdaQueryWrapper<SupplierProductScopeDO> w = new LambdaQueryWrapper<SupplierProductScopeDO>()
                .select(SupplierProductScopeDO::getSupplierId)
                .eq(SupplierProductScopeDO::getTenantId, tenantId)
                .isNull(SupplierProductScopeDO::getDeletedAt)
                .eq(brandId != null, SupplierProductScopeDO::getBrandId, brandId);
        if (categoryId != null) {
            if (brandId != null) {
                w.and(x -> x.eq(SupplierProductScopeDO::getCategoryId, categoryId)
                        .or().isNull(SupplierProductScopeDO::getCategoryId));
            } else {
                w.eq(SupplierProductScopeDO::getCategoryId, categoryId);
            }
        }
        return scopeMapper.selectList(w).stream().map(SupplierProductScopeDO::getSupplierId).collect(Collectors.toSet());
    }

    /** 导出用的文本：Siemens(PLC/HMI); ABB(全部品类); Hengstler[待确认](全部品类) */
    public static String toText(List<SupplierProductScopeVO> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "";
        }
        return scopes.stream().map(s -> s.getBrandName() + (s.isPending() ? "[待确认]" : "") + "("
                + (s.getCategories().isEmpty() ? "全部品类"
                : s.getCategories().stream().map(SupplierProductScopeVO.CategoryRef::getName)
                .collect(Collectors.joining("/"))) + ")")
                .collect(Collectors.joining("; "));
    }
}

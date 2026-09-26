package com.zhul.erp.modules.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.BrandOptionVO;
import com.zhul.erp.modules.product.dto.BrandQuery;
import com.zhul.erp.modules.product.dto.BrandVO;
import com.zhul.erp.modules.product.dto.SaveBrandRequest;
import com.zhul.erp.modules.product.entity.ProductBrandAliasDO;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.IdCount;
import com.zhul.erp.modules.product.repository.ProductBrandAliasMapper;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.service.BrandService;
import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.product.support.CountryCatalog;
import com.zhul.erp.modules.product.support.LikeUtils;
import com.zhul.erp.modules.product.support.OptionsCache;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.TextRules;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private static final int NAME_MAX = 64;
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final ProductBrandMapper brandMapper;
    private final ProductMapper productMapper;
    private final PlatformScopeGuard platformScopeGuard;
    private final OptionsCache optionsCache;
    private final CountryCatalog countryCatalog;
    private final ProductBrandAliasMapper aliasMapper;

    @Override
    public PageResult<BrandVO> page(BrandQuery query) {
        LambdaQueryWrapper<ProductBrandDO> wrapper = new LambdaQueryWrapper<ProductBrandDO>()
                .eq(ProductBrandDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductBrandDO::getDeletedAt)
                .eq(query.getStatus() != null, ProductBrandDO::getStatus, query.getStatus())
                .orderByAsc(ProductBrandDO::getBrandName)
                .orderByAsc(ProductBrandDO::getId);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = LikeUtils.escape(query.getKeyword().trim());
            List<Long> aliasHits = aliasMapper.selectList(new LambdaQueryWrapper<ProductBrandAliasDO>()
                    .eq(ProductBrandAliasDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                    .like(ProductBrandAliasDO::getAliasKey, keyword.toLowerCase(Locale.ROOT)))
                    .stream().map(ProductBrandAliasDO::getBrandId).distinct().toList();
            wrapper.and(w -> w.like(ProductBrandDO::getBrandName, keyword)
                    .or(!aliasHits.isEmpty(), x -> x.in(ProductBrandDO::getId, aliasHits)));
        }
        Page<ProductBrandDO> page = brandMapper.selectPage(
                new Page<>(query.pageOrDefault(), query.pageSizeOrDefault()), wrapper);

        Map<Long, Long> counts = toCountMap(page.getRecords().isEmpty() ? List.of()
                : productMapper.countByBrandIds(page.getRecords().stream().map(ProductBrandDO::getId).toList()));
        Map<Long, List<String>> aliases = aliasesOf(page.getRecords().stream().map(ProductBrandDO::getId).toList());
        List<BrandVO> records = new ArrayList<>(page.getRecords().size());
        for (ProductBrandDO brand : page.getRecords()) {
            BrandVO vo = toVO(brand, counts.getOrDefault(brand.getId(), 0L));
            vo.setAliases(aliases.getOrDefault(brand.getId(), List.of()));
            records.add(vo);
        }
        return PageResult.of(page.getTotal(), records);
    }

    @Override
    public List<BrandOptionVO> options() {
        List<BrandOptionVO> cached = optionsCache.get(ProductConstants.CACHE_KEY_BRAND_OPTIONS, BrandOptionVO.class);
        if (cached != null) {
            return cached;
        }
        List<ProductBrandDO> brands = brandMapper.selectList(new LambdaQueryWrapper<ProductBrandDO>()
                .eq(ProductBrandDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductBrandDO::getDeletedAt)
                .eq(ProductBrandDO::getStatus, ProductConstants.STATUS_ENABLED)
                .orderByAsc(ProductBrandDO::getBrandName)
                .orderByAsc(ProductBrandDO::getId));
        Map<Long, List<String>> aliases = aliasesOf(brands.stream().map(ProductBrandDO::getId).toList());
        List<BrandOptionVO> options = new ArrayList<>(brands.size());
        for (ProductBrandDO brand : brands) {
            BrandOptionVO vo = new BrandOptionVO();
            vo.setId(brand.getId());
            vo.setBrandName(brand.getBrandName());
            vo.setLogoUrl(brand.getLogoUrl());
            vo.setDescription(brand.getDescription());
            vo.setIsGenuine(brand.getIsGenuine());
            vo.setAliases(aliases.getOrDefault(brand.getId(), List.of()));
            options.add(vo);
        }
        optionsCache.put(ProductConstants.CACHE_KEY_BRAND_OPTIONS, options);
        return options;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BrandVO create(SaveBrandRequest req) {
        platformScopeGuard.requirePlatform();
        String name = TextRules.required(req.getBrandName(), "品牌名称", NAME_MAX);
        assertNameFree(name, null);
        List<String> aliases = validAliases(req.getAliases(), name, null);

        ProductBrandDO brand = new ProductBrandDO();
        brand.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
        brand.setBrandName(name);
        brand.setCountry(validCountry(req.getCountry()));
        brand.setLogoUrl(TextRules.optional(req.getLogoUrl(), "Logo 地址", 256));
        brand.setBrandColor(validColor(req.getBrandColor()));
        brand.setDescription(TextRules.optional(req.getDescription(), "品牌简介", ProductConstants.DESCRIPTION_MAX));
        brand.setIsGenuine(genuine(req.getIsGenuine(), 1));
        brand.setStatus(ProductConstants.STATUS_ENABLED);
        try {
            brandMapper.insert(brand);
        } catch (DuplicateKeyException e) {
            throw duplicate(null, false, e);
        }
        if (aliases != null) {
            replaceAliases(brand.getId(), aliases);
        }
        optionsCache.evictAfterCommit(ProductConstants.CACHE_KEY_BRAND_OPTIONS);
        return withAliases(toVO(brand, 0L));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BrandVO update(Long id, SaveBrandRequest req) {
        platformScopeGuard.requirePlatform();
        ProductBrandDO current = getActive(id);
        String name = TextRules.required(req.getBrandName(), "品牌名称", NAME_MAX);
        assertNameFree(name, id);
        List<String> aliases = validAliases(req.getAliases(), name, id);

        // 用只带变更字段的新对象更新，才能触发 update_time / update_by 的自动填充
        ProductBrandDO change = new ProductBrandDO();
        change.setId(id);
        change.setBrandName(name);
        change.setCountry(validCountry(req.getCountry()));
        change.setLogoUrl(TextRules.optional(req.getLogoUrl(), "Logo 地址", 256));
        change.setBrandColor(validColor(req.getBrandColor()));
        change.setDescription(TextRules.optional(req.getDescription(), "品牌简介", ProductConstants.DESCRIPTION_MAX));
        change.setIsGenuine(genuine(req.getIsGenuine(), current.getIsGenuine()));
        try {
            brandMapper.updateById(change);
        } catch (DuplicateKeyException e) {
            throw duplicate(null, false, e);
        }
        if (aliases != null) {
            replaceAliases(id, aliases);
        }
        optionsCache.evictAfterCommit(ProductConstants.CACHE_KEY_BRAND_OPTIONS);
        return withAliases(toVO(getActive(id), countProducts(id)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addAlias(Long brandId, String alias) {
        platformScopeGuard.requirePlatform();
        ProductBrandDO brand = getActive(brandId);
        List<String> valid = validAliases(List.of(alias), brand.getBrandName(), brandId);
        Set<String> existing = new HashSet<>(aliasesOf(List.of(brandId)).getOrDefault(brandId, List.of()).stream()
                .map(BrandResolver::key).toList());
        for (String a : valid) {
            if (existing.add(BrandResolver.key(a))) {
                ProductBrandAliasDO row = new ProductBrandAliasDO();
                row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
                row.setBrandId(brandId);
                row.setAlias(a);
                row.setAliasKey(BrandResolver.key(a));
                aliasMapper.insert(row);
            }
        }
        optionsCache.evictAfterCommit(ProductConstants.CACHE_KEY_BRAND_OPTIONS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        platformScopeGuard.requirePlatform();
        int value = TextRules.status(status);
        getActive(id);
        ProductBrandDO change = new ProductBrandDO();
        change.setId(id);
        change.setStatus(value);
        brandMapper.updateById(change);
        optionsCache.evictAfterCommit(ProductConstants.CACHE_KEY_BRAND_OPTIONS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        platformScopeGuard.requirePlatform();
        getActive(id);
        long usage = countProducts(id);
        if (usage > 0) {
            throw BizException.of(ProductErrorCodes.BRAND_IN_USE,
                    "已有 " + usage + " 个商品使用该品牌，请先停用", Map.of("usageCount", usage));
        }
        long supplierUsage = brandMapper.countSupplierScopes(id);
        if (supplierUsage > 0) {
            throw BizException.of(ProductErrorCodes.BRAND_IN_USE, "该品牌已被供应商主营产品使用，可改为停用",
                    Map.of("supplierUsageCount", supplierUsage));
        }
        ProductBrandDO change = new ProductBrandDO();
        change.setId(id);
        change.setDeletedAt(LocalDateTime.now());
        brandMapper.updateById(change);
        optionsCache.evictAfterCommit(ProductConstants.CACHE_KEY_BRAND_OPTIONS);
    }

    private ProductBrandDO getActive(Long id) {
        ProductBrandDO brand = id == null ? null : brandMapper.selectOne(new LambdaQueryWrapper<ProductBrandDO>()
                .eq(ProductBrandDO::getId, id)
                .eq(ProductBrandDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductBrandDO::getDeletedAt));
        if (brand == null) {
            throw BizException.of(ProductErrorCodes.BRAND_NOT_FOUND, "品牌不存在");
        }
        return brand;
    }

    /**
     * 别名规则：去首尾空格、按比较键去重、每个 ≤64、不能与自己的名称相同（相同的直接忽略），
     * 不能等于其他品牌的名称或别名。为 null 表示不修改。
     */
    private List<String> validAliases(List<String> raw, String ownName, Long selfId) {
        if (raw == null) {
            return null;
        }
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String a : raw) {
            if (!StringUtils.hasText(a)) {
                continue;
            }
            String alias = TextRules.required(a, "别名", NAME_MAX);
            String key = BrandResolver.key(alias);
            if (!key.equals(BrandResolver.key(ownName))) {
                byKey.putIfAbsent(key, alias);
            }
        }
        for (Map.Entry<String, String> e : byKey.entrySet()) {
            ProductBrandDO sameName = brandMapper.selectOne(new LambdaQueryWrapper<ProductBrandDO>()
                    .eq(ProductBrandDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                    .eq(ProductBrandDO::getBrandName, e.getValue())
                    .isNull(ProductBrandDO::getDeletedAt)
                    .ne(selfId != null, ProductBrandDO::getId, selfId)
                    .last("LIMIT 1"));
            if (sameName != null) {
                throw aliasConflict(e.getValue(), sameName.getBrandName(), "名称");
            }
            ProductBrandAliasDO taken = aliasMapper.selectOne(new LambdaQueryWrapper<ProductBrandAliasDO>()
                    .eq(ProductBrandAliasDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                    .eq(ProductBrandAliasDO::getAliasKey, e.getKey())
                    .ne(selfId != null, ProductBrandAliasDO::getBrandId, selfId)
                    .last("LIMIT 1"));
            if (taken != null) {
                ProductBrandDO owner = brandMapper.selectById(taken.getBrandId());
                throw aliasConflict(e.getValue(), owner == null ? "" : owner.getBrandName(), "别名");
            }
        }
        return List.copyOf(byKey.values());
    }

    private static BizException aliasConflict(String alias, String ownerName, String what) {
        return BizException.of(ProductErrorCodes.BRAND_ALIAS_CONFLICT,
                "「" + alias + "」已是品牌 " + ownerName + " 的" + what, Map.of("ownerName", ownerName));
    }

    /** 整体替换品牌的别名列表 */
    private void replaceAliases(Long brandId, List<String> aliases) {
        aliasMapper.delete(new LambdaQueryWrapper<ProductBrandAliasDO>().eq(ProductBrandAliasDO::getBrandId, brandId));
        for (String alias : aliases) {
            ProductBrandAliasDO row = new ProductBrandAliasDO();
            row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            row.setBrandId(brandId);
            row.setAlias(alias);
            row.setAliasKey(BrandResolver.key(alias));
            aliasMapper.insert(row);
        }
    }

    private Map<Long, List<String>> aliasesOf(Collection<Long> brandIds) {
        Map<Long, List<String>> map = new HashMap<>(brandIds.size() * 2);
        if (brandIds.isEmpty()) {
            return map;
        }
        for (ProductBrandAliasDO a : aliasMapper.selectList(new LambdaQueryWrapper<ProductBrandAliasDO>()
                .in(ProductBrandAliasDO::getBrandId, brandIds)
                .orderByAsc(ProductBrandAliasDO::getId))) {
            map.computeIfAbsent(a.getBrandId(), k -> new ArrayList<>()).add(a.getAlias());
        }
        return map;
    }

    private BrandVO withAliases(BrandVO vo) {
        vo.setAliases(vo.getId() == null ? List.of()
                : aliasesOf(List.of(vo.getId())).getOrDefault(vo.getId(), List.of()));
        return vo;
    }

    /** 名称唯一性检查包含已软删除的行（唯一键也包含它们）；excludeId 用于修改时排除自己 */
    private void assertNameFree(String name, Long excludeId) {
        ProductBrandAliasDO aliasOwner = aliasMapper.selectOne(new LambdaQueryWrapper<ProductBrandAliasDO>()
                .eq(ProductBrandAliasDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductBrandAliasDO::getAliasKey, BrandResolver.key(name))
                .ne(excludeId != null, ProductBrandAliasDO::getBrandId, excludeId)
                .last("LIMIT 1"));
        if (aliasOwner != null) {
            ProductBrandDO owner = brandMapper.selectById(aliasOwner.getBrandId());
            throw aliasConflict(name, owner == null ? "" : owner.getBrandName(), "别名");
        }
        ProductBrandDO existing = brandMapper.selectOne(new LambdaQueryWrapper<ProductBrandDO>()
                .eq(ProductBrandDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductBrandDO::getBrandName, name)
                .ne(excludeId != null, ProductBrandDO::getId, excludeId)
                .last("LIMIT 1"));
        if (existing != null) {
            throw duplicate(existing.getId(), existing.getDeletedAt() != null, null);
        }
    }

    private BizException duplicate(Long existingId, boolean deleted, Throwable cause) {
        Map<String, Object> detail = new HashMap<>(4);
        detail.put("existingId", existingId);
        detail.put("deleted", deleted);
        BizException e = BizException.of(ProductErrorCodes.BRAND_DUPLICATE,
                deleted ? "同名品牌曾被删除，不能重复创建" : "品牌已存在", detail);
        if (cause != null) {
            e.initCause(cause);
        }
        return e;
    }

    private long countProducts(Long brandId) {
        return productMapper.selectCount(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductDO::getBrandId, brandId)
                .isNull(ProductDO::getDeletedAt));
    }

    /** 主题色：可空；非空必须是 #RRGGBB，统一存为大写 */
    private static String validColor(String color) {
        String text = TextRules.optional(color, "品牌主题色", 16);
        if (text.isEmpty()) {
            return text;
        }
        if (!HEX_COLOR.matcher(text).matches()) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "主题色格式应为 #RRGGBB");
        }
        return text.toUpperCase(Locale.ROOT);
    }

    /** 原产地：可空；非空必须在国家清单内，存清单里的规范英文名 */
    private String validCountry(String country) {
        String text = TextRules.optional(country, "原产地", 64);
        if (text.isEmpty()) {
            return text;
        }
        String canonical = countryCatalog.canonicalName(text);
        if (canonical == null) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "原产地不在可选清单内");
        }
        return canonical;
    }

    private static int genuine(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value != 0 && value != 1) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "是否原厂正品只能是 0 或 1");
        }
        return value;
    }

    private static Map<Long, Long> toCountMap(Collection<IdCount> rows) {
        Map<Long, Long> map = new HashMap<>(rows.size() * 2);
        for (IdCount row : rows) {
            map.put(row.getId(), row.getCnt());
        }
        return map;
    }

    private static BrandVO toVO(ProductBrandDO brand, long productCount) {
        BrandVO vo = new BrandVO();
        vo.setId(brand.getId());
        vo.setBrandName(brand.getBrandName());
        vo.setCountry(brand.getCountry());
        vo.setLogoUrl(brand.getLogoUrl());
        vo.setBrandColor(brand.getBrandColor());
        vo.setDescription(brand.getDescription());
        vo.setIsGenuine(brand.getIsGenuine());
        vo.setStatus(brand.getStatus());
        vo.setProductCount(productCount);
        vo.setCreateBy(brand.getCreateBy());
        vo.setCreateTime(brand.getCreateTime());
        vo.setUpdateBy(brand.getUpdateBy());
        vo.setUpdateTime(brand.getUpdateTime());
        return vo;
    }
}

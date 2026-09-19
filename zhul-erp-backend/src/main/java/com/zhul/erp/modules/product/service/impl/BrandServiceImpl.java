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
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.IdCount;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.service.BrandService;
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
import java.util.List;
import java.util.Map;
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

    @Override
    public PageResult<BrandVO> page(BrandQuery query) {
        LambdaQueryWrapper<ProductBrandDO> wrapper = new LambdaQueryWrapper<ProductBrandDO>()
                .eq(ProductBrandDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductBrandDO::getDeletedAt)
                .eq(query.getStatus() != null, ProductBrandDO::getStatus, query.getStatus())
                .orderByAsc(ProductBrandDO::getBrandName)
                .orderByAsc(ProductBrandDO::getId);
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(ProductBrandDO::getBrandName, LikeUtils.escape(query.getKeyword().trim()));
        }
        Page<ProductBrandDO> page = brandMapper.selectPage(
                new Page<>(query.pageOrDefault(), query.pageSizeOrDefault()), wrapper);

        Map<Long, Long> counts = toCountMap(page.getRecords().isEmpty() ? List.of()
                : productMapper.countByBrandIds(page.getRecords().stream().map(ProductBrandDO::getId).toList()));
        List<BrandVO> records = new ArrayList<>(page.getRecords().size());
        for (ProductBrandDO brand : page.getRecords()) {
            records.add(toVO(brand, counts.getOrDefault(brand.getId(), 0L)));
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
        List<BrandOptionVO> options = new ArrayList<>(brands.size());
        for (ProductBrandDO brand : brands) {
            BrandOptionVO vo = new BrandOptionVO();
            vo.setId(brand.getId());
            vo.setBrandName(brand.getBrandName());
            vo.setLogoUrl(brand.getLogoUrl());
            vo.setIsGenuine(brand.getIsGenuine());
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

        ProductBrandDO brand = new ProductBrandDO();
        brand.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
        brand.setBrandName(name);
        brand.setCountry(TextRules.optional(req.getCountry(), "原产国", 64));
        brand.setLogoUrl(TextRules.optional(req.getLogoUrl(), "Logo 地址", 256));
        brand.setBrandColor(validColor(req.getBrandColor()));
        brand.setIsGenuine(genuine(req.getIsGenuine(), 1));
        brand.setStatus(ProductConstants.STATUS_ENABLED);
        try {
            brandMapper.insert(brand);
        } catch (DuplicateKeyException e) {
            throw duplicate(null, false, e);
        }
        optionsCache.evictAfterCommit(ProductConstants.CACHE_KEY_BRAND_OPTIONS);
        return toVO(brand, 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BrandVO update(Long id, SaveBrandRequest req) {
        platformScopeGuard.requirePlatform();
        ProductBrandDO current = getActive(id);
        String name = TextRules.required(req.getBrandName(), "品牌名称", NAME_MAX);
        assertNameFree(name, id);

        // 用只带变更字段的新对象更新，才能触发 update_time / update_by 的自动填充
        ProductBrandDO change = new ProductBrandDO();
        change.setId(id);
        change.setBrandName(name);
        change.setCountry(TextRules.optional(req.getCountry(), "原产国", 64));
        change.setLogoUrl(TextRules.optional(req.getLogoUrl(), "Logo 地址", 256));
        change.setBrandColor(validColor(req.getBrandColor()));
        change.setIsGenuine(genuine(req.getIsGenuine(), current.getIsGenuine()));
        try {
            brandMapper.updateById(change);
        } catch (DuplicateKeyException e) {
            throw duplicate(null, false, e);
        }
        optionsCache.evictAfterCommit(ProductConstants.CACHE_KEY_BRAND_OPTIONS);
        return toVO(getActive(id), countProducts(id));
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

    /** 名称唯一性检查包含已软删除的行（唯一键也包含它们）；excludeId 用于修改时排除自己 */
    private void assertNameFree(String name, Long excludeId) {
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

    private static String validColor(String color) {
        String text = TextRules.optional(color, "品牌主题色", 16);
        if (!text.isEmpty() && !HEX_COLOR.matcher(text).matches()) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "品牌主题色格式不正确，应为 #RRGGBB");
        }
        return text;
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
        vo.setIsGenuine(brand.getIsGenuine());
        vo.setStatus(brand.getStatus());
        vo.setProductCount(productCount);
        vo.setCreateTime(brand.getCreateTime());
        vo.setUpdateTime(brand.getUpdateTime());
        return vo;
    }
}

package com.zhul.erp.modules.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.CreateSeriesRequest;
import com.zhul.erp.modules.product.dto.SeriesOptionVO;
import com.zhul.erp.modules.product.dto.SeriesQuery;
import com.zhul.erp.modules.product.dto.SeriesVO;
import com.zhul.erp.modules.product.dto.UpdateSeriesRequest;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductSeriesDO;
import com.zhul.erp.modules.product.repository.IdCount;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.repository.ProductSeriesMapper;
import com.zhul.erp.modules.product.service.SeriesService;
import com.zhul.erp.modules.product.support.LikeUtils;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.TextRules;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeriesServiceImpl implements SeriesService {

    private static final int NAME_MAX = 64;
    private static final int DESCRIPTION_MAX = 500;

    private final ProductSeriesMapper seriesMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductMapper productMapper;
    private final PlatformScopeGuard platformScopeGuard;

    @Override
    public PageResult<SeriesVO> page(SeriesQuery query) {
        LambdaQueryWrapper<ProductSeriesDO> wrapper = new LambdaQueryWrapper<ProductSeriesDO>()
                .eq(ProductSeriesDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductSeriesDO::getDeletedAt)
                .eq(query.getBrandId() != null, ProductSeriesDO::getBrandId, query.getBrandId())
                .eq(query.getStatus() != null, ProductSeriesDO::getStatus, query.getStatus())
                .orderByAsc(ProductSeriesDO::getBrandId)
                .orderByAsc(ProductSeriesDO::getSeriesName)
                .orderByAsc(ProductSeriesDO::getId);
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(ProductSeriesDO::getSeriesName, LikeUtils.escape(query.getKeyword().trim()));
        }
        Page<ProductSeriesDO> page = seriesMapper.selectPage(
                new Page<>(query.pageOrDefault(), query.pageSizeOrDefault()), wrapper);
        List<ProductSeriesDO> rows = page.getRecords();

        Map<Long, Long> counts = new HashMap<>();
        Map<Long, String> brandNames = new HashMap<>();
        if (!rows.isEmpty()) {
            for (IdCount c : productMapper.countBySeriesIds(rows.stream().map(ProductSeriesDO::getId).toList())) {
                counts.put(c.getId(), c.getCnt());
            }
            Set<Long> brandIds = rows.stream().map(ProductSeriesDO::getBrandId).collect(Collectors.toSet());
            // 品牌已被删除时系列仍要显示品牌名，所以这里不过滤 deleted_at
            for (ProductBrandDO brand : brandMapper.selectBatchIds(brandIds)) {
                brandNames.put(brand.getId(), brand.getBrandName());
            }
        }
        List<SeriesVO> records = new ArrayList<>(rows.size());
        for (ProductSeriesDO series : rows) {
            records.add(toVO(series, brandNames.get(series.getBrandId()), counts.getOrDefault(series.getId(), 0L)));
        }
        return PageResult.of(page.getTotal(), records);
    }

    @Override
    public List<SeriesOptionVO> options(Long brandId) {
        List<ProductSeriesDO> rows = seriesMapper.selectList(new LambdaQueryWrapper<ProductSeriesDO>()
                .eq(ProductSeriesDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductSeriesDO::getDeletedAt)
                .eq(ProductSeriesDO::getStatus, ProductConstants.STATUS_ENABLED)
                .eq(brandId != null, ProductSeriesDO::getBrandId, brandId)
                .orderByAsc(ProductSeriesDO::getSeriesName)
                .orderByAsc(ProductSeriesDO::getId));
        List<SeriesOptionVO> options = new ArrayList<>(rows.size());
        for (ProductSeriesDO series : rows) {
            SeriesOptionVO vo = new SeriesOptionVO();
            vo.setId(series.getId());
            vo.setBrandId(series.getBrandId());
            vo.setSeriesName(series.getSeriesName());
            options.add(vo);
        }
        return options;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeriesVO create(CreateSeriesRequest req) {
        platformScopeGuard.requirePlatform();
        String name = TextRules.required(req.getSeriesName(), "系列名称", NAME_MAX);
        String description = TextRules.optional(req.getDescription(), "系列简介", DESCRIPTION_MAX);
        ProductBrandDO brand = getActiveBrand(req.getBrandId());
        assertNameFree(brand.getId(), name, null);

        ProductSeriesDO series = new ProductSeriesDO();
        series.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
        series.setBrandId(brand.getId());
        series.setSeriesName(name);
        series.setDescription(description);
        series.setStatus(ProductConstants.STATUS_ENABLED);
        try {
            seriesMapper.insert(series);
        } catch (DuplicateKeyException e) {
            throw duplicate(null, false, e);
        }
        return toVO(series, brand.getBrandName(), 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeriesVO update(Long id, UpdateSeriesRequest req) {
        platformScopeGuard.requirePlatform();
        ProductSeriesDO current = getActive(id);
        String name = TextRules.required(req.getSeriesName(), "系列名称", NAME_MAX);
        String description = TextRules.optional(req.getDescription(), "系列简介", DESCRIPTION_MAX);
        assertNameFree(current.getBrandId(), name, id);

        ProductSeriesDO change = new ProductSeriesDO();
        change.setId(id);
        change.setSeriesName(name);
        change.setDescription(description);
        try {
            seriesMapper.updateById(change);
        } catch (DuplicateKeyException e) {
            throw duplicate(null, false, e);
        }
        ProductBrandDO brand = brandMapper.selectById(current.getBrandId());
        return toVO(getActive(id), brand == null ? null : brand.getBrandName(), countProducts(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        platformScopeGuard.requirePlatform();
        int value = TextRules.status(status);
        getActive(id);
        ProductSeriesDO change = new ProductSeriesDO();
        change.setId(id);
        change.setStatus(value);
        seriesMapper.updateById(change);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        platformScopeGuard.requirePlatform();
        getActive(id);
        long usage = countProducts(id);
        if (usage > 0) {
            throw BizException.of(ProductErrorCodes.SERIES_IN_USE,
                    "已有 " + usage + " 个商品使用该系列，请先停用", Map.of("usageCount", usage));
        }
        ProductSeriesDO change = new ProductSeriesDO();
        change.setId(id);
        change.setDeletedAt(LocalDateTime.now());
        seriesMapper.updateById(change);
    }

    private ProductSeriesDO getActive(Long id) {
        ProductSeriesDO series = id == null ? null : seriesMapper.selectOne(new LambdaQueryWrapper<ProductSeriesDO>()
                .eq(ProductSeriesDO::getId, id)
                .eq(ProductSeriesDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductSeriesDO::getDeletedAt));
        if (series == null) {
            throw BizException.of(ProductErrorCodes.SERIES_NOT_FOUND, "系列不存在");
        }
        return series;
    }

    private ProductBrandDO getActiveBrand(Long brandId) {
        ProductBrandDO brand = brandId == null ? null : brandMapper.selectOne(new LambdaQueryWrapper<ProductBrandDO>()
                .eq(ProductBrandDO::getId, brandId)
                .eq(ProductBrandDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductBrandDO::getDeletedAt));
        if (brand == null) {
            throw BizException.of(ProductErrorCodes.BRAND_NOT_FOUND, "品牌不存在");
        }
        return brand;
    }

    /** 同品牌内系列名称唯一性检查，包含已软删除的行（唯一键也包含它们） */
    private void assertNameFree(Long brandId, String name, Long excludeId) {
        ProductSeriesDO existing = seriesMapper.selectOne(new LambdaQueryWrapper<ProductSeriesDO>()
                .eq(ProductSeriesDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductSeriesDO::getBrandId, brandId)
                .eq(ProductSeriesDO::getSeriesName, name)
                .ne(excludeId != null, ProductSeriesDO::getId, excludeId)
                .last("LIMIT 1"));
        if (existing != null) {
            throw duplicate(existing.getId(), existing.getDeletedAt() != null, null);
        }
    }

    private BizException duplicate(Long existingId, boolean deleted, Throwable cause) {
        Map<String, Object> detail = new HashMap<>(4);
        detail.put("existingId", existingId);
        detail.put("deleted", deleted);
        BizException e = BizException.of(ProductErrorCodes.SERIES_DUPLICATE,
                deleted ? "该品牌下同名系列曾被删除，不能重复创建" : "该品牌下系列已存在", detail);
        if (cause != null) {
            e.initCause(cause);
        }
        return e;
    }

    private long countProducts(Long seriesId) {
        return productMapper.selectCount(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductDO::getSeriesId, seriesId)
                .isNull(ProductDO::getDeletedAt));
    }

    private static SeriesVO toVO(ProductSeriesDO series, String brandName, long productCount) {
        SeriesVO vo = new SeriesVO();
        vo.setId(series.getId());
        vo.setBrandId(series.getBrandId());
        vo.setBrandName(brandName);
        vo.setSeriesName(series.getSeriesName());
        vo.setDescription(series.getDescription());
        vo.setStatus(series.getStatus());
        vo.setProductCount(productCount);
        vo.setCreateBy(series.getCreateBy());
        vo.setCreateTime(series.getCreateTime());
        vo.setUpdateBy(series.getUpdateBy());
        vo.setUpdateTime(series.getUpdateTime());
        return vo;
    }
}

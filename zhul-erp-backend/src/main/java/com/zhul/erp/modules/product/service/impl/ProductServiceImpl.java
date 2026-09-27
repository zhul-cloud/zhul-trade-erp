package com.zhul.erp.modules.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.product.constants.LifecycleStatus;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.constants.RelationshipType;
import com.zhul.erp.modules.product.dto.CompletenessSummaryVO;
import com.zhul.erp.modules.product.dto.CompletenessVO;
import com.zhul.erp.modules.product.dto.ProductQuery;
import com.zhul.erp.modules.product.dto.ProductVO;
import com.zhul.erp.modules.product.dto.SaveProductRequest;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductRelationshipDO;
import com.zhul.erp.modules.product.entity.ProductSeriesDO;
import com.zhul.erp.modules.product.repository.CompletenessSql;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.repository.ProductRelationshipMapper;
import com.zhul.erp.modules.product.repository.ProductSeriesMapper;
import com.zhul.erp.modules.product.service.ProductCompletenessService;
import com.zhul.erp.modules.product.service.ProductService;
import com.zhul.erp.modules.product.support.LikeUtils;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.ProductFinder;
import com.zhul.erp.modules.product.support.ProductNames;
import com.zhul.erp.modules.product.support.ProductUsageChecker;
import com.zhul.erp.modules.product.support.TextRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    private static final int MPN_MAX = 128;
    private static final String WARN_OBSOLETE_WITH_REPLACEMENT =
            "该商品存在替代型号记录，请确认生命周期是否应为「已停产」而不是「停产无替代」";

    private final ProductMapper productMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductSeriesMapper seriesMapper;
    private final ProductRelationshipMapper relationshipMapper;
    private final ProductFinder productFinder;
    private final ProductNames productNames;
    private final ProductCompletenessService completenessService;
    private final PlatformScopeGuard platformScopeGuard;
    private final ObjectProvider<ProductUsageChecker> usageCheckers;

    public ProductServiceImpl(ProductMapper productMapper, ProductBrandMapper brandMapper,
                              ProductCategoryMapper categoryMapper, ProductSeriesMapper seriesMapper,
                              ProductRelationshipMapper relationshipMapper, ProductFinder productFinder,
                              ProductNames productNames, ProductCompletenessService completenessService,
                              PlatformScopeGuard platformScopeGuard,
                              ObjectProvider<ProductUsageChecker> usageCheckers) {
        this.productMapper = productMapper;
        this.brandMapper = brandMapper;
        this.categoryMapper = categoryMapper;
        this.seriesMapper = seriesMapper;
        this.relationshipMapper = relationshipMapper;
        this.productFinder = productFinder;
        this.productNames = productNames;
        this.completenessService = completenessService;
        this.platformScopeGuard = platformScopeGuard;
        this.usageCheckers = usageCheckers;
    }

    // ---------- 读取 ----------

    @Override
    public PageResult<ProductVO> page(ProductQuery query) {
        // includeDeleted 只对平台账号生效，租户账号传了也当没传
        boolean includeDeleted = Boolean.TRUE.equals(query.getIncludeDeleted()) && platformScopeGuard.isPlatform();
        LambdaQueryWrapper<ProductDO> wrapper = new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(!includeDeleted, ProductDO::getDeletedAt)
                .eq(query.getBrandId() != null, ProductDO::getBrandId, query.getBrandId())
                .eq(query.getCategoryId() != null, ProductDO::getCategoryId, query.getCategoryId())
                .eq(query.getSeriesId() != null, ProductDO::getSeriesId, query.getSeriesId())
                .eq(query.getLifecycleStatus() != null, ProductDO::getLifecycleStatus, query.getLifecycleStatus())
                .eq(query.getStatus() != null, ProductDO::getStatus, query.getStatus())
                .orderByDesc(ProductDO::getId);
        // 缺项筛选只对平台账号生效，租户账号传了也当没传
        if (platformScopeGuard.isPlatform() && StringUtils.hasText(query.getMissing())) {
            wrapper.notExists(missingSql(query.getMissing().trim()));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = LikeUtils.escape(query.getKeyword().trim());
            String normalized = MpnNormalizer.normalize(query.getKeyword());
            wrapper.and(w -> {
                w.like(ProductDO::getProductName, keyword).or().like(ProductDO::getMpnRaw, keyword);
                if (!normalized.isEmpty()) {
                    w.or().like(ProductDO::getMpnNormalized, normalized);
                }
            });
        }
        Page<ProductDO> page = productMapper.selectPage(
                new Page<>(query.pageOrDefault(), query.pageSizeOrDefault()), wrapper);

        ProductNames.Lookup lookup = productNames.load(page.getRecords());
        // 完整度只对平台账号返回；一页商品的完整度只发固定的一条查询
        Map<Long, CompletenessVO> completeness = platformScopeGuard.isPlatform()
                ? completenessService.compute(page.getRecords()) : Map.of();
        List<ProductVO> records = new ArrayList<>(page.getRecords().size());
        for (ProductDO product : page.getRecords()) {
            ProductVO vo = toVO(product, lookup);
            vo.setCompleteness(completeness.get(product.getId()));
            records.add(vo);
        }
        return PageResult.of(page.getTotal(), records);
    }

    @Override
    public ProductVO getById(Long id) {
        ProductDO product = productFinder.active(id);
        ProductVO vo = toVO(product, productNames.load(List.of(product)));
        vo.setUsageCount(usageCount(id));
        if (platformScopeGuard.isPlatform()) {
            vo.setCompleteness(completenessService.compute(List.of(product)).get(id));
        }
        return vo;
    }

    // ---------- 创建 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductVO create(SaveProductRequest req) {
        platformScopeGuard.requirePlatform();
        ProductDO product = buildFields(req, null);
        assertMpnFree(product.getBrandId(), product.getMpnNormalized(), null);
        product.setStatus(ProductConstants.STATUS_ENABLED);
        try {
            productMapper.insert(product);
        } catch (DuplicateKeyException e) {
            // 并发创建同一型号：唯一键兜底，只成功一个，另一个走到这里
            throw duplicate(findByMpnLocking(product.getBrandId(), product.getMpnNormalized()), e);
        }
        log.info("商品创建，productId={}, brandId={}, mpn={}", product.getId(), product.getBrandId(),
                product.getMpnNormalized());
        return toVO(product, productNames.load(List.of(product)));
    }

    // ---------- 修改 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductVO update(Long id, SaveProductRequest req) {
        platformScopeGuard.requirePlatform();
        ProductDO current = productFinder.lockActive(id);
        long usage = usageCount(id);
        ProductDO change = buildFields(req, current);

        boolean identityChanged = !Objects.equals(change.getBrandId(), current.getBrandId())
                || !change.getMpnRaw().equals(current.getMpnRaw());
        if (identityChanged) {
            if (usage > 0) {
                throw BizException.of(ProductErrorCodes.PRODUCT_MPN_IMMUTABLE,
                        "商品已被 " + usage + " 处引用，不可修改品牌和型号", Map.of("usageCount", usage));
            }
            assertMpnFree(change.getBrandId(), change.getMpnNormalized(), id);
        }

        // series_id 可能要清空为 NULL，实体更新会跳过 null，所以单独用 set 写；
        // 其余字段放在实体里，才能触发 update_time / update_by 的自动填充
        Long seriesId = change.getSeriesId();
        change.setSeriesId(null);
        try {
            productMapper.update(change, new LambdaUpdateWrapper<ProductDO>()
                    .eq(ProductDO::getId, id)
                    .set(ProductDO::getSeriesId, seriesId));
        } catch (DuplicateKeyException e) {
            throw duplicate(findByMpnLocking(change.getBrandId(), change.getMpnNormalized()), e);
        }

        ProductDO saved = productFinder.active(id);
        ProductVO vo = toVO(saved, productNames.load(List.of(saved)));
        vo.setUsageCount(usage);
        vo.setWarnings(warningsFor(saved));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        platformScopeGuard.requirePlatform();
        int value = TextRules.status(status);
        productFinder.active(id);
        ProductDO change = new ProductDO();
        change.setId(id);
        change.setStatus(value);
        productMapper.updateById(change);
    }

    // ---------- 删除与恢复 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        platformScopeGuard.requirePlatform();
        // 先锁商品行再汇总引用次数，业务模块关联商品时对同一行加共享锁，避免"刚检查完无引用就被关联"
        productFinder.lockActive(id);
        long usage = usageCount(id);
        if (usage > 0) {
            throw BizException.of(ProductErrorCodes.PRODUCT_IN_USE,
                    "商品已被 " + usage + " 处引用，请改为停用", Map.of("usageCount", usage));
        }
        ProductDO change = new ProductDO();
        change.setId(id);
        change.setDeletedAt(LocalDateTime.now());
        productMapper.updateById(change);
        log.info("商品软删除，productId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductVO restore(Long id) {
        platformScopeGuard.requirePlatform();
        ProductDO deleted = id == null ? null : productMapper.selectOne(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getId, id)
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNotNull(ProductDO::getDeletedAt)
                .last("FOR UPDATE"));
        if (deleted == null) {
            throw BizException.of(ProductErrorCodes.PRODUCT_NOT_FOUND, "商品不存在或未被删除");
        }
        // 品牌、品类被删除后商品挂在已删除的记录上，恢复出来会显示不全，所以要求它们仍然存在
        if (brandMapper.selectCount(new LambdaQueryWrapper<ProductBrandDO>()
                .eq(ProductBrandDO::getId, deleted.getBrandId()).isNull(ProductBrandDO::getDeletedAt)) == 0) {
            throw BizException.of(ProductErrorCodes.BRAND_NOT_FOUND, "该商品所属品牌已被删除，无法恢复");
        }
        if (categoryMapper.selectCount(new LambdaQueryWrapper<ProductCategoryDO>()
                .eq(ProductCategoryDO::getId, deleted.getCategoryId()).isNull(ProductCategoryDO::getDeletedAt)) == 0) {
            throw BizException.of(ProductErrorCodes.CATEGORY_NOT_FOUND, "该商品所属品类已被删除，无法恢复");
        }
        // deleted_at 要写回 NULL：用空实体触发审计字段填充，再由 set 写入 NULL
        productMapper.update(new ProductDO(), new LambdaUpdateWrapper<ProductDO>()
                .eq(ProductDO::getId, id)
                .set(ProductDO::getDeletedAt, null));
        log.info("商品恢复，productId={}", id);
        ProductDO restored = productFinder.active(id);
        return toVO(restored, productNames.load(List.of(restored)));
    }

    @Override
    public CompletenessSummaryVO completenessSummary() {
        platformScopeGuard.requirePlatform();
        CompletenessSummaryVO summary = new CompletenessSummaryVO();
        summary.setTotal(countUndeleted(null));
        summary.setMissingMedia(countUndeleted(CompletenessSql.MISSING_MEDIA));
        summary.setMissingLogistics(countUndeleted(CompletenessSql.MISSING_LOGISTICS));
        summary.setMissingCustoms(countUndeleted(CompletenessSql.MISSING_CUSTOMS));
        summary.setMissingPrice(countUndeleted(CompletenessSql.MISSING_PRICE));
        return summary;
    }

    /** 与列表的缺项筛选使用同一组 SQL 片段，统计数量与筛选结果条数才一致 */
    private long countUndeleted(String notExistsSql) {
        LambdaQueryWrapper<ProductDO> wrapper = new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductDO::getDeletedAt);
        if (notExistsSql != null) {
            wrapper.notExists(notExistsSql);
        }
        return productMapper.selectCount(wrapper);
    }

    private static String missingSql(String missing) {
        return switch (missing) {
            case "media" -> CompletenessSql.MISSING_MEDIA;
            case "logistics" -> CompletenessSql.MISSING_LOGISTICS;
            case "customs" -> CompletenessSql.MISSING_CUSTOMS;
            case "price" -> CompletenessSql.MISSING_PRICE;
            default -> throw BizException.of(ProductErrorCodes.PARAM_INVALID,
                    "缺项筛选只支持 media、logistics、customs、price");
        };
    }

    // ---------- 校验与组装 ----------

    /**
     * 校验入参并组装要写入的字段。current 为 null 表示创建；修改时未改动的品牌、品类即使已被停用也照常通过。
     */
    private ProductDO buildFields(SaveProductRequest req, ProductDO current) {
        String raw = TextRules.required(req.getMpnRaw(), "原始型号", MPN_MAX);
        String normalized = MpnNormalizer.normalize(raw);
        if (normalized.isEmpty()) {
            throw BizException.of(ProductErrorCodes.PRODUCT_MPN_INVALID, "型号至少包含一个字母或数字");
        }
        String display = TextRules.optional(req.getMpnDisplay(), "展示型号", MPN_MAX);

        Long brandId = req.getBrandId();
        boolean brandChanged = current == null || !Objects.equals(brandId, current.getBrandId());
        ProductBrandDO brand = activeBrand(brandId);
        if (brandChanged && !Objects.equals(brand.getStatus(), ProductConstants.STATUS_ENABLED)) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "品牌已停用，不能用于新商品");
        }
        Long categoryId = req.getCategoryId();
        ProductCategoryDO category = activeCategory(categoryId);
        if (category.getParentId() != null) {
            throw BizException.of(ProductErrorCodes.CATEGORY_LEVEL_INVALID, "商品只能选择一级品类");
        }
        if ((current == null || !Objects.equals(categoryId, current.getCategoryId()))
                && !Objects.equals(category.getStatus(), ProductConstants.STATUS_ENABLED)) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "品类已停用，不能用于新商品");
        }
        Long seriesId = req.getSeriesId();
        if (seriesId != null) {
            ProductSeriesDO series = seriesMapper.selectOne(new LambdaQueryWrapper<ProductSeriesDO>()
                    .eq(ProductSeriesDO::getId, seriesId)
                    .eq(ProductSeriesDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                    .isNull(ProductSeriesDO::getDeletedAt));
            if (series == null) {
                throw BizException.of(ProductErrorCodes.SERIES_NOT_FOUND, "系列不存在");
            }
            if (!series.getBrandId().equals(brand.getId())) {
                throw BizException.of(ProductErrorCodes.PRODUCT_SERIES_MISMATCH, "所选系列不属于该品牌");
            }
        }

        int lifecycle = lifecycle(req.getLifecycleStatus(), current);
        String source = req.getLifecycleSource() != null
                ? TextRules.optional(req.getLifecycleSource(), "生命周期依据", 128)
                : (current == null ? "" : current.getLifecycleSource());
        if (LifecycleStatus.requiresSource(lifecycle) && !StringUtils.hasText(source)) {
            throw BizException.of(ProductErrorCodes.PRODUCT_LIFECYCLE_SOURCE_REQUIRED, "已停产或停产无替代必须填写生命周期依据");
        }

        ProductDO product = new ProductDO();
        product.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
        product.setBrandId(brand.getId());
        product.setCategoryId(category.getId());
        product.setSeriesId(seriesId);
        product.setMpnRaw(raw);
        product.setMpnNormalized(normalized);
        product.setMpnDisplay(display.isEmpty() ? raw : display);
        product.setProductName(TextRules.optional(req.getProductName(), "产品名称", 128));
        product.setShortDescription(TextRules.optional(req.getShortDescription(), "简介", 500));
        product.setSpecSummary(TextRules.optional(req.getSpecSummary(), "规格摘要", 300));
        product.setLifecycleStatus(lifecycle);
        product.setLifecycleSource(source);
        return product;
    }

    private static int lifecycle(Integer requested, ProductDO current) {
        if (requested == null) {
            return current == null ? LifecycleStatus.UNKNOWN : current.getLifecycleStatus();
        }
        if (!LifecycleStatus.isValid(requested)) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "生命周期取值不合法");
        }
        return requested;
    }

    private ProductBrandDO activeBrand(Long brandId) {
        ProductBrandDO brand = brandId == null ? null : brandMapper.selectOne(new LambdaQueryWrapper<ProductBrandDO>()
                .eq(ProductBrandDO::getId, brandId)
                .eq(ProductBrandDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductBrandDO::getDeletedAt));
        if (brand == null) {
            throw BizException.of(ProductErrorCodes.BRAND_NOT_FOUND, "品牌不存在");
        }
        return brand;
    }

    private ProductCategoryDO activeCategory(Long categoryId) {
        ProductCategoryDO category = categoryId == null ? null : categoryMapper.selectOne(
                new LambdaQueryWrapper<ProductCategoryDO>()
                        .eq(ProductCategoryDO::getId, categoryId)
                        .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .isNull(ProductCategoryDO::getDeletedAt));
        if (category == null) {
            throw BizException.of(ProductErrorCodes.CATEGORY_NOT_FOUND, "品类不存在");
        }
        return category;
    }

    /** 同品牌下归一化型号唯一，包含已软删除的商品；excludeId 用于修改时排除自己 */
    private void assertMpnFree(Long brandId, String normalized, Long excludeId) {
        ProductDO existing = productMapper.selectOne(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductDO::getBrandId, brandId)
                .eq(ProductDO::getMpnNormalized, normalized)
                .ne(excludeId != null, ProductDO::getId, excludeId)
                .last("LIMIT 1"));
        if (existing != null) {
            throw duplicate(existing, null);
        }
    }

    /** 加锁读取（读最新已提交数据）：并发冲突后当前事务的一致性快照里还看不到对方刚提交的行 */
    private ProductDO findByMpnLocking(Long brandId, String normalized) {
        return productMapper.selectOne(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductDO::getBrandId, brandId)
                .eq(ProductDO::getMpnNormalized, normalized)
                .last("LIMIT 1 FOR SHARE"));
    }

    private BizException duplicate(ProductDO existing, Throwable cause) {
        Map<String, Object> detail = new HashMap<>(8);
        boolean deleted = existing != null && existing.getDeletedAt() != null;
        if (existing != null) {
            detail.put("existingId", existing.getId());
            detail.put("mpnDisplay", existing.getMpnDisplay());
        }
        detail.put("deleted", deleted);
        BizException e = BizException.of(ProductErrorCodes.PRODUCT_DUPLICATE,
                deleted ? "该型号曾被删除，可以恢复原商品" : "该型号已存在", detail);
        if (cause != null) {
            e.initCause(cause);
        }
        return e;
    }

    private long usageCount(Long productId) {
        return usageCheckers.orderedStream().mapToLong(checker -> checker.countUsage(productId)).sum();
    }

    /** 生命周期为"停产无替代"却存在替代类关系时，保存仍成功，只是给出提醒 */
    private List<String> warningsFor(ProductDO product) {
        List<String> warnings = new ArrayList<>(2);
        if (Objects.equals(product.getLifecycleStatus(), LifecycleStatus.OBSOLETE)) {
            Long replacements = relationshipMapper.selectCount(new LambdaQueryWrapper<ProductRelationshipDO>()
                    .eq(ProductRelationshipDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                    .eq(ProductRelationshipDO::getProductId, product.getId())
                    .isNull(ProductRelationshipDO::getDeletedAt)
                    .in(ProductRelationshipDO::getRelationshipType, RelationshipType.OFFICIAL_REPLACEMENT,
                            RelationshipType.SUCCESSOR, RelationshipType.FUNCTIONAL_REPLACEMENT));
            if (replacements > 0) {
                warnings.add(WARN_OBSOLETE_WITH_REPLACEMENT);
            }
        }
        return warnings;
    }

    private static ProductVO toVO(ProductDO product, ProductNames.Lookup lookup) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setBrandId(product.getBrandId());
        ProductBrandDO brand = lookup.brands().get(product.getBrandId());
        if (brand != null) {
            vo.setBrandName(brand.getBrandName());
            vo.setBrandIsGenuine(brand.getIsGenuine());
        }
        vo.setCategoryId(product.getCategoryId());
        ProductCategoryDO category = lookup.categories().get(product.getCategoryId());
        if (category != null) {
            vo.setCategoryCode(category.getCategoryCode());
            vo.setCategoryName(category.getCategoryName());
        }
        vo.setSeriesId(product.getSeriesId());
        if (product.getSeriesId() != null) {
            ProductSeriesDO series = lookup.series().get(product.getSeriesId());
            vo.setSeriesName(series == null ? null : series.getSeriesName());
        }
        vo.setMpnRaw(product.getMpnRaw());
        vo.setMpnNormalized(product.getMpnNormalized());
        vo.setMpnDisplay(product.getMpnDisplay());
        vo.setProductName(product.getProductName());
        vo.setShortDescription(product.getShortDescription());
        vo.setSpecSummary(product.getSpecSummary());
        vo.setLifecycleStatus(product.getLifecycleStatus());
        vo.setLifecycleSource(product.getLifecycleSource());
        vo.setStatus(product.getStatus());
        vo.setDeleted(product.getDeletedAt() != null);
        vo.setCreateTime(product.getCreateTime());
        vo.setUpdateTime(product.getUpdateTime());
        return vo;
    }
}

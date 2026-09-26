package com.zhul.erp.modules.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.CategoryOptionVO;
import com.zhul.erp.modules.product.dto.CategoryQuery;
import com.zhul.erp.modules.product.dto.CategoryVO;
import com.zhul.erp.modules.product.dto.SaveCategoryRequest;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.IdCount;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.service.CategoryService;
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
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final int CODE_MAX = 32;
    private static final int NAME_MAX = 64;
    private static final int NAME_ZH_MAX = 32;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    private final ProductCategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final PlatformScopeGuard platformScopeGuard;
    private final OptionsCache optionsCache;

    @Override
    public PageResult<CategoryVO> page(CategoryQuery query) {
        LambdaQueryWrapper<ProductCategoryDO> wrapper = new LambdaQueryWrapper<ProductCategoryDO>()
                .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductCategoryDO::getDeletedAt)
                .eq(query.getStatus() != null, ProductCategoryDO::getStatus, query.getStatus())
                .isNull(query.getParentId() == null, ProductCategoryDO::getParentId)
                .eq(query.getParentId() != null, ProductCategoryDO::getParentId, query.getParentId())
                .orderByAsc(ProductCategoryDO::getSortOrder)
                .orderByAsc(ProductCategoryDO::getId);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = LikeUtils.escape(query.getKeyword().trim());
            wrapper.and(w -> w.like(ProductCategoryDO::getCategoryCode, keyword)
                    .or().like(ProductCategoryDO::getCategoryName, keyword)
                    .or().like(ProductCategoryDO::getCategoryNameZh, keyword));
        }
        Page<ProductCategoryDO> page = categoryMapper.selectPage(
                new Page<>(query.pageOrDefault(), query.pageSizeOrDefault()), wrapper);

        Map<Long, Long> counts = toCountMap(page.getRecords().isEmpty() ? List.of()
                : productMapper.countByCategoryIds(page.getRecords().stream().map(ProductCategoryDO::getId).toList()));
        List<CategoryVO> records = new ArrayList<>(page.getRecords().size());
        for (ProductCategoryDO category : page.getRecords()) {
            records.add(toVO(category, counts.getOrDefault(category.getId(), 0L)));
        }
        return PageResult.of(page.getTotal(), records);
    }

    @Override
    public List<CategoryOptionVO> options(Integer level) {
        List<CategoryOptionVO> all = allOptions();
        if (level != null && level == 0) {
            return all;
        }
        boolean subLevel = level != null && level == 2;
        return all.stream().filter(o -> (o.getParentId() != null) == subLevel).toList();
    }

    /** 全部启用品类（两级都有），缓存整份清单，按层级在内存里过滤 */
    private List<CategoryOptionVO> allOptions() {
        List<CategoryOptionVO> cached = optionsCache.get(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS,
                CategoryOptionVO.class);
        if (cached != null) {
            return cached;
        }
        List<ProductCategoryDO> categories = categoryMapper.selectList(new LambdaQueryWrapper<ProductCategoryDO>()
                .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductCategoryDO::getDeletedAt)
                .eq(ProductCategoryDO::getStatus, ProductConstants.STATUS_ENABLED)
                .orderByAsc(ProductCategoryDO::getSortOrder)
                .orderByAsc(ProductCategoryDO::getId));
        List<CategoryOptionVO> options = new ArrayList<>(categories.size());
        for (ProductCategoryDO category : categories) {
            CategoryOptionVO vo = new CategoryOptionVO();
            vo.setId(category.getId());
            vo.setCategoryCode(category.getCategoryCode());
            vo.setCategoryName(category.getCategoryName());
            vo.setCategoryNameZh(category.getCategoryNameZh());
            vo.setParentId(category.getParentId());
            vo.setDescription(category.getDescription());
            options.add(vo);
        }
        optionsCache.put(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS, options);
        return options;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryVO create(SaveCategoryRequest req) {
        platformScopeGuard.requirePlatform();
        String code = validCode(req.getCategoryCode());
        String name = TextRules.required(req.getCategoryName(), "品类名称", NAME_MAX);
        Long parentId = validParent(req.getParentId(), null);
        String nameZh = validNameZh(req.getCategoryNameZh(), parentId);
        assertCodeFree(code, null);

        ProductCategoryDO category = new ProductCategoryDO();
        category.setParentId(parentId);
        category.setCategoryNameZh(nameZh);
        category.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
        category.setCategoryCode(code);
        category.setCategoryName(name);
        category.setDescription(TextRules.optional(req.getDescription(), "品类简介", ProductConstants.DESCRIPTION_MAX));
        category.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        category.setStatus(ProductConstants.STATUS_ENABLED);
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException e) {
            throw duplicate(null, false, e);
        }
        optionsCache.evictAfterCommit(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS);
        return toVO(category, 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryVO update(Long id, SaveCategoryRequest req) {
        platformScopeGuard.requirePlatform();
        ProductCategoryDO current = getActive(id);
        String code = validCode(req.getCategoryCode());
        String name = TextRules.required(req.getCategoryName(), "品类名称", NAME_MAX);
        long usage = countProducts(id);
        Long parentId = validParent(req.getParentId(), current);
        if (parentId != null && usage > 0) {
            throw BizException.of(ProductErrorCodes.CATEGORY_LEVEL_INVALID, "该品类下已有商品，商品只能挂一级品类，不能改为细分品类");
        }
        String nameZh = validNameZh(req.getCategoryNameZh(), parentId);
        if (!code.equals(current.getCategoryCode())) {
            if (usage > 0) {
                throw BizException.of(ProductErrorCodes.CATEGORY_CODE_IMMUTABLE,
                        "该品类下已有 " + usage + " 个商品，编码已被使用，不可修改", Map.of("usageCount", usage));
            }
            assertCodeFree(code, id);
        }

        ProductCategoryDO change = new ProductCategoryDO();
        change.setId(id);
        change.setCategoryCode(code);
        change.setCategoryName(name);
        change.setCategoryNameZh(nameZh);
        change.setDescription(TextRules.optional(req.getDescription(), "品类简介", ProductConstants.DESCRIPTION_MAX));
        change.setSortOrder(req.getSortOrder() == null ? current.getSortOrder() : req.getSortOrder());
        try {
            categoryMapper.updateById(change);
            if (!Objects.equals(parentId, current.getParentId())) {
                // 只带部分字段的 updateById 不会把 null 写回，一级 / 细分之间切换时单独更新 parent_id
                categoryMapper.update(new ProductCategoryDO(), new LambdaUpdateWrapper<ProductCategoryDO>()
                        .set(ProductCategoryDO::getParentId, parentId)
                        .eq(ProductCategoryDO::getId, id));
            }
        } catch (DuplicateKeyException e) {
            throw duplicate(null, false, e);
        }
        optionsCache.evictAfterCommit(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS);
        return toVO(getActive(id), usage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        platformScopeGuard.requirePlatform();
        int value = TextRules.status(status);
        getActive(id);
        ProductCategoryDO change = new ProductCategoryDO();
        change.setId(id);
        change.setStatus(value);
        categoryMapper.updateById(change);
        optionsCache.evictAfterCommit(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        platformScopeGuard.requirePlatform();
        getActive(id);
        long usage = countProducts(id);
        if (usage > 0) {
            throw BizException.of(ProductErrorCodes.CATEGORY_IN_USE,
                    "已有 " + usage + " 个商品使用该品类，请先停用", Map.of("usageCount", usage));
        }
        if (countChildren(id) > 0) {
            throw BizException.of(ProductErrorCodes.CATEGORY_HAS_CHILDREN, "该品类下还有细分品类，请先删除或移走细分品类");
        }
        long supplierUsage = categoryMapper.countSupplierScopes(id);
        if (supplierUsage > 0) {
            throw BizException.of(ProductErrorCodes.CATEGORY_IN_USE, "该品类已被供应商主营产品使用，可改为停用",
                    Map.of("supplierUsageCount", supplierUsage));
        }
        ProductCategoryDO change = new ProductCategoryDO();
        change.setId(id);
        change.setDeletedAt(LocalDateTime.now());
        categoryMapper.updateById(change);
        optionsCache.evictAfterCommit(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS);
    }

    @Override
    public List<CategoryVO> tree() {
        List<ProductCategoryDO> all = categoryMapper.selectList(new LambdaQueryWrapper<ProductCategoryDO>()
                .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductCategoryDO::getDeletedAt)
                .orderByAsc(ProductCategoryDO::getSortOrder)
                .orderByAsc(ProductCategoryDO::getId));
        Map<Long, Long> counts = toCountMap(all.isEmpty() ? List.of()
                : productMapper.countByCategoryIds(all.stream().map(ProductCategoryDO::getId).toList()));
        Map<Long, List<CategoryVO>> children = new HashMap<>(all.size() * 2);
        List<CategoryVO> roots = new ArrayList<>();
        for (ProductCategoryDO c : all) {
            CategoryVO vo = toVO(c, counts.getOrDefault(c.getId(), 0L));
            if (c.getParentId() == null) {
                vo.setChildren(children.computeIfAbsent(c.getId(), k -> new ArrayList<>()));
                roots.add(vo);
            } else {
                children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(vo);
            }
        }
        return roots;
    }

    /**
     * 上级品类校验：为空表示一级；否则上级必须是未删除的一级品类，且自己不能有细分品类（最多两级）。
     * current 为空表示新建。
     */
    private Long validParent(Long parentId, ProductCategoryDO current) {
        if (parentId == null) {
            return null;
        }
        if (current != null && parentId.equals(current.getId())) {
            throw BizException.of(ProductErrorCodes.CATEGORY_LEVEL_INVALID, "品类不能以自己为上级");
        }
        ProductCategoryDO parent = getActive(parentId);
        if (parent.getParentId() != null) {
            throw BizException.of(ProductErrorCodes.CATEGORY_LEVEL_INVALID, "品类最多两级，不能在细分品类下再建品类");
        }
        if (current != null && countChildren(current.getId()) > 0) {
            throw BizException.of(ProductErrorCodes.CATEGORY_LEVEL_INVALID, "该品类下还有细分品类，不能挂到其他品类下");
        }
        return parentId;
    }

    private static String validNameZh(String raw, Long parentId) {
        return parentId != null
                ? TextRules.required(raw, "中文名称", NAME_ZH_MAX)
                : TextRules.optional(raw, "中文名称", NAME_ZH_MAX);
    }

    private long countChildren(Long id) {
        return categoryMapper.selectCount(new LambdaQueryWrapper<ProductCategoryDO>()
                .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductCategoryDO::getParentId, id)
                .isNull(ProductCategoryDO::getDeletedAt));
    }

    private ProductCategoryDO getActive(Long id) {
        ProductCategoryDO category = id == null ? null : categoryMapper.selectOne(
                new LambdaQueryWrapper<ProductCategoryDO>()
                        .eq(ProductCategoryDO::getId, id)
                        .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .isNull(ProductCategoryDO::getDeletedAt));
        if (category == null) {
            throw BizException.of(ProductErrorCodes.CATEGORY_NOT_FOUND, "品类不存在");
        }
        return category;
    }

    private static String validCode(String raw) {
        String code = TextRules.required(raw, "品类编码", CODE_MAX);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID,
                    "品类编码格式不合法，须以小写字母开头，只含小写字母、数字和下划线");
        }
        return code;
    }

    /** 编码唯一性检查包含已软删除的行（唯一键也包含它们） */
    private void assertCodeFree(String code, Long excludeId) {
        ProductCategoryDO existing = categoryMapper.selectOne(new LambdaQueryWrapper<ProductCategoryDO>()
                .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductCategoryDO::getCategoryCode, code)
                .ne(excludeId != null, ProductCategoryDO::getId, excludeId)
                .last("LIMIT 1"));
        if (existing != null) {
            throw duplicate(existing.getId(), existing.getDeletedAt() != null, null);
        }
    }

    private BizException duplicate(Long existingId, boolean deleted, Throwable cause) {
        Map<String, Object> detail = new HashMap<>(4);
        detail.put("existingId", existingId);
        detail.put("deleted", deleted);
        BizException e = BizException.of(ProductErrorCodes.CATEGORY_DUPLICATE,
                deleted ? "同编码品类曾被删除，不能重复创建" : "品类编码已存在", detail);
        if (cause != null) {
            e.initCause(cause);
        }
        return e;
    }

    private long countProducts(Long categoryId) {
        return productMapper.selectCount(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductDO::getCategoryId, categoryId)
                .isNull(ProductDO::getDeletedAt));
    }

    private static Map<Long, Long> toCountMap(Collection<IdCount> rows) {
        Map<Long, Long> map = new HashMap<>(rows.size() * 2);
        for (IdCount row : rows) {
            map.put(row.getId(), row.getCnt());
        }
        return map;
    }

    private static CategoryVO toVO(ProductCategoryDO category, long productCount) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setCategoryCode(category.getCategoryCode());
        vo.setCategoryName(category.getCategoryName());
        vo.setCategoryNameZh(category.getCategoryNameZh());
        vo.setParentId(category.getParentId());
        vo.setDescription(category.getDescription());
        vo.setSortOrder(category.getSortOrder());
        vo.setStatus(category.getStatus());
        vo.setProductCount(productCount);
        vo.setCreateBy(category.getCreateBy());
        vo.setCreateTime(category.getCreateTime());
        vo.setUpdateBy(category.getUpdateBy());
        vo.setUpdateTime(category.getUpdateTime());
        return vo;
    }
}

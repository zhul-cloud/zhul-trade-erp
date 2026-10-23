package com.zhul.erp.modules.product.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 品类匹配（spec product/product-candidate「商品候选池」）：品类文字与品类中文名、英文名、编码比较，依次按
 * ①精确相等（两级都比）②一级品类名包含 ③细分品类名包含 三轮，哪一轮有结果就用哪一轮。一轮的命中按所属一级归并：
 * 多个一级为「无法判断」；一个一级时恰好命中一个细分取细分，否则取一级；第②轮命中一级后，再在它的细分里按第③轮的方式找，唯一则细化。
 * 商品候选、询盘确认、询价任务分配规则共用；「其他」兜底由调用方决定。
 */
@Component
@RequiredArgsConstructor
public class CategoryMatcher {

    /** 细分品类名短于这个长度时，只做精确匹配或「品类名包含品类文字」，不做「品类文字包含品类名」，避免「附件」「光电」误命中 */
    private static final int MIN_CONTAINED_NAME = 3;

    private final ProductCategoryMapper categoryMapper;
    private final IndustryScope industryScope;

    /**
     * 匹配结果
     *
     * @param categoryId 命中的品类（细分或一级），没命中或无法判断时为空
     * @param ambiguous  同一轮命中多个一级品类
     */
    public record Result(Long categoryId, boolean ambiguous) {
        static final Result NONE = new Result(null, false);
        static final Result AMBIGUOUS = new Result(null, true);
    }

    /** 启用、未删除的平台品类；租户账号只返回本租户行业的（spec product/industry） */
    public List<ProductCategoryDO> enabledCategories() {
        java.util.Set<Long> visible = industryScope.visibleCategoryIds();
        List<ProductCategoryDO> all = categoryMapper.selectList(new LambdaQueryWrapper<ProductCategoryDO>()
                .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductCategoryDO::getStatus, ProductConstants.STATUS_ENABLED)
                .isNull(ProductCategoryDO::getDeletedAt));
        return visible == null ? all : all.stream().filter(c -> visible.contains(c.getId())).toList();
    }

    public static Result match(String text, List<ProductCategoryDO> categories) {
        String t = BrandResolver.key(text);
        if (t.isEmpty()) {
            return Result.NONE;
        }
        Map<Long, ProductCategoryDO> byId = categories.stream()
                .collect(Collectors.toMap(ProductCategoryDO::getId, Function.identity(), (a, b) -> a));
        for (int round = 0; round < 3; round++) {
            Set<ProductCategoryDO> hits = new LinkedHashSet<>();
            for (ProductCategoryDO c : categories) {
                boolean top = c.getParentId() == null;
                if (round == 1 && !top || round == 2 && top) {
                    continue;
                }
                if (round == 0 ? equalsAnyName(c, t) : contains(c, t)) {
                    hits.add(c);
                }
            }
            if (hits.isEmpty()) {
                continue;
            }
            Set<Long> roots = new LinkedHashSet<>();
            for (ProductCategoryDO c : hits) {
                ProductCategoryDO root = c.getParentId() == null ? c : byId.get(c.getParentId());
                if (root != null && root.getParentId() == null) {
                    roots.add(root.getId());
                }
            }
            if (roots.size() > 1) {
                return Result.AMBIGUOUS;
            }
            if (roots.isEmpty()) {
                continue;
            }
            Long rootId = roots.iterator().next();
            List<ProductCategoryDO> subs = round == 1
                    ? categories.stream().filter(c -> rootId.equals(c.getParentId()) && contains(c, t)).toList()
                    : hits.stream().filter(c -> c.getParentId() != null).toList();
            return new Result(subs.size() == 1 ? subs.get(0).getId() : rootId, false);
        }
        return Result.NONE;
    }

    /** 品类所属的一级品类：一级返回自身，细分返回上级；品类不在清单里返回空 */
    public static Long rootOf(Long categoryId, List<ProductCategoryDO> categories) {
        if (categoryId == null) {
            return null;
        }
        for (ProductCategoryDO c : categories) {
            if (categoryId.equals(c.getId())) {
                return c.getParentId() == null ? c.getId() : c.getParentId();
            }
        }
        return null;
    }

    private static boolean equalsAnyName(ProductCategoryDO c, String t) {
        for (String n : names(c)) {
            if (n.equals(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(ProductCategoryDO c, String t) {
        boolean top = c.getParentId() == null;
        for (String n : names(c)) {
            if (n.contains(t) || (top || n.length() >= MIN_CONTAINED_NAME) && t.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> names(ProductCategoryDO c) {
        return Stream.of(c.getCategoryNameZh(), c.getCategoryName(), c.getCategoryCode())
                .map(BrandResolver::key).filter(n -> !n.isEmpty()).collect(Collectors.toList());
    }

    /** 兜底品类「其他」的 ID，不存在时为空 */
    public static Long otherId(List<ProductCategoryDO> categories, String otherCode) {
        return categories.stream().filter(c -> c.getParentId() == null && Objects.equals(otherCode, c.getCategoryCode()))
                .map(ProductCategoryDO::getId).findFirst().orElse(null);
    }
}

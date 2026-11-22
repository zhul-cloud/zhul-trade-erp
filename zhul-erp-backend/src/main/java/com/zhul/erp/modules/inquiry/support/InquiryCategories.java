package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.support.CategoryMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 询盘型号与品类表的关联（spec inquiry/inquiry-intake「AI 解析」「解析确认与历史价复用」、inquiry/sourcing-task「生成询价任务」）：
 * 按品类 ID、品类编码、品类文字匹配的顺序关联启用的品类；询价任务按「品牌 + 一级品类」分组，没关联上品类的按品类文字分组。
 */
@Component
@RequiredArgsConstructor
public class InquiryCategories {

    private final CategoryMatcher categoryMatcher;

    /**
     * 关联结果
     *
     * @param categoryId 关联的品类，关联不上时为空
     * @param text       品类文字：关联上时为品类中文名，否则为原文
     */
    public record Linked(Long categoryId, String text) {
    }

    public List<ProductCategoryDO> categories() {
        return categoryMatcher.enabledCategories();
    }

    public static Linked link(Long categoryId, String categoryCode, String text, List<ProductCategoryDO> categories) {
        ProductCategoryDO c = byId(categoryId, categories);
        if (c == null && StringUtils.hasText(categoryCode)) {
            String code = categoryCode.trim();
            c = categories.stream().filter(x -> code.equalsIgnoreCase(x.getCategoryCode())).findFirst().orElse(null);
        }
        if (c == null) {
            c = byId(CategoryMatcher.match(text, categories).categoryId(), categories);
        }
        return c == null ? new Linked(null, text == null ? "" : text.trim()) : new Linked(c.getId(), name(c));
    }

    /** 分组键：品牌 + 一级品类；品类不在启用清单里时按品类文字 */
    public static String groupKey(String brandKey, Long categoryId, String text, List<ProductCategoryDO> categories) {
        Long root = CategoryMatcher.rootOf(categoryId, categories);
        return brandKey + (root != null ? "|c:" + root : "|t:" + (text == null ? "" : text.trim()));
    }

    /** 一级品类（细分取上级），不在启用清单里时为空 */
    public static ProductCategoryDO root(Long categoryId, List<ProductCategoryDO> categories) {
        return byId(CategoryMatcher.rootOf(categoryId, categories), categories);
    }

    /**
     * 型号的一级品类：有品类 ID 时取它的一级；没有时按品类文字与品类中文名或英文名精确匹配（不分大小写），
     * 匹配不上为空。无货分析与询价记录的品类筛选共用这一条规则。
     */
    public static Long rootOfItem(Long categoryId, String text, List<ProductCategoryDO> categories) {
        if (categoryId != null) {
            return CategoryMatcher.rootOf(categoryId, categories);
        }
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String t = text.trim();
        return categories.stream()
                .filter(c -> t.equalsIgnoreCase(c.getCategoryNameZh()) || t.equalsIgnoreCase(c.getCategoryName()))
                .findFirst().map(c -> CategoryMatcher.rootOf(c.getId(), categories)).orElse(null);
    }

    /** 一级品类连同细分的全部 ID 与名称（中文、英文），用于按一级品类筛选型号 */
    public static List<ProductCategoryDO> under(Long rootId, List<ProductCategoryDO> categories) {
        return categories.stream().filter(c -> rootId.equals(CategoryMatcher.rootOf(c.getId(), categories))).toList();
    }

    public static String name(ProductCategoryDO c) {
        return StringUtils.hasText(c.getCategoryNameZh()) ? c.getCategoryNameZh() : c.getCategoryName();
    }

    private static ProductCategoryDO byId(Long id, List<ProductCategoryDO> categories) {
        return id == null ? null : categories.stream().filter(c -> id.equals(c.getId())).findFirst().orElse(null);
    }
}

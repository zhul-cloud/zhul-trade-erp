package com.zhul.erp.modules.website.support;

import org.springframework.util.StringUtils;

import java.util.Map;

/** 品类对照的取值规则（spec website-site「品类对照」）：二级品类有对照用二级的，没有用一级的，都没有为 null */
public final class CategoryResolver {

    private CategoryResolver() {
    }

    /**
     * @param map        站点的对照：ERP 品类 id → 站点品类代码
     * @param categoryId 商品的品类（可能是一级或二级）
     * @param parentId   这个品类的上级 id；一级品类为 null
     */
    public static String resolve(Map<Long, String> map, Long categoryId, Long parentId) {
        if (categoryId == null) {
            return null;
        }
        String own = map.get(categoryId);
        if (StringUtils.hasText(own)) {
            return own;
        }
        String parent = parentId == null ? null : map.get(parentId);
        return StringUtils.hasText(parent) ? parent : null;
    }
}

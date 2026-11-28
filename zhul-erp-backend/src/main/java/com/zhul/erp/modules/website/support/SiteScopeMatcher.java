package com.zhul.erp.modules.website.support;

import com.zhul.erp.modules.website.dto.SiteScope;

import java.util.List;

/**
 * 站点商品范围（spec website-site「站点」）：按品牌、品类（含下级）筛选；某一项为空表示这一项不限，都为空表示全部商品。
 * 品牌和品类都设置时两项都要满足。
 */
public final class SiteScopeMatcher {

    private SiteScopeMatcher() {
    }

    /**
     * @param categoryId       商品的品类
     * @param categoryParentId 商品品类的上级（一级品类为 null）
     */
    public static boolean matches(SiteScope scope, Long brandId, Long categoryId, Long categoryParentId) {
        if (scope == null) {
            return true;
        }
        List<Long> brands = scope.brandIds() == null ? List.of() : scope.brandIds();
        List<Long> categories = scope.categoryIds() == null ? List.of() : scope.categoryIds();
        if (!brands.isEmpty() && (brandId == null || !brands.contains(brandId))) {
            return false;
        }
        if (categories.isEmpty()) {
            return true;
        }
        return (categoryId != null && categories.contains(categoryId))
                || (categoryParentId != null && categories.contains(categoryParentId));
    }
}

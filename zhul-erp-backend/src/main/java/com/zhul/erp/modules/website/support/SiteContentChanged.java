package com.zhul.erp.modules.website.support;

import java.util.Collection;
import java.util.Set;

/**
 * 可能影响独立站已发布商品的变化（spec website-publish「同步更新」）：事务提交后由 SiteOutdatedService 重算「内容有更新」。
 * 按商品（商品库是共享的，各公司的任务都重算）、按型号素材键（当前公司）或按站点（当前公司）三选一。
 */
public record SiteContentChanged(Set<Long> productIds, Set<String> assetKeys, Long siteId) {

    public static SiteContentChanged products(Collection<Long> ids) {
        return new SiteContentChanged(Set.copyOf(ids), Set.of(), null);
    }

    public static SiteContentChanged product(Long id) {
        return products(Set.of(id));
    }

    public static SiteContentChanged assets(String assetKey) {
        return new SiteContentChanged(Set.of(), Set.of(assetKey), null);
    }

    public static SiteContentChanged site(Long siteId) {
        return new SiteContentChanged(Set.of(), Set.of(), siteId);
    }
}

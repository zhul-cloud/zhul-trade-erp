package com.zhul.erp.modules.product.support;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 单元测试用：不限行业、不读数据库的行业范围；新建时的默认行业为工业控制 */
public class UnlimitedIndustryScope extends IndustryScope {

    public static final String DEFAULT = "INDUSTRIAL_CONTROL";

    public UnlimitedIndustryScope() {
        super(null, null, null, null);
    }

    @Override
    public String currentIndustry() {
        return null;
    }

    @Override
    public Set<Long> visibleBrandIds() {
        return null;
    }

    @Override
    public Set<Long> visibleCategoryIds() {
        return null;
    }

    @Override
    public Map<Long, List<String>> industriesOfBrands(Collection<Long> brandIds) {
        return Map.of();
    }

    @Override
    public Map<String, String> enabledIndustries() {
        return Map.of(DEFAULT, "工业控制");
    }

    @Override
    public void replaceBrandIndustries(Long brandId, Collection<String> codes) {
        // 单元测试不写行业关系
    }
}

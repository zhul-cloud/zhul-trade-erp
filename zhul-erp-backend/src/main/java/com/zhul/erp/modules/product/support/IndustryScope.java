package com.zhul.erp.modules.product.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.entity.ProductBrandIndustryDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.repository.ProductBrandIndustryMapper;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.system.entity.DictItemDO;
import com.zhul.erp.modules.system.repository.DictItemMapper;
import com.zhul.erp.modules.tenant.entity.TenantDO;
import com.zhul.erp.modules.tenant.repository.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品主数据的行业范围（spec product/industry）：租户账号只读到本租户行业的品牌、品类、系列与商品。
 * 一级品类带行业、细分品类跟随上级；品牌与行业多对多；系列跟随品牌，商品跟随一级品类。
 * 平台账号、没有租户上下文（后台任务）、租户行业为空时不限制，返回 null。
 */
@Component
@RequiredArgsConstructor
public class IndustryScope {

    /** 行业字典类型 */
    public static final String DICT_TYPE = "industry";

    private final TenantMapper tenantMapper;
    private final DictItemMapper dictItemMapper;
    private final ProductBrandIndustryMapper brandIndustryMapper;
    private final ProductCategoryMapper categoryMapper;

    /** 当前账号所属租户的行业；不限制时返回 null */
    public String currentIndustry() {
        Integer tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId == ProductConstants.PLATFORM_TENANT_ID) {
            return null;
        }
        TenantDO tenant = tenantMapper.selectById(tenantId);
        return tenant == null || !StringUtils.hasText(tenant.getIndustryCode()) ? null : tenant.getIndustryCode();
    }

    /** 当前账号可见的品牌 ID；不限制时返回 null */
    public Set<Long> visibleBrandIds() {
        String industry = currentIndustry();
        return industry == null ? null : brandIdsOf(industry);
    }

    /** 当前账号可见的品类 ID（本行业的一级品类及其细分品类，含停用、已删除的，便于直接用作过滤条件）；不限制时返回 null */
    public Set<Long> visibleCategoryIds() {
        String industry = currentIndustry();
        return industry == null ? null : categoryIdsOf(industry);
    }

    public boolean brandVisible(Long brandId) {
        Set<Long> ids = visibleBrandIds();
        return ids == null || brandId != null && ids.contains(brandId);
    }

    public Set<Long> brandIdsOf(String industry) {
        return brandIndustryMapper.selectList(new LambdaQueryWrapper<ProductBrandIndustryDO>()
                        .select(ProductBrandIndustryDO::getBrandId)
                        .eq(ProductBrandIndustryDO::getIndustryCode, industry))
                .stream().map(ProductBrandIndustryDO::getBrandId).collect(Collectors.toSet());
    }

    public Set<Long> categoryIdsOf(String industry) {
        List<ProductCategoryDO> all = categoryMapper.selectList(new LambdaQueryWrapper<ProductCategoryDO>()
                .select(ProductCategoryDO::getId, ProductCategoryDO::getParentId, ProductCategoryDO::getIndustryCode)
                .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID));
        Set<Long> roots = all.stream().filter(c -> c.getParentId() == null && industry.equals(c.getIndustryCode()))
                .map(ProductCategoryDO::getId).collect(Collectors.toSet());
        Set<Long> ids = new HashSet<>(roots);
        for (ProductCategoryDO c : all) {
            if (c.getParentId() != null && roots.contains(c.getParentId())) {
                ids.add(c.getId());
            }
        }
        return ids;
    }

    /** 品牌所属行业，按品牌分组 */
    public Map<Long, List<String>> industriesOfBrands(Collection<Long> brandIds) {
        if (brandIds == null || brandIds.isEmpty()) {
            return Map.of();
        }
        return brandIndustryMapper.selectList(new LambdaQueryWrapper<ProductBrandIndustryDO>()
                        .in(ProductBrandIndustryDO::getBrandId, brandIds)
                        .orderByAsc(ProductBrandIndustryDO::getId))
                .stream().collect(Collectors.groupingBy(ProductBrandIndustryDO::getBrandId,
                        Collectors.mapping(ProductBrandIndustryDO::getIndustryCode, Collectors.toList())));
    }

    /** 启用的行业：编码 → 名称，按排序 */
    public Map<String, String> enabledIndustries() {
        Map<String, String> result = new LinkedHashMap<>();
        for (DictItemDO item : dictItemMapper.selectList(new LambdaQueryWrapper<DictItemDO>()
                .eq(DictItemDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(DictItemDO::getDictType, DICT_TYPE)
                .eq(DictItemDO::getStatus, ProductConstants.STATUS_ENABLED)
                .isNull(DictItemDO::getDeletedAt)
                .orderByAsc(DictItemDO::getSortOrder))) {
            result.put(item.getItemCode(), item.getItemName());
        }
        return result;
    }

    /** 校验单个行业编码：必填且是启用的行业 */
    public String requireIndustry(String code) {
        if (!StringUtils.hasText(code)) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "请选择行业");
        }
        String trimmed = code.trim();
        if (!enabledIndustries().containsKey(trimmed)) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "行业不存在或已停用");
        }
        return trimmed;
    }

    /** 传了就校验；没传时用默认行业（排第一的启用行业） */
    public String industryOrDefault(String code) {
        if (StringUtils.hasText(code)) {
            return requireIndustry(code);
        }
        return enabledIndustries().keySet().stream().findFirst()
                .orElseThrow(() -> BizException.of(ProductErrorCodes.PARAM_INVALID, "请选择行业"));
    }

    /** 校验行业编码列表：至少一个，都是启用的行业，去重保序 */
    public List<String> requireIndustries(Collection<String> codes) {
        if (codes == null || codes.stream().noneMatch(StringUtils::hasText)) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "请选择行业");
        }
        return codes.stream().filter(StringUtils::hasText).map(this::requireIndustry).distinct().toList();
    }

    /** 整体替换品牌的行业 */
    public void replaceBrandIndustries(Long brandId, Collection<String> codes) {
        brandIndustryMapper.delete(new LambdaQueryWrapper<ProductBrandIndustryDO>()
                .eq(ProductBrandIndustryDO::getBrandId, brandId));
        for (String code : codes) {
            ProductBrandIndustryDO row = new ProductBrandIndustryDO();
            row.setBrandId(brandId);
            row.setIndustryCode(code);
            brandIndustryMapper.insert(row);
        }
    }
}

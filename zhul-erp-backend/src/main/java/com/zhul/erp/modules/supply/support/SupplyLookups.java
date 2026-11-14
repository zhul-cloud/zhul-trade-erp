package com.zhul.erp.modules.supply.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.system.entity.DictItemDO;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.repository.DictItemMapper;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 供应链用到的字典、系统设置、品牌品类名称；都在当前租户上下文里读 */
@Component
@RequiredArgsConstructor
public class SupplyLookups {

    private final SysConfigMapper sysConfigMapper;
    private final DictItemMapper dictItemMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductMapper productMapper;

    /** 供应证据有效天数：租户设置优先，其次平台模板，没配或填错时 30 */
    public int evidenceDays() {
        String v = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigDO>()
                        .eq(SysConfigDO::getConfigKey, SupplyConstants.CFG_EVIDENCE_DAYS)
                        .in(SysConfigDO::getTenantId, List.of(0, PiStore.tenantId()))
                        .isNull(SysConfigDO::getDeletedAt))
                .stream().max(Comparator.comparing(SysConfigDO::getTenantId)).map(SysConfigDO::getConfigValue).orElse(null);
        try {
            int n = v == null ? 0 : Integer.parseInt(v.trim());
            return n > 0 ? n : SupplyConstants.DEFAULT_EVIDENCE_DAYS;
        } catch (NumberFormatException e) {
            return SupplyConstants.DEFAULT_EVIDENCE_DAYS;
        }
    }

    /** 字典 编码或值 → 名称，租户自己的覆盖平台的 */
    public Map<String, String> dict(String type, boolean byValue) {
        Map<String, String> out = new HashMap<>(32);
        dictItemMapper.selectList(new LambdaQueryWrapper<DictItemDO>()
                        .eq(DictItemDO::getDictType, type)
                        .isNull(DictItemDO::getDeletedAt))
                .stream().sorted(Comparator.comparing(DictItemDO::getTenantId))
                .forEach(d -> out.put(byValue ? d.getItemValue() : d.getItemCode(), d.getItemName()));
        return out;
    }

    /** 属于「交期/物流」分类的未成交原因编码；至少返回一个元素，便于直接放进 SQL IN */
    public List<String> supplyLostCodes() {
        List<String> out = new ArrayList<>(8);
        dict("quotation_lost_reason", false).forEach((code, name) -> {
            if (name != null && name.startsWith(SupplyConstants.LOST_SUPPLY_PREFIX)) {
                out.add(code);
            }
        });
        if (out.isEmpty()) {
            out.add("");
        }
        return out;
    }

    /** 全部品类：id → 上级 id（一级品类为 null） */
    public Map<Long, Long> categoryParents() {
        Map<Long, Long> out = new HashMap<>(256);
        categoryMapper.selectList(new LambdaQueryWrapper<ProductCategoryDO>()
                        .select(ProductCategoryDO::getId, ProductCategoryDO::getParentId)
                        .isNull(ProductCategoryDO::getDeletedAt))
                .forEach(c -> out.put(c.getId(), c.getParentId()));
        return out;
    }

    /** 是否细分（二级）品类 */
    public boolean isLevel2(Long id) {
        if (id == null || id <= 0) {
            return false;
        }
        ProductCategoryDO c = categoryMapper.selectById(id);
        return c != null && c.getDeletedAt() == null && c.getParentId() != null;
    }

    /** 品类显示名：细分品类取中文名，没有时取英文名 */
    public Map<Long, String> categoryNames(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>(32);
        List<Long> list = ids.stream().filter(i -> i != null && i > 0).distinct().toList();
        if (list.isEmpty()) {
            return out;
        }
        categoryMapper.selectBatchIds(list).forEach(c -> out.put(c.getId(),
                c.getCategoryNameZh() != null && !c.getCategoryNameZh().isBlank() ? c.getCategoryNameZh() : c.getCategoryName()));
        return out;
    }

    public Map<Long, String> brandNames(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>(32);
        List<Long> list = ids.stream().filter(i -> i != null && i > 0).distinct().toList();
        if (list.isEmpty()) {
            return out;
        }
        brandMapper.selectBatchIds(list).forEach(b -> out.put(b.getId(), b.getBrandName()));
        return out;
    }

    /** 商品档案的品类 */
    public Map<Long, Long> productCategories(Collection<Long> productIds) {
        Map<Long, Long> out = new HashMap<>(64);
        List<Long> list = productIds.stream().filter(i -> i != null && i > 0).distinct().toList();
        for (int from = 0; from < list.size(); from += 500) {
            productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                            .select(ProductDO::getId, ProductDO::getCategoryId)
                            .in(ProductDO::getId, list.subList(from, Math.min(list.size(), from + 500))))
                    .forEach(p -> {
                        if (p.getCategoryId() != null) {
                            out.put(p.getId(), p.getCategoryId());
                        }
                    });
        }
        return out;
    }

    /** 品牌是否存在（未删除） */
    public boolean brandExists(Long brandId) {
        if (brandId == null) {
            return false;
        }
        ProductBrandDO b = brandMapper.selectById(brandId);
        return b != null && b.getDeletedAt() == null;
    }
}

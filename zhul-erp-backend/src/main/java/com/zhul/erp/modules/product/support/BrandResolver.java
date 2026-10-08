package com.zhul.erp.modules.product.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.entity.ProductBrandAliasDO;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.repository.ProductBrandAliasMapper;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 把品牌的各种写法归一到正式品牌：先按品牌名称，再按别名比较，都忽略大小写和首尾空格。
 * 供应商主营产品、询盘快速创建、存量迁移、待确认品牌确认都用它；停用的品牌同样能解析（它仍是真实品牌），已删除的不能。
 */
@Component
@RequiredArgsConstructor
public class BrandResolver {

    private final ProductBrandMapper brandMapper;
    private final ProductBrandAliasMapper aliasMapper;

    /** 比较键：去首尾空格、转小写 */
    public static String key(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    /** 返回正式品牌 ID；匹配不到返回 null */
    public Long resolve(String text) {
        String key = key(text);
        if (!StringUtils.hasText(key)) {
            return null;
        }
        // brand_name 列的排序规则不区分大小写，直接按去空格后的原文比较即可
        ProductBrandDO brand = brandMapper.selectOne(new LambdaQueryWrapper<ProductBrandDO>()
                .eq(ProductBrandDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductBrandDO::getBrandName, text.trim())
                .isNull(ProductBrandDO::getDeletedAt)
                .last("LIMIT 1"));
        if (brand != null) {
            return brand.getId();
        }
        ProductBrandAliasDO alias = aliasMapper.selectOne(new LambdaQueryWrapper<ProductBrandAliasDO>()
                .eq(ProductBrandAliasDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductBrandAliasDO::getAliasKey, key)
                .last("LIMIT 1"));
        if (alias == null) {
            return null;
        }
        ProductBrandDO owner = brandMapper.selectById(alias.getBrandId());
        return owner == null || owner.getDeletedAt() != null ? null : owner.getId();
    }

    /**
     * 发给客户的单据用的品牌名：按品牌名称或别名匹配到正式品牌时返回正式品牌名（英文，如「西门子」→ Siemens），
     * 匹配不到时返回原文。批量查询，返回 原文 → 输出名。
     */
    public Map<String, String> displayNames(Collection<String> texts) {
        Map<String, String> result = new HashMap<>();
        Map<String, String> byKey = new HashMap<>();
        for (String t : texts) {
            if (t != null) {
                result.put(t, t);
                if (StringUtils.hasText(t)) {
                    byKey.putIfAbsent(key(t), t);
                }
            }
        }
        if (byKey.isEmpty()) {
            return result;
        }
        Map<String, String> nameOfKey = new HashMap<>();
        brandMapper.selectList(new LambdaQueryWrapper<ProductBrandDO>()
                        .select(ProductBrandDO::getId, ProductBrandDO::getBrandName)
                        .eq(ProductBrandDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .in(ProductBrandDO::getBrandName, byKey.values().stream().map(String::trim).toList())
                        .isNull(ProductBrandDO::getDeletedAt))
                .forEach(b -> nameOfKey.put(key(b.getBrandName()), b.getBrandName()));
        List<String> rest = byKey.keySet().stream().filter(k -> !nameOfKey.containsKey(k)).toList();
        if (!rest.isEmpty()) {
            List<ProductBrandAliasDO> aliases = aliasMapper.selectList(new LambdaQueryWrapper<ProductBrandAliasDO>()
                    .select(ProductBrandAliasDO::getBrandId, ProductBrandAliasDO::getAliasKey)
                    .eq(ProductBrandAliasDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                    .in(ProductBrandAliasDO::getAliasKey, rest));
            if (!aliases.isEmpty()) {
                Map<Long, String> brands = new HashMap<>();
                brandMapper.selectBatchIds(aliases.stream().map(ProductBrandAliasDO::getBrandId).distinct().toList()).stream()
                        .filter(b -> b.getDeletedAt() == null)
                        .forEach(b -> brands.put(b.getId(), b.getBrandName()));
                aliases.forEach(a -> {
                    String name = brands.get(a.getBrandId());
                    if (name != null) {
                        nameOfKey.putIfAbsent(a.getAliasKey(), name);
                    }
                });
            }
        }
        for (Map.Entry<String, String> e : result.entrySet()) {
            String name = nameOfKey.get(key(e.getKey()));
            if (name != null && !Objects.equals(name, e.getKey())) {
                e.setValue(name);
            }
        }
        return result;
    }
}

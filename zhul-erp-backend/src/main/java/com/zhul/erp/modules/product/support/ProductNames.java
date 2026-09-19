package com.zhul.erp.modules.product.support;

import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductSeriesDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.repository.ProductSeriesMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 一批商品的品牌、品类、系列名称，用三次批量查询取回（不逐个商品查询）。
 * 品牌、品类、系列即使已被停用或删除也照常返回，历史商品仍要显示它们的名称。
 */
@Component
@RequiredArgsConstructor
public class ProductNames {

    private final ProductBrandMapper brandMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductSeriesMapper seriesMapper;

    public record Lookup(Map<Long, ProductBrandDO> brands, Map<Long, ProductCategoryDO> categories,
                         Map<Long, ProductSeriesDO> series) {
    }

    public Lookup load(Collection<ProductDO> products) {
        if (products.isEmpty()) {
            return new Lookup(Map.of(), Map.of(), Map.of());
        }
        Set<Long> brandIds = ids(products, ProductDO::getBrandId);
        Set<Long> categoryIds = ids(products, ProductDO::getCategoryId);
        Set<Long> seriesIds = ids(products, ProductDO::getSeriesId);
        Map<Long, ProductBrandDO> brands = brandMapper.selectBatchIds(brandIds).stream()
                .collect(Collectors.toMap(ProductBrandDO::getId, Function.identity()));
        Map<Long, ProductCategoryDO> categories = categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(ProductCategoryDO::getId, Function.identity()));
        Map<Long, ProductSeriesDO> series = seriesIds.isEmpty() ? new HashMap<>()
                : seriesMapper.selectBatchIds(seriesIds).stream()
                .collect(Collectors.toMap(ProductSeriesDO::getId, Function.identity()));
        return new Lookup(brands, categories, series);
    }

    private static Set<Long> ids(Collection<ProductDO> products, Function<ProductDO, Long> getter) {
        return products.stream().map(getter).filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
    }
}

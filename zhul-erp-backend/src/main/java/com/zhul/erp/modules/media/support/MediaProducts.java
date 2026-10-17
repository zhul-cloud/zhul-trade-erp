package com.zhul.erp.modules.media.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.media.service.MediaIndexer;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** 素材的型号（品牌 + 型号）与共享商品库商品的对应：品牌按名称与别名解析，型号按归一化规则匹配 */
@Component
@RequiredArgsConstructor
public class MediaProducts {

    /** 共享商品库的租户 */
    private static final int SHARED = 0;

    private final BrandResolver brandResolver;
    private final ProductMapper productMapper;
    private final ProductBrandMapper brandMapper;

    public Optional<ProductDO> find(String brand, String model) {
        Long brandId = brandResolver.resolve(brand);
        if (brandId == null || model == null || model.isBlank()) {
            return Optional.empty();
        }
        return productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                        .eq(ProductDO::getTenantId, SHARED)
                        .eq(ProductDO::getBrandId, brandId)
                        .eq(ProductDO::getMpnNormalized, MpnNormalizer.normalize(model))
                        .isNull(ProductDO::getDeletedAt)
                        .last("LIMIT 1")).stream().findFirst();
    }

    /** 批量：型号归并键 → 商品（找不到的不在结果里） */
    public Map<String, ProductDO> findAll(Collection<MediaIndexer.ModelRef> models) {
        Map<String, ProductDO> out = new HashMap<>();
        Map<Long, Map<String, String>> byBrand = new HashMap<>();
        Map<String, Long> brandIds = new HashMap<>();
        for (MediaIndexer.ModelRef m : models) {
            Long brandId = brandIds.computeIfAbsent(BrandResolver.key(m.brand()), k -> brandResolver.resolve(m.brand()));
            if (brandId != null && m.model() != null && !m.model().isBlank()) {
                byBrand.computeIfAbsent(brandId, k -> new HashMap<>()).put(MpnNormalizer.normalize(m.model()), m.assetKey());
            }
        }
        byBrand.forEach((brandId, mpns) -> productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                        .eq(ProductDO::getTenantId, SHARED)
                        .eq(ProductDO::getBrandId, brandId)
                        .in(ProductDO::getMpnNormalized, mpns.keySet())
                        .isNull(ProductDO::getDeletedAt))
                .forEach(p -> out.put(mpns.get(MpnNormalizer.normalize(p.getMpnNormalized())), p)));
        return out;
    }

    /** 商品 → 品牌名 */
    public Map<Long, String> brandNames(Collection<ProductDO> products) {
        List<Long> ids = products.stream().map(ProductDO::getBrandId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return brandMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(ProductBrandDO::getId, ProductBrandDO::getBrandName));
    }
}

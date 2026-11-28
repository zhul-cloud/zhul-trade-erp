package com.zhul.erp.modules.media.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.media.service.MediaIndexer;
import com.zhul.erp.modules.product.constants.ProductConstants;
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

/** 素材的型号（品牌 + 型号）与共享商品库商品的对应：品牌按名称与别名解析，型号按归一化规则匹配；没填品牌时只按型号且须唯一 */
@Component
@RequiredArgsConstructor
public class MediaProducts {

    /** 共享商品库的租户 */
    private static final int SHARED = 0;

    private final BrandResolver brandResolver;
    private final ProductMapper productMapper;
    private final ProductBrandMapper brandMapper;

    public Optional<ProductDO> find(String brand, String model) {
        if (model == null || model.isBlank()) {
            return Optional.empty();
        }
        String mpn = MpnNormalizer.normalize(model);
        if (brand == null || brand.isBlank()) {
            return Optional.ofNullable(uniqueByModel(List.of(mpn)).get(mpn));
        }
        Long brandId = brandResolver.resolve(brand);
        if (brandId == null) {
            return Optional.empty();
        }
        return productMapper.selectList(shared()
                        .eq(ProductDO::getBrandId, brandId)
                        .eq(ProductDO::getMpnNormalized, mpn)
                        .last("LIMIT 1")).stream().findFirst();
    }

    /** 批量：型号归并键 → 商品（找不到的不在结果里） */
    public Map<String, ProductDO> findAll(Collection<MediaIndexer.ModelRef> models) {
        Map<String, ProductDO> out = new HashMap<>();
        Map<Long, Map<String, String>> byBrand = new HashMap<>();
        Map<String, String> noBrand = new HashMap<>();
        Map<String, Long> brandIds = new HashMap<>();
        for (MediaIndexer.ModelRef m : models) {
            if (m.model() == null || m.model().isBlank()) {
                continue;
            }
            String mpn = MpnNormalizer.normalize(m.model());
            if (m.brand() == null || m.brand().isBlank()) {
                noBrand.put(mpn, m.assetKey());
                continue;
            }
            Long brandId = brandIds.computeIfAbsent(BrandResolver.key(m.brand()), k -> brandResolver.resolve(m.brand()));
            if (brandId != null) {
                byBrand.computeIfAbsent(brandId, k -> new HashMap<>()).put(mpn, m.assetKey());
            }
        }
        byBrand.forEach((brandId, mpns) -> productMapper.selectList(shared()
                        .eq(ProductDO::getBrandId, brandId)
                        .in(ProductDO::getMpnNormalized, mpns.keySet()))
                .forEach(p -> out.put(mpns.get(MpnNormalizer.normalize(p.getMpnNormalized())), p)));
        uniqueByModel(noBrand.keySet()).forEach((mpn, p) -> out.put(noBrand.get(mpn), p));
        return out;
    }

    /** 没填品牌的素材只按型号匹配：共享商品库里该型号恰好一个商品才算匹配，多个品牌同型号时不猜 */
    private Map<String, ProductDO> uniqueByModel(Collection<String> mpns) {
        if (mpns.isEmpty()) {
            return Map.of();
        }
        Map<String, List<ProductDO>> grouped = productMapper.selectList(shared().in(ProductDO::getMpnNormalized, mpns))
                .stream().collect(Collectors.groupingBy(p -> MpnNormalizer.normalize(p.getMpnNormalized())));
        Map<String, ProductDO> out = new HashMap<>();
        grouped.forEach((mpn, list) -> {
            if (list.size() == 1) {
                out.put(mpn, list.get(0));
            }
        });
        return out;
    }

    /** 共享商品库里已建档、未删除的商品 */
    private static LambdaQueryWrapper<ProductDO> shared() {
        return new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getArchiveStatus, ProductConstants.ARCHIVE_DONE)
                .eq(ProductDO::getTenantId, SHARED)
                .isNull(ProductDO::getDeletedAt);
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

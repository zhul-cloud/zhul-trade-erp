package com.zhul.erp.modules.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.media.dto.MediaReferenceVO;
import com.zhul.erp.modules.media.entity.MediaItemDO;
import com.zhul.erp.modules.media.repository.MediaItemMapper;
import com.zhul.erp.modules.media.support.MediaProducts;
import com.zhul.erp.modules.product.candidate.repository.ProductHeatMapper;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductMediaDO;
import com.zhul.erp.modules.product.repository.IdCount;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.repository.ProductMediaMapper;
import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 参考素材（规格「参考素材」）：型号没有可发客户的图片或视频时，按 同系列 → 同品牌同品类 → 同品牌 → 同品类其他品牌
 * 逐层找有可发客户图片或视频的型号（本公司素材，或共享商品库的官方图片），找到一层就停；层内按本公司的询盘次数、成交次数排序，最多 20 个。
 * 每层是「商品库候选 + 有素材的型号」两次查询后在内存里求交集，不跨三张以上的表联查。
 */
@Service
@RequiredArgsConstructor
public class MediaReferenceService {

    private static final int SHARED = 0;
    private static final int MAX_RESULTS = 20;
    /** 每层最多看多少个候选商品 */
    private static final int MAX_CANDIDATES = 2000;
    /** 只在同品牌里找，不回退到其他品牌 */
    private static final String[] LEVEL_NAMES = {"", "同系列", "同品牌同品类", "同品牌"};

    private final MediaProducts products;
    private final BrandResolver brandResolver;
    private final ProductMapper productMapper;
    private final ProductMediaMapper productMediaMapper;
    private final MediaItemMapper itemMapper;
    private final ProductHeatMapper heatMapper;

    public MediaReferenceVO references(String brand, String model) {
        if (!StringUtils.hasText(model)) {
            throw new BizException("请指定型号");
        }
        Optional<ProductDO> self = products.find(brand, model);
        Long brandId = self.map(ProductDO::getBrandId).orElseGet(() -> brandResolver.resolve(brand));
        Long selfId = self.map(ProductDO::getId).orElse(-1L);
        for (int level = 1; level < LEVEL_NAMES.length; level++) {
            Consumer<LambdaQueryWrapper<ProductDO>> filter = levelFilter(level, self.orElse(null), brandId);
            if (filter == null) {
                continue;
            }
            LambdaQueryWrapper<ProductDO> w = new LambdaQueryWrapper<ProductDO>()
                    .eq(ProductDO::getTenantId, SHARED)
                    .ne(ProductDO::getId, selfId)
                    .isNull(ProductDO::getDeletedAt);
            filter.accept(w);
            List<ProductDO> candidates = productMapper.selectList(w.last("LIMIT " + MAX_CANDIDATES));
            List<ProductDO> withMedia = withSendableMedia(candidates);
            if (!withMedia.isEmpty()) {
                return result(level, withMedia);
            }
        }
        MediaReferenceVO vo = new MediaReferenceVO();
        vo.setLevel(0);
        vo.setLevelName("");
        vo.setModels(List.of());
        return vo;
    }

    /** 每一层的商品库筛选条件；条件缺失（如没有系列）时跳过这一层 */
    private static Consumer<LambdaQueryWrapper<ProductDO>> levelFilter(int level, ProductDO self, Long brandId) {
        return switch (level) {
            case 1 -> self == null || self.getSeriesId() == null ? null : w -> w.eq(ProductDO::getSeriesId, self.getSeriesId());
            case 2 -> self == null || self.getCategoryId() == null ? null
                    : w -> w.eq(ProductDO::getBrandId, self.getBrandId()).eq(ProductDO::getCategoryId, self.getCategoryId());
            case 3 -> brandId == null ? null : w -> w.eq(ProductDO::getBrandId, brandId);
            default -> null;
        };
    }

    /** 候选里有可发客户的图片或视频的：本公司素材（可发客户、图片或视频）或共享商品库的官方图片 */
    private List<ProductDO> withSendableMedia(List<ProductDO> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, String> brandNames = products.brandNames(candidates);
        Map<String, ProductDO> byKey = new HashMap<>();
        for (ProductDO p : candidates) {
            byKey.put(MediaIndexer.key(brandNames.get(p.getBrandId()), p.getMpnRaw()), p);
        }
        Set<Long> hit = new HashSet<>();
        itemMapper.selectList(new LambdaQueryWrapper<MediaItemDO>()
                        .select(MediaItemDO::getAssetKey)
                        .eq(MediaItemDO::getTenantId, PiStore.tenantId())
                        .in(MediaItemDO::getAssetKey, byKey.keySet())
                        .eq(MediaItemDO::getCanSend, 1)
                        .in(MediaItemDO::getKind, 1, 2)
                        .isNull(MediaItemDO::getDeletedAt))
                .forEach(i -> hit.add(byKey.get(i.getAssetKey()).getId()));
        productMediaMapper.selectList(new LambdaQueryWrapper<ProductMediaDO>()
                        .select(ProductMediaDO::getProductId)
                        .in(ProductMediaDO::getProductId, candidates.stream().map(ProductDO::getId).toList())
                        .isNull(ProductMediaDO::getDeletedAt))
                .forEach(m -> hit.add(m.getProductId()));
        return candidates.stream().filter(p -> hit.contains(p.getId())).toList();
    }

    private MediaReferenceVO result(int level, List<ProductDO> found) {
        List<Long> ids = found.stream().map(ProductDO::getId).toList();
        Integer tenant = PiStore.tenantId();
        Map<Long, Long> inquiries = heatMapper.inquiryCounts(ids, tenant).stream()
                .collect(Collectors.toMap(IdCount::getId, IdCount::getCnt));
        Map<Long, Long> deals = heatMapper.dealCounts(ids, tenant).stream()
                .collect(Collectors.toMap(IdCount::getId, IdCount::getCnt));
        Map<Long, String> brandNames = products.brandNames(found);
        MediaReferenceVO vo = new MediaReferenceVO();
        vo.setLevel(level);
        vo.setLevelName(LEVEL_NAMES[level]);
        vo.setModels(found.stream()
                .sorted(Comparator.comparing((ProductDO p) -> inquiries.getOrDefault(p.getId(), 0L)).reversed()
                        .thenComparing(p -> deals.getOrDefault(p.getId(), 0L), Comparator.reverseOrder())
                        .thenComparing(ProductDO::getId))
                .limit(MAX_RESULTS)
                .map(p -> {
                    MediaReferenceVO.Model m = new MediaReferenceVO.Model();
                    m.setProductId(p.getId());
                    m.setBrand(brandNames.get(p.getBrandId()));
                    m.setModel(p.getMpnRaw());
                    m.setProductName(p.getProductName());
                    m.setInquiryCount(inquiries.getOrDefault(p.getId(), 0L));
                    m.setDealCount(deals.getOrDefault(p.getId(), 0L));
                    return m;
                }).toList());
        return vo;
    }
}

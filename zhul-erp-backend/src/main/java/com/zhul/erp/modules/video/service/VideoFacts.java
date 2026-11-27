package com.zhul.erp.modules.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.content.entity.ProductFactTaskDO;
import com.zhul.erp.modules.product.content.entity.ProductLocaleDO;
import com.zhul.erp.modules.product.content.entity.ProductPlatformContentDO;
import com.zhul.erp.modules.product.content.repository.ProductFactTaskMapper;
import com.zhul.erp.modules.product.content.repository.ProductLocaleMapper;
import com.zhul.erp.modules.product.content.repository.ProductPlatformContentMapper;
import com.zhul.erp.modules.product.content.support.MarketingPlatform;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 生成脚本用的商品事实：资料任务的核对状态、商品档案的规格与英文规格、社媒营销内容里的卖点与视频方案 */
@Component
@RequiredArgsConstructor
public class VideoFacts {

    private final ProductMapper productMapper;
    private final ProductFactTaskMapper factTaskMapper;
    private final ProductLocaleMapper localeMapper;
    private final ProductPlatformContentMapper platformContentMapper;

    /** 资料待补（没有资料任务、没有导入）或资料待核（没核对或核对早于导入） */
    public boolean unverified(Long productId) {
        ProductFactTaskDO f = factTaskMapper.selectOne(new LambdaQueryWrapper<ProductFactTaskDO>()
                .eq(ProductFactTaskDO::getProductId, productId)
                .isNull(ProductFactTaskDO::getDeletedAt)
                .last("LIMIT 1"));
        return f == null || f.getImportedAt() == null || f.getVerifiedAt() == null || f.getVerifiedAt().isBefore(f.getImportedAt());
    }

    /** AI 输入里的商品事实（字段为空的不放） */
    public Map<String, Object> of(Long productId, String brand, String model) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("brand", brand);
        out.put("model", model);
        ProductDO p = productMapper.selectById(productId);
        if (p != null) {
            put(out, "productName", p.getProductName());
            put(out, "shortDescription", p.getShortDescription());
            put(out, "specSummary", p.getSpecSummary());
        }
        ProductLocaleDO en = localeMapper.selectOne(new LambdaQueryWrapper<ProductLocaleDO>()
                .eq(ProductLocaleDO::getProductId, productId)
                .eq(ProductLocaleDO::getLang, "en")
                .last("LIMIT 1"));
        if (en != null) {
            put(out, "specSummaryEn", en.getSpecSummary());
        }
        ProductPlatformContentDO social = platformContentMapper.selectOne(new LambdaQueryWrapper<ProductPlatformContentDO>()
                .eq(ProductPlatformContentDO::getTenantId, TenantContext.getTenantId())
                .eq(ProductPlatformContentDO::getProductId, productId)
                .eq(ProductPlatformContentDO::getPlatform, MarketingPlatform.SOCIAL.code())
                .isNull(ProductPlatformContentDO::getDeletedAt)
                .last("LIMIT 1"));
        if (social != null) {
            put(out, "sellingPoints", social.getSellingPoints());
            put(out, "videoPlans", social.getVideos());
            put(out, "youtubeDescription", social.getYoutubeDescription());
        }
        return out;
    }

    private static void put(Map<String, Object> m, String k, String v) {
        if (v != null && !v.isBlank()) {
            m.put(k, v.trim());
        }
    }
}

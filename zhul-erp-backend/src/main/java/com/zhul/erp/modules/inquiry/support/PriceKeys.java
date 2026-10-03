package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 历史询价的匹配键，与商品主数据同一套规则（design.md 决策 3）：
 * 型号用 {@link MpnNormalizer} 归一化；品牌用 {@link BrandResolver} 按品牌及别名识别，
 * 识别到时键为「#品牌ID」，识别不到时为去首尾空格、转小写的品牌名。
 */
@Component
@RequiredArgsConstructor
public class PriceKeys {

    private final BrandResolver brandResolver;

    public record BrandRef(Long brandId, String brandKey) {
    }

    public BrandRef brand(String brand) {
        Long id = brandResolver.resolve(brand);
        return new BrandRef(id, id != null ? "#" + id : BrandResolver.key(brand));
    }

    public static String model(String model) {
        return MpnNormalizer.normalize(model);
    }
}

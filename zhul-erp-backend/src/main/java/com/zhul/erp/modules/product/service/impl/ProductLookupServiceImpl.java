package com.zhul.erp.modules.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.dto.ProductMatchVO;
import com.zhul.erp.modules.product.dto.ProductOptionVO;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.service.ProductLookupService;
import com.zhul.erp.modules.product.support.LikeUtils;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.product.support.ProductNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductLookupServiceImpl implements ProductLookupService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_CANDIDATES = 10;

    private final ProductMapper productMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductNames productNames;

    @Override
    public List<ProductOptionVO> search(String keyword, Integer limit, Long brandId) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String trimmed = keyword.trim();
        String normalized = MpnNormalizer.normalize(trimmed);
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        LambdaQueryWrapper<ProductDO> wrapper = selectable()
                .eq(brandId != null, ProductDO::getBrandId, brandId)
                .orderByAsc(ProductDO::getMpnNormalized)
                .orderByAsc(ProductDO::getId)
                .last("LIMIT " + size);
        wrapper.and(w -> {
            // 归一化后为空（如 "---"）时跳过型号分支，否则 LIKE '%' 会命中全部商品
            if (!normalized.isEmpty()) {
                w.likeRight(ProductDO::getMpnNormalized, normalized).or();
            }
            w.like(ProductDO::getProductName, LikeUtils.escape(trimmed));
        });
        return toOptions(productMapper.selectList(wrapper));
    }

    @Override
    public ProductMatchVO match(String brand, String mpn) {
        ProductMatchVO result = new ProductMatchVO();
        result.setCandidates(List.of());
        String normalized = MpnNormalizer.normalize(mpn);
        if (normalized.isEmpty()) {
            return result;
        }

        ProductDO exact = null;
        if (StringUtils.hasText(brand)) {
            // 品牌按唯一性规则比较（数据库排序规则忽略大小写，首尾空格在这里去掉）
            ProductBrandDO matchedBrand = brandMapper.selectOne(new LambdaQueryWrapper<ProductBrandDO>()
                    .eq(ProductBrandDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                    .isNull(ProductBrandDO::getDeletedAt)
                    .eq(ProductBrandDO::getBrandName, brand.trim())
                    .last("LIMIT 1"));
            if (matchedBrand != null) {
                exact = productMapper.selectOne(selectable()
                        .eq(ProductDO::getBrandId, matchedBrand.getId())
                        .eq(ProductDO::getMpnNormalized, normalized)
                        .last("LIMIT 1"));
            }
        }

        final Long exactId = exact == null ? null : exact.getId();
        // 归一化型号相同的排在前缀相同的之前：相同的字符串一定比以它为前缀的更长的字符串小
        List<ProductDO> candidates = productMapper.selectList(selectable()
                .likeRight(ProductDO::getMpnNormalized, normalized)
                .ne(exactId != null, ProductDO::getId, exactId)
                .orderByAsc(ProductDO::getMpnNormalized)
                .orderByAsc(ProductDO::getId)
                .last("LIMIT " + MAX_CANDIDATES));

        List<ProductDO> all = new ArrayList<>(candidates);
        if (exact != null) {
            all.add(exact);
        }
        List<ProductOptionVO> options = toOptions(all);
        if (exact != null) {
            result.setExact(options.get(options.size() - 1));
            options = options.subList(0, options.size() - 1);
        }
        result.setCandidates(new ArrayList<>(options));
        return result;
    }

    /** 只有启用且未删除的商品才能被业务单据选用 */
    private LambdaQueryWrapper<ProductDO> selectable() {
        return new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductDO::getDeletedAt)
                .eq(ProductDO::getStatus, ProductConstants.STATUS_ENABLED);
    }

    private List<ProductOptionVO> toOptions(List<ProductDO> products) {
        ProductNames.Lookup lookup = productNames.load(products);
        List<ProductOptionVO> options = new ArrayList<>(products.size());
        for (ProductDO product : products) {
            ProductOptionVO vo = new ProductOptionVO();
            vo.setId(product.getId());
            vo.setMpnDisplay(product.getMpnDisplay());
            ProductBrandDO brand = lookup.brands().get(product.getBrandId());
            vo.setBrandName(brand == null ? null : brand.getBrandName());
            ProductCategoryDO category = lookup.categories().get(product.getCategoryId());
            vo.setCategoryName(category == null ? null : category.getCategoryName());
            vo.setProductName(product.getProductName());
            options.add(vo);
        }
        return options;
    }
}

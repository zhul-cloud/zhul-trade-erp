package com.zhul.erp.modules.inventory.service;

import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 库存页面显示商品的型号、品牌、品类（按销售订单的显示方式：型号用商品显示型号，品类显示中文名） */
@Service
@RequiredArgsConstructor
public class InventoryProducts {

    private final ProductMapper productMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductCategoryMapper categoryMapper;

    public record Label(String model, String brand, String category) {
        public static final Label NONE = new Label("", "", "");
    }

    public Map<Long, Label> labels(Collection<Long> productIds) {
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, Label> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        List<ProductDO> products = productMapper.selectBatchIds(ids);
        Map<Long, String> brands = new HashMap<>();
        List<Long> brandIds = products.stream().map(ProductDO::getBrandId).filter(Objects::nonNull).distinct().toList();
        if (!brandIds.isEmpty()) {
            for (ProductBrandDO b : brandMapper.selectBatchIds(brandIds)) {
                brands.put(b.getId(), b.getBrandName());
            }
        }
        Map<Long, String> categories = new HashMap<>();
        List<Long> categoryIds = products.stream().map(ProductDO::getCategoryId).filter(Objects::nonNull).distinct().toList();
        if (!categoryIds.isEmpty()) {
            for (ProductCategoryDO c : categoryMapper.selectBatchIds(categoryIds)) {
                categories.put(c.getId(), StringUtils.hasText(c.getCategoryNameZh()) ? c.getCategoryNameZh() : c.getCategoryName());
            }
        }
        for (ProductDO p : products) {
            String model = StringUtils.hasText(p.getMpnDisplay()) ? p.getMpnDisplay() : Objects.toString(p.getMpnRaw(), "");
            out.put(p.getId(), new Label(model, brands.getOrDefault(p.getBrandId(), ""), categories.getOrDefault(p.getCategoryId(), "")));
        }
        return out;
    }

    public Label label(Long productId) {
        return labels(List.of(productId == null ? -1L : productId)).getOrDefault(productId, Label.NONE);
    }
}

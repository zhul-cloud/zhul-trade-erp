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

    /** 一级品类：0 表示未分类（商品没有品类或品类已删除） */
    public record TopCategory(long id, String name) {
        public static final String UNCATEGORIZED = "未分类";
        public static final TopCategory NONE = new TopCategory(0L, UNCATEGORIZED);
    }

    /** 与 {@link #topCategories} 同一口径的 SQL：一级品类为 ? 的商品 ID（JdbcTemplate 子查询用；商品与品类是平台共享表） */
    public static final String PRODUCTS_OF_TOP_CATEGORY_SQL = "SELECT p.id FROM product p"
            + " LEFT JOIN product_category c ON c.id = p.category_id AND c.deleted_at IS NULL"
            + " WHERE COALESCE(c.parent_id, c.id, 0) = ?";

    /** 商品当前所属的一级品类：品类没有上级即为一级，有上级取上级；没有品类或品类已删除为未分类 */
    public Map<Long, TopCategory> topCategories(Collection<Long> productIds) {
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, TopCategory> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        List<ProductDO> products = productMapper.selectBatchIds(ids);
        List<Long> categoryIds = products.stream().map(ProductDO::getCategoryId).filter(Objects::nonNull).distinct().toList();
        Map<Long, ProductCategoryDO> categories = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            for (ProductCategoryDO c : categoryMapper.selectBatchIds(categoryIds)) {
                if (c.getDeletedAt() == null) {
                    categories.put(c.getId(), c);
                }
            }
        }
        Map<Long, Long> topIds = new HashMap<>();
        for (ProductDO p : products) {
            ProductCategoryDO c = categories.get(p.getCategoryId());
            topIds.put(p.getId(), c == null ? 0L : (c.getParentId() != null ? c.getParentId() : c.getId()));
        }
        Map<Long, String> names = topCategoryNames(topIds.values());
        for (Long id : ids) {
            long top = topIds.getOrDefault(id, 0L);
            out.put(id, top == 0L ? TopCategory.NONE : new TopCategory(top, names.getOrDefault(top, "")));
        }
        return out;
    }

    /** 一级品类 ID → 中文名（0 为「未分类」） */
    public Map<Long, String> topCategoryNames(Collection<Long> topIds) {
        Map<Long, String> names = new HashMap<>();
        List<Long> ids = topIds.stream().filter(id -> id != null && id != 0L).distinct().toList();
        if (!ids.isEmpty()) {
            for (ProductCategoryDO c : categoryMapper.selectBatchIds(ids)) {
                names.put(c.getId(), StringUtils.hasText(c.getCategoryNameZh()) ? c.getCategoryNameZh() : c.getCategoryName());
            }
        }
        names.put(0L, TopCategory.UNCATEGORIZED);
        return names;
    }

    public Label label(Long productId) {
        return labels(List.of(productId == null ? -1L : productId)).getOrDefault(productId, Label.NONE);
    }
}

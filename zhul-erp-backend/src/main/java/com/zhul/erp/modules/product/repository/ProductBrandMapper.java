package com.zhul.erp.modules.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductBrandMapper extends BaseMapper<ProductBrandDO> {

    /**
     * 引用该品牌的供应商主营产品数（跨所有租户，只用于删除前判断，不返回租户数据）。
     * 直接查 supplier_product_scope，避免商品模块依赖客商模块。
     * 已删除供应商的主营行不算引用。
     */
    @Select("SELECT COUNT(*) FROM supplier_product_scope WHERE brand_id = #{brandId} AND deleted_at IS NULL"
            + " AND EXISTS (SELECT 1 FROM supplier s WHERE s.id = supplier_product_scope.supplier_id AND s.deleted_at IS NULL)")
    long countSupplierScopes(@Param("brandId") Long brandId);
}

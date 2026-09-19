package com.zhul.erp.modules.product.service;

import com.zhul.erp.modules.product.dto.ProductMatchVO;
import com.zhul.erp.modules.product.dto.ProductOptionVO;

import java.util.List;

/** 供选择器与业务模块使用的只读查找，只返回启用且未删除的商品。 */
public interface ProductLookupService {

    /**
     * 选择器搜索：关键词归一化后按型号前缀匹配，或按产品名称匹配。
     * brandId 不为空时只在该品牌下查找（向导里提示相近型号用）。
     */
    List<ProductOptionVO> search(String keyword, Integer limit, Long brandId);

    /** 型号匹配：同品牌同归一化型号为精确匹配，其余相同或前缀相同的为候选 */
    ProductMatchVO match(String brand, String mpn);
}

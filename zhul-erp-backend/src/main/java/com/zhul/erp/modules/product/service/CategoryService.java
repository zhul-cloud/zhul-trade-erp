package com.zhul.erp.modules.product.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.product.dto.CategoryOptionVO;
import com.zhul.erp.modules.product.dto.CategoryQuery;
import com.zhul.erp.modules.product.dto.CategoryVO;
import com.zhul.erp.modules.product.dto.SaveCategoryRequest;

import java.util.List;

public interface CategoryService {

    PageResult<CategoryVO> page(CategoryQuery query);

    /**
     * 启用品类，走缓存。level：1 或空只返回一级品类（商品选择用），2 只返回细分品类，0 返回全部。
     */
    List<CategoryOptionVO> options(Integer level);

    /** 两级品类树：未删除的一级品类（含停用）及其细分品类，按排序号 */
    List<CategoryVO> tree();

    CategoryVO create(SaveCategoryRequest req);

    /** 有商品使用后编码不可修改，名称与排序仍可改 */
    CategoryVO update(Long id, SaveCategoryRequest req);

    void updateStatus(Long id, Integer status);

    /** 软删除；下面仍有未删除商品、细分品类，或被供应商主营产品引用时拒绝 */
    void delete(Long id);
}

package com.zhul.erp.modules.product.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.product.dto.CategoryOptionVO;
import com.zhul.erp.modules.product.dto.CategoryQuery;
import com.zhul.erp.modules.product.dto.CategoryVO;
import com.zhul.erp.modules.product.dto.SaveCategoryRequest;

import java.util.List;

public interface CategoryService {

    PageResult<CategoryVO> page(CategoryQuery query);

    /** 全部启用品类，走缓存 */
    List<CategoryOptionVO> options();

    CategoryVO create(SaveCategoryRequest req);

    /** 有商品使用后编码不可修改，名称与排序仍可改 */
    CategoryVO update(Long id, SaveCategoryRequest req);

    void updateStatus(Long id, Integer status);

    /** 软删除；下面仍有未删除商品时拒绝 */
    void delete(Long id);
}

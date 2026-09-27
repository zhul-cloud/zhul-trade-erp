package com.zhul.erp.modules.product.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.product.dto.BrandOptionVO;
import com.zhul.erp.modules.product.dto.BrandQuery;
import com.zhul.erp.modules.product.dto.BrandVO;
import com.zhul.erp.modules.product.dto.SaveBrandRequest;

import java.util.List;

public interface BrandService {

    PageResult<BrandVO> page(BrandQuery query);

    /** 全部启用品牌，走缓存 */
    List<BrandOptionVO> options();

    BrandVO create(SaveBrandRequest req);

    BrandVO update(Long id, SaveBrandRequest req);

    /** 给品牌追加一个别名（平台账号）；与自己名称相同或已是自己的别名时不重复添加，与其他品牌冲突时拒绝 */
    void addAlias(Long brandId, String alias);

    void updateStatus(Long id, Integer status);

    /** 软删除；下面仍有未删除商品时拒绝 */
    void delete(Long id);
}

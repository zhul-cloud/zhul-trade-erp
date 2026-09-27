package com.zhul.erp.modules.product.service;

import com.zhul.erp.modules.product.dto.CompletenessVO;
import com.zhul.erp.modules.product.entity.ProductDO;

import java.util.Collection;
import java.util.Map;

public interface ProductCompletenessService {

    /** 批量计算，返回 商品ID → 完整度；一批商品只发出固定次数的查询，与商品数量无关 */
    Map<Long, CompletenessVO> compute(Collection<ProductDO> products);
}

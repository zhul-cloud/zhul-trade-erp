package com.zhul.erp.modules.product.service;

import com.zhul.erp.modules.product.dto.SaveSpecificationsRequest;
import com.zhul.erp.modules.product.dto.SpecificationVO;

import java.util.List;

public interface ProductSpecificationService {

    List<SpecificationVO> list(Long productId);

    /** 整体替换：保存后商品的规格集合与提交内容完全一致 */
    List<SpecificationVO> replace(Long productId, SaveSpecificationsRequest req);
}

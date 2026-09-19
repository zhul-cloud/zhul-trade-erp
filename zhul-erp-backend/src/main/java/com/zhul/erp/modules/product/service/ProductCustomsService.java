package com.zhul.erp.modules.product.service;

import com.zhul.erp.modules.product.dto.CustomsVO;
import com.zhul.erp.modules.product.dto.SaveCustomsRequest;

public interface ProductCustomsService {

    /** 没有记录时返回空对象（id 为空） */
    CustomsVO get(Long productId);

    /** 整体覆盖保存 */
    CustomsVO save(Long productId, SaveCustomsRequest req);
}

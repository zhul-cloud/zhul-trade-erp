package com.zhul.erp.modules.product.service;

import com.zhul.erp.modules.product.dto.LogisticsVO;
import com.zhul.erp.modules.product.dto.SaveLogisticsRequest;

public interface ProductLogisticsService {

    /** 没有记录时返回空对象（id 为空，数值为 null） */
    LogisticsVO get(Long productId);

    /** 整体覆盖保存 */
    LogisticsVO save(Long productId, SaveLogisticsRequest req);
}

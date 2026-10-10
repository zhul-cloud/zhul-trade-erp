package com.zhul.erp.modules.product.service;

import com.zhul.erp.modules.product.dto.SaveSpecificationsRequest;
import com.zhul.erp.modules.product.dto.SpecificationVO;

import java.util.List;

public interface ProductSpecificationService {

    /** 英文规格（兼容现有调用方） */
    default List<SpecificationVO> list(Long productId) {
        return list(productId, null);
    }

    /** 某种语言的规格；lang 不传为英文 */
    List<SpecificationVO> list(Long productId, String lang);

    /** 按语言整体替换：保存后该语言的规格集合与提交内容完全一致，其他语言不受影响 */
    List<SpecificationVO> replace(Long productId, SaveSpecificationsRequest req);
}

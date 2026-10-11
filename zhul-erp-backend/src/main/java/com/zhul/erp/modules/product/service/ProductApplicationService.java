package com.zhul.erp.modules.product.service;

import com.zhul.erp.modules.product.dto.ApplicationVO;
import com.zhul.erp.modules.product.dto.SaveApplicationRequest;

import java.util.List;

public interface ProductApplicationService {

    /** 英文应用场景（兼容现有调用方） */
    default List<ApplicationVO> list(Long productId) {
        return list(productId, null);
    }

    /** 某种语言的应用场景；lang 不传为英文 */
    List<ApplicationVO> list(Long productId, String lang);

    ApplicationVO create(Long productId, SaveApplicationRequest req);

    ApplicationVO update(Long productId, Long itemId, SaveApplicationRequest req);

    void delete(Long productId, Long itemId);
}

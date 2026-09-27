package com.zhul.erp.modules.product.service;

import com.zhul.erp.modules.product.dto.ApplicationVO;
import com.zhul.erp.modules.product.dto.SaveApplicationRequest;

import java.util.List;

public interface ProductApplicationService {

    List<ApplicationVO> list(Long productId);

    ApplicationVO create(Long productId, SaveApplicationRequest req);

    ApplicationVO update(Long productId, Long itemId, SaveApplicationRequest req);

    void delete(Long productId, Long itemId);
}

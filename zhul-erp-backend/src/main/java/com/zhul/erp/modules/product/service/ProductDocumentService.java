package com.zhul.erp.modules.product.service;

import com.zhul.erp.modules.product.dto.DocumentVO;
import com.zhul.erp.modules.product.dto.SaveDocumentRequest;

import java.util.List;

public interface ProductDocumentService {

    /** 该商品自己的技术资料，按 sort_order、id 排序 */
    List<DocumentVO> list(Long productId);

    DocumentVO create(Long productId, SaveDocumentRequest req);

    DocumentVO update(Long productId, Long itemId, SaveDocumentRequest req);

    void delete(Long productId, Long itemId);
}

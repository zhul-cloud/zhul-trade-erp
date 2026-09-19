package com.zhul.erp.modules.product.service;

import com.zhul.erp.modules.product.dto.ReferencePriceVO;
import com.zhul.erp.modules.product.dto.SaveReferencePriceRequest;

public interface ProductReferencePriceService {

    /** 没有参考价时返回空对象（id 为空） */
    ReferencePriceVO get(Long productId);

    /** 保存并计算本位币金额；清除后再次保存会复用原行 */
    ReferencePriceVO save(Long productId, SaveReferencePriceRequest req);

    /** 清除（软删除）；没有参考价时什么也不做 */
    void clear(Long productId);
}

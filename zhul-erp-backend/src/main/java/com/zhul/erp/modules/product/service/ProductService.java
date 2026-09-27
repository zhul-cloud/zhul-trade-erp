package com.zhul.erp.modules.product.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.product.dto.CompletenessSummaryVO;
import com.zhul.erp.modules.product.dto.ProductQuery;
import com.zhul.erp.modules.product.dto.ProductVO;
import com.zhul.erp.modules.product.dto.SaveProductRequest;

public interface ProductService {

    PageResult<ProductVO> page(ProductQuery query);

    /** 详情，含被引用次数 */
    ProductVO getById(Long id);

    ProductVO create(SaveProductRequest req);

    ProductVO update(Long id, SaveProductRequest req);

    void updateStatus(Long id, Integer status);

    /** 软删除；被业务单据引用时拒绝 */
    void delete(Long id);

    /** 恢复已软删除的商品，保留原 ID 和全部子资料 */
    ProductVO restore(Long id);

    /** 各缺项对应的商品数量，仅平台账号 */
    CompletenessSummaryVO completenessSummary();
}

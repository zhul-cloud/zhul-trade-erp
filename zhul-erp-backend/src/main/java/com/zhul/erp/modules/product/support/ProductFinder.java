package com.zhul.erp.modules.product.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 商品及其子资料的写操作都先经过这里取到"存在且未删除"的商品，并可锁住商品行让同一商品的写操作串行。 */
@Component
@RequiredArgsConstructor
public class ProductFinder {

    private final ProductMapper productMapper;

    public ProductDO active(Long id) {
        return find(id, false);
    }

    /** 加行锁（SELECT ... FOR UPDATE），必须在事务内调用，事务结束释放 */
    public ProductDO lockActive(Long id) {
        return find(id, true);
    }

    private ProductDO find(Long id, boolean lock) {
        ProductDO product = id == null ? null : productMapper.selectOne(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getId, id)
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductDO::getDeletedAt)
                .last(lock ? "FOR UPDATE" : ""));
        if (product == null) {
            throw BizException.of(ProductErrorCodes.PRODUCT_NOT_FOUND, "商品不存在");
        }
        return product;
    }
}

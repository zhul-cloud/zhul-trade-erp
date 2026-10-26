package com.zhul.erp.modules.product.support;

import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** 单元测试用：所有商品都属于指定租户（0 为平台）的写入守卫 */
public final class TestProductWriteGuards {

    private TestProductWriteGuards() {
    }

    public static ProductWriteGuard ownedBy(int ownerTenantId) {
        ProductMapper mapper = mock(ProductMapper.class);
        ProductDO product = new ProductDO();
        product.setOwnerTenantId(ownerTenantId);
        lenient().when(mapper.selectById(any())).thenReturn(product);
        return new ProductWriteGuard(mapper, new PlatformScopeGuard());
    }
}

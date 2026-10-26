package com.zhul.erp.modules.product.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 商品写入的归属检查（spec product/product「商品数据平台共享，按归属维护」）：
 * 平台账号、候选审核范围内放行；租户账号只能写所属租户为本租户的商品。按钮权限仍由接口上的权限码判断，两者都满足才能写。
 */
@Component
@RequiredArgsConstructor
public class ProductWriteGuard {

    private final ProductMapper productMapper;
    private final PlatformScopeGuard platformScopeGuard;

    /** 当前账号能否修改该商品（不含按钮权限） */
    public boolean canWrite(ProductDO product) {
        if (platformScopeGuard.isPlatform() || platformScopeGuard.inCandidateReview()) {
            return true;
        }
        Integer tenantId = TenantContext.getTenantId();
        return product != null && tenantId != null && tenantId.equals(product.getOwnerTenantId());
    }

    /** 写商品及其资料前调用；商品不存在时放行，由各服务按原逻辑报「商品不存在」 */
    public void require(Long productId) {
        ProductDO product = productId == null ? null : productMapper.selectById(productId);
        if (product != null && !canWrite(product)) {
            throw BizException.of(ProductErrorCodes.PRODUCT_NOT_OWNED, "该商品由其他公司维护，只能查看");
        }
    }

    /** 新建商品的所属租户：平台账号为平台，租户账号（含候选审核建档）为该租户 */
    public int ownerForCreate() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId == null ? ProductConstants.PLATFORM_TENANT_ID : tenantId;
    }
}

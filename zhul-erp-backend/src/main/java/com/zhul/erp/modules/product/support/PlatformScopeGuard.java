package com.zhul.erp.modules.product.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import org.springframework.stereotype.Component;

/**
 * 商品主数据是平台共享数据，写操作除权限码外，还必须由平台账号（JWT 中 tenantId 为 0）执行
 * （design.md 决策 2）。这里是判定"是否平台账号"的唯一位置，识别规则日后变化只改这一处。
 */
@Component
public class PlatformScopeGuard {

    public boolean isPlatform() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId != null && tenantId == ProductConstants.PLATFORM_TENANT_ID;
    }

    public void requirePlatform() {
        if (!isPlatform()) {
            throw BizException.of(ProductErrorCodes.PLATFORM_ADMIN_REQUIRED, "仅平台账号可维护商品主数据");
        }
    }
}

package com.zhul.erp.modules.product.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformScopeGuardTest {

    private final PlatformScopeGuard guard = new PlatformScopeGuard();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void platformAccountPasses() {
        TenantContext.setTenantId(0);
        assertTrue(guard.isPlatform());
        assertDoesNotThrow(guard::requirePlatform);
    }

    @Test
    void tenantAccountIsRejected() {
        TenantContext.setTenantId(1001);
        assertFalse(guard.isPlatform());
        BizException e = assertThrows(BizException.class, guard::requirePlatform);
        assertEquals(ProductErrorCodes.PLATFORM_ADMIN_REQUIRED, e.getErrorCode());
    }

    @Test
    void missingTenantContextIsRejected() {
        TenantContext.clear();
        assertFalse(guard.isPlatform());
        BizException e = assertThrows(BizException.class, guard::requirePlatform);
        assertEquals(ProductErrorCodes.PLATFORM_ADMIN_REQUIRED, e.getErrorCode());
    }
}

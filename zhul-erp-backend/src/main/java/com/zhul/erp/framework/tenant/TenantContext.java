package com.zhul.erp.framework.tenant;

public class TenantContext {
    private static final ThreadLocal<Integer> TENANT_ID = new ThreadLocal<>();

    public static void setTenantId(Integer tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Integer getTenantId() {
        return TENANT_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}

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

    /** 以指定租户执行（如 AI 回调、异步线程按任务所属租户处理），结束后恢复原来的上下文 */
    public static void runAs(Integer tenantId, Runnable action) {
        Integer previous = TENANT_ID.get();
        TENANT_ID.set(tenantId);
        try {
            action.run();
        } finally {
            if (previous == null) {
                TENANT_ID.remove();
            } else {
                TENANT_ID.set(previous);
            }
        }
    }
}

package com.zhul.erp.framework.tenant;

import java.util.function.Supplier;

/**
 * 平台范围：在其中执行的 SQL 不由租户拦截器追加租户条件。只用于本来就跨租户的入口，
 * 如登录与找回密码（登录前还不知道租户）、平台的租户管理、定时任务、AI 回调；调用处须自行带上需要的租户条件。
 */
public final class TenantScope {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private TenantScope() {
    }

    public static <T> T platform(Supplier<T> action) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            return action.get();
        } finally {
            int depth = DEPTH.get() - 1;
            if (depth == 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(depth);
            }
        }
    }

    public static void platform(Runnable action) {
        platform(() -> {
            action.run();
            return null;
        });
    }

    public static boolean isPlatform() {
        return DEPTH.get() > 0;
    }
}

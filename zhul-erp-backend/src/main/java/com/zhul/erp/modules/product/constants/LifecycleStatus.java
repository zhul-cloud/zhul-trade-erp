package com.zhul.erp.modules.product.constants;

/** 商品生命周期（product.lifecycle_status），描述零件本身的事实，与启停状态 status 相互独立。 */
public final class LifecycleStatus {

    private LifecycleStatus() {
    }

    public static final int ACTIVE = 1;
    public static final int CURRENT = 2;
    public static final int LEGACY = 3;
    public static final int DISCONTINUED = 4;
    public static final int OBSOLETE = 5;
    public static final int UNKNOWN = 6;

    public static boolean isValid(Integer value) {
        return value != null && value >= ACTIVE && value <= UNKNOWN;
    }

    /** 已停产（4）和停产无替代（5）必须写明判断依据 */
    public static boolean requiresSource(Integer value) {
        return value != null && (value == DISCONTINUED || value == OBSOLETE);
    }
}

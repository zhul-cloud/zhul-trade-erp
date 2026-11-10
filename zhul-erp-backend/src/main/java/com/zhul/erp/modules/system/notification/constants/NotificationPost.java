package com.zhul.erp.modules.system.notification.constants;

/**
 * 通知接收岗位：没有单据负责人、等人来领的通知发给岗位接收人（系统设置「通知接收人」）。
 * 从没保存过的岗位，默认接收人为有 {@link #permission} 的有效用户，去掉有系统设置权限的管理角色。
 */
public enum NotificationPost {
    PURCHASE_LEAD("采购负责人", "purchase:requirement:assign"),
    /** 收货人：默认「采购订单的采购员」（存为 user_id = 0），也可以改为指定的人 */
    RECEIVER("收货人", "warehouse:receipt:create"),
    FINANCE("财务", "sales:pi:receipt-confirm");

    /** 收货人岗位里表示「采购订单的采购员」的 user_id */
    public static final long PO_BUYER = 0L;

    /** 有这个权限的角色视为管理角色，不默认接收岗位通知 */
    public static final String ADMIN_PERMISSION = "system:config:edit";

    private final String label;
    private final String permission;

    NotificationPost(String label, String permission) {
        this.label = label;
        this.permission = permission;
    }

    public String label() {
        return label;
    }

    public String permission() {
        return permission;
    }
}

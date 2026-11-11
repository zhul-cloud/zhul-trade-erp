package com.zhul.erp.modules.purchase.purchasereturn;

import java.util.Map;

/** 采购退货单常量（spec purchase/purchase-return） */
public final class PurchaseReturnConstants {

    private PurchaseReturnConstants() {
    }

    public static final String MENU = "采购退货";
    public static final String PERM_EDIT = "purchase:purchase-return:edit";

    /** 来源 */
    public static final int SRC_AFTER_SALES = 1;
    public static final int SRC_DEFECT_EXCHANGE = 2;
    public static final int SRC_DEFECT_RETURN = 3;
    public static final int SRC_OVER_RETURN = 4;
    public static final Map<Integer, String> SOURCE_NAMES = Map.of(SRC_AFTER_SALES, "售后退货", SRC_DEFECT_EXCHANGE, "到货不良 · 退货换货",
            SRC_DEFECT_RETURN, "到货不良 · 退货不补", SRC_OVER_RETURN, "多发退回");

    /** 状态 */
    public static final int PENDING = 1;
    public static final int SHIPPED = 2;
    public static final int DONE = 3;
    public static final int VOID = 4;
    public static final Map<Integer, String> STATUS_NAMES = Map.of(PENDING, "待寄出", SHIPPED, "已寄出", DONE, "已完成", VOID, "已作废");
}

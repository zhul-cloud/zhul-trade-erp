package com.zhul.erp.modules.inquiry.constants;

/** inquiry_order_item.confidence 枚举值。手动创建（跳过AI）的明细固定为 CONFIRMED。 */
public final class ConfidenceLevel {
    private ConfidenceLevel() {
    }

    public static final int CONFIRMED = 1;
    public static final int CORRECTED = 2;
    public static final int PENDING_VERIFY = 3;
    public static final int UNRECOGNIZED = 4;
}

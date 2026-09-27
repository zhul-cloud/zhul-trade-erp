package com.zhul.erp.modules.inquiry.inquiryorder.constants;

/** inquiry_order_supplier.status 枚举值。 */
public final class SupplierAssociationStatus {
    private SupplierAssociationStatus() {
    }

    public static final int PENDING_SEND = 1;
    public static final int SENT = 2;
    public static final int REPLIED = 3;
    public static final int NO_REPLY = 4;
}

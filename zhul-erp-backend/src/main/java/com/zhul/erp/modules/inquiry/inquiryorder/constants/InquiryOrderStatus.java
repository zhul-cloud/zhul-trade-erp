package com.zhul.erp.modules.inquiry.inquiryorder.constants;

/** inquiry_order.status 枚举值，见询盘单-PRD-V1.0.md 3.3。 */
public final class InquiryOrderStatus {
    private InquiryOrderStatus() {
    }

    public static final int PENDING_ASSIGN = 1;
    public static final int ASSIGNED = 2;
    public static final int SENT_TO_SUPPLIER = 3;
    public static final int QUOTE_RECEIVED = 4;
    public static final int QUOTED_TO_CUSTOMER = 5;
    public static final int DEAL = 6;
    public static final int CANCELLED = 7;

    /** 人工可推进的状态序列（不含取消，取消可从已分配之后的任意非终态触发）。 */
    public static final int[] MANUAL_ADVANCE_SEQUENCE = {
            ASSIGNED, SENT_TO_SUPPLIER, QUOTE_RECEIVED, QUOTED_TO_CUSTOMER, DEAL
    };
}

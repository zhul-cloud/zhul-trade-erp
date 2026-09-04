package com.zhul.erp.modules.inquiry.customerinquiry.constants;

/** customer_inquiry.status 枚举值，见询盘单-PRD-V1.0.md 3.2。 */
public final class CustomerInquiryStatus {
    private CustomerInquiryStatus() {
    }

    public static final int PENDING_PARSE = 1;
    public static final int PARSING = 2;
    public static final int PENDING_CONFIRM = 3;
    public static final int PARSE_FAILED = 4;
    public static final int CONFIRMED = 5;
    public static final int PENDING_QUOTE = 6;
    public static final int QUOTING = 7;
    public static final int QUOTED = 8;
    public static final int DEAL = 9;
    public static final int CANCELLED = 10;

    /** 人工可推进的状态序列（PRD 6.3.4）：已确认之后按顺序推进，不允许跳跃或倒退。 */
    public static final int[] MANUAL_ADVANCE_SEQUENCE = {
            CONFIRMED, PENDING_QUOTE, QUOTING, QUOTED, DEAL
    };
}

package com.zhul.erp.modules.inquiry.inquiryorder.constants;

/** inquiry_order_item_quote.quote_status 枚举值。 */
public final class QuoteStatus {
    private QuoteStatus() {
    }

    public static final int PENDING = 1;
    public static final int QUOTED = 2;
    public static final int UNABLE_TO_QUOTE = 3;
    public static final int CUSTOMER_CONFIRMED = 4;
}

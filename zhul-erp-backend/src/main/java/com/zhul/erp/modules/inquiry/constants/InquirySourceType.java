package com.zhul.erp.modules.inquiry.constants;

/**
 * inquiry_order_supplier.source_type 枚举值。见
 * openspec/changes/add-inquiry-management/design.md 决策11：
 * 电商询价渠道（淘宝/1688/闲鱼等）不强制绑定正式供应商主数据。
 */
public final class InquirySourceType {
    private InquirySourceType() {
    }

    public static final int FORMAL_SUPPLIER = 1;
    public static final int ECOMMERCE_CHANNEL = 2;
}

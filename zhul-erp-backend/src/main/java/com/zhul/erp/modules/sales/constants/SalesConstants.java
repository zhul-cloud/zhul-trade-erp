package com.zhul.erp.modules.sales.constants;

import java.util.Map;
import java.util.Set;

/** 销售管理（PI、收款、销售订单）常量，码值与 V1.2.27__proforma_invoice.sql 的字段注释一致 */
public final class SalesConstants {

    /** PI 状态 */
    public static final int PI_DRAFT = 1;
    public static final int PI_SENT = 2;
    public static final int PI_CONVERTED = 3;
    public static final int PI_VOID = 4;
    /** 客户最终没有付款，业务员关闭 */
    public static final int PI_CLOSED = 5;
    public static final Map<Integer, String> PI_STATUS_NAMES = Map.of(PI_DRAFT, "草稿", PI_SENT, "已发送", PI_CONVERTED, "已转订单", PI_VOID, "已作废",
            PI_CLOSED, "已关闭");
    /** 有效期默认天数：开 PI 当天 + 60 天 */
    public static final int PI_VALID_DAYS = 60;
    public static final String CLOSED_MESSAGE = "PI 已关闭，需要继续请先重新打开";

    /** PI 版本状态 */
    public static final int VERSION_EDITING = 1;
    public static final int VERSION_SENT = 2;
    public static final int VERSION_ABANDONED = 3;

    /** 收款状态 */
    public static final int RECEIPT_NONE = 1;
    public static final int RECEIPT_SLIP_ONLY = 2;
    public static final int RECEIPT_PARTIAL = 3;
    public static final int RECEIPT_PAID = 4;
    public static final Map<Integer, String> RECEIPT_STATUS_NAMES = Map.of(RECEIPT_NONE, "未付款", RECEIPT_SLIP_ONLY, "待到账",
            RECEIPT_PARTIAL, "部分到账", RECEIPT_PAID, "已到账");

    /** 收款记录类型与状态 */
    public static final int KIND_SLIP = 1;
    public static final int KIND_RECEIPT = 2;

    /** 付款方式：字典与线上 / 线下（字典项的值 ONLINE / OFFLINE） */
    public static final String DICT_PAYMENT_METHOD = "payment_method";
    public static final String METHOD_ONLINE = "ONLINE";
    public static final String PERM_RECEIPT_CONFIRM = "sales:pi:receipt-confirm";
    public static final int CHANNEL_OFFLINE = 1;
    public static final int CHANNEL_ONLINE = 2;
    public static final Map<Integer, String> RECEIPT_CHANNEL_NAMES = Map.of(CHANNEL_OFFLINE, "线下", CHANNEL_ONLINE, "线上");
    /** 实收人民币的汇率来源 */
    public static final int RATE_SYSTEM = 1;
    public static final int RATE_ACTUAL = 2;
    public static final int RECORD_VALID = 1;
    public static final int RECORD_VOID = 2;

    /** 销售订单状态 */
    public static final int SO_ACTIVE = 1;
    public static final int SO_CANCELLED = 2;

    /** 整单折扣方式 */
    public static final int DISCOUNT_NONE = 0;
    public static final int DISCOUNT_PERCENT = 1;
    public static final int DISCOUNT_AMOUNT = 2;

    /** 发送方式（与报价单一致，PI 没有文字） */
    public static final int CHANNEL_EXCEL = 2;
    public static final int CHANNEL_PDF = 3;
    public static final int CHANNEL_IMAGE = 4;
    public static final Map<Integer, String> CHANNEL_NAMES = Map.of(CHANNEL_EXCEL, "Excel", CHANNEL_PDF, "PDF", CHANNEL_IMAGE, "图片");

    public static final Set<String> DEFAULT_FEES = Set.of("Shipping Cost", "Bank Charge");
    public static final String DISCOUNT_NAME = "Discount";
    public static final String DEFAULT_WARRANTY = "1 year";
    public static final int MAX_ITEMS = 300;
    public static final int MAX_SLIP_FILES = 5;
    public static final long MAX_SLIP_BYTES = 10L * 1024 * 1024;
    public static final String SLIP_MODULE = "payment-slip";
    public static final String CONFIG_FEE_TOLERANCE = "sales.receipt.fee-tolerance";
    public static final int DEFAULT_FEE_TOLERANCE = 50;

    public static final String PERM_SLIP = "sales:pi:receipt-slip";
    public static final String PERM_CONFIRM = "sales:pi:receipt-confirm";
    public static final String MENU_PI = "PI";
    public static final String MENU_RECEIPTS = "收款管理";
    public static final String MENU_SO = "销售订单";

    private SalesConstants() {
    }
}

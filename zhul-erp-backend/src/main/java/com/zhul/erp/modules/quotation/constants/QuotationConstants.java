package com.zhul.erp.modules.quotation.constants;

/** 报价中心常量 */
public final class QuotationConstants {

    public static final String DICT_CONDITION = "inquiry_item_condition";
    public static final String DICT_LEAD_TIME = "inquiry_lead_time";
    public static final String DICT_LOST_REASON = "quotation_lost_reason";

    /** 货况码值（字典 inquiry_item_condition） */
    public static final int CONDITION_DOMESTIC_ALT = 6;
    public static final int CONDITION_TO_CONFIRM = 7;

    /** 生命周期码值（字典 inquiry_lifecycle） */
    public static final int LIFECYCLE_DISCONTINUED = 2;
    public static final int LIFECYCLE_TO_CONFIRM = 3;

    public static final String CONFIG_GROUP = "quotation";
    public static final String CONFIG_HINT_DISCONTINUED_URGENT = "quotation.hint.discontinued-urgent";
    public static final String CONFIG_HINT_PREMIUM_BRAND = "quotation.hint.premium-brand";
    public static final String CONFIG_HINT_RETURNING_CUSTOMER = "quotation.hint.returning-customer";
    public static final String CONFIG_HINT_TO_CONFIRM = "quotation.hint.to-confirm";
    public static final String CONFIG_PREMIUM_BRANDS = "quotation.premium-brands";
    /** 报价策略「按金额分层毛利」的默认档位（JSON） */
    public static final String CONFIG_STRATEGY_TIERS = "quotation.strategy.cost-tiers";

    public static final String PERM_PRICING_EDIT = "quotation:pricing:edit";

    /** 报价单状态 */
    public static final int STATUS_DRAFT = 1;
    public static final int STATUS_SENT = 2;
    public static final int STATUS_WON = 3;
    public static final int STATUS_LOST = 4;
    public static final int STATUS_VOID = 5;
    /** 部分型号进入有效销售订单 */
    public static final int STATUS_PARTIAL = 6;
    public static final java.util.Map<Integer, String> STATUS_NAMES = java.util.Map.of(
            STATUS_DRAFT, "草稿", STATUS_SENT, "已发送", STATUS_WON, "已成交", STATUS_LOST, "未成交", STATUS_VOID, "已作废",
            STATUS_PARTIAL, "部分成交");
    /** 报价单到了这些状态，其中的型号对客户已报出：回价只读 */
    /** 版本状态 */
    public static final int VERSION_EDITING = 1;
    public static final int VERSION_SENT = 2;
    public static final int VERSION_ABANDONED = 3;
    public static final java.util.Set<Integer> ITEM_LOCKING_STATUSES = java.util.Set.of(STATUS_SENT, STATUS_WON, STATUS_LOST, STATUS_PARTIAL);

    /** 发送方式 */
    public static final int CHANNEL_TEXT = 1;
    public static final int CHANNEL_EXCEL = 2;
    public static final int CHANNEL_PDF = 3;
    public static final int CHANNEL_IMAGE = 4;
    public static final java.util.Map<Integer, String> CHANNEL_NAMES = java.util.Map.of(
            CHANNEL_TEXT, "文字", CHANNEL_EXCEL, "Excel", CHANNEL_PDF, "PDF", CHANNEL_IMAGE, "图片");

    public static final int VALID_DAYS = 15;
    public static final String DEFAULT_WARRANTY = "1 year";
    /** 客户没有默认交易条件时：DAP，地点为客户国家 */
    public static final String DEFAULT_INCOTERM = "DAP";
    public static final String DEFAULT_CURRENCY = "USD";
    public static final String LOST_REASON_OTHER = "OTHER";
    public static final int MAX_ITEMS = 300;
    public static final int MAX_FEES = 20;
    public static final String MENU = "报价单";

    private QuotationConstants() {
    }
}

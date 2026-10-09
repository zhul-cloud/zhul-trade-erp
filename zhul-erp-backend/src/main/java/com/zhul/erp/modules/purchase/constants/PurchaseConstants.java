package com.zhul.erp.modules.purchase.constants;

import java.util.Map;

/** 采购需求与采购单 */
public final class PurchaseConstants {

    public static final String MENU_REQUIREMENT = "采购需求";
    public static final String MENU_ORDER = "采购单";
    public static final String PATH_REQUIREMENT = "/purchase/requirements";
    public static final String PATH_ORDER = "/purchase/orders";

    public static final String PERM_SPLIT = "purchase:requirement:split";
    public static final String PERM_ASSIGN = "purchase:requirement:assign";
    public static final String PERM_CREATE = "purchase:order:create";
    public static final String PERM_CANCEL = "purchase:order:cancel";

    /** 需求状态（落库） */
    public static final int REQ_ACTIVE = 1;
    public static final int REQ_CLOSED = 2;
    public static final int REQ_ORDER_CANCELLED = 3;

    /** 需求显示状态（由数量得出） */
    public static final String REQ_VIEW_PENDING = "待下单";
    public static final String REQ_VIEW_DRAFT = "草稿中";
    public static final String REQ_VIEW_PARTIAL = "部分下单";
    public static final String REQ_VIEW_ORDERED = "已下单";
    public static final String REQ_VIEW_CLOSED = "已关闭";
    public static final String REQ_VIEW_ORDER_CANCELLED = "订单已取消";

    /** 采购单状态 */
    public static final int PO_DRAFT = 1;
    public static final int PO_ORDERED = 2;
    public static final int PO_CANCELLED = 3;
    public static final Map<Integer, String> PO_STATUS_NAMES = Map.of(PO_DRAFT, "草稿", PO_ORDERED, "已下单", PO_CANCELLED, "已取消");

    public static final String CNY = "CNY";
    /** 含税时的默认税率（%） */
    public static final String DEFAULT_TAX_RATE = "13";
    /** 回价渠道：4-供应商，其余为电商店铺 */
    public static final int CHANNEL_SUPPLIER = 4;
    public static final Map<Integer, String> CHANNEL_NAMES = Map.of(1, "淘宝", 2, "1688", 3, "闲鱼", 4, "供应商", 5, "其他");

    /** 草稿放置超过这些天在列表上提醒 */
    public static final int STALE_DRAFT_DAYS = 3;
    public static final int MAX_ATTACHMENTS = 10;
    public static final long ATTACHMENT_MAX_BYTES = 10L * 1024 * 1024;

    private PurchaseConstants() {
    }
}

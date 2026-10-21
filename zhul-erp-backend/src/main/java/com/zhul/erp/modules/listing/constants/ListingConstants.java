package com.zhul.erp.modules.listing.constants;

/** 发品任务常量（spec product/listing-task） */
public final class ListingConstants {

    private ListingConstants() {
    }

    /** 状态：1-待发布、2-已发布、3-已取消 */
    public static final int STATUS_PENDING = 1;
    public static final int STATUS_PUBLISHED = 2;
    public static final int STATUS_CANCELLED = 3;

    public static final String CANCEL_DISABLED = "平台已停用";
    public static final String CANCEL_SKIPPED = "标记不发布";

    public static final String MENU = "/operations/listings";
    public static final String PERM_EDIT = "operations:listing:edit";

    /** 平台设置修改后按批重算，每批商品数 */
    public static final int SYNC_BATCH = 500;

    public static final int MAX_URL = 500;
    public static final int MAX_ITEM_ID = 64;
    public static final int MAX_NOTE = 300;
}

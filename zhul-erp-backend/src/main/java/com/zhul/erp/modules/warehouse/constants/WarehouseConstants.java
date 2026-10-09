package com.zhul.erp.modules.warehouse.constants;

import java.util.Map;

/** 到货与入库：发货单、入库单、到货差异、暂存货、拍摄任务 */
public final class WarehouseConstants {

    public static final String MENU_SHIPMENT = "供应商发货";
    public static final String MENU_RECEIPT = "入库验收";
    public static final String MENU_HOLD = "暂存货";
    public static final String MENU_SHOOT = "拍摄任务";

    public static final int SHIP_BY_BUYER = 1;
    public static final int SHIP_BY_WAREHOUSE = 2;
    public static final Map<Integer, String> SHIP_SOURCE_NAMES = Map.of(SHIP_BY_BUYER, "采购员登记", SHIP_BY_WAREHOUSE, "仓库补登");

    public static final int SHIP_IN_TRANSIT = 1;
    public static final int SHIP_RECEIVED = 2;
    public static final int SHIP_VOID = 3;
    public static final Map<Integer, String> SHIP_STATUS_NAMES = Map.of(SHIP_IN_TRANSIT, "在途", SHIP_RECEIVED, "已入库", SHIP_VOID, "已作废");

    public static final int GR_VALID = 1;
    public static final int GR_REVERSED = 2;
    public static final Map<Integer, String> GR_STATUS_NAMES = Map.of(GR_VALID, "有效", GR_REVERSED, "已冲销");

    public static final int DIFF_SHORT = 1;
    public static final int DIFF_DEFECTIVE = 2;
    public static final int DIFF_OVER = 3;
    public static final Map<Integer, String> DIFF_TYPE_NAMES = Map.of(DIFF_SHORT, "少发", DIFF_DEFECTIVE, "不良", DIFF_OVER, "多发");

    public static final int DIFF_PENDING = 1;
    public static final int DIFF_HANDLED = 2;

    public static final int RES_WAIT_RESEND = 1;
    public static final int RES_NO_RESEND = 2;
    public static final int RES_RETURN_EXCHANGE = 3;
    public static final int RES_RETURN_NO_RESEND = 4;
    public static final int RES_DISCOUNT = 5;
    public static final int RES_RETURN_OVER = 6;
    public static final int RES_HOLD = 7;
    public static final Map<Integer, String> RESOLUTION_NAMES = Map.of(RES_WAIT_RESEND, "等补发", RES_NO_RESEND, "不补了",
            RES_RETURN_EXCHANGE, "退货换货", RES_RETURN_NO_RESEND, "退货不补", RES_DISCOUNT, "折价接收", RES_RETURN_OVER, "退回供应商",
            RES_HOLD, "暂存");
    /** 差异类型 → 可选的处理方式 */
    public static final Map<Integer, java.util.Set<Integer>> RESOLUTIONS_BY_TYPE = Map.of(
            DIFF_SHORT, java.util.Set.of(RES_WAIT_RESEND, RES_NO_RESEND),
            DIFF_DEFECTIVE, java.util.Set.of(RES_RETURN_EXCHANGE, RES_RETURN_NO_RESEND, RES_DISCOUNT),
            DIFF_OVER, java.util.Set.of(RES_RETURN_OVER, RES_HOLD));

    public static final int HOLD_ACTIVE = 1;
    public static final int HOLD_RETURNED = 2;
    public static final int HOLD_SCRAPPED = 3;
    public static final int HOLD_SAMPLE = 4;
    public static final Map<Integer, String> HOLD_STATUS_NAMES = Map.of(HOLD_ACTIVE, "暂存中", HOLD_RETURNED, "已退回",
            HOLD_SCRAPPED, "已报废", HOLD_SAMPLE, "已转样品");

    public static final int SHOOT_PENDING = 1;
    public static final int SHOOT_DONE = 2;
    public static final int SHOOT_SKIPPED = 3;
    public static final Map<Integer, String> SHOOT_STATUS_NAMES = Map.of(SHOOT_PENDING, "待拍摄", SHOOT_DONE, "已完成", SHOOT_SKIPPED, "已跳过");

    public static final int MEDIA_UNBOXING = 1;
    public static final int MEDIA_INSPECTION = 2;
    public static final int MEDIA_PHOTO = 3;
    public static final Map<Integer, String> MEDIA_TYPE_NAMES = Map.of(MEDIA_UNBOXING, "拆箱视频", MEDIA_INSPECTION, "验货视频", MEDIA_PHOTO, "实物图");
    /** 齐全的门槛：拆箱视频 1、验货视频 1、实物图 6 */
    public static final int NEED_VIDEO = 1;
    public static final int NEED_PHOTOS = 6;

    private WarehouseConstants() {
    }
}

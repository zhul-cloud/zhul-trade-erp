package com.zhul.erp.modules.inventory.constants;

import java.util.Map;

/** 库存模块常量（openspec inventory-foundation） */
public final class InvConstants {

    private InvConstants() {
    }

    /** 仓库类型 */
    public static final int WH_OWN = 1;
    public static final int WH_FORWARDER = 2;
    public static final Map<Integer, String> WH_TYPE_NAMES = Map.of(WH_OWN, "自有仓", WH_FORWARDER, "货代仓");
    public static final String OWN_WAREHOUSE_NAME = "公司仓库";

    /** 库存状态 */
    public static final int GOOD = 1;
    public static final int DEFECTIVE = 2;
    public static final Map<Integer, String> STATUS_NAMES = Map.of(GOOD, "合格", DEFECTIVE, "不良");

    /** 操作类型：入库、出库、调入、调出、转入、转出、预占、释放 */
    public static final int OP_IN = 1;
    public static final int OP_OUT = 2;
    public static final int OP_TRANSFER_IN = 3;
    public static final int OP_TRANSFER_OUT = 4;
    public static final int OP_CONVERT_IN = 5;
    public static final int OP_CONVERT_OUT = 6;
    public static final int OP_RESERVE = 7;
    public static final int OP_RELEASE = 8;
    public static final Map<Integer, String> OP_NAMES = Map.of(OP_IN, "入库", OP_OUT, "出库", OP_TRANSFER_IN, "调入",
            OP_TRANSFER_OUT, "调出", OP_CONVERT_IN, "转入", OP_CONVERT_OUT, "转出", OP_RESERVE, "预占", OP_RELEASE, "释放");

    /** 操作类型的方向：入库、调入、转入、预占为正 */
    public static boolean positive(int op) {
        return op == OP_IN || op == OP_TRANSFER_IN || op == OP_CONVERT_IN || op == OP_RESERVE;
    }

    /** 只改预占、不改结存 */
    public static boolean reservationOnly(int op) {
        return op == OP_RESERVE || op == OP_RELEASE;
    }

    /** 反向冲销取相反的操作类型 */
    public static int opposite(int op) {
        return switch (op) {
            case OP_IN -> OP_OUT;
            case OP_OUT -> OP_IN;
            case OP_TRANSFER_IN -> OP_TRANSFER_OUT;
            case OP_TRANSFER_OUT -> OP_TRANSFER_IN;
            case OP_CONVERT_IN -> OP_CONVERT_OUT;
            case OP_CONVERT_OUT -> OP_CONVERT_IN;
            case OP_RESERVE -> OP_RELEASE;
            case OP_RELEASE -> OP_RESERVE;
            default -> throw new IllegalArgumentException("未知操作类型 " + op);
        };
    }

    /** 预占对象 */
    public static final int TARGET_SO_ITEM = 1;
    public static final int TARGET_RETURN_ITEM = 2;

    /** 批次来源 */
    public static final int BATCH_PURCHASE = 1;
    public static final int BATCH_RETURN = 2;
    public static final int BATCH_SYSTEM = 3;
    public static final int BATCH_OPENING = 4;
    public static final Map<Integer, String> BATCH_SOURCE_NAMES = Map.of(BATCH_PURCHASE, "采购入库", BATCH_RETURN, "退货入库",
            BATCH_SYSTEM, "系统外入库", BATCH_OPENING, "期初");

    /** 业务类型 */
    public static final String BIZ_PURCHASE_IN = "PURCHASE_IN";
    public static final String BIZ_PURCHASE_IN_REVERSE = "PURCHASE_IN_REVERSE";
    public static final String BIZ_DISCOUNT_ACCEPT = "DISCOUNT_ACCEPT";
    public static final String BIZ_DISCOUNT_ACCEPT_UNDO = "DISCOUNT_ACCEPT_UNDO";
    public static final String BIZ_HANDOVER = "HANDOVER";
    public static final String BIZ_HANDOVER_UNDO = "HANDOVER_UNDO";
    public static final String BIZ_SALES_OUT = "SALES_OUT";
    public static final String BIZ_SALES_OUT_UNDO = "SALES_OUT_UNDO";
    public static final String BIZ_RETURN_IN = "RETURN_IN";
    public static final String BIZ_RETURN_IN_UNDO = "RETURN_IN_UNDO";
    public static final String BIZ_PURCHASE_RETURN_OUT = "PURCHASE_RETURN_OUT";
    public static final String BIZ_OTHER_OUT = "OTHER_OUT";
    public static final String BIZ_OTHER_OUT_VOID = "OTHER_OUT_VOID";
    public static final String BIZ_SYSTEM_IN = "SYSTEM_IN";
    public static final String BIZ_RESERVE = "RESERVE";
    public static final String BIZ_RELEASE = "RELEASE";
    public static final String BIZ_MERGE = "MERGE";
    public static final String BIZ_OPENING = "OPENING";

    public static final Map<String, String> BIZ_NAMES = Map.ofEntries(
            Map.entry(BIZ_PURCHASE_IN, "采购入库"),
            Map.entry(BIZ_PURCHASE_IN_REVERSE, "冲销采购入库"),
            Map.entry(BIZ_DISCOUNT_ACCEPT, "折价接收"),
            Map.entry(BIZ_DISCOUNT_ACCEPT_UNDO, "撤销折价接收"),
            Map.entry(BIZ_HANDOVER, "交货代"),
            Map.entry(BIZ_HANDOVER_UNDO, "撤销交货代"),
            Map.entry(BIZ_SALES_OUT, "销售出库"),
            Map.entry(BIZ_SALES_OUT_UNDO, "撤销销售出库"),
            Map.entry(BIZ_RETURN_IN, "退货入库"),
            Map.entry(BIZ_RETURN_IN_UNDO, "撤回退货入库"),
            Map.entry(BIZ_PURCHASE_RETURN_OUT, "采购退货出库"),
            Map.entry(BIZ_OTHER_OUT, "其他出库"),
            Map.entry(BIZ_OTHER_OUT_VOID, "作废其他出库"),
            Map.entry(BIZ_SYSTEM_IN, "系统外入库"),
            Map.entry(BIZ_RESERVE, "预占"),
            Map.entry(BIZ_RELEASE, "释放"),
            Map.entry(BIZ_MERGE, "商品合并"),
            Map.entry(BIZ_OPENING, "期初"));

    /** 其他出库类型 */
    public static final int OO_SCRAP = 1;
    public static final int OO_SAMPLE = 2;
    public static final int OO_LOST = 3;
    public static final int OO_DAMAGED = 4;
    public static final Map<Integer, String> OO_TYPE_NAMES = Map.of(OO_SCRAP, "报废", OO_SAMPLE, "转样品", OO_LOST, "货代丢失",
            OO_DAMAGED, "货损");
    public static final int OO_DONE = 1;
    public static final int OO_VOID = 2;
    public static final Map<Integer, String> OO_STATUS_NAMES = Map.of(OO_DONE, "已出库", OO_VOID, "已作废");

    /** 库存模式 */
    public static final String CONFIG_MODE = "inventory.mode";
    public static final String MODE_DROPSHIP = "dropship";
    public static final String MODE_STOCK = "stock";
    public static final String CONFIG_OPENING_DONE = "inventory.opening.done";

    /** 菜单路径 */
    public static final String MENU_STOCKS = "/warehouse/stocks";
    public static final String MENU_MOVES = "/warehouse/moves";
    public static final String MENU_LEDGER = "/warehouse/ledger";
    public static final String MENU_WAREHOUSES = "/warehouse/warehouses";
    public static final String MENU_OTHER_OUTBOUNDS = "/warehouse/other-outbounds";
}

package com.zhul.erp.modules.aftersales.constants;

import java.util.Map;

/** 售后单、退货单常量（spec sales/after-sales、warehouse/sales-return） */
public final class AfterSalesConstants {

    private AfterSalesConstants() {
    }

    public static final String MENU = "售后管理";
    public static final String MENU_RETURN = "退货入库";

    public static final String DICT_LEVEL = "after_sales_level";
    public static final String DICT_REASON = "after_sales_reason";
    public static final String DICT_SOLUTION = "after_sales_solution";
    public static final String LEVEL_PENDING = "PENDING";
    public static final String REASON_INVESTIGATING = "INVESTIGATING";

    /** 售后单状态 */
    public static final int AS_OPEN = 1;
    public static final int AS_WAITING = 2;
    public static final int AS_CLOSED = 3;
    public static final int AS_CANCELLED = 4;
    public static final Map<Integer, String> AS_STATUS_NAMES = Map.of(AS_OPEN, "处理中", AS_WAITING, "待客户确认", AS_CLOSED, "已结案",
            AS_CANCELLED, "已撤销");

    /** 跟进记录类型 */
    public static final String LOG_NOTE = "NOTE";
    public static final String LOG_LEVEL = "LEVEL";
    public static final String LOG_STATUS = "STATUS";
    public static final String LOG_PLAN = "PLAN";
    public static final String LOG_RETURN = "RETURN";
    public static final String LOG_REFUND = "REFUND";
    public static final String LOG_EXPENSE = "EXPENSE";
    public static final String LOG_CREATE = "CREATE";
    public static final String LOG_REPLACEMENT = "REPLACEMENT";
    public static final Map<String, String> LOG_TYPE_NAMES = Map.of(LOG_NOTE, "跟进", LOG_LEVEL, "等级", LOG_STATUS, "状态", LOG_PLAN, "处理",
            LOG_RETURN, "退货", LOG_REFUND, "退款", LOG_EXPENSE, "费用", LOG_CREATE, "创建", LOG_REPLACEMENT, "补发");

    /** 退货单状态 */
    public static final int RT_PENDING = 1;
    public static final int RT_RECEIVED = 2;
    public static final int RT_DONE = 3;
    public static final int RT_VOID = 4;
    public static final Map<Integer, String> RT_STATUS_NAMES = Map.of(RT_PENDING, "待入库", RT_RECEIVED, "待处理", RT_DONE, "已处理",
            RT_VOID, "已作废");

    /** 退货去向 */
    public static final int DISP_SUPPLIER = 1;
    public static final int DISP_HOLD = 2;
    public static final int DISP_SCRAP = 3;
    public static final Map<Integer, String> DISP_NAMES = Map.of(DISP_SUPPLIER, "退回供应商", DISP_HOLD, "转闲置库存", DISP_SCRAP, "报废");

    public static final String PERM_RETURN_RECEIVE = "warehouse:return:receive";
    public static final String PERM_RETURN_DISPOSE = "purchase:return:dispose";
}

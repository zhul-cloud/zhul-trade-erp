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

    /** 截止事项（spec sales/after-sales「处理时限」） */
    public static final int DUE_NONE = 0;
    public static final int DUE_FIRST = 1;
    public static final int DUE_UPDATE = 2;
    public static final int DUE_MANUAL = 3;
    public static final Map<Integer, String> DUE_TYPE_NAMES = Map.of(DUE_FIRST, "首次响应", DUE_UPDATE, "更新客户", DUE_MANUAL, "手填时间");
    /** 距截止不到这么多分钟为「快到期」 */
    public static final long DUE_SOON_MINUTES = 60;

    /**
     * 各等级时限（按售后流程 SOP，固定）：首次响应分钟、之后更新客户间隔分钟（0 表示不按等级）、是否按自然时间。
     * 工作时间 1 个工作日 = 9 个工作小时；「待确认」按 S2。
     */
    public record SlaRule(long firstMinutes, long intervalMinutes, boolean natural) {
    }

    public static final SlaRule SLA_DEFAULT = new SlaRule(2 * 60, 9 * 60, false);
    public static final Map<String, SlaRule> SLA_RULES = Map.of(
            "S1", new SlaRule(60, 2 * 60, true),
            "S2", SLA_DEFAULT,
            "S3", new SlaRule(9 * 60, 18 * 60, false),
            "S4", new SlaRule(9 * 60, 27 * 60, false),
            "S5", new SlaRule(9 * 60, 0, false),
            LEVEL_PENDING, SLA_DEFAULT);

    public static final String PERM_RETURN_RECEIVE = "warehouse:return:receive";
    public static final String PERM_RETURN_DISPOSE = "purchase:return:dispose";
}

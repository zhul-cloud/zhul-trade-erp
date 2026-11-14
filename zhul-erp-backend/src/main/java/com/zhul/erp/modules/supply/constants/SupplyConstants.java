package com.zhul.erp.modules.supply.constants;

/** 供应链管理码值，与 V1.5.34__supply_chain_phase1.sql 字段注释一致 */
public final class SupplyConstants {

    private SupplyConstants() {
    }

    /** 信号来源 */
    public static final int SRC_INQUIRY = 1;
    public static final int SRC_ORDER = 2;
    public static final int SRC_STOCKOUT = 3;
    public static final int SRC_LOST = 4;
    public static final int SRC_SEARCH = 5;
    public static final int SRC_RESEARCH = 6;

    /** 需求对象状态 */
    public static final int DEMAND_WATCHING = 1;
    public static final int DEMAND_IN_MATRIX = 2;
    public static final int DEMAND_PAUSED = 3;

    /** 需求等级 */
    public static final int LEVEL_OBSERVE = 1;
    public static final int LEVEL_REPEAT = 2;
    public static final int LEVEL_VERIFIED = 3;
    public static final int LEVEL_COMMERCIAL = 4;

    /** 优先级 */
    public static final int PRIORITY_LOW = 1;
    public static final int PRIORITY_MID = 2;
    public static final int PRIORITY_HIGH = 3;

    /** 覆盖状态，数字越大越好 */
    public static final int COVER_NONE = 0;
    public static final int COVER_NO_CHANNEL = 1;
    public static final int COVER_LEAD = 2;
    public static final int COVER_CONFIRM = 3;
    public static final int COVER_READY = 4;

    /** 证据类型 */
    public static final int EVIDENCE_NONE = 0;
    public static final int EVIDENCE_QUOTE = 1;
    public static final int EVIDENCE_PO = 2;

    /** 下一步来源 */
    public static final int NEXT_NONE = 0;
    public static final int NEXT_MANUAL = 1;
    public static final int NEXT_SYSTEM = 2;

    /** 系统提醒 */
    public static final int REMIND_NONE = 0;
    public static final int REMIND_STOCKOUT = 1;
    public static final int REMIND_EXPIRED = 2;
    public static final int REMIND_NEW_IN_MATRIX = 3;
    public static final int REMIND_NO_NEXT = 4;

    /** 处理记录 */
    public static final int LOG_MANUAL = 1;
    public static final int LOG_SYSTEM = 2;
    public static final int LOG_SRC_MANUAL = 0;
    public static final int LOG_SRC_QUOTED = 1;
    public static final int LOG_SRC_NO_STOCK = 2;
    public static final int LOG_SRC_WITHDRAWN = 3;
    public static final int LOG_SRC_REMINDER = 4;

    /** 渠道角色 */
    public static final int CHANNEL_PRIMARY = 1;
    public static final int CHANNEL_BACKUP = 2;
    public static final int MAX_BACKUP_CHANNELS = 5;

    /** 专项状态 */
    public static final int PROJECT_ACTIVE = 1;
    public static final int PROJECT_DONE = 2;
    public static final int PROJECT_CANCELLED = 3;

    /** 统计窗口 */
    public static final int PRIORITY_WINDOW_DAYS = 90;
    public static final int FIRST_BACKFILL_DAYS = 180;
    public static final int MULTI_DEFER = 3;

    /** 系统设置 */
    public static final String CFG_EVIDENCE_DAYS = "supply.evidence-valid-days";
    public static final int DEFAULT_EVIDENCE_DAYS = 30;

    /** 作战卡所在的 sop_stage 板块 */
    public static final int SOP_BOARD = 6;

    /** 未成交原因里属于货源类的分类前缀（字典 quotation_lost_reason 的名称） */
    public static final String LOST_SUPPLY_PREFIX = "【交期/物流】";

    /** 按钮权限 */
    public static final String PERM_FOLLOWUP_EDIT = "supply:followup:edit";
    public static final String PERM_TEAM = "supply:followup:team";
    public static final String PERM_PROJECT_EDIT = "supply:project:edit";
    public static final String PERM_COVERAGE_EDIT = "supply:coverage:edit";
    public static final String PERM_SUPPLIER_VIEW = "inquiry:supplier:view";
    public static final String PERM_DEV_EDIT = "supply:dev:edit";

    // ---------------------------------------------------------------- 第二期：供应商开发（V1.5.35）

    /** 合格供应商看近多少天的证据（含今天） */
    public static final int DEV_WINDOW_DAYS = 90;
    /** 已报价超过多少天还没关联供应商库时提醒 */
    public static final int DEV_UNLINKED_REMIND_DAYS = 7;
    public static final int DEV_TARGET_MAX = 10;

    /** 热度，与优先级同值 */
    public static final int HEAT_LOW = 1;
    public static final int HEAT_MID = 2;
    public static final int HEAT_HIGH = 3;

    /** 候选阶段 */
    public static final int CAND_TODO = 1;
    public static final int CAND_CONTACTED = 2;
    public static final int CAND_QUOTED = 3;
    public static final int CAND_QUALIFIED = 4;
    public static final int CAND_ABANDONED = 5;

    /** 候选来源 */
    public static final int CAND_SRC_SEARCH = 1;
    public static final int CAND_SRC_SUPPLIER_LIB = 5;
    public static final int CAND_SRC_OTHER = 6;

    /** 处理记录的对象类型 */
    public static final int OBJ_DEMAND = 1;
    public static final int OBJ_CANDIDATE = 2;

    /** 要求家数：高 3、中 2、低 1 */
    public static int requiredSuppliers(int heat) {
        return heat >= HEAT_HIGH ? 3 : heat == HEAT_MID ? 2 : 1;
    }
}

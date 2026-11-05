package com.zhul.erp.modules.crm.followup.constants;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 客户跟进常量，码值与 V1.5.5__customer_followup.sql 的字段注释一致 */
public final class FollowupConstants {

    private FollowupConstants() {
    }

    /** 板块 */
    public static final int BOARD_DEVELOP = 1;
    public static final int BOARD_SALES = 2;
    public static final int BOARD_FULFIL = 3;
    public static final int BOARD_AFTER_SALES = 4;
    public static final int BOARD_ACCOUNT = 5;

    /** 来源单据类型 */
    public static final int TARGET_OPPORTUNITY = 1;
    public static final int TARGET_INQUIRY = 2;
    public static final int TARGET_QUOTATION = 3;
    public static final int TARGET_PI = 4;
    public static final int TARGET_ORDER = 5;

    /** 阶段分组 */
    public static final int GROUP_BEFORE_QUOTE = 1;
    public static final int GROUP_AFTER_QUOTE = 2;
    public static final int GROUP_AFTER_PI = 3;
    /** 订单履约：采购中（F1–F3）、验收发货（F4–F5）、在途（F6） */
    public static final int GROUP_PURCHASING = 4;
    public static final int GROUP_SHIPPING = 5;
    public static final int GROUP_IN_TRANSIT = 6;
    /** 售后评价：签收（A1）、使用（A2）、问题（A3–A4）、满意评价（A5–A6） */
    public static final int GROUP_SIGN = 7;
    public static final int GROUP_USE = 8;
    public static final int GROUP_ISSUE = 9;
    public static final int GROUP_REVIEW = 10;
    public static final Map<Integer, String> GROUP_NAMES = Map.of(
            GROUP_BEFORE_QUOTE, "报价前", GROUP_AFTER_QUOTE, "报价后", GROUP_AFTER_PI, "PI 后",
            GROUP_PURCHASING, "采购中", GROUP_SHIPPING, "验收发货", GROUP_IN_TRANSIT, "在途",
            GROUP_SIGN, "签收", GROUP_USE, "使用", GROUP_ISSUE, "问题", GROUP_REVIEW, "满意评价");

    /** 下一步来源 */
    public static final int NEXT_SYSTEM = 1;
    public static final int NEXT_USER = 2;
    /** 系统一次性提醒（培育到期、延后的系统提醒）：日期固定，不再按节奏刷新 */
    public static final int NEXT_SYSTEM_ONCE = 3;
    /** 来自售后单的下一步（A3、A4）：只读显示，不进今日待办 */
    public static final int NEXT_AFTER_SALES = 4;

    /** 移出原因 */
    public static final int CLOSE_NONE = 0;
    public static final int CLOSE_DOC_DONE = 1;
    public static final int CLOSE_REPLACED = 2;
    public static final int CLOSE_OPPORTUNITY = 3;
    public static final int CLOSE_DOC_GONE = 4;
    public static final int CLOSE_AFTER_SALES = 5;

    /** 记录类型 */
    public static final int RECORD_USER = 1;
    public static final int RECORD_SYSTEM = 2;

    /** 客户情况位掩码 */
    public static final int SITUATION_FEEDBACK = 1;
    public static final int SITUATION_NEGOTIATING = 2;
    public static final int SITUATION_NO_REPLY = 4;

    /** 方式 */
    public static final Map<Integer, String> CHANNEL_NAMES = Map.of(1, "WhatsApp", 2, "邮件", 3, "电话", 4, "其他");

    /** 商机成交板块的阶段编码 */
    public static final String S1 = "S1";
    public static final String S3 = "S3";
    public static final String S4 = "S4";
    public static final String S5 = "S5";
    public static final String S6 = "S6";
    public static final String S7 = "S7";
    public static final List<String> SALES_STAGES = List.of("S1", "S2", "S3", "S4", "S5", "S6", "S7");
    /** 订单履约、售后评价的阶段编码 */
    public static final List<String> FULFIL_STAGES = List.of("F1", "F2", "F3", "F4", "F5", "F6");
    public static final List<String> AFTER_SALES_STAGES = List.of("A1", "A2", "A3", "A4", "A5", "A6");

    /** 订单履约节点（node_code） */
    public static final String NODE_CREATED = "created";
    public static final String NODE_ORDERED = "ordered";
    public static final String NODE_SHOT = "shot";
    public static final String NODE_SHIPPED = "shipped";
    public static final String NODE_EXCEPTION = "exception";

    /** 履约异常位 */
    public static final int EX_PURCHASE_OVERDUE = 1;
    public static final int EX_DISCREPANCY = 2;
    public static final int EX_OUTBOUND_WITHDRAWN = 4;
    public static final Map<Integer, String> EXCEPTION_NAMES = Map.of(EX_PURCHASE_OVERDUE, "采购超期未到仓",
            EX_DISCREPANCY, "有到货差异待处理", EX_OUTBOUND_WITHDRAWN, "出库单已撤回");

    /** 预计送达来源 */
    public static final int ETA_NONE = 0;
    public static final int ETA_ESTIMATED = 1;
    public static final int ETA_USER = 2;

    /** 履约「沟通了什么」位掩码 */
    public static final int NOTICE_WAYBILL = 1;
    public static final int NOTICE_NEW_DATE = 2;
    public static final int NOTICE_NEED_CUSTOMER = 4;
    public static final int NOTICE_NO_REPLY = 8;
    /** 告诉了客户的节点（「客户要配合」「没回复」不算） */
    public static final Map<Integer, String> NOTICE_TOLD_NAMES = Map.of(NOTICE_WAYBILL, "运单号和发货照片",
            NOTICE_NEW_DATE, "新的交期 / 送达日");

    /** 签收确认、使用确认的回答 */
    public static final int RECEIPT_OK = 1;
    public static final int RECEIPT_ISSUE = 2;
    public static final int RECEIPT_NOT_YET = 3;
    public static final int USE_OK = 1;
    public static final int USE_ISSUE = 2;
    public static final int USE_NOT_YET = 3;
    public static final List<String> ISSUE_TYPES = List.of("少件", "破损", "型号不对", "其他");

    /** 满意度与原因 */
    public static final int SATISFIED = 1;
    public static final int SATISFACTION_NEUTRAL = 2;
    public static final int DISSATISFIED = 3;
    public static final Map<Integer, String> SATISFACTION_NAMES = Map.of(SATISFIED, "满意",
            SATISFACTION_NEUTRAL, "一般", DISSATISFIED, "不满意");
    public static final Map<String, String> DISSATISFIED_REASONS = Map.of("QUALITY", "产品质量", "DELIVERY", "交期",
            "COMMUNICATION", "沟通", "PRICE", "价格", "OTHER", "其他");

    /** 评价状态（跟进对象上 1–5；跟进记录上另有 6-还没评） */
    public static final int REVIEW_NONE = 0;
    public static final int REVIEW_SKIP = 1;
    public static final int REVIEW_INVITED = 2;
    public static final int REVIEW_DONE = 3;
    public static final int REVIEW_DECLINED = 4;
    public static final int REVIEW_MISSED = 5;
    public static final int REVIEW_NOT_YET = 6;
    public static final Map<Integer, String> REVIEW_NAMES = Map.of(REVIEW_SKIP, "这次先不邀请", REVIEW_INVITED, "已邀请",
            REVIEW_DONE, "已评价", REVIEW_DECLINED, "婉拒", REVIEW_MISSED, "未评价", REVIEW_NOT_YET, "还没评");

    /** 上线补生成售后评价 A1：预计送达在这么多天之内 */
    public static final int BOOTSTRAP_A1_DAYS = 30;

    /** 五个板块，链路卡片顺序；商机成交、订单履约、售后评价已上线 */
    public static final List<String> BOARD_NAMES = List.of("主动开发", "商机成交", "订单履约", "售后评价", "客户经营");

    /** 系统参数键（design D6、D7） */
    public static final String CFG_QUOTE_DAYS = "sales.followup.quote.days";
    public static final String CFG_PI_INTERVAL = "sales.followup.pi.interval-days";
    public static final String CFG_NURTURE_DEFAULT = "sales.followup.nurture.default-days";
    public static final String CFG_NURTURE_QUOTE_DAYS = "sales.followup.nurture.quote-days";
    public static final String CFG_NURTURE_NO_REPLY = "sales.followup.nurture.no-reply-times";
    public static final String CFG_AMOUNT_TIERS = "sales.followup.amount-tiers";
    public static final String CFG_EXPRESS_CARRIERS = "sales.followup.transit.express-carriers";
    public static final String CFG_EXPRESS_DAYS = "sales.followup.transit.express-days";
    public static final String CFG_OTHER_DAYS = "sales.followup.transit.other-days";
    public static final String CFG_REVIEW_DAYS = "sales.followup.review.remind-days";
    public static final String CFG_REVIEW_TIMES = "sales.followup.review.remind-times";

    /** 多次延后：连续延后达到这个次数，主管查看团队时标出 */
    public static final int SNOOZE_WARN_TIMES = 3;
    /** 列表每次加载条数 */
    public static final int PAGE_SIZE = 20;
    /** 下一步最长 */
    public static final int NEXT_ACTION_MAX = 200;
    /** 粘贴原文最多保留的字数（AI 整理也只取最后这么多字） */
    public static final int RAW_MAX = 20000;

    /** 按钮权限 */
    public static final String PERM_RECORD = "crm:followup:record";
    public static final String PERM_TEAM = "crm:followup:team";
    public static final String MENU_PATH = "/sales/followup";

    /** 记跟进时可补的档案字段 → 中文名（与 CustomerProfileService.FOLLOWUP_FIELDS 一致） */
    public static final Map<String, String> PROFILE_LABELS = Map.of("mainProducts", "主营产品", "cnSuppliers", "中国供应商",
            "city", "城市", "website", "官网", "linkedin", "LinkedIn", "socialUrl", "社媒主页");

    /** AI 技能（编排服务 scripts/ai-orchestrator/skills/） */
    public static final String SKILL_EXTRACT = "followup-extract";
    public static final String SKILL_REPLY = "followup-reply";
    /** 「AI 写一条」的意图（第二期 design D13）：空为按卡点写跟进，另有物流更新、评价邀请 */
    public static final String INTENT_LOGISTICS = "logistics_update";
    public static final String INTENT_REVIEW = "review_invite";
    public static final Set<String> REPLY_INTENTS = Set.of(INTENT_LOGISTICS, INTENT_REVIEW);
    /** 英文回复同步等待的上限 */
    public static final int REPLY_TIMEOUT_SECONDS = 30;
    public static final String ERROR_AI_NOT_CONFIGURED = "AI_NOT_CONFIGURED";
    public static final String ERROR_AI_FAILED = "AI_FAILED";
    public static final String ERROR_AI_TIMEOUT = "AI_TIMEOUT";
    public static final String ERROR_STAGE_MISMATCH = "FOLLOWUP_STAGE_MISMATCH";
}

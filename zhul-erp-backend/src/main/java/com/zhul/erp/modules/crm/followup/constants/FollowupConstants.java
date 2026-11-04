package com.zhul.erp.modules.crm.followup.constants;

import java.util.List;
import java.util.Map;

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

    /** 阶段分组 */
    public static final int GROUP_BEFORE_QUOTE = 1;
    public static final int GROUP_AFTER_QUOTE = 2;
    public static final int GROUP_AFTER_PI = 3;
    public static final Map<Integer, String> GROUP_NAMES = Map.of(
            GROUP_BEFORE_QUOTE, "报价前", GROUP_AFTER_QUOTE, "报价后", GROUP_AFTER_PI, "PI 后");

    /** 下一步来源 */
    public static final int NEXT_SYSTEM = 1;
    public static final int NEXT_USER = 2;
    /** 系统一次性提醒（培育到期、延后的系统提醒）：日期固定，不再按节奏刷新 */
    public static final int NEXT_SYSTEM_ONCE = 3;

    /** 移出原因 */
    public static final int CLOSE_NONE = 0;
    public static final int CLOSE_DOC_DONE = 1;
    public static final int CLOSE_REPLACED = 2;
    public static final int CLOSE_OPPORTUNITY = 3;
    public static final int CLOSE_DOC_GONE = 4;

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

    /** 五个板块，链路卡片顺序；本期只有商机成交上线 */
    public static final List<String> BOARD_NAMES = List.of("主动开发", "商机成交", "订单履约", "售后评价", "客户经营");

    /** 系统参数键（design D6、D7） */
    public static final String CFG_QUOTE_DAYS = "sales.followup.quote.days";
    public static final String CFG_PI_INTERVAL = "sales.followup.pi.interval-days";
    public static final String CFG_NURTURE_DEFAULT = "sales.followup.nurture.default-days";
    public static final String CFG_NURTURE_QUOTE_DAYS = "sales.followup.nurture.quote-days";
    public static final String CFG_NURTURE_NO_REPLY = "sales.followup.nurture.no-reply-times";
    public static final String CFG_AMOUNT_TIERS = "sales.followup.amount-tiers";

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
    /** 英文回复同步等待的上限 */
    public static final int REPLY_TIMEOUT_SECONDS = 30;
    public static final String ERROR_AI_NOT_CONFIGURED = "AI_NOT_CONFIGURED";
    public static final String ERROR_AI_FAILED = "AI_FAILED";
    public static final String ERROR_AI_TIMEOUT = "AI_TIMEOUT";
}

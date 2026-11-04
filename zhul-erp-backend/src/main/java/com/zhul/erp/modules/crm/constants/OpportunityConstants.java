package com.zhul.erp.modules.crm.constants;

import java.util.Map;
import java.util.Set;

/** 商机常量，码值与 V1.2.12__opportunity.sql 的字段注释一致 */
public final class OpportunityConstants {

    private OpportunityConstants() {
    }

    public static final String CODE_PREFIX = "OPP";

    /** 阶段类别 */
    public static final int CATEGORY_ACTIVE = 1;
    public static final int CATEGORY_WON = 2;
    public static final int CATEGORY_LOST = 3;
    public static final int CATEGORY_INVALID = 4;

    public static final String STAGE_FIRST = "S1";
    /** 已报价：关联询盘生成报价单时自动推进到这里 */
    public static final String STAGE_QUOTED = "S4";
    public static final String STAGE_WON = "WON";
    public static final String STAGE_LOST = "LOST";
    public static final String STAGE_INVALID = "INVALID";

    /** 阶段记录动作 */
    public static final int ACTION_REGISTER = 1;
    public static final int ACTION_CHANGE = 2;
    public static final int ACTION_CLOSE = 3;
    public static final int ACTION_REOPEN = 4;

    /** 无效原因 */
    public static final Map<Integer, String> INVALID_REASONS = Map.of(
            1, "同行套价", 2, "垃圾询盘", 3, "联系不上", 4, "需求不符", 9, "其他");
    /**
     * 输单原因：按 SOP 卡点字典（sop_blocker.lost_reason），加无货源、客户取消、其他。
     * 原有码值 11–15、19 的文字不变；16、17、18、20–23 为客户跟进新增（openspec customer-followup）。按弹窗显示顺序排列。
     */
    public static final Map<Integer, String> LOST_REASONS = lostReasons();

    private static Map<Integer, String> lostReasons() {
        Map<Integer, String> m = new java.util.LinkedHashMap<>(16);
        m.put(11, "价格");
        m.put(16, "技术");
        m.put(17, "付款");
        m.put(12, "交期");
        m.put(18, "信任");
        m.put(20, "预算");
        m.put(15, "选择了竞争对手");
        m.put(21, "内部审批");
        m.put(22, "项目延期");
        m.put(23, "无回复");
        m.put(13, "无货源");
        m.put(14, "客户取消");
        m.put(19, "其他");
        return java.util.Collections.unmodifiableMap(m);
    }

    /** 自动推进、自动赢单的阶段记录操作人 */
    public static final String OPERATOR_SYSTEM = "系统";

    /** 手动赢单被拒绝时的提示：赢单只由收款到账自动触发 */
    public static final String AUTO_WIN_MESSAGE = "收款到账后系统自动赢单";

    /** 需求附件：图片与 Excel，单个 ≤10MB，每条商机 ≤10 个 */
    public static final Set<String> ATTACHMENT_EXTS = Set.of("jpg", "png", "xlsx", "xls", "csv");
    public static final long ATTACHMENT_MAX_BYTES = 10L * 1024 * 1024;
    public static final int MAX_ATTACHMENTS = 10;
    public static final String ATTACHMENT_TYPE_MESSAGE = "只支持图片（JPG、PNG）和 Excel";

    /** S1 超过这么多天没有推进，列表概览里单独计数 */
    public static final int STALE_DAYS = 2;
    /** 统计日期范围上限 */
    public static final int STATS_MAX_DAYS = 366;

    /** 来源渠道「阿里巴巴国际站」与阿里等级 */
    public static final int CHANNEL_ALIBABA = com.zhul.erp.modules.masterdata.constants.CustomerConstants.CHANNEL_ALIBABA;

    public static final String ERROR_EXISTS = "OPPORTUNITY_EXISTS";
}

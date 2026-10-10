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
    /** 输单原因 */
    public static final Map<Integer, String> LOST_REASONS = Map.of(
            11, "价格", 12, "交期", 13, "无货源", 14, "客户取消", 15, "选择了竞争对手", 19, "其他");

    /** 需求附件：图片与 Excel，单个 ≤10MB，每条商机 ≤10 个 */
    public static final String ATTACHMENT_MODULE = "opportunity";
    public static final Set<String> ATTACHMENT_EXTS = Set.of("jpg", "png", "xlsx", "xls", "csv");
    public static final long ATTACHMENT_MAX_BYTES = 10L * 1024 * 1024;
    public static final int MAX_ATTACHMENTS = 10;
    public static final String ATTACHMENT_TYPE_MESSAGE = "只支持图片（JPG、PNG）和 Excel";

    /** S1 超过这么多天没有推进，列表概览里单独计数 */
    public static final int STALE_DAYS = 2;
    /** 统计日期范围上限 */
    public static final int STATS_MAX_DAYS = 366;

    public static final String ERROR_EXISTS = "OPPORTUNITY_EXISTS";
}

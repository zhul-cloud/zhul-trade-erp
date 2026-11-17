package com.zhul.erp.modules.product.content.constants;

import java.util.List;

/** 内容任务常量 */
public final class ContentConstants {

    private ContentConstants() {
    }

    /** 任务阶段：资料待补、资料待核、营销待生成、已完成 */
    public static final int STAGE_FACTS_PENDING = 1;
    public static final int STAGE_FACTS_REVIEW = 2;
    public static final int STAGE_MARKETING = 3;
    public static final int STAGE_DONE = 4;

    /** 包类型：资料包（中文，写共享商品库）、营销包（英文、俄文，写本公司） */
    public static final String TYPE_FACTS = "facts";
    public static final String TYPE_MARKETING = "marketing";

    public static final String FACTS_LANG = "zh";
    /** 独立站营销内容的语言 */
    public static final List<String> MARKETING_LANGS = List.of("en", "ru");

    /** 本公司启用的营销平台（sys_config，平台模板 tenant 0，公司可覆盖；逗号分隔的平台编码） */
    public static final String CONFIG_PLATFORMS = "product.content.platforms";

    /** 关键词档位：核心、主要、长尾 */
    public static final int TIER_CORE = 1;
    public static final int TIER_MAIN = 2;
    public static final int TIER_LONG_TAIL = 3;
    public static final int MAX_KEYWORDS = 200;

    public static final int MAX_FILE_BYTES = 1024 * 1024;
    public static final int MAX_PACKAGE_PRODUCTS = 200;

    public static final String TARGET_SHARED = "shared";
    public static final String TARGET_COMPANY = "company";

    /** 上传记录来源：手动上传、AI 生成 */
    public static final int SOURCE_MANUAL = 1;
    public static final int SOURCE_AI = 2;

    /** AI 生成（spec「AI 生成资料与营销内容」）：编排服务技能、技能来源版本 */
    public static final String AI_SKILL = "product-content";
    public static final String AI_SKILL_VERSION = "zhul-skills@6bcd5f920c";
    /** AI 生成状态：排队中、生成中、待确认、已导入、失败、已取消 */
    public static final int AI_QUEUED = 1;
    public static final int AI_RUNNING = 2;
    public static final int AI_PENDING = 3;
    public static final int AI_IMPORTED = 4;
    public static final int AI_FAILED = 5;
    public static final int AI_CANCELLED = 6;
    /** 未结束（不能对同一商品再次发起）的状态 */
    public static final List<Integer> AI_OPEN = List.of(AI_QUEUED, AI_RUNNING, AI_PENDING);
    /** 一次最多发起的商品数、同一公司同时生成中的上限、待确认超过这么多天自动取消 */
    public static final int AI_MAX_BATCH = 20;
    public static final int AI_MAX_RUNNING = 3;
    public static final int AI_PENDING_DAYS = 30;

    /** 兼容型号写成「兼容」型号关系，置信度未知 */
    public static final int RELATIONSHIP_COMPATIBLE = 4;
    public static final int CONFIDENCE_UNKNOWN = 5;
}

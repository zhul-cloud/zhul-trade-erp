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

    /** 兼容型号写成「兼容」型号关系，置信度未知 */
    public static final int RELATIONSHIP_COMPATIBLE = 4;
    public static final int CONFIDENCE_UNKNOWN = 5;
}

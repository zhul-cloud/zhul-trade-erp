package com.zhul.erp.modules.product.constants;

/** 商品模块通用常量。 */
public final class ProductConstants {

    private ProductConstants() {
    }

    /** 平台级共享数据的租户ID，本模块所有行的 tenant_id 恒为 0 */
    public static final int PLATFORM_TENANT_ID = 0;

    /** 平台品牌 Others（迁移 V1.5.26 种子数据，按名称查找）：AI 认不出品牌的商品候选统一归到这里 */
    public static final String OTHERS_BRAND = "Others";

    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;

    /** 兜底一级品类「其他」的编码（其下没有细分品类） */
    public static final String CATEGORY_OTHER_CODE = "other";
    /** AI 细分品类状态（product.ai_category_state）：0-无、1-待确认建议、2-已忽略 */
    public static final int AI_CATEGORY_NONE = 0;
    public static final int AI_CATEGORY_PENDING = 1;
    public static final int AI_CATEGORY_IGNORED = 2;

    /** 品牌选项缓存，Cache-Aside，TTL 300s，写库事务提交后删除 */
    /** 品牌、品类简介的长度上限 */
    public static final int DESCRIPTION_MAX = 500;

    /** 品牌等级：0-普通、1-常做、2-核心 */
    public static final int BRAND_LEVEL_NORMAL = 0;
    public static final int BRAND_LEVEL_FREQUENT = 1;
    public static final int BRAND_LEVEL_CORE = 2;

    public static final String CACHE_KEY_BRAND_OPTIONS = "zhul:erp:list:0:product_brand";
    /** 品类选项缓存，规则同上 */
    public static final String CACHE_KEY_CATEGORY_OPTIONS = "zhul:erp:list:0:product_category";
    public static final long OPTIONS_CACHE_TTL_SECONDS = 300L;

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
}

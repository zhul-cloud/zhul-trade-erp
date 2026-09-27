package com.zhul.erp.modules.product.constants;

/** 商品模块通用常量。 */
public final class ProductConstants {

    private ProductConstants() {
    }

    /** 平台级共享数据的租户ID，本模块所有行的 tenant_id 恒为 0 */
    public static final int PLATFORM_TENANT_ID = 0;

    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;

    /** 品牌选项缓存，Cache-Aside，TTL 300s，写库事务提交后删除 */
    /** 品牌、品类简介的长度上限 */
    public static final int DESCRIPTION_MAX = 500;

    public static final String CACHE_KEY_BRAND_OPTIONS = "zhul:erp:list:0:product_brand";
    /** 品类选项缓存，规则同上 */
    public static final String CACHE_KEY_CATEGORY_OPTIONS = "zhul:erp:list:0:product_category";
    public static final long OPTIONS_CACHE_TTL_SECONDS = 300L;

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
}

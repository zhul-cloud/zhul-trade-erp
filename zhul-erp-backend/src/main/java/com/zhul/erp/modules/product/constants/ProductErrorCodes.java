package com.zhul.erp.modules.product.constants;

/** 商品模块错误码，通过 BizException.of(...) 放进响应的 data.errorCode（design.md 决策 10）。 */
public final class ProductErrorCodes {

    private ProductErrorCodes() {
    }

    public static final String PLATFORM_ADMIN_REQUIRED = "PLATFORM_ADMIN_REQUIRED";

    public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
    public static final String PRODUCT_DUPLICATE = "PRODUCT_DUPLICATE";
    public static final String PRODUCT_MPN_INVALID = "PRODUCT_MPN_INVALID";
    public static final String PRODUCT_MPN_IMMUTABLE = "PRODUCT_MPN_IMMUTABLE";
    public static final String PRODUCT_IN_USE = "PRODUCT_IN_USE";
    public static final String PRODUCT_SERIES_MISMATCH = "PRODUCT_SERIES_MISMATCH";
    public static final String PRODUCT_LIFECYCLE_SOURCE_REQUIRED = "PRODUCT_LIFECYCLE_SOURCE_REQUIRED";

    public static final String BRAND_NOT_FOUND = "BRAND_NOT_FOUND";
    public static final String BRAND_DUPLICATE = "BRAND_DUPLICATE";
    public static final String BRAND_IN_USE = "BRAND_IN_USE";

    public static final String CATEGORY_NOT_FOUND = "CATEGORY_NOT_FOUND";
    public static final String CATEGORY_DUPLICATE = "CATEGORY_DUPLICATE";
    public static final String CATEGORY_IN_USE = "CATEGORY_IN_USE";
    public static final String CATEGORY_CODE_IMMUTABLE = "CATEGORY_CODE_IMMUTABLE";

    public static final String SERIES_NOT_FOUND = "SERIES_NOT_FOUND";
    public static final String SERIES_DUPLICATE = "SERIES_DUPLICATE";
    public static final String SERIES_IN_USE = "SERIES_IN_USE";

    public static final String RELATIONSHIP_INVALID = "RELATIONSHIP_INVALID";
    public static final String RELATIONSHIP_DUPLICATE = "RELATIONSHIP_DUPLICATE";

    public static final String CONTENT_NOT_FOUND = "CONTENT_NOT_FOUND";
    public static final String CONTENT_DUPLICATE = "CONTENT_DUPLICATE";
    public static final String DOCUMENT_URL_INVALID = "DOCUMENT_URL_INVALID";
    public static final String FAQ_NOT_PENDING = "FAQ_NOT_PENDING";

    public static final String MEDIA_URL_INVALID = "MEDIA_URL_INVALID";
    public static final String MEDIA_FILE_INVALID = "MEDIA_FILE_INVALID";
    public static final String MEDIA_TOO_LARGE = "MEDIA_TOO_LARGE";
    public static final String MEDIA_NOT_IMAGE = "MEDIA_NOT_IMAGE";

    public static final String LOGISTICS_INVALID = "LOGISTICS_INVALID";
    public static final String HS_CODE_INVALID = "HS_CODE_INVALID";
    public static final String COUNTRY_CODE_INVALID = "COUNTRY_CODE_INVALID";
    public static final String PRICE_INVALID = "PRICE_INVALID";
    public static final String CURRENCY_REQUIRED = "CURRENCY_REQUIRED";
    public static final String RATE_LIMITED = "RATE_LIMITED";

    public static final String PARAM_INVALID = "PARAM_INVALID";
}

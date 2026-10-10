package com.zhul.erp.modules.product.content.constants;

import java.util.List;

/** 内容任务常量 */
public final class ContentConstants {

    private ContentConstants() {
    }

    public static final int STATUS_PENDING = 1;
    public static final int STATUS_PARTIAL = 2;
    public static final int STATUS_DONE = 3;

    public static final List<String> LANGS = List.of("zh", "en", "ru");

    public static final int MAX_FILE_BYTES = 1024 * 1024;
    public static final int MAX_PACKAGE_PRODUCTS = 200;

    public static final String TARGET_SHARED = "shared";
    public static final String TARGET_COMPANY = "company";

    /** 兼容型号写成「兼容」型号关系，置信度未知 */
    public static final int RELATIONSHIP_COMPATIBLE = 4;
    public static final int CONFIDENCE_UNKNOWN = 5;
}

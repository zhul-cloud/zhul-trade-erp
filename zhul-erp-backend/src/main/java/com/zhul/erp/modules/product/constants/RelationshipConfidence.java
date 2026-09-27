package com.zhul.erp.modules.product.constants;

/** 型号关系置信度（product_relationship.confidence）。4/5 时下游不得展示为"推荐替代"类强断言。 */
public final class RelationshipConfidence {

    private RelationshipConfidence() {
    }

    public static final int VERIFIED = 1;
    public static final int HIGH = 2;
    public static final int MEDIUM = 3;
    public static final int LOW = 4;
    public static final int UNKNOWN = 5;

    public static boolean isValid(Integer value) {
        return value != null && value >= VERIFIED && value <= UNKNOWN;
    }
}

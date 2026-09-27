package com.zhul.erp.modules.product.constants;

/** 型号关系类型（product_relationship.relationship_type）。3/4/5/6 为对称类型，1/2 非对称。 */
public final class RelationshipType {

    private RelationshipType() {
    }

    public static final int OFFICIAL_REPLACEMENT = 1;
    public static final int SUCCESSOR = 2;
    public static final int FUNCTIONAL_REPLACEMENT = 3;
    public static final int COMPATIBLE = 4;
    public static final int CROSS_REFERENCE = 5;
    public static final int SAME_SERIES = 6;

    public static boolean isValid(Integer value) {
        return value != null && value >= OFFICIAL_REPLACEMENT && value <= SAME_SERIES;
    }

    public static boolean isSymmetric(Integer value) {
        return value != null && value >= FUNCTIONAL_REPLACEMENT && value <= SAME_SERIES;
    }
}

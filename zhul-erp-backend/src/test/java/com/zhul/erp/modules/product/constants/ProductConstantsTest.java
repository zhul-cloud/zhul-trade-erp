package com.zhul.erp.modules.product.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 常量取值必须与 design.md 决策 11 的字段注释一致，改动其中一处要同步改另一处。 */
class ProductConstantsTest {

    @Test
    void lifecycleValuesMatchDesign() {
        assertEquals(1, LifecycleStatus.ACTIVE);
        assertEquals(2, LifecycleStatus.CURRENT);
        assertEquals(3, LifecycleStatus.LEGACY);
        assertEquals(4, LifecycleStatus.DISCONTINUED);
        assertEquals(5, LifecycleStatus.OBSOLETE);
        assertEquals(6, LifecycleStatus.UNKNOWN);
        assertTrue(LifecycleStatus.isValid(1));
        assertTrue(LifecycleStatus.isValid(6));
        assertFalse(LifecycleStatus.isValid(0));
        assertFalse(LifecycleStatus.isValid(7));
        assertFalse(LifecycleStatus.isValid(null));
    }

    @Test
    void discontinuedAndObsoleteRequireSource() {
        assertTrue(LifecycleStatus.requiresSource(4));
        assertTrue(LifecycleStatus.requiresSource(5));
        assertFalse(LifecycleStatus.requiresSource(1));
        assertFalse(LifecycleStatus.requiresSource(3));
        assertFalse(LifecycleStatus.requiresSource(6));
    }

    @Test
    void relationshipTypesMatchDesign() {
        assertEquals(1, RelationshipType.OFFICIAL_REPLACEMENT);
        assertEquals(2, RelationshipType.SUCCESSOR);
        assertEquals(3, RelationshipType.FUNCTIONAL_REPLACEMENT);
        assertEquals(4, RelationshipType.COMPATIBLE);
        assertEquals(5, RelationshipType.CROSS_REFERENCE);
        assertEquals(6, RelationshipType.SAME_SERIES);
        assertFalse(RelationshipType.isValid(0));
        assertFalse(RelationshipType.isValid(7));
        assertFalse(RelationshipType.isValid(null));
    }

    @Test
    void symmetricTypesAreThreeToSix() {
        assertFalse(RelationshipType.isSymmetric(1));
        assertFalse(RelationshipType.isSymmetric(2));
        for (int type = 3; type <= 6; type++) {
            assertTrue(RelationshipType.isSymmetric(type), "类型 " + type + " 应为对称");
        }
        assertFalse(RelationshipType.isSymmetric(null));
    }

    @Test
    void confidenceValuesMatchDesign() {
        assertEquals(1, RelationshipConfidence.VERIFIED);
        assertEquals(2, RelationshipConfidence.HIGH);
        assertEquals(3, RelationshipConfidence.MEDIUM);
        assertEquals(4, RelationshipConfidence.LOW);
        assertEquals(5, RelationshipConfidence.UNKNOWN);
        assertFalse(RelationshipConfidence.isValid(0));
        assertFalse(RelationshipConfidence.isValid(6));
        assertFalse(RelationshipConfidence.isValid(null));
    }

    @Test
    void platformTenantIsZero() {
        assertEquals(0, ProductConstants.PLATFORM_TENANT_ID);
    }
}

package com.zhul.erp.modules.product;

import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** data_v1.2.sql 的商品域按钮权限能被现有 PermissionChecker 识别（任务 2.3）。 */
class ProductPermissionSeedIntegrationTest extends IntegrationTestBase {

    private static final String ROLE = "IT_PRODUCT";
    private static final String USERNAME = "it_product_user";
    private static final int BRAND_ADD_RESOURCE_ID = 110101;

    @Autowired
    private PermissionChecker perm;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag) values (?, 0, ?, ?, 0)",
                99000001, 99000001, USERNAME);
        jdbc.update("insert into user_basic (id, tenant_id, name, username, role_code) values (?, 0, 'IT', ?, ?)",
                99000001, USERNAME, ROLE);
        jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, 'RS3110101')",
                ROLE, BRAND_ADD_RESOURCE_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USERNAME, null, List.of()));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        jdbc.update("delete from role_resource where role_code = ?", ROLE);
        jdbc.update("delete from user_basic where username = ?", USERNAME);
        jdbc.update("delete from account where username = ?", USERNAME);
    }

    @Test
    void seedContainsAllTwelveProductButtons() {
        Integer buttons = jdbc.queryForObject(
                "select count(*) from resource where type = 3 and permission like 'product:%'", Integer.class);
        assertEquals(12, buttons);
    }

    @Test
    void assignedProductPermissionIsGranted() {
        assertTrue(perm.has("product:brand:add"));
    }

    @Test
    void unassignedProductPermissionIsDenied() {
        assertFalse(perm.has("product:brand:delete"));
        assertFalse(perm.has("product:product:add"));
    }
}

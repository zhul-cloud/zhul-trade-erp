package com.zhul.erp.framework.security;

import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证：租户套餐的 menu_ids 能真正约束该租户账号（不管是不是 admin_flag=1 的
 * 租户管理员）最终看到的菜单和能调的按钮接口——对应"福州四记"这个真实反馈的场景：
 * 套餐没勾的菜单，即使是租户自己的管理员账号登录也不该看到。
 */
class EffectivePermissionIntegrationTest extends IntegrationTestBase {

    private static final int TEST_PACKAGE_ID = 91001;
    private static final int TEST_TENANT_ID = 91001;
    private static final int TEST_ACCOUNT_ID = 91001;
    private static final String USERNAME = "package_scoped_admin";

    // 标准版模拟：只给工作台 + 商品管理目录 + 品牌管理菜单，不给品牌新增按钮，不给询盘管理
    private static final String LIMITED_MENU_IDS = "[100001,100004,100031]";

    @Autowired
    private com.zhul.erp.modules.system.service.MenuService menuService;
    @Autowired
    private PermissionChecker permissionChecker;

    @BeforeEach
    void setUpFixture() {
        cleanUp();
        jdbc.update("insert into tenant_package (id, name, menu_ids, remark, status) values (?, ?, ?, '', 1)",
                TEST_PACKAGE_ID, "集成测试-受限套餐", LIMITED_MENU_IDS);
        jdbc.update("insert into tenant (id, code, name, package_id, contact_name, contact_phone, expire_time, status) "
                        + "values (?, ?, ?, ?, '测试联系人', '13900000001', ?, 1)",
                TEST_TENANT_ID, "TN" + TEST_TENANT_ID, "集成测试受限租户", TEST_PACKAGE_ID,
                LocalDateTime.now().plusYears(1));
        jdbc.update("insert into user_basic (id, tenant_id, name, username, role_code) values (?, ?, 'IT', ?, ?)",
                TEST_ACCOUNT_ID, TEST_TENANT_ID, USERNAME, "ROLE_ADMIN");
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag) values (?, ?, ?, ?, 1)",
                TEST_ACCOUNT_ID, TEST_TENANT_ID, TEST_ACCOUNT_ID, USERNAME);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USERNAME, null, List.of()));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from account where id = ?", TEST_ACCOUNT_ID);
        jdbc.update("delete from user_basic where id = ?", TEST_ACCOUNT_ID);
        jdbc.update("delete from tenant where id = ?", TEST_TENANT_ID);
        jdbc.update("delete from tenant_package where id = ?", TEST_PACKAGE_ID);
    }

    @Test
    void tenantAdminOnlySeesMenusInsideCurrentPackage() {
        List<String> keys = menuService.getEffectiveMenuKeys(USERNAME);

        assertTrue(keys.contains("/dashboard"), "套餐包含工作台，应该看得到");
        assertTrue(keys.contains("/product/brands"), "套餐包含品牌管理，应该看得到");
        assertFalse(keys.contains("/inquiry"), "套餐没勾询盘管理，即使是租户管理员也不应该看到");
    }

    @Test
    void tenantAdminCannotCallButtonNotInPackageEvenThoughAdminFlagIsSet() {
        // 套餐里只给了「品牌管理」这个菜单本身，没给「新增品牌」这个按钮
        assertFalse(permissionChecker.has("product:brand:add"),
                "admin_flag=1 不再是万能通行证，套餐没给的按钮也不能调");
    }
}

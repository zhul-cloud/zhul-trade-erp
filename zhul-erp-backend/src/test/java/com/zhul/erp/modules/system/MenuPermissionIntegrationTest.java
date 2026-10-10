package com.zhul.erp.modules.system;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.system.dto.MenuVO;
import com.zhul.erp.modules.system.service.MenuService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 菜单权限：角色分配用的菜单树只给当前账号能访问的部分；只授权了子菜单时，登录菜单自动补上上级目录。
 */
class MenuPermissionIntegrationTest extends IntegrationTestBase {

    private static final int TENANT = 99001;
    private static final int PACKAGE = 99001;
    private static final int TENANT_ADMIN_ID = 99000031;
    private static final String TENANT_ADMIN = "it_menu_tenant_admin";
    private static final int DIR_TENANT = 100003;
    /** 菜单按部门分组后，客户在「业务管理」下，供应商在「采购管理」下 */
    private static final int DIR_BUSINESS = 100081;
    private static final int DIR_PURCHASE = 100082;
    private static final int MENU_CUSTOMER = 100061;
    private static final int MENU_SUPPLIER = 100062;

    @Autowired private MenuService menuService;

    @BeforeEach
    void setUp() {
        cleanup();
        jdbc.update("insert into tenant_package (id, name, menu_ids) values (?, 'IT-套餐', '[100061, 100062, 110141]')", PACKAGE);
        jdbc.update("insert into tenant (id, name, package_id) values (?, 'IT-租户', ?)", TENANT, PACKAGE);
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag) values (?, ?, ?, ?, 1)",
                TENANT_ADMIN_ID, TENANT, TENANT_ADMIN_ID, TENANT_ADMIN);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("delete from account where id = ?", TENANT_ADMIN_ID);
        jdbc.update("delete from tenant where id = ?", TENANT);
        jdbc.update("delete from tenant_package where id = ?", PACKAGE);
    }

    private static List<Integer> ids(List<MenuVO> tree) {
        List<Integer> result = new ArrayList<>();
        for (MenuVO m : tree) {
            result.add(m.getId());
            if (m.getChildren() != null) {
                result.addAll(ids(m.getChildren()));
            }
        }
        return result;
    }

    @Test
    void tenantAdmin_menuTreeOnlyShowsPackage_andCannotAssignOthers() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TENANT_ADMIN, null, List.of()));
        List<Integer> visible = ids(menuService.getMenuTree());
        assertFalse(visible.contains(DIR_TENANT), "租户管理员看不到租户管理");
        assertTrue(visible.containsAll(List.of(DIR_BUSINESS, DIR_PURCHASE, MENU_CUSTOMER, MENU_SUPPLIER, 110141)), visible.toString());

        BizException e = assertThrows(BizException.class,
                () -> menuService.assignRoleMenus("IT_MENU_ROLE", List.of(MENU_CUSTOMER, DIR_TENANT)));
        assertEquals("包含当前账号无权分配的菜单", e.getMessage());
    }

    @Test
    void platformAdmin_seesAllMenus() {
        loginAsAdmin("it_menu_platform");
        assertTrue(ids(menuService.getMenuTree()).contains(DIR_TENANT));
    }

    @Test
    void roleWithOnlyChildMenu_stillGetsParentDirectory() {
        loginWithResources("it_menu_staff", MENU_CUSTOMER);
        List<String> keys = menuService.getEffectiveMenuKeys("it_menu_staff");
        assertTrue(keys.contains("/business"), "只授权客户时，所在的业务管理分组也要返回：" + keys);
        assertTrue(keys.contains("/customer/list"));
        assertFalse(keys.contains("/purchase"), "没授权供应商时，采购管理分组不能出现");
        assertFalse(keys.contains("/supplier/list"));
    }
}

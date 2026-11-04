package com.zhul.erp.modules.system;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.service.UserService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** audit SEC-012：启停接口只能在启用、停用之间切换，不能借它删除用户或写入非法状态 */
class UserStatusContractTest extends IntegrationTestBase {

    private static final int TENANT_ID = 99000062;
    private static final int USER_ID = 99000062;
    private static final String USERNAME = "it_user_status";

    @Autowired private UserService userService;

    @BeforeEach
    void user() {
        cleanup();
        jdbc.update("insert into tenant (id, name, status) values (?, '启停测试租户', 1)", TENANT_ID);
        jdbc.update("insert into user_basic (id, tenant_id, name, username, status) values (?, ?, '启停测试员', ?, 1)",
                USER_ID, TENANT_ID, USERNAME);
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag, status) values (?, ?, ?, ?, 0, 1)",
                USER_ID, TENANT_ID, USER_ID, USERNAME);
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from account_access_token where account_id = ?", USER_ID);
        jdbc.update("delete from account where id = ?", USER_ID);
        jdbc.update("delete from user_basic where id = ?", USER_ID);
        jdbc.update("delete from tenant where id = ?", TENANT_ID);
    }

    private int status(String table, String idColumn) {
        return jdbc.queryForObject("select status from " + table + " where " + idColumn + " = ?", Integer.class, USER_ID);
    }

    @Test
    void onlyEnableAndDisableAreAccepted() {
        for (Integer bad : new Integer[]{UserService.STATUS_DELETED, 9, -1, null}) {
            BizException e = assertThrows(BizException.class, () -> userService.updateUserStatus(USER_ID, bad));
            assertEquals("用户状态只能是启用或停用", e.getMessage());
            assertEquals(UserService.STATUS_ENABLED, status("user_basic", "id"));
            assertEquals(UserService.STATUS_ENABLED, status("account", "user_id"));
        }

        userService.updateUserStatus(USER_ID, UserService.STATUS_DISABLED);
        assertEquals(UserService.STATUS_DISABLED, status("user_basic", "id"));
        assertEquals(UserService.STATUS_DISABLED, status("account", "user_id"));

        userService.updateUserStatus(USER_ID, UserService.STATUS_ENABLED);
        assertEquals(UserService.STATUS_ENABLED, status("user_basic", "id"));
        assertEquals(UserService.STATUS_ENABLED, status("account", "user_id"));
    }
}

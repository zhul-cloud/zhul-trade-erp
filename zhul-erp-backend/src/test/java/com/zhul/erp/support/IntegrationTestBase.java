package com.zhul.erp.support;

import com.zhul.erp.common.utils.AesUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 集成测试基类：连 zhul_erp_test 库（先执行 sql/build/test/reset-test-db.sh 建库）。
 * 每个用例开始前确认连的确实是测试库，避免配置写错时把测试数据写进开发库；
 * 用例之间不共享租户上下文。数据不自动回滚（并发用例需要真实提交），
 * 各用例自己造数据、自己清理，或使用互不冲突的唯一值。
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String TEST_DB = "zhul_erp_test";
    private static final int TEST_ACCOUNT_ID = 99000002;
    private static final String TEST_ROLE = "IT_ROLE";

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private AesUtils aesUtils;

    private boolean categoriesSnapshotted;

    @BeforeEach
    void assertUsingTestDatabase() {
        assertEquals(TEST_DB, jdbc.queryForObject("select database()", String.class),
                "集成测试必须连 " + TEST_DB + "，当前连的不是测试库，已中止");
    }

    /**
     * 会清空品类表的用例在清空前调用：先存下品类表（含种子品类），用例结束时自动恢复。
     * 询盘、询价等用例依赖种子品类，不恢复的话结果取决于测试执行顺序。
     */
    protected void snapshotCategories() {
        jdbc.update("drop table if exists it_category_snapshot");
        jdbc.update("create table it_category_snapshot as select * from product_category");
        categoriesSnapshotted = true;
    }

    @AfterEach
    void restoreCategories() {
        if (!categoriesSnapshotted) {
            return;
        }
        jdbc.update("delete from product_category");
        jdbc.update("insert into product_category select * from it_category_snapshot");
        jdbc.update("drop table it_category_snapshot");
        categoriesSnapshotted = false;
    }

    @AfterEach
    void clearTenantContextAndLogin() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        removeTestAccount();
    }

    /**
     * 以一个非超管账号登录：账号所在角色只拥有指定的资源（按钮权限）。
     * 与生产一致，PermissionChecker 会去库里查角色和资源；租户由调用方用 TenantContext 单独设置。
     */
    protected void loginWithResources(String username, int... resourceIds) {
        loginAccount(username, 0, resourceIds);
    }

    /** 以超级管理员（adminFlag=1）登录：PermissionChecker 直接放行，不查角色资源 */
    protected void loginAsAdmin(String username) {
        loginAccount(username, 1);
    }

    private void loginAccount(String username, int adminFlag, int... resourceIds) {
        removeTestAccount();
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag) values (?, 0, ?, ?, ?)",
                TEST_ACCOUNT_ID, TEST_ACCOUNT_ID, username, adminFlag);
        jdbc.update("insert into user_basic (id, tenant_id, name, username, role_code) values (?, 0, 'IT', ?, ?)",
                TEST_ACCOUNT_ID, username, TEST_ROLE);
        for (int resourceId : resourceIds) {
            jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')",
                    TEST_ROLE, resourceId);
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    /** 给租户写一份 API 方式的 AI 配置（测试 Key），供会提交 AI 任务的用例使用；用例结束后调 removeAiConfig 清理 */
    protected void seedAiConfig(int tenantId) {
        removeAiConfig(tenantId);
        jdbc.update("insert into sys_ai_config (tenant_id, mode, provider) values (?, 'api', 'claude')", tenantId);
        jdbc.update("insert into sys_ai_provider_config (tenant_id, mode, provider, api_key) values (?, 'api', 'claude', ?)",
                tenantId, aesUtils.encrypt("sk-it-test-key-0000"));
    }

    protected void removeAiConfig(int tenantId) {
        jdbc.update("delete from sys_ai_config where tenant_id = ?", tenantId);
        jdbc.update("delete from sys_ai_provider_config where tenant_id = ?", tenantId);
    }

    private void removeTestAccount() {
        jdbc.update("delete from role_resource where role_code = ?", TEST_ROLE);
        jdbc.update("delete from user_basic where id = ?", TEST_ACCOUNT_ID);
        jdbc.update("delete from account where id = ?", TEST_ACCOUNT_ID);
    }
}

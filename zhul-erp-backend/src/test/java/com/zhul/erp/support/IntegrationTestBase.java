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

import java.util.ArrayList;
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

    private static final String SNAPSHOT_PREFIX = "it_snapshot_";
    /** 本用例存过快照、结束时要恢复的表，按存快照的顺序 */
    private final List<String> snapshots = new ArrayList<>();
    /** loginWithResources 建的账号与用户所属租户，默认平台；非 0 时该租户须有套餐（权限 = 套餐 ∩ 角色） */
    protected int testUserTenant = 0;

    @BeforeEach
    void assertUsingTestDatabase() {
        // 离午夜太近先等过零点，用例中途跨天时单号日期会与断言对不上（audit DOC-007）
        TestDates.awayFromDayEnd();
        assertEquals(TEST_DB, jdbc.queryForObject("select database()", String.class),
                "集成测试必须连 " + TEST_DB + "，当前连的不是测试库，已中止");
        // 默认不经过业务员优先阶段，任务生成后直接进入待分配；测这个阶段的用例自己设时限
        jdbc.update("update sys_config set config_value = '0' where tenant_id = 0"
                + " and config_key in ('inquiry.sourcing.sales-window-minutes', 'inquiry.sourcing.sales-window-urgent-minutes')");
    }

    /**
     * 会清空品类表的用例在清空前调用：先存下品类表（含种子品类），用例结束时自动恢复。
     * 询盘、询价等用例依赖种子品类，不恢复的话结果取决于测试执行顺序。
     */
    protected void snapshotCategories() {
        snapshotTables("product_category");
    }

    /**
     * 整表清空（delete from，不带条件）。清空前先存下每张表的快照（含迁移写入的种子数据），
     * 用例结束时自动恢复，后面的用例不受执行顺序影响（audit DOC-004）。同一用例里重复清空只存第一次的快照。
     */
    protected void wipeTables(String... tables) {
        snapshotTables(tables);
        for (String t : tables) {
            jdbc.update("delete from `" + t + "`");
        }
    }

    private void snapshotTables(String... tables) {
        for (String t : tables) {
            if (snapshots.contains(t)) {
                continue;
            }
            jdbc.update("drop table if exists `" + SNAPSHOT_PREFIX + t + "`");
            jdbc.update("create table `" + SNAPSHOT_PREFIX + t + "` as select * from `" + t + "`");
            snapshots.add(t);
        }
    }

    /** 子类的 @AfterEach 先执行（可能再次清表），这里最后恢复 */
    @AfterEach
    void restoreSnapshots() {
        for (String t : snapshots) {
            jdbc.update("delete from `" + t + "`");
            jdbc.update("insert into `" + t + "` select * from `" + SNAPSHOT_PREFIX + t + "`");
            jdbc.update("drop table `" + SNAPSHOT_PREFIX + t + "`");
        }
        snapshots.clear();
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
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag) values (?, ?, ?, ?, ?)",
                TEST_ACCOUNT_ID, testUserTenant, TEST_ACCOUNT_ID, username, adminFlag);
        jdbc.update("insert into user_basic (id, tenant_id, name, username, role_code) values (?, ?, 'IT', ?, ?)",
                TEST_ACCOUNT_ID, testUserTenant, username, TEST_ROLE);
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

package com.zhul.erp.framework.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 租户拦截器按表的隔离方式生成的 SQL（只改写、不执行） */
class TenantInterceptorTest {

    private final TenantInterceptor on = new TenantInterceptor(true);

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private String rewrite(String sql) {
        TenantContext.setTenantId(7);
        return on.parserMulti(sql, null);
    }

    @Test
    void privateTableIsCurrentTenantOnly() {
        assertThat(rewrite("SELECT id FROM user_basic WHERE id = ?")).contains("tenant_id = 7");
        assertThat(rewrite("UPDATE account SET status = 0 WHERE user_id = ?")).contains("tenant_id = 7");
        assertThat(rewrite("DELETE FROM department WHERE id = ?")).contains("tenant_id = 7");
    }

    @Test
    void sharedReadTableSeesPlatformButWritesOwnOnly() {
        assertThat(rewrite("SELECT id FROM role WHERE code = ?")).contains("tenant_id IN (0, 7)");
        assertThat(rewrite("UPDATE role SET name = ? WHERE id = ?")).contains("tenant_id = 7").doesNotContain("IN (0");
        assertThat(rewrite("UPDATE sys_config SET config_value = ? WHERE id = ?")).contains("tenant_id = 7");
    }

    @Test
    void sharedWriteTableSeesAndWritesPlatform() {
        assertThat(rewrite("SELECT id FROM dict_item WHERE dict_type_id = ?")).contains("tenant_id IN (0, 7)");
        assertThat(rewrite("UPDATE dict_item SET item_name = ? WHERE id = ?")).contains("tenant_id IN (0, 7)");
    }

    @Test
    void joinedTablesEachGetTheirOwnCondition() {
        String sql = rewrite("SELECT u.id FROM user_basic u JOIN role r ON r.code = u.role_code WHERE u.id = ?");
        assertThat(sql).contains("u.tenant_id = 7").contains("r.tenant_id IN (0, 7)");
    }

    @Test
    void untouchedOutsideEnforcedTablesOrPlatformScope() {
        assertThat(TenantTables.mentions("SELECT id FROM tenant_package ORDER BY id LIMIT 1 FOR UPDATE")).isFalse();
        assertThat(TenantTables.mentions("SELECT id FROM account_access_token WHERE id = ?")).isFalse();
        assertThat(TenantTables.mentions("SELECT `id` FROM `account` WHERE id = ?")).isTrue();
        TenantContext.setTenantId(7);
        String platform = TenantScope.platform(() -> on.parserMulti("SELECT id FROM user_basic WHERE id = ?", null));
        assertThat(platform).doesNotContain("tenant_id");
        String disabled = new TenantInterceptor(false).parserMulti("SELECT id FROM user_basic WHERE id = ?", null);
        assertThat(disabled).doesNotContain("tenant_id");
    }

    @Test
    void lockClauseStaysAtTheEnd() {
        TenantContext.setTenantId(7);
        String sql = on.rewriteQuery("SELECT id FROM user_basic WHERE status = ? ORDER BY id DESC LIMIT 1 FOR UPDATE");
        assertThat(sql).contains("tenant_id = 7").endsWith("ORDER BY id DESC LIMIT 1 FOR UPDATE");
        assertThat(on.rewriteQuery("SELECT id FROM user_basic WHERE id IN (?, ?) ORDER BY id FOR UPDATE"))
                .endsWith("ORDER BY id FOR UPDATE");
        assertThat(on.rewriteQuery("SELECT id FROM user_basic WHERE id = ? LOCK IN SHARE MODE")).endsWith("LOCK IN SHARE MODE");
    }

    @Test
    void reservedWordOutputNeedsBackticks() {
        TenantContext.setTenantId(7);
        assertThrows(Exception.class, () -> on.parserMulti("UPDATE user_basic SET output = ? WHERE id = ?", null));
        assertThat(on.parserMulti("UPDATE user_basic SET `output` = ? WHERE id = ?", null)).contains("tenant_id = 7");
    }

    @Test
    void missingTenantContextFailsClosed() {
        assertThrows(Exception.class, () -> on.parserMulti("SELECT id FROM user_basic WHERE id = ?", null));
    }
}

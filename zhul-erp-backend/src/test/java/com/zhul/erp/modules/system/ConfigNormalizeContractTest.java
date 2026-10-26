package com.zhul.erp.modules.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 系统参数规范化（openspec system-config-normalize）：配置键命名规则、配置类型校验、内置配置键名约定，
 * 以及迁移 V1.3.15（运输天数改名、NUMBER 改为 INTEGER）。
 */
class ConfigNormalizeContractTest extends TenantContractSupport {

    private static final String API = "/api/v1/system/configs";
    private static final String KEY_RULE = "^[a-z][a-z0-9]*(-[a-z0-9]+)*(\\.[a-z][a-z0-9]*(-[a-z0-9]+)*)+$";
    private static final Set<String> TYPES = Set.of("STRING", "INTEGER", "BOOLEAN", "JSON", "URL");
    private static final String TRANSIT = "purchase.transit.default-days";

    private String admin;
    private String transitValue;

    @BeforeEach
    void setUp() {
        loginAsAdmin("it_cfg_admin");
        admin = token("it_cfg_admin", 0);
        transitValue = jdbc.queryForObject(
                "select config_value from sys_config where tenant_id = 0 and config_key = ?", String.class, TRANSIT);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("update sys_config set config_value = ? where tenant_id = 0 and config_key = ?", transitValue, TRANSIT);
        jdbc.update("delete from sys_config where config_key like 'custom.it-normalize%'");
    }

    private JsonNode create(String key) throws Exception {
        String body = write(Map.of("configKey", key, "configName", "规范化测试", "configValue", "1",
                "configType", "STRING", "configGroup", "custom"));
        return call(json(post(API), body), admin);
    }

    private JsonNode updateValue(int id, String value) throws Exception {
        return call(json(put(API + "/" + id), write(Map.of("value", value))), admin);
    }

    @Test
    void migration_renamesTransitKey_andConvertsNumberToInteger() {
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from sys_config where config_key = 'transit.default-days'", Integer.class));
        assertEquals("INTEGER", jdbc.queryForObject(
                "select config_type from sys_config where tenant_id = 0 and config_key = ?", String.class, TRANSIT));
        assertEquals("purchase", jdbc.queryForObject(
                "select config_group from sys_config where tenant_id = 0 and config_key = ?", String.class, TRANSIT));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from sys_config where config_type = 'NUMBER'", Integer.class));
    }

    @Test
    void createConfig_acceptsDottedKebabKey() throws Exception {
        ok(create("custom.it-normalize.auto-close"));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from sys_config where config_key = 'custom.it-normalize.auto-close' and deleted_at is null",
                Integer.class));
    }

    @Test
    void createConfig_rejectsKeysOutsideNamingRule() throws Exception {
        for (String bad : List.of("custom.it-normalize.auto_close", "autoclose", "custom..close",
                "custom.-close", "Custom.close", "custom.close-", "a.b")) {
            JsonNode res = fail(create(bad));
            assertTrue(res.path("message").asText().contains("配置键"), bad + " → " + res);
        }
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from sys_config where config_key like 'custom.it-normalize%'", Integer.class));
    }

    @Test
    void updateValue_rejectsNonIntegerForTransitDays() throws Exception {
        int id = jdbc.queryForObject(
                "select id from sys_config where tenant_id = 0 and config_key = ?", Integer.class, TRANSIT);
        JsonNode res = fail(updateValue(id, "abc"));
        assertEquals("配置值必须为整数", res.path("message").asText());
        assertEquals(transitValue, jdbc.queryForObject(
                "select config_value from sys_config where id = ?", String.class, id));
        ok(updateValue(id, "5"));
        assertEquals("5", jdbc.queryForObject("select config_value from sys_config where id = ?", String.class, id));
    }

    @Test
    void updateValue_rejectsUnsupportedType() throws Exception {
        jdbc.update("insert into sys_config (tenant_id, config_key, config_name, config_value, config_type, is_builtin, "
                + "is_encrypted, config_group, remark) values (0, 'custom.it-normalize.decimal', '小数', '1.5', 'DECIMAL', 0, 0, "
                + "'custom', '')");
        int id = jdbc.queryForObject(
                "select id from sys_config where config_key = 'custom.it-normalize.decimal'", Integer.class);
        JsonNode res = fail(updateValue(id, "2.5"));
        assertEquals("配置类型不支持", res.path("message").asText());
    }

    @Test
    void builtinConfigs_followKeyAndTypeConventions() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select config_key, config_group, config_type from sys_config "
                        + "where tenant_id = 0 and is_builtin = 1 and deleted_at is null");
        assertTrue(rows.size() >= 19, "内置配置应至少 19 项：" + rows.size());
        for (Map<String, Object> row : rows) {
            String key = (String) row.get("config_key");
            assertTrue(key.matches(KEY_RULE) && key.length() >= 4 && key.length() <= 128, "键不符合命名规则：" + key);
            assertTrue(TYPES.contains((String) row.get("config_type")), "类型不支持：" + row);
            assertEquals(row.get("config_group"), key.substring(0, key.indexOf('.')), "第一段应为分组代码：" + key);
        }
    }
}

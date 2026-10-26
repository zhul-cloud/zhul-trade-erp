package com.zhul.erp.modules.system;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.dto.AppearanceVO;
import com.zhul.erp.modules.system.dto.ConfigGroupCountVO;
import com.zhul.erp.modules.system.dto.ConfigVO;
import com.zhul.erp.modules.system.dto.SaveConfigRequest;
import com.zhul.erp.modules.system.service.ConfigService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外观（openspec site-appearance）：内置配置由迁移 V1.3.14 写入，平台统一，租户不可见、不可改。
 * 测试库共享，用例开始前记下 5 项外观配置的原值，结束后还原。
 */
class ConfigAppearanceIntegrationTest extends IntegrationTestBase {

    private static final List<String> KEYS = List.of(
            "sys.site.name", "sys.site.subtitle", "sys.site.logo", "sys.login.background", "sys.login.footer");
    private static final int TENANT = 99000601;

    @Autowired private ConfigService configService;

    private final Map<String, String> original = new HashMap<>();

    @BeforeEach
    void rememberValues() {
        for (String key : KEYS) {
            original.put(key, jdbc.queryForObject(
                    "select config_value from sys_config where tenant_id = 0 and config_key = ? and deleted_at is null",
                    String.class, key));
        }
    }

    @AfterEach
    void restoreValues() {
        original.forEach((key, value) -> jdbc.update(
                "update sys_config set config_value = ? where tenant_id = 0 and config_key = ?", value, key));
        jdbc.update("delete from sys_config where tenant_id = ? and config_key like 'sys.%'", TENANT);
    }

    private int idOf(String key) {
        return jdbc.queryForObject("select id from sys_config where tenant_id = 0 and config_key = ?", Integer.class, key);
    }

    private String valueOf(String key) {
        return jdbc.queryForObject("select config_value from sys_config where tenant_id = 0 and config_key = ?", String.class, key);
    }

    @Test
    void migration_writesBuiltinAppearanceDefaults() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select config_key, config_type, is_builtin, config_group from sys_config where tenant_id = 0 and config_key in "
                        + "('sys.site.name','sys.site.subtitle','sys.site.logo','sys.login.background','sys.login.footer')");
        assertEquals(5, rows.size());
        for (Map<String, Object> row : rows) {
            assertEquals(1, ((Number) row.get("is_builtin")).intValue(), row.toString());
            assertEquals("appearance", row.get("config_group"), row.toString());
        }
    }

    @Test
    void publicAppearance_usesDefaults_whenValuesEmpty() {
        KEYS.forEach(key -> jdbc.update("update sys_config set config_value = '' where tenant_id = 0 and config_key = ?", key));
        AppearanceVO vo = configService.getPublicAppearance();
        assertEquals("烛龙ERP", vo.getSiteName());
        assertEquals("烛龙外贸业财一体化中台", vo.getSiteSubtitle());
        assertEquals("© 2026 烛龙科技", vo.getLoginFooter());
        assertNull(vo.getLogoUrl());
        assertNull(vo.getLoginBackgroundUrl());
    }

    @Test
    void publicAppearance_returnsConfiguredValues() {
        jdbc.update("update sys_config set config_value = '福唯ERP' where tenant_id = 0 and config_key = 'sys.site.name'");
        jdbc.update("update sys_config set config_value = '福唯外贸业务平台' where tenant_id = 0 and config_key = 'sys.site.subtitle'");
        jdbc.update("update sys_config set config_value = '/files/logo.png' where tenant_id = 0 and config_key = 'sys.site.logo'");
        AppearanceVO vo = configService.getPublicAppearance();
        assertEquals("福唯ERP", vo.getSiteName());
        assertEquals("福唯外贸业务平台", vo.getSiteSubtitle());
        assertEquals("/files/logo.png", vo.getLogoUrl());
    }

    @Test
    void tenant_doesNotSeeAppearanceConfigs() {
        TenantContext.setTenantId(TENANT);
        List<ConfigVO> list = configService.listAll(1, 1000, null, null).getRecords();
        assertTrue(list.stream().noneMatch(c -> KEYS.contains(c.getConfigKey())), "租户列表不应包含外观配置");
        assertTrue(configService.listAll(1, 1000, null, "appearance").getRecords().isEmpty());
        List<ConfigGroupCountVO> counts = configService.groupCounts();
        assertTrue(counts.stream().noneMatch(g -> "appearance".equals(g.getGroup())), counts.toString());
    }

    @Test
    void platform_seesAppearanceConfigs() {
        List<ConfigVO> list = configService.listAll(1, 1000, null, "appearance").getRecords();
        assertTrue(list.stream().map(ConfigVO::getConfigKey).toList().containsAll(KEYS), list.toString());
    }

    @Test
    void tenant_cannotModifyAppearance() {
        TenantContext.setTenantId(TENANT);
        BizException e = assertThrows(BizException.class,
                () -> configService.updateValue(idOf("sys.site.name"), "某租户ERP"));
        assertEquals("外观由平台统一管理", e.getMessage());
        assertEquals(original.get("sys.site.name"), valueOf("sys.site.name"), "平台的系统名称不应被改");
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from sys_config where tenant_id = ? and config_key = 'sys.site.name'", Integer.class, TENANT));
    }

    @Test
    void createCustomConfig_onlyAcceptsCustomGroupCodes() {
        for (String bad : List.of("appearance", "外观", "自定义", "inquiry")) {
            SaveConfigRequest req = new SaveConfigRequest();
            req.setConfigKey("it.appearance.group-check");
            req.setConfigName("分组校验");
            req.setConfigValue("1");
            req.setConfigType("STRING");
            req.setConfigGroup(bad);
            BizException e = assertThrows(BizException.class, () -> configService.create(req), bad);
            assertTrue(e.getMessage().startsWith("配置分组不合法"), e.getMessage());
        }
        SaveConfigRequest ok = new SaveConfigRequest();
        ok.setConfigKey("it.appearance.group-check");
        ok.setConfigName("分组校验");
        ok.setConfigValue("1");
        ok.setConfigType("STRING");
        ok.setConfigGroup("custom");
        try {
            configService.create(ok);
            assertEquals("custom", jdbc.queryForObject(
                    "select config_group from sys_config where tenant_id = 0 and config_key = 'it.appearance.group-check'", String.class));
        } finally {
            jdbc.update("delete from sys_config where config_key = 'it.appearance.group-check'");
        }
    }

    @Test
    void platform_canModifyAppearance_andClearLogo() {
        configService.updateValue(idOf("sys.site.name"), "福唯ERP");
        assertEquals("福唯ERP", valueOf("sys.site.name"));

        configService.updateValue(idOf("sys.site.logo"), "/files/logo.png");
        assertEquals("/files/logo.png", valueOf("sys.site.logo"));
        configService.updateValue(idOf("sys.site.logo"), "");
        assertEquals("", valueOf("sys.site.logo"), "Logo 可以清空，恢复默认 Logo");

        BizException e = assertThrows(BizException.class,
                () -> configService.updateValue(idOf("sys.site.logo"), "not-a-url"));
        assertEquals("请输入合法的 URL 地址", e.getMessage());
        assertFalse(valueOf("sys.site.logo").equals("not-a-url"));
    }
}

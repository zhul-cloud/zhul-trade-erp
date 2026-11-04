package com.zhul.erp.modules.crm.followup;

import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.crm.followup.support.FollowupSettings;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 系统参数确实按迁移写入的 sales.followup.* 键读取：平台模板生效，租户覆盖优先 */
class FollowupSettingsTest extends IntegrationTestBase {

    private static final int TENANT = 99031;

    @Autowired private FollowupSettings settings;

    @AfterEach
    void restore() {
        jdbc.update("update sys_config set config_value = '1,3,7,14' where tenant_id = 0 and config_key = 'sales.followup.quote.days'");
        jdbc.update("delete from sys_config where tenant_id = ? and config_key like 'sales.followup.%'", TENANT);
    }

    @Test
    void readsPlatformTemplateAndTenantOverride() {
        jdbc.update("update sys_config set config_value = '2,5' where tenant_id = 0 and config_key = 'sales.followup.quote.days'");
        jdbc.update("insert into sys_config (tenant_id, config_key, config_name, config_value, is_builtin) "
                + "values (?, 'sales.followup.pi.interval-days', '客户跟进：PI 后提醒间隔（天）', '4', 0)", TENANT);

        FollowupSettings.Params[] got = new FollowupSettings.Params[2];
        TenantContext.runAs(TENANT, () -> got[0] = settings.params(TENANT));
        TenantContext.runAs(0, () -> got[1] = settings.params(0));
        FollowupSettings.Params tenant = got[0];
        FollowupSettings.Params platform = got[1];

        assertThat(tenant.quoteDays()).isEqualTo(List.of(2, 5));
        assertThat(tenant.piIntervalDays()).isEqualTo(4);
        assertThat(platform.quoteDays()).isEqualTo(List.of(2, 5));
        assertThat(platform.piIntervalDays()).isEqualTo(2);
    }
}

package com.zhul.erp.modules.system.numbering;

import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** spec system/document-numbering「租户单据前缀」：在「业务设置 → 单据编号」查看，修改需要「编辑单据编号」 */
class DocumentNumberContractTest extends TenantContractSupport {

    private static final String API = "/api/v1/system/document-numbering/prefix";
    private static final int TENANT = 99321;
    private static final int MENU_NUMBERING = 100086;
    private static final int BTN_EDIT = 110182;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbc.update("delete from sys_config where tenant_id = ? and config_key = 'document.number-prefix'", TENANT);
    }

    @Test
    void readOnlyWithMenu_editNeedsButton() throws Exception {
        loginWithResources("it_num_viewer", MENU_NUMBERING);
        String viewer = token("it_num_viewer", TENANT);
        assertEquals("", ok(call(get(API), viewer)).path("prefix").asText());
        assertEquals(403, perform(json(put(API), "{\"prefix\":\"FW\"}"), viewer).getStatus(), "只读：没有编辑单据编号");

        loginWithResources("it_num_editor", MENU_NUMBERING, BTN_EDIT);
        String editor = token("it_num_editor", TENANT);
        assertEquals("FW", ok(call(json(put(API), "{\"prefix\":\"FW\"}"), editor)).path("prefix").asText());
        assertEquals("单据前缀只能是 2–4 位大写字母", fail(call(json(put(API), "{\"prefix\":\"FW-\"}"), editor)).path("message").asText());

        loginWithResources("it_num_none", 100073);
        assertEquals(403, perform(get(API), token("it_num_none", TENANT)).getStatus(), "没有单据编号菜单");
    }
}

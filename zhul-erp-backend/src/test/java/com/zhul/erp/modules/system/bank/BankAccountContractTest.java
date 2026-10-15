package com.zhul.erp.modules.system.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** spec system/bank-account：维护收款账户、每币种一个默认、脱敏、租户隔离 */
class BankAccountContractTest extends TenantContractSupport {

    private static final String API = "/api/v1/system/bank-accounts";
    private static final int TENANT_A = 99311;
    private static final int TENANT_B = 99312;

    @BeforeEach
    void setUp() {
        cleanup();
        loginAsAdmin("it_bank_admin");
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("delete from tenant_bank_account where tenant_id in (?, ?)", TENANT_A, TENANT_B);
    }

    private static Map<String, Object> account(String currency, String bank, String no) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("currencyCode", currency);
        m.put("bankName", bank);
        m.put("accountName", "Fuzhou Fouwell Technology Co., Ltd.");
        m.put("accountNo", no);
        m.put("swiftCode", "CHASSGSGXXX");
        m.put("country", "Singapore");
        return m;
    }

    @Test
    void firstAccountBecomesDefault_listMasked_tenantIsolated() throws Exception {
        String a = token("it_bank_admin", TENANT_A);
        JsonNode usd = ok(call(json(post(API), write(account("USD", "JPMorgan Chase Bank N.A., Singapore Branch", "10141740757803"))), a));
        assertTrue(usd.path("isDefault").asBoolean(), "第一个美元账户自动成为默认");
        JsonNode second = ok(call(json(post(API), write(account("USD", "Citibank N.A., Hong Kong Branch", "6012342216"))), a));
        assertFalse(second.path("isDefault").asBoolean());

        JsonNode list = ok(call(get(API), a));
        assertEquals("****7803", list.get(0).path("accountNoMasked").asText());
        assertTrue(list.get(0).path("accountNo").isMissingNode() || list.get(0).path("accountNo").isNull(), "列表不返回完整账号");
        assertEquals("10141740757803", ok(call(get(API + "/options"), a)).get(0).path("accountNo").asText(), "开单选项含完整账号");
        assertEquals(0, ok(call(get(API), token("it_bank_admin", TENANT_B))).size(), "租户隔离");

        // 切换默认
        ok(call(put(API + "/" + second.path("id").asInt() + "/default"), a));
        list = ok(call(get(API), a));
        assertFalse(list.get(0).path("isDefault").asBoolean());
        assertTrue(list.get(1).path("isDefault").asBoolean());
    }

    @Test
    void cannotDisableDefaultWhileAnotherEnabled() throws Exception {
        String a = token("it_bank_admin", TENANT_A);
        long first = ok(call(json(post(API), write(account("USD", "JPMorgan", "10141740757803"))), a)).path("id").asLong();
        ok(call(json(post(API), write(account("USD", "Citibank", "6012342216"))), a));
        assertEquals("这是 USD 的默认账户，请先把另一个账户设为默认",
                fail(call(put(API + "/" + first + "/enabled").param("enabled", "false"), a)).path("message").asText());
        long cny = ok(call(json(post(API), write(account("CNY", "China Merchants Bank", "591912346621"))), a)).path("id").asLong();
        ok(call(put(API + "/" + cny + "/enabled").param("enabled", "false"), a));
        JsonNode cnyRow = null;
        for (JsonNode r : ok(call(get(API), a))) {
            if (r.path("id").asLong() == cny) {
                cnyRow = r;
            }
        }
        assertFalse(cnyRow.path("enabled").asBoolean(), "币种里只有它一个时可以停用");
        assertTrue(fail(call(json(post(API), write(account("USD", "X", "12"))), a)).path("message").asText().contains("账号"));
    }

    @Test
    void privateAccountNeverDefault() throws Exception {
        String a = token("it_bank_admin", TENANT_A);
        Map<String, Object> personal = account("CNY", "招商银行", "6225888866881234");
        personal.put("accountName", "张总");
        personal.put("accountType", 2);
        JsonNode p = ok(call(json(post(API), write(personal)), a));
        assertEquals(2, p.path("accountType").asInt());
        assertFalse(p.path("isDefault").asBoolean(), "对私账户不当默认，即使是该币种第一个");
        assertEquals("对私账户不能设为默认", fail(call(put(API + "/" + p.path("id").asInt() + "/default"), a)).path("message").asText());
        JsonNode pub = ok(call(json(post(API), write(account("CNY", "中国银行", "591912346621"))), a));
        assertTrue(pub.path("isDefault").asBoolean(), "第一个对公账户成为默认");
        assertEquals(1, pub.path("accountType").asInt(), "不传类型按对公");

        // 默认的对公账户改成对私：默认交给同币种的其他对公账户（这里没有，就没有默认）
        Map<String, Object> change = account("CNY", "中国银行", "591912346621");
        change.put("accountType", 2);
        JsonNode changed = ok(call(json(put(API + "/" + pub.path("id").asInt()), write(change)), a));
        assertFalse(changed.path("isDefault").asBoolean());
    }
}

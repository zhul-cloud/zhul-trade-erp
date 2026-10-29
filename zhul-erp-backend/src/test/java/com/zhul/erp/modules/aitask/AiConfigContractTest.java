package com.zhul.erp.modules.aitask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.utils.AesUtils;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.dto.AiCallConfig;
import com.zhul.erp.modules.aitask.dto.AiConfigVO;
import com.zhul.erp.modules.aitask.dto.SaveAiConfigRequest;
import com.zhul.erp.modules.aitask.exception.AiNotConfiguredException;
import com.zhul.erp.modules.aitask.service.AiConfigService;
import com.zhul.erp.support.IntegrationTestBase;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 对应 openspec ai-platform/tenant-ai-settings：租户 AI 配置的隔离、校验、加密脱敏与权限 */
@AutoConfigureMockMvc
class AiConfigContractTest extends IntegrationTestBase {

    private static final int PLATFORM = 0;
    private static final int TENANT = 1001;
    private static final String BASE = "/api/v1/system/ai-config";
    private static final String PLATFORM_KEY = "sk-ant-platform-key-0001";
    private static final String TENANT_KEY = "sk-openai-tenant-key-9876";

    @Autowired private AiConfigService service;
    @Autowired private AesUtils aesUtils;
    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("delete from sys_ai_config where tenant_id in (?, ?)", PLATFORM, TENANT);
    }

    private static SaveAiConfigRequest req(String mode, String provider, String key) {
        SaveAiConfigRequest r = new SaveAiConfigRequest();
        r.setMode(mode);
        r.setProvider(provider);
        r.setApiKey(key);
        r.setMaxWebSearches(20);
        return r;
    }

    private void saveAs(int tenant, SaveAiConfigRequest r) {
        TenantContext.setTenantId(tenant);
        service.save(r);
    }

    private AiCallConfig resolveAs(int tenant) {
        TenantContext.setTenantId(tenant);
        return service.resolveForCurrentTenant();
    }

    @Test
    void tenantsAreIsolatedAndPlatformIsNotInherited() {
        saveAs(PLATFORM, req("api", "claude", PLATFORM_KEY));

        TenantContext.setTenantId(TENANT);
        assertFalse(service.get().isConfigured());
        assertThrows(AiNotConfiguredException.class, service::resolveForCurrentTenant);

        saveAs(TENANT, req("api", "openai", TENANT_KEY));
        AiCallConfig platform = resolveAs(PLATFORM);
        AiCallConfig tenant = resolveAs(TENANT);
        assertEquals("claude", platform.getProvider());
        assertEquals(PLATFORM_KEY, platform.getApiKey());
        assertEquals("openai", tenant.getProvider());
        assertEquals(TENANT_KEY, tenant.getApiKey());
    }

    @Test
    void blanksUseDefaults() {
        saveAs(TENANT, req("", "", TENANT_KEY));
        AiCallConfig c = resolveAs(TENANT);
        assertEquals("api", c.getMode());
        assertEquals("claude", c.getProvider());
        assertEquals("claude-opus-5-5", c.getParseModel());
        assertEquals("claude-haiku-5-5", c.getTranslateModel());
        assertEquals("medium", c.getParseEffort());
        assertEquals("medium", c.getTranslateEffort());
        assertEquals(20, c.getMaxWebSearches());

        SaveAiConfigRequest openai = req("api", "openai", "");
        openai.setParseEffort("high");
        openai.setMaxWebSearches(5);
        saveAs(TENANT, openai);
        c = resolveAs(TENANT);
        assertEquals("gpt-6.1-sol", c.getParseModel());
        assertEquals("gpt-6-luna", c.getTranslateModel());
        assertEquals("high", c.getParseEffort());
        assertEquals(5, c.getMaxWebSearches());
    }

    @Test
    void keyIsEncryptedMaskedAndKeptWhenBlank() {
        saveAs(TENANT, req("api", "openai", TENANT_KEY));
        String stored = jdbc.queryForObject("select api_key from sys_ai_config where tenant_id = ?", String.class, TENANT);
        assertTrue(stored.startsWith(AesUtils.V2_PREFIX));
        assertFalse(stored.contains(TENANT_KEY));

        SaveAiConfigRequest onlyModel = req("api", "openai", "");
        onlyModel.setTranslateModel("gpt-6-terra");
        saveAs(TENANT, onlyModel);
        assertEquals(TENANT_KEY, resolveAs(TENANT).getApiKey());
        assertEquals("gpt-6-terra", resolveAs(TENANT).getTranslateModel());

        AiConfigVO vo = service.get();
        assertTrue(vo.isHasKey());
        assertEquals("sk-open••••••••••••••••9876", vo.getApiKeyMasked());
        assertFalse(vo.isSubscriptionAllowed());
    }

    @Test
    void apiModeWithoutKeyIsNotConfigured() {
        saveAs(TENANT, req("api", "claude", ""));
        assertThrows(AiNotConfiguredException.class, () -> resolveAs(TENANT));
    }

    @Test
    void subscriptionOnlyForPlatform() {
        TenantContext.setTenantId(TENANT);
        BizException e = assertThrows(BizException.class, () -> service.save(req("subscription", "openai", "")));
        assertEquals(400, e.getCode());
        assertEquals(0, jdbc.queryForObject("select count(*) from sys_ai_config where tenant_id = ?", Integer.class, TENANT));

        saveAs(PLATFORM, req("subscription", "openai", ""));
        AiCallConfig c = resolveAs(PLATFORM);
        assertTrue(c.isSubscription());
        assertEquals("", c.getApiKey());
        TenantContext.setTenantId(PLATFORM);
        assertTrue(service.get().isSubscriptionAllowed());

        TenantContext.setTenantId(TENANT);
        assertEquals(403, assertThrows(BizException.class, service::subscriptionStatus).getCode());
    }

    @Test
    void invalidValuesAreRejected() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        SaveAiConfigRequest bad = req("api", "deepseek", "k");
        bad.setTranslateEffort("fast");
        bad.setMaxWebSearches(0);
        assertEquals(3, validator.validate(bad).size());
        SaveAiConfigRequest tooMany = req("api", "claude", "k");
        tooMany.setMaxWebSearches(101);
        assertEquals(1, validator.validate(tooMany).size());

        TenantContext.setTenantId(TENANT);
        assertThrows(BizException.class, () -> service.save(req("api", "deepseek", TENANT_KEY)));
        assertEquals(0, jdbc.queryForObject("select count(*) from sys_ai_config where tenant_id = ?", Integer.class, TENANT));
    }

    private String token(String username, int tenant) {
        String token = jwtUtils.generateToken(Map.of("tenantId", tenant), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token) throws Exception {
        request.header("Authorization", "Bearer " + token);
        return objectMapper.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void userTriggeredAiActionReportsNotConfigured() throws Exception {
        loginAsAdmin("it_ai_admin");
        int before = jdbc.queryForObject("select count(*) from ai_task where tenant_id = ?", Integer.class, PLATFORM);
        JsonNode res = call(post("/api/v1/translations/item-descriptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"key\":\"1\",\"text\":\"西门子 PLC\"}]}"), token("it_ai_admin", PLATFORM));
        assertEquals(400, res.path("code").asInt(), res.toString());
        assertEquals("AI 未配置，请在「业务设置 → AI 设置」中填写", res.path("message").asText());
        assertEquals(before, jdbc.queryForObject("select count(*) from ai_task where tenant_id = ?", Integer.class, PLATFORM));
    }

    @Test
    void apiRequiresEditPermissionAndNeverReturnsKey() throws Exception {
        String body = "{\"mode\":\"api\",\"provider\":\"claude\",\"apiKey\":\"" + PLATFORM_KEY + "\",\"maxWebSearches\":20}";

        loginWithResources("it_ai_outsider");
        JsonNode hidden = call(get(BASE), token("it_ai_outsider", PLATFORM));
        assertEquals(403, hidden.path("code").asInt(), hidden.toString());

        // 只有 AI 设置菜单（100118）、没有「编辑 AI 设置」按钮（110214）：能查看，不能保存
        loginWithResources("it_ai_viewer", 100118);
        String viewer = token("it_ai_viewer", PLATFORM);
        assertEquals(0, call(get(BASE), viewer).path("code").asInt());
        JsonNode denied = call(put(BASE).contentType(MediaType.APPLICATION_JSON).content(body), viewer);
        assertEquals(403, denied.path("code").asInt(), denied.toString());

        loginAsAdmin("it_ai_admin");
        String admin = token("it_ai_admin", PLATFORM);
        JsonNode saved = call(put(BASE).contentType(MediaType.APPLICATION_JSON).content(body), admin);
        assertEquals(0, saved.path("code").asInt(), saved.toString());
        String got = mvc.perform(get(BASE).header("Authorization", "Bearer " + admin)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertFalse(got.contains(PLATFORM_KEY));
        assertTrue(got.contains("sk-ant-••••••••••••••••0001"));

        JsonNode status = call(get(BASE + "/subscription-status"), token("it_ai_admin", TENANT));
        assertEquals(403, status.path("code").asInt(), status.toString());
    }
}

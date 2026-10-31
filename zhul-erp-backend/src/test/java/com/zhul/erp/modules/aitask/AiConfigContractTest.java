package com.zhul.erp.modules.aitask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.utils.AesUtils;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.constants.AiConfigConstants;
import com.zhul.erp.modules.aitask.dto.AiCallConfig;
import com.zhul.erp.modules.aitask.dto.AiConfigVO;
import com.zhul.erp.modules.aitask.dto.AiLoginStatus;
import com.zhul.erp.modules.aitask.dto.AiSubscriptionStatusVO;
import com.zhul.erp.modules.aitask.dto.SaveAiConfigRequest;
import com.zhul.erp.modules.aitask.exception.AiNotConfiguredException;
import com.zhul.erp.modules.aitask.exception.AiUnavailableException;
import com.zhul.erp.modules.aitask.service.AiConfigService;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.support.IntegrationTestBase;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 对应 openspec ai-platform/tenant-ai-settings：按连接方式分别保存、租户隔离、加密脱敏、
 * 平台订阅按套餐授权给公司、租户只看到可用与否、权限。编排服务用替身控制登录状态。
 */
@AutoConfigureMockMvc
class AiConfigContractTest extends IntegrationTestBase {

    private static final int PLATFORM = 0;
    private static final int TENANT = 1001;
    /** 套餐包含「使用平台订阅」的公司 */
    private static final int GRANTED = 99021;
    private static final int GRANTED_PACKAGE = 99021;
    private static final String BASE = "/api/v1/system/ai-config";
    private static final String PLATFORM_KEY = "sk-ant-platform-key-0001";
    private static final String TENANT_KEY = "sk-openai-tenant-key-9876";
    private static final String CLAUDE_KEY = "sk-ant-tenant-claude-5555";

    @Autowired private AiConfigService service;
    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AiOrchestratorClient orchestratorClient;

    @BeforeEach
    void setUp() {
        cleanup();
        jdbc.update("insert into tenant_package (id, name, menu_ids) values (?, 'IT-订阅授权', ?)",
                GRANTED_PACKAGE, "[100118, 110214, " + AiConfigConstants.RESOURCE_USE_SUBSCRIPTION + "]");
        jdbc.update("insert into tenant (id, name, package_id) values (?, 'IT-订阅授权', ?)", GRANTED, GRANTED_PACKAGE);
        loggedIn(true, false);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        for (int t : new int[] {PLATFORM, TENANT, GRANTED}) {
            removeAiConfig(t);
        }
        jdbc.update("delete from tenant where id = ?", GRANTED);
        jdbc.update("delete from tenant_package where id = ?", GRANTED_PACKAGE);
    }

    private void loggedIn(boolean claude, boolean openai) {
        // doReturn：status() 被设为抛异常后也能重新设值
        doReturn(new AiLoginStatus(claude, openai)).when(orchestratorClient).status();
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

    private int profileRows(int tenant) {
        return jdbc.queryForObject("select count(*) from sys_ai_provider_config where tenant_id = ?", Integer.class, tenant);
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

        SaveAiConfigRequest openai = req("api", "openai", TENANT_KEY);
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
    void eachProviderKeepsItsOwnSettings() {
        SaveAiConfigRequest claude = req("api", "claude", CLAUDE_KEY);
        claude.setParseModel("claude-sonnet-5-5");
        saveAs(TENANT, claude);
        SaveAiConfigRequest openai = req("api", "openai", TENANT_KEY);
        openai.setTranslateModel("gpt-6-terra");
        saveAs(TENANT, openai);
        assertEquals(TENANT_KEY, resolveAs(TENANT).getApiKey());

        // 切回 Claude：Key 留空沿用 Claude 自己的 Key，页面带回 Claude 已保存的模型
        SaveAiConfigRequest back = req("api", "claude", "");
        back.setParseModel(service.get().getApiProfiles().get("claude").getParseModel());
        saveAs(TENANT, back);
        AiCallConfig c = resolveAs(TENANT);
        assertEquals(CLAUDE_KEY, c.getApiKey());
        assertEquals("claude-sonnet-5-5", c.getParseModel());

        AiConfigVO vo = service.get();
        assertEquals("sk-ant-••••••••••••••••5555", vo.getApiProfiles().get("claude").getApiKeyMasked());
        assertEquals("sk-open••••••••••••••••9876", vo.getApiProfiles().get("openai").getApiKeyMasked());
        assertEquals("gpt-6-terra", vo.getApiProfiles().get("openai").getTranslateModel());
        assertEquals(2, profileRows(TENANT));
    }

    @Test
    void keyIsEncryptedMaskedAndKeptWhenBlank() {
        saveAs(TENANT, req("api", "openai", TENANT_KEY));
        String stored = jdbc.queryForObject(
                "select api_key from sys_ai_provider_config where tenant_id = ? and provider = 'openai'", String.class, TENANT);
        assertTrue(stored.startsWith(AesUtils.V2_PREFIX));
        assertFalse(stored.contains(TENANT_KEY));

        SaveAiConfigRequest onlyModel = req("api", "openai", "");
        onlyModel.setTranslateModel("gpt-6-terra");
        saveAs(TENANT, onlyModel);
        assertEquals(TENANT_KEY, resolveAs(TENANT).getApiKey());
        assertEquals("gpt-6-terra", resolveAs(TENANT).getTranslateModel());

        AiConfigVO vo = service.get();
        assertTrue(vo.getApiProfiles().get("openai").isHasKey());
        assertFalse(vo.getApiProfiles().get("claude").isHasKey());
        assertFalse(vo.isSubscriptionAllowed());
        assertNull(vo.getSubscriptionProfiles());
    }

    @Test
    void apiModeWithoutKeyIsNotConfigured() {
        saveAs(TENANT, req("api", "claude", ""));
        assertThrows(AiNotConfiguredException.class, () -> resolveAs(TENANT));
        TenantContext.setTenantId(TENANT);
        assertFalse(service.isConfiguredForCurrentTenant());
    }

    @Test
    void tenantWithoutGrantCannotUseSubscription() {
        TenantContext.setTenantId(TENANT);
        BizException e = assertThrows(BizException.class, () -> service.save(req("subscription", "claude", "")));
        assertEquals(400, e.getCode());
        assertEquals(0, jdbc.queryForObject("select count(*) from sys_ai_config where tenant_id = ?", Integer.class, TENANT));
        assertEquals(403, assertThrows(BizException.class, service::subscriptionStatus).getCode());
        assertEquals(403, assertThrows(BizException.class, () -> service.models(req("subscription", "claude", ""))).getCode());
    }

    @Test
    void grantedTenantUsesPlatformSubscriptionSettings() {
        SaveAiConfigRequest platformSub = req("subscription", "claude", "");
        platformSub.setParseModel("claude-opus-5-1");
        platformSub.setParseEffort("high");
        saveAs(PLATFORM, platformSub);

        // 租户提交的模型不生效，只用平台的设置；订阅不用 Key
        SaveAiConfigRequest tenantSub = req("subscription", "claude", "sk-should-be-ignored");
        tenantSub.setParseModel("my-own-model");
        saveAs(GRANTED, tenantSub);
        AiCallConfig c = resolveAs(GRANTED);
        assertTrue(c.isSubscription());
        assertEquals("claude-opus-5-1", c.getParseModel());
        assertEquals("high", c.getParseEffort());
        assertEquals("", c.getApiKey());
        assertEquals(0, profileRows(GRANTED));

        AiConfigVO vo = service.get();
        assertTrue(vo.isSubscriptionAllowed());
        assertFalse(vo.isPlatform());
        assertEquals("claude-opus-5-1", vo.getSubscriptionProfiles().get("claude").getParseModel());

        // 平台的 OpenAI 未登录：租户不能选
        TenantContext.setTenantId(GRANTED);
        assertEquals(400, assertThrows(BizException.class, () -> service.save(req("subscription", "openai", ""))).getCode());
        assertEquals("claude", resolveAs(GRANTED).getProvider());
    }

    @Test
    void tenantSeesOnlyAvailabilityPlatformSeesLoginDetails() throws Exception {
        TenantContext.setTenantId(GRANTED);
        AiSubscriptionStatusVO tenant = service.subscriptionStatus();
        assertEquals(Boolean.TRUE, tenant.getAvailable().get("claude"));
        assertEquals(Boolean.FALSE, tenant.getAvailable().get("openai"));
        assertNull(tenant.getStates());
        assertNull(tenant.getLoginCommands());
        String json = objectMapper.writeValueAsString(tenant);
        assertFalse(json.contains("login") || json.contains("logged"), json);

        TenantContext.setTenantId(PLATFORM);
        AiSubscriptionStatusVO platform = service.subscriptionStatus();
        assertEquals("logged_in", platform.getStates().get("claude"));
        assertEquals("logged_out", platform.getStates().get("openai"));
        assertTrue(platform.getLoginCommands().get("openai").contains("codex login"));

        // 状态查询失败：未知，不当作未登录
        when(orchestratorClient.status()).thenThrow(new BizException("AI 编排服务不可用"));
        platform = service.subscriptionStatus();
        assertEquals("unknown", platform.getStates().get("claude"));
        assertNull(platform.getAvailable().get("claude"));
    }

    @Test
    void revokedGrantOrLoggedOutPlatformMakesAiUnavailable() {
        saveAs(GRANTED, req("subscription", "claude", ""));
        assertTrue(resolveAs(GRANTED).isSubscription());
        TenantContext.setTenantId(GRANTED);
        assertTrue(service.isConfiguredForCurrentTenant());

        // 平台的 Claude 退出登录：不创建任务，不自动换到 OpenAI，选择不变
        loggedIn(false, true);
        AiUnavailableException e = assertThrows(AiUnavailableException.class, () -> resolveAs(GRANTED));
        assertEquals(AiConfigConstants.UNAVAILABLE_MESSAGE, e.getMessage());
        // 顺带触发的任务（如保存商机时的联系人名称翻译）据此跳过，不在业务事务里抛异常
        TenantContext.setTenantId(GRANTED);
        assertFalse(service.isConfiguredForCurrentTenant());
        // 状态查询失败：可用与否未知，同样跳过
        when(orchestratorClient.status()).thenThrow(new BizException("AI 编排服务不可用"));
        assertFalse(service.isConfiguredForCurrentTenant());
        assertEquals("claude", jdbc.queryForObject("select provider from sys_ai_config where tenant_id = ?", String.class, GRANTED));

        // 收回授权：套餐去掉「使用平台订阅」
        loggedIn(true, true);
        jdbc.update("update tenant_package set menu_ids = '[100118, 110214]' where id = ?", GRANTED_PACKAGE);
        assertThrows(AiUnavailableException.class, () -> resolveAs(GRANTED));
        TenantContext.setTenantId(GRANTED);
        assertFalse(service.isConfiguredForCurrentTenant());
        assertFalse(service.get().isSubscriptionAllowed());
        assertEquals(403, assertThrows(BizException.class, service::subscriptionStatus).getCode());
    }

    @Test
    void disabledResourceRevokesEveryone() {
        jdbc.update("update resource set status = 0 where id = ?", AiConfigConstants.RESOURCE_USE_SUBSCRIPTION);
        try {
            TenantContext.setTenantId(GRANTED);
            assertFalse(service.get().isSubscriptionAllowed());
            TenantContext.setTenantId(PLATFORM);
            assertTrue(service.get().isSubscriptionAllowed());
        } finally {
            jdbc.update("update resource set status = 1 where id = ?", AiConfigConstants.RESOURCE_USE_SUBSCRIPTION);
        }
    }

    @Test
    void platformSubscriptionNeedsNoLoginCheckToSave() {
        loggedIn(false, false);
        saveAs(PLATFORM, req("subscription", "openai", ""));
        AiCallConfig c = resolveAs(PLATFORM);
        assertTrue(c.isSubscription());
        assertEquals("openai", c.getProvider());
        assertEquals("", c.getApiKey());
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
        assertEquals(0, profileRows(TENANT));
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

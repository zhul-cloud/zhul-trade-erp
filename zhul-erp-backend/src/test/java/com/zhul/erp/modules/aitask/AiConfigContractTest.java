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
import com.zhul.erp.modules.aitask.dto.AiConnectionTestVO;
import com.zhul.erp.modules.aitask.dto.AiConnectionVO;
import com.zhul.erp.modules.aitask.dto.AiLoginStatus;
import com.zhul.erp.modules.aitask.dto.AiSubscriptionStatusVO;
import com.zhul.erp.modules.aitask.dto.SaveAiConfigRequest;
import com.zhul.erp.modules.aitask.dto.SaveAiSettingsRequest;
import com.zhul.erp.modules.aitask.exception.AiNotConfiguredException;
import com.zhul.erp.modules.aitask.exception.AiUnavailableException;
import com.zhul.erp.modules.aitask.service.AiConfigService;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.modules.aitask.support.AiConnectionPauses;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 对应 openspec ai-platform/tenant-ai-settings 与 ai-platform/connection-failover：按连接方式分别保存、
 * 按「API Key 组在前、组内顺序」挑出可用的方式、启用开关、暂停与解除、租户隔离、加密脱敏、
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
    @Autowired private AiConnectionPauses pauses;
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
            for (String mode : List.of("api", "subscription")) {
                for (String provider : AiConfigConstants.PROVIDERS) {
                    pauses.clear(t, mode, provider);
                }
            }
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

    private static SaveAiSettingsRequest.Item item(String mode, String provider, boolean enabled) {
        SaveAiSettingsRequest.Item i = new SaveAiSettingsRequest.Item();
        i.setMode(mode);
        i.setProvider(provider);
        i.setEnabled(enabled);
        return i;
    }

    /** 只保存设置，不改顺序 */
    private void saveAs(int tenant, SaveAiConfigRequest... profiles) {
        saveAs(tenant, List.of(), profiles);
    }

    private void saveAs(int tenant, List<SaveAiSettingsRequest.Item> order, SaveAiConfigRequest... profiles) {
        TenantContext.setTenantId(tenant);
        SaveAiSettingsRequest r = new SaveAiSettingsRequest();
        r.setOrder(new ArrayList<>(order));
        r.setProfiles(List.of(profiles));
        service.save(r);
    }

    private List<AiCallConfig> chainAs(int tenant) {
        TenantContext.setTenantId(tenant);
        return service.resolveChainForCurrentTenant();
    }

    private static List<String> names(List<AiCallConfig> chain) {
        return chain.stream().map(c -> c.getMode() + ":" + c.getProvider()).toList();
    }

    private AiConnectionVO card(int tenant, String mode, String provider) {
        TenantContext.setTenantId(tenant);
        return service.get().getConnections().stream()
                .filter(c -> c.getMode().equals(mode) && c.getProvider().equals(provider)).findFirst().orElse(null);
    }

    private int profileRows(int tenant) {
        return jdbc.queryForObject("select count(*) from sys_ai_provider_config where tenant_id = ?", Integer.class, tenant);
    }

    @Test
    void tenantsAreIsolatedAndPlatformIsNotInherited() {
        saveAs(PLATFORM, req("api", "claude", PLATFORM_KEY));

        TenantContext.setTenantId(TENANT);
        assertThrows(AiNotConfiguredException.class, service::resolveChainForCurrentTenant);
        assertFalse(service.isConfiguredForCurrentTenant());

        saveAs(TENANT, req("api", "openai", TENANT_KEY));
        AiCallConfig platform = chainAs(PLATFORM).get(0);
        AiCallConfig tenant = chainAs(TENANT).get(0);
        assertEquals("claude", platform.getProvider());
        assertEquals(PLATFORM_KEY, platform.getApiKey());
        assertEquals("openai", tenant.getProvider());
        assertEquals(TENANT_KEY, tenant.getApiKey());
        assertEquals(1, chainAs(TENANT).size(), "没有 Key 的 Claude API 不参与");
    }

    @Test
    void blanksUseDefaults() {
        saveAs(TENANT, req("", "", TENANT_KEY));
        AiCallConfig c = chainAs(TENANT).get(0);
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
        c = chainAs(TENANT).get(1);
        assertEquals("openai", c.getProvider());
        assertEquals("gpt-6.1-sol", c.getParseModel());
        assertEquals("gpt-6-luna", c.getTranslateModel());
        assertEquals("high", c.getParseEffort());
        assertEquals(5, c.getMaxWebSearches());
    }

    /** 连接方式的使用顺序：默认 API 在前、按保存的顺序、每个方式用自己的设置 */
    @Test
    void apiFirstAndOrderFollowsSaved() {
        SaveAiConfigRequest claude = req("api", "claude", CLAUDE_KEY);
        claude.setParseModel("claude-sonnet-5-5");
        SaveAiConfigRequest openai = req("api", "openai", TENANT_KEY);
        openai.setTranslateModel("gpt-6-terra");
        saveAs(GRANTED, claude, openai);
        loggedIn(true, true);
        assertEquals(List.of("api:claude", "api:openai", "subscription:claude", "subscription:openai"), names(chainAs(GRANTED)));

        saveAs(GRANTED, List.of(item("api", "openai", true), item("api", "claude", true),
                item("subscription", "openai", true), item("subscription", "claude", true)));
        List<AiCallConfig> chain = chainAs(GRANTED);
        assertEquals(List.of("api:openai", "api:claude", "subscription:openai", "subscription:claude"), names(chain));
        assertEquals(TENANT_KEY, chain.get(0).getApiKey());
        assertEquals("gpt-6-terra", chain.get(0).getTranslateModel());
        assertEquals("claude-sonnet-5-5", chain.get(1).getParseModel());

        AiConfigVO vo = service.get();
        assertEquals(List.of(1, 2, 3, 4), vo.getConnections().stream().map(AiConnectionVO::getOrder).toList());
        assertEquals("api", vo.getConnections().get(0).getMode());
        assertEquals("openai", vo.getConnections().get(0).getProvider());
        assertEquals("sk-open••••••••••••••••9876", vo.getConnections().get(0).getApiKeyMasked());
        assertEquals("sk-ant-••••••••••••••••5555", vo.getConnections().get(1).getApiKeyMasked());
    }

    /** openspec ai-connection-free-order：API Key 与订阅在一个列表里任意排序，按列表顺序使用 */
    @Test
    void subscriptionCanBeOrderedBeforeApi() {
        saveAs(GRANTED, req("api", "claude", CLAUDE_KEY), req("api", "openai", TENANT_KEY));
        saveAs(GRANTED, List.of(item("subscription", "claude", true), item("api", "openai", true),
                item("api", "claude", true), item("subscription", "openai", true)));
        // 有可用的 API Key：订阅不查询登录状态，按列表位置参与
        when(orchestratorClient.status()).thenThrow(new AssertionError("有可用 API Key 时不应查询订阅登录状态"));
        assertEquals(List.of("subscription:claude", "api:openai", "api:claude", "subscription:openai"),
                names(chainAs(GRANTED)));
        loggedIn(true, true);
        TenantContext.setTenantId(GRANTED);
        assertEquals(List.of("subscription:claude", "api:openai", "api:claude", "subscription:openai"),
                service.get().getConnections().stream().map(c -> c.getMode() + ":" + c.getProvider()).toList());

        assertEquals("连接方式重复", assertThrows(BizException.class, () -> saveAs(GRANTED,
                List.of(item("api", "claude", true), item("api", "claude", false)))).getMessage());
    }

    /** 升级（V1.5.48 订阅行平移 20）后顺序与升级前相同：API 在前、各自保持原来的先后 */
    @Test
    void upgradedOrderKeepsApiFirst() {
        saveAs(GRANTED, req("api", "claude", CLAUDE_KEY), req("api", "openai", TENANT_KEY));
        jdbc.update("update sys_ai_provider_config set sort_order = case provider when 'openai' then 1 else 2 end "
                + "where tenant_id = ? and mode = 'api'", GRANTED);
        jdbc.update("insert into sys_ai_provider_config (tenant_id, mode, provider, sort_order, status) values "
                + "(?, 'subscription', 'openai', 22, 1), (?, 'subscription', 'claude', 21, 1)", GRANTED, GRANTED);
        loggedIn(true, true);
        assertEquals(List.of("api:openai", "api:claude", "subscription:claude", "subscription:openai"),
                names(chainAs(GRANTED)));
    }

    @Test
    void disabledConnectionIsSkippedButKept() {
        saveAs(TENANT, req("api", "claude", CLAUDE_KEY), req("api", "openai", TENANT_KEY));
        saveAs(TENANT, List.of(item("api", "claude", true), item("api", "openai", false)));
        assertEquals(List.of("api:claude"), names(chainAs(TENANT)));
        AiConnectionVO openai = card(TENANT, "api", "openai");
        assertFalse(openai.isEnabled());
        assertTrue(openai.isHasKey(), "关闭后 Key 保留");

        saveAs(TENANT, List.of(item("api", "claude", false), item("api", "openai", false)));
        TenantContext.setTenantId(TENANT);
        assertThrows(AiNotConfiguredException.class, service::resolveChainForCurrentTenant, "都关闭视为未配置");

        saveAs(TENANT, List.of(item("api", "claude", true), item("api", "openai", true)));
        assertEquals(List.of("api:claude", "api:openai"), names(chainAs(TENANT)));
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
        assertEquals(TENANT_KEY, chainAs(TENANT).get(0).getApiKey());
        assertEquals("gpt-6-terra", chainAs(TENANT).get(0).getTranslateModel());

        TenantContext.setTenantId(TENANT);
        AiConfigVO vo = service.get();
        assertTrue(card(TENANT, "api", "openai").isHasKey());
        assertEquals("ready", card(TENANT, "api", "openai").getState());
        assertEquals("no_key", card(TENANT, "api", "claude").getState());
        assertFalse(vo.isSubscriptionAllowed());
        assertTrue(vo.getConnections().stream().noneMatch(c -> "subscription".equals(c.getMode())));
    }

    @Test
    void tenantWithoutGrantCannotUseSubscription() {
        TenantContext.setTenantId(TENANT);
        BizException e = assertThrows(BizException.class, () -> saveAs(TENANT, List.of(item("subscription", "claude", true))));
        assertEquals(400, e.getCode());
        assertEquals(400, assertThrows(BizException.class,
                () -> saveAs(TENANT, req("subscription", "claude", ""))).getCode());
        assertEquals(0, profileRows(TENANT));
        TenantContext.setTenantId(TENANT);
        assertEquals(403, assertThrows(BizException.class, service::subscriptionStatus).getCode());
        assertEquals(403, assertThrows(BizException.class, () -> service.models(req("subscription", "claude", ""))).getCode());
    }

    @Test
    void grantedTenantUsesPlatformSubscriptionSettings() {
        SaveAiConfigRequest platformSub = req("subscription", "claude", "");
        platformSub.setParseModel("claude-opus-5-1");
        platformSub.setParseEffort("high");
        saveAs(PLATFORM, platformSub);

        // 租户没有 API Key：订阅组参与（默认启用）；平台的 OpenAI 未登录，跳过
        SaveAiConfigRequest tenantSub = req("subscription", "claude", "sk-should-be-ignored");
        tenantSub.setParseModel("my-own-model");
        saveAs(GRANTED, tenantSub);
        assertEquals(0, profileRows(GRANTED), "租户提交的订阅设置不生效");
        List<AiCallConfig> chain = chainAs(GRANTED);
        assertEquals(List.of("subscription:claude"), names(chain));
        assertEquals("claude-opus-5-1", chain.get(0).getParseModel());
        assertEquals("high", chain.get(0).getParseEffort());
        assertEquals("", chain.get(0).getApiKey());

        TenantContext.setTenantId(GRANTED);
        AiConfigVO vo = service.get();
        assertTrue(vo.isSubscriptionAllowed());
        assertFalse(vo.isPlatform());
        AiConnectionVO claude = card(GRANTED, "subscription", "claude");
        assertEquals("available", claude.getState());
        assertTrue(claude.isSettingsReadonly());
        assertEquals("claude-opus-5-1", claude.getParseModel());
        assertEquals("unavailable", card(GRANTED, "subscription", "openai").getState());
    }

    @Test
    void subscriptionFallsBackAndBecomesUnavailable() {
        loggedIn(false, true);
        assertEquals(List.of("subscription:openai"), names(chainAs(GRANTED)), "Claude 订阅退出登录，用 OpenAI 订阅");

        loggedIn(false, false);
        AiUnavailableException e = assertThrows(AiUnavailableException.class, () -> chainAs(GRANTED));
        assertEquals(AiConfigConstants.UNAVAILABLE_MESSAGE, e.getMessage());
        // 顺带触发的任务（如保存商机时的联系人名称翻译）据此跳过，不在业务事务里抛异常
        TenantContext.setTenantId(GRANTED);
        assertFalse(service.isConfiguredForCurrentTenant());
        // 状态查询失败：租户按不可用处理
        when(orchestratorClient.status()).thenThrow(new BizException("AI 编排服务不可用"));
        assertFalse(service.isConfiguredForCurrentTenant());

        // 收回授权：套餐去掉「使用平台订阅」；没有本公司 Key，提示未配置
        loggedIn(true, true);
        jdbc.update("update tenant_package set menu_ids = '[100118, 110214]' where id = ?", GRANTED_PACKAGE);
        assertThrows(AiNotConfiguredException.class, () -> chainAs(GRANTED));
        TenantContext.setTenantId(GRANTED);
        assertFalse(service.get().isSubscriptionAllowed());
        assertEquals(403, assertThrows(BizException.class, service::subscriptionStatus).getCode());
    }

    /** API 组有可用方式时，订阅只按授权与开关附在后面，不查询登录状态 */
    @Test
    void subscriptionAppendedWithoutLoginCheckWhenApiAvailable() {
        saveAs(GRANTED, req("api", "claude", CLAUDE_KEY));
        when(orchestratorClient.status()).thenThrow(new AssertionError("API 可用时不应查询订阅登录状态"));
        assertEquals(List.of("api:claude", "subscription:claude", "subscription:openai"), names(chainAs(GRANTED)));
    }

    @Test
    void pausedConnectionIsSkippedUntilSavedOrTested() {
        saveAs(TENANT, req("api", "claude", CLAUDE_KEY), req("api", "openai", TENANT_KEY));
        pauses.pause(TENANT, "api", "claude", "quota", Instant.now());
        assertEquals(List.of("api:openai"), names(chainAs(TENANT)));
        AiConnectionVO claude = card(TENANT, "api", "claude");
        assertEquals("quota", claude.getPausedReason());
        assertNotNull(claude.getPausedUntil());

        // 更换 Key 并保存：立即解除
        saveAs(TENANT, req("api", "claude", "sk-ant-tenant-claude-6666"));
        assertEquals(List.of("api:claude", "api:openai"), names(chainAs(TENANT)));

        // 测试连接通过：立即解除
        pauses.pause(TENANT, "api", "openai", "auth", Instant.now());
        AiConnectionTestVO ok = new AiConnectionTestVO();
        ok.setOk(true);
        when(orchestratorClient.test(any())).thenReturn(ok);
        TenantContext.setTenantId(TENANT);
        service.test(req("api", "openai", ""));
        assertNull(card(TENANT, "api", "openai").getPausedReason());

        // 都暂停：已配置但暂不可用
        pauses.pause(TENANT, "api", "claude", "quota", Instant.now());
        pauses.pause(TENANT, "api", "openai", "quota", Instant.now());
        assertThrows(AiUnavailableException.class, () -> chainAs(TENANT));

        // 10 分钟前发生的失败不再暂停（很晚才到的回调）
        pauses.clear(TENANT, "api", "claude");
        pauses.pause(TENANT, "api", "claude", "quota", Instant.now().minus(Duration.ofMinutes(11)));
        assertNull(card(TENANT, "api", "claude").getPausedReason());
    }

    @Test
    void platformSubscriptionPauseAppliesToEveryTenant() {
        loggedIn(true, true);
        pauses.pause(GRANTED, "subscription", "claude", "usage_limit", Instant.now());
        assertEquals(List.of("subscription:openai"), names(chainAs(GRANTED)));
        assertEquals("usage_limit", card(PLATFORM, "subscription", "claude").getPausedReason(), "平台也看到暂停");
        assertEquals(List.of("subscription:openai"), names(chainAs(PLATFORM)));
    }

    @Test
    void platformKeepsUsingSubscriptionWhenStatusUnknown() {
        saveAs(PLATFORM, req("subscription", "openai", ""));
        when(orchestratorClient.status()).thenThrow(new BizException("AI 编排服务不可用"));
        // 只照常使用保存过的订阅；从没保存过的 Claude 订阅要确认登录后才用
        List<AiCallConfig> chain = chainAs(PLATFORM);
        assertEquals(List.of("subscription:openai"), names(chain));
        assertEquals("", chain.get(0).getApiKey());
        assertEquals("unknown", card(PLATFORM, "subscription", "openai").getState());
    }

    @Test
    void nothingSavedAndStatusUnknownIsNotConfigured() {
        when(orchestratorClient.status()).thenThrow(new BizException("AI 编排服务不可用"));
        assertThrows(AiNotConfiguredException.class, () -> chainAs(PLATFORM));
        // 状态查得到但都未登录：已配置但暂不可用
        loggedIn(false, false);
        assertThrows(AiUnavailableException.class, () -> chainAs(PLATFORM));
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
        assertEquals("logged_in", card(PLATFORM, "subscription", "claude").getState());

        // 状态查询失败：未知，不当作未登录
        when(orchestratorClient.status()).thenThrow(new BizException("AI 编排服务不可用"));
        TenantContext.setTenantId(PLATFORM);
        platform = service.subscriptionStatus();
        assertEquals("unknown", platform.getStates().get("claude"));
        assertNull(platform.getAvailable().get("claude"));
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
    void invalidValuesAreRejected() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        SaveAiConfigRequest bad = req("api", "deepseek", "k");
        bad.setTranslateEffort("fast");
        bad.setMaxWebSearches(0);
        assertEquals(3, validator.validate(bad).size());
        SaveAiConfigRequest tooMany = req("api", "claude", "k");
        tooMany.setMaxWebSearches(101);
        assertEquals(1, validator.validate(tooMany).size());
        assertEquals(1, validator.validate(item("api", "deepseek", true)).size());

        assertThrows(BizException.class, () -> saveAs(TENANT, req("api", "deepseek", TENANT_KEY)));
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
        loggedIn(false, false);
        JsonNode res = call(post("/api/v1/translations/item-descriptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"key\":\"1\",\"text\":\"西门子 PLC\"}]}"), token("it_ai_admin", PLATFORM));
        assertEquals(400, res.path("code").asInt(), res.toString());
        assertEquals(before, jdbc.queryForObject("select count(*) from ai_task where tenant_id = ?", Integer.class, PLATFORM));
    }

    @Test
    void apiRequiresEditPermissionAndNeverReturnsKey() throws Exception {
        String body = "{\"order\":[{\"mode\":\"api\",\"provider\":\"claude\",\"enabled\":true}],"
                + "\"profiles\":[{\"mode\":\"api\",\"provider\":\"claude\",\"apiKey\":\"" + PLATFORM_KEY + "\",\"maxWebSearches\":20}]}";

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
        JsonNode first = objectMapper.readTree(got).path("data").path("connections").get(0);
        assertEquals("api", first.path("mode").asText());
        assertEquals("claude", first.path("provider").asText());
        assertEquals("ready", first.path("state").asText());

        JsonNode status = call(get(BASE + "/subscription-status"), token("it_ai_admin", TENANT));
        assertEquals(403, status.path("code").asInt(), status.toString());
    }
}

package com.zhul.erp.modules.aitask.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.ResultCode;
import com.zhul.erp.common.utils.AesUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.constants.AiConfigConstants;
import com.zhul.erp.modules.aitask.dto.AiCallConfig;
import com.zhul.erp.modules.aitask.dto.AiConfigVO;
import com.zhul.erp.modules.aitask.dto.AiConnectionTestVO;
import com.zhul.erp.modules.aitask.dto.AiConnectionVO;
import com.zhul.erp.modules.aitask.dto.AiLoginStatus;
import com.zhul.erp.modules.aitask.dto.AiModelListVO;
import com.zhul.erp.modules.aitask.dto.AiSubscriptionStatusVO;
import com.zhul.erp.modules.aitask.dto.SaveAiConfigRequest;
import com.zhul.erp.modules.aitask.dto.SaveAiSettingsRequest;
import com.zhul.erp.modules.aitask.entity.SysAiProviderConfigDO;
import com.zhul.erp.modules.aitask.exception.AiNotConfiguredException;
import com.zhul.erp.modules.aitask.exception.AiUnavailableException;
import com.zhul.erp.modules.aitask.repository.SysAiProviderConfigMapper;
import com.zhul.erp.modules.aitask.service.AiConfigService;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.modules.aitask.service.AiSubscriptionAccess;
import com.zhul.erp.modules.aitask.support.AiConnectionPauses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 租户 AI 配置：sys_ai_provider_config 按连接方式（租户 × 用法 × 服务商）保存设置、顺序与启用开关；
 * 已配置且启用的方式（本公司 API Key、平台订阅）按一个统一列表的顺序使用，失败的方式暂停 10 分钟。
 * 平台订阅按套餐授权给公司使用，租户使用平台的订阅设置。
 * 见 openspec ai-platform/tenant-ai-settings、ai-platform/connection-failover。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    private static final List<String> MODES = List.of(AiConfigConstants.MODE_API, AiConfigConstants.MODE_SUBSCRIPTION);

    private final SysAiProviderConfigMapper profileMapper;
    private final AesUtils aesUtils;
    private final AiOrchestratorClient orchestratorClient;
    private final AiSubscriptionAccess subscriptionAccess;
    private final AiConnectionPauses pauses;

    /**
     * 一张连接方式卡片：row 为本租户的行（没保存过时为空），index 为服务商的默认次序。
     * 没保存过的卡片排序值：API 为 10 + 次序，订阅为 30 + 次序（V1.5.48 已把旧的订阅组编号平移 20），
     * 默认仍是 API 在前；保存后整个列表统一编号 1..n。
     */
    private record Slot(String mode, String provider, SysAiProviderConfigDO row, int index) {
        int sort() {
            if (row != null && row.getSortOrder() != null) {
                return row.getSortOrder();
            }
            return (AiConfigConstants.MODE_API.equals(mode) ? 10 : 30) + index;
        }

        boolean enabled() {
            return row == null || !Integer.valueOf(0).equals(row.getStatus());
        }
    }

    /** 提交任务用的有序列表；configured 表示有已配置且启用的方式（全部不可用时区分「未配置」与「暂不可用」） */
    private record Chain(List<AiCallConfig> calls, boolean configured) {
    }

    @Override
    public AiConfigVO get() {
        int tenantId = currentTenantId();
        boolean platform = tenantId == AiConfigConstants.PLATFORM_TENANT_ID;
        boolean allowed = subscriptionAccess.allowed(tenantId);
        AiLoginStatus login = allowed ? loginStatus() : null;
        AiConfigVO vo = new AiConfigVO();
        vo.setPlatform(platform);
        vo.setSubscriptionAllowed(allowed);
        vo.setDefaultModels(AiConfigConstants.DEFAULT_MODELS);
        List<AiConnectionVO> list = new ArrayList<>();
        for (Slot s : ordered(tenantId, allowed)) {
            boolean api = AiConfigConstants.MODE_API.equals(s.mode());
            SysAiProviderConfigDO settings = api ? s.row() : platformSubscription(s.provider());
            AiConnectionVO c = new AiConnectionVO();
            c.setMode(s.mode());
            c.setProvider(s.provider());
            c.setOrder(list.size() + 1);
            c.setEnabled(s.enabled());
            c.setSettingsReadonly(!api && !platform);
            fillSettings(c, settings, api);
            if (api) {
                c.setState(c.isHasKey() ? "ready" : "no_key");
            } else {
                Boolean ok = available(login, s.provider());
                c.setState(ok == null ? "unknown" : platform ? (ok ? "logged_in" : "logged_out")
                        : (ok ? "available" : "unavailable"));
            }
            AiConnectionPauses.Pause p = pauses.get(tenantId, s.mode(), s.provider());
            if (p != null) {
                c.setPausedReason(p.reason());
                c.setPausedUntil(p.until());
            }
            list.add(c);
        }
        vo.setConnections(list);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(SaveAiSettingsRequest req) {
        int tenantId = currentTenantId();
        boolean platform = tenantId == AiConfigConstants.PLATFORM_TENANT_ID;
        boolean allowed = subscriptionAccess.allowed(tenantId);
        Set<String> seen = new HashSet<>();
        int sort = 0;
        for (SaveAiSettingsRequest.Item item : req.getOrder()) {
            String mode = normalizeMode(item.getMode());
            String provider = normalizeProvider(item.getProvider());
            if (!seen.add(mode + ":" + provider)) {
                throw new BizException(ResultCode.PARAM_ERROR, "连接方式重复");
            }
            if (AiConfigConstants.MODE_SUBSCRIPTION.equals(mode)) {
                if (!allowed) {
                    throw new BizException(ResultCode.PARAM_ERROR, "只能使用本公司 API Key");
                }
            }
            writeOrder(tenantId, mode, provider, ++sort, item.isEnabled());
        }
        if (req.getProfiles() != null) {
            for (SaveAiConfigRequest p : req.getProfiles()) {
                String mode = normalizeMode(p.getMode());
                String provider = normalizeProvider(p.getProvider());
                if (AiConfigConstants.MODE_SUBSCRIPTION.equals(mode)) {
                    if (!allowed) {
                        throw new BizException(ResultCode.PARAM_ERROR, "只能使用本公司 API Key");
                    }
                    // 租户的订阅设置用平台的，提交的修改不生效
                    if (platform) {
                        writeProfile(tenantId, mode, provider, p, false);
                        pauses.clear(tenantId, mode, provider);
                    }
                } else {
                    writeProfile(tenantId, mode, provider, p, true);
                    pauses.clear(tenantId, mode, provider);
                }
            }
        }
        log.info("AI 设置已保存，tenantId={}, order={}, profiles={}", tenantId, seen,
                req.getProfiles() == null ? 0 : req.getProfiles().size());
    }

    @Override
    public List<AiCallConfig> resolveChainForCurrentTenant() {
        Chain c = chain(currentTenantId());
        if (c.calls().isEmpty()) {
            throw c.configured() ? new AiUnavailableException() : new AiNotConfiguredException();
        }
        return c.calls();
    }

    @Override
    public boolean isConfiguredForCurrentTenant() {
        return !chain(currentTenantId()).calls().isEmpty();
    }

    @Override
    public AiConnectionTestVO test(SaveAiConfigRequest req) {
        AiConnectionTestVO result = orchestratorClient.test(fromRequest(req));
        if (result != null && result.isOk()) {
            pauses.clear(currentTenantId(), normalizeMode(req.getMode()), normalizeProvider(req.getProvider()));
        }
        return result;
    }

    @Override
    public AiModelListVO models(SaveAiConfigRequest req) {
        return orchestratorClient.models(fromRequest(req));
    }

    @Override
    public AiSubscriptionStatusVO subscriptionStatus() {
        int tenantId = currentTenantId();
        if (!subscriptionAccess.allowed(tenantId)) {
            throw new BizException(ResultCode.FORBIDDEN, "没有使用平台订阅的权限");
        }
        AiLoginStatus login = loginStatus();
        AiSubscriptionStatusVO vo = new AiSubscriptionStatusVO();
        Map<String, Boolean> available = new HashMap<>();
        for (String provider : AiConfigConstants.PROVIDERS) {
            available.put(provider, available(login, provider));
        }
        vo.setAvailable(available);
        if (tenantId == AiConfigConstants.PLATFORM_TENANT_ID) {
            Map<String, String> states = new HashMap<>();
            for (String provider : AiConfigConstants.PROVIDERS) {
                states.put(provider, login == null ? "unknown" : login.loggedIn(provider) ? "logged_in" : "logged_out");
            }
            vo.setStates(states);
            vo.setLoginCommands(AiConfigConstants.LOGIN_COMMANDS);
        }
        return vo;
    }

    /**
     * 按列表顺序挑出可用的连接方式：跳过未启用、API 没有 Key、处于暂停中的方式，API Key 与订阅按各自位置排列。
     * 有可用的 API Key 时，订阅只按授权与开关参与，是否登录由编排服务调用时判断（未登录会切换并暂停）；
     * 没有可用的 API Key 时才查询订阅登录状态，用来决定能否创建任务——每次查询都要让编排服务运行一次 CLI，不放在每个任务上。
     */
    private Chain chain(int tenantId) {
        boolean allowed = subscriptionAccess.allowed(tenantId);
        List<Slot> usable = new ArrayList<>();
        Map<String, String> keys = new HashMap<>();
        boolean configured = false;
        boolean apiUsable = false;
        boolean subscriptionUsable = false;
        for (Slot s : ordered(tenantId, allowed)) {
            if (!s.enabled()) {
                continue;
            }
            boolean api = AiConfigConstants.MODE_API.equals(s.mode());
            if (api) {
                String key = decryptKey(s.row());
                if (key.isEmpty()) {
                    continue;
                }
                configured = true;
                keys.put(s.provider(), key);
            } else {
                // 保存过的订阅算已配置；从没保存过的（默认参与）要确认过登录状态才算，见下方
                configured |= s.row() != null;
            }
            if (pauses.get(tenantId, s.mode(), s.provider()) != null) {
                configured = true;
                continue;
            }
            usable.add(s);
            apiUsable |= api;
            subscriptionUsable |= !api;
        }
        boolean checkLogin = !apiUsable && subscriptionUsable;
        AiLoginStatus login = checkLogin ? loginStatus() : null;
        List<AiCallConfig> calls = new ArrayList<>();
        for (Slot s : usable) {
            if (AiConfigConstants.MODE_API.equals(s.mode())) {
                calls.add(build(s.mode(), s.provider(), keys.get(s.provider()), s.row()));
                continue;
            }
            if (checkLogin) {
                Boolean ok = available(login, s.provider());
                configured |= ok != null;
                // 状态获取失败时，平台照旧可以用自己保存过的订阅；租户与从没保存过的订阅按不可用处理
                boolean keepUnknown = ok == null && tenantId == AiConfigConstants.PLATFORM_TENANT_ID && s.row() != null;
                if (!Boolean.TRUE.equals(ok) && !keepUnknown) {
                    continue;
                }
            }
            calls.add(build(s.mode(), s.provider(), "", platformSubscription(s.provider())));
        }
        return new Chain(calls, configured);
    }

    /** 本租户按使用顺序排好的卡片（API Key 与订阅在一个列表里）；订阅只在能使用平台订阅时列出 */
    private List<Slot> ordered(int tenantId, boolean allowed) {
        Map<String, SysAiProviderConfigDO> rows = new HashMap<>();
        for (SysAiProviderConfigDO r : profileMapper.selectList(new LambdaQueryWrapper<SysAiProviderConfigDO>()
                .eq(SysAiProviderConfigDO::getTenantId, tenantId))) {
            rows.put(r.getMode() + ":" + r.getProvider(), r);
        }
        // 先按默认次序（Claude API、OpenAI API、Claude 订阅、OpenAI 订阅）排好，排序值相同时保持这个次序（稳定排序）
        List<Slot> out = new ArrayList<>();
        for (String mode : MODES) {
            if (AiConfigConstants.MODE_SUBSCRIPTION.equals(mode) && !allowed) {
                continue;
            }
            for (int i = 0; i < AiConfigConstants.PROVIDERS.size(); i++) {
                String provider = AiConfigConstants.PROVIDERS.get(i);
                out.add(new Slot(mode, provider, rows.get(mode + ":" + provider), i));
            }
        }
        out.sort(Comparator.comparingInt(Slot::sort));
        return out;
    }

    /** 页面当前的值：API 方式 Key 留空时用该服务商已保存的 Key；租户的订阅只用平台的设置 */
    private AiCallConfig fromRequest(SaveAiConfigRequest req) {
        int tenantId = currentTenantId();
        String mode = normalizeMode(req.getMode());
        String provider = normalizeProvider(req.getProvider());
        if (AiConfigConstants.MODE_SUBSCRIPTION.equals(mode)) {
            if (!subscriptionAccess.allowed(tenantId)) {
                throw new BizException(ResultCode.FORBIDDEN, "没有使用平台订阅的权限");
            }
            if (tenantId != AiConfigConstants.PLATFORM_TENANT_ID) {
                return build(mode, provider, "", platformSubscription(provider));
            }
            return build(mode, provider, "", req.getParseModel(), req.getTranslateModel(), req.getParseEffort(),
                    req.getTranslateEffort(), req.getMaxWebSearches());
        }
        String key = trim(req.getApiKey());
        if (key.isEmpty()) {
            key = decryptKey(profile(tenantId, AiConfigConstants.MODE_API, provider));
        }
        return build(mode, provider, key, req.getParseModel(), req.getTranslateModel(), req.getParseEffort(),
                req.getTranslateEffort(), req.getMaxWebSearches());
    }

    /** 写这张卡片在列表中的顺序与开关；没有行时新建（只记顺序与开关，设置为空即默认值） */
    private void writeOrder(int tenantId, String mode, String provider, int sort, boolean enabled) {
        SysAiProviderConfigDO row = profile(tenantId, mode, provider);
        if (row == null) {
            row = newRow(tenantId, mode, provider);
            row.setSortOrder(sort);
            row.setStatus(enabled ? 1 : 0);
            profileMapper.insert(row);
            return;
        }
        row.setSortOrder(sort);
        row.setStatus(enabled ? 1 : 0);
        profileMapper.updateById(row);
    }

    /** 保存这一种连接方式的设置；withKey 为 false 时不写 Key（订阅） */
    private void writeProfile(int tenantId, String mode, String provider, SaveAiConfigRequest req, boolean withKey) {
        SysAiProviderConfigDO row = profile(tenantId, mode, provider);
        boolean insert = row == null;
        if (insert) {
            row = newRow(tenantId, mode, provider);
        }
        if (withKey && StringUtils.hasText(req.getApiKey())) {
            row.setApiKey(aesUtils.encrypt(req.getApiKey().trim()));
        }
        row.setParseModel(trim(req.getParseModel()));
        row.setTranslateModel(trim(req.getTranslateModel()));
        row.setParseEffort(trim(req.getParseEffort()));
        row.setTranslateEffort(trim(req.getTranslateEffort()));
        row.setMaxWebSearches(req.getMaxWebSearches() == null
                ? AiConfigConstants.DEFAULT_MAX_WEB_SEARCHES : req.getMaxWebSearches());
        if (insert) {
            profileMapper.insert(row);
        } else {
            profileMapper.updateById(row);
        }
    }

    private static SysAiProviderConfigDO newRow(int tenantId, String mode, String provider) {
        SysAiProviderConfigDO row = new SysAiProviderConfigDO();
        row.setTenantId(tenantId);
        row.setMode(mode);
        row.setProvider(provider);
        row.setSortOrder(10 + AiConfigConstants.PROVIDERS.indexOf(provider));
        row.setApiKey("");
        row.setMaxWebSearches(AiConfigConstants.DEFAULT_MAX_WEB_SEARCHES);
        row.setStatus(1);
        return row;
    }

    private void fillSettings(AiConnectionVO c, SysAiProviderConfigDO row, boolean api) {
        c.setMaxWebSearches(AiConfigConstants.DEFAULT_MAX_WEB_SEARCHES);
        if (row == null) {
            return;
        }
        c.setSaved(true);
        if (api) {
            c.setHasKey(StringUtils.hasText(row.getApiKey()));
            c.setApiKeyMasked(c.isHasKey() ? AiCallConfig.mask(aesUtils.decrypt(row.getApiKey())) : "");
        }
        c.setParseModel(row.getParseModel());
        c.setTranslateModel(row.getTranslateModel());
        c.setParseEffort(row.getParseEffort());
        c.setTranslateEffort(row.getTranslateEffort());
        c.setMaxWebSearches(row.getMaxWebSearches());
    }

    /** 编排服务报告的登录状态；服务不可达或出错时为 null（未知） */
    private AiLoginStatus loginStatus() {
        try {
            return orchestratorClient.status();
        } catch (BizException e) {
            log.warn("查询订阅登录状态失败：{}", e.getMessage());
            return null;
        }
    }

    private static Boolean available(AiLoginStatus login, String provider) {
        return login == null ? null : login.loggedIn(provider);
    }

    private static AiCallConfig build(String mode, String provider, String key, SysAiProviderConfigDO row) {
        return row == null
                ? build(mode, provider, key, null, null, null, null, null)
                : build(mode, provider, key, row.getParseModel(), row.getTranslateModel(), row.getParseEffort(),
                        row.getTranslateEffort(), row.getMaxWebSearches());
    }

    /** 补全默认值：模型按服务商、思考强度 medium、最大搜索 20 */
    static AiCallConfig build(String mode, String provider, String key, String parseModel, String translateModel,
                              String parseEffort, String translateEffort, Integer maxWebSearches) {
        String p = normalizeProvider(provider);
        return AiCallConfig.builder()
                .mode(StringUtils.hasText(mode) ? mode : AiConfigConstants.MODE_API)
                .provider(p)
                .apiKey(key == null ? "" : key)
                .parseModel(orDefault(parseModel, AiConfigConstants.DEFAULT_MODELS.get(p).get("parse")))
                .translateModel(orDefault(translateModel, AiConfigConstants.DEFAULT_MODELS.get(p).get("translate")))
                .parseEffort(orDefault(parseEffort, AiConfigConstants.DEFAULT_EFFORT))
                .translateEffort(orDefault(translateEffort, AiConfigConstants.DEFAULT_EFFORT))
                .maxWebSearches(maxWebSearches == null ? AiConfigConstants.DEFAULT_MAX_WEB_SEARCHES : maxWebSearches)
                .build();
    }

    private String decryptKey(SysAiProviderConfigDO row) {
        return row != null && StringUtils.hasText(row.getApiKey()) ? aesUtils.decrypt(row.getApiKey()) : "";
    }

    private static String normalizeMode(String mode) {
        String m = StringUtils.hasText(mode) ? mode.trim() : AiConfigConstants.MODE_API;
        if (!MODES.contains(m)) {
            throw new BizException(ResultCode.PARAM_ERROR, "运行方式只能是 api 或 subscription");
        }
        return m;
    }

    private static String normalizeProvider(String provider) {
        String p = StringUtils.hasText(provider) ? provider.trim() : AiConfigConstants.PROVIDER_CLAUDE;
        if (!AiConfigConstants.PROVIDERS.contains(p)) {
            throw new BizException(ResultCode.PARAM_ERROR, "服务商只能是 claude 或 openai");
        }
        return p;
    }

    private static String orDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private SysAiProviderConfigDO profile(int tenantId, String mode, String provider) {
        return profileMapper.selectOne(new LambdaQueryWrapper<SysAiProviderConfigDO>()
                .eq(SysAiProviderConfigDO::getTenantId, tenantId)
                .eq(SysAiProviderConfigDO::getMode, mode)
                .eq(SysAiProviderConfigDO::getProvider, provider));
    }

    /** 平台为该服务商保存的订阅设置；没保存过时为 null（用默认值） */
    private SysAiProviderConfigDO platformSubscription(String provider) {
        return profile(AiConfigConstants.PLATFORM_TENANT_ID, AiConfigConstants.MODE_SUBSCRIPTION, provider);
    }

    private static int currentTenantId() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : AiConfigConstants.PLATFORM_TENANT_ID;
    }
}

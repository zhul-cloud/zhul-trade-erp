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
import com.zhul.erp.modules.aitask.dto.AiLoginStatus;
import com.zhul.erp.modules.aitask.dto.AiModelListVO;
import com.zhul.erp.modules.aitask.dto.AiProfileVO;
import com.zhul.erp.modules.aitask.dto.AiSubscriptionStatusVO;
import com.zhul.erp.modules.aitask.dto.SaveAiConfigRequest;
import com.zhul.erp.modules.aitask.entity.SysAiConfigDO;
import com.zhul.erp.modules.aitask.entity.SysAiProviderConfigDO;
import com.zhul.erp.modules.aitask.exception.AiNotConfiguredException;
import com.zhul.erp.modules.aitask.exception.AiUnavailableException;
import com.zhul.erp.modules.aitask.repository.SysAiConfigMapper;
import com.zhul.erp.modules.aitask.repository.SysAiProviderConfigMapper;
import com.zhul.erp.modules.aitask.service.AiConfigService;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.modules.aitask.service.AiSubscriptionAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 租户 AI 配置：sys_ai_config 记录当前选择（用法 + 服务商），sys_ai_provider_config 按连接方式分别保存设置；
 * 平台订阅按套餐授权给公司使用，租户使用平台的订阅设置。见 openspec ai-platform/tenant-ai-settings。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    private final SysAiConfigMapper mapper;
    private final SysAiProviderConfigMapper profileMapper;
    private final AesUtils aesUtils;
    private final AiOrchestratorClient orchestratorClient;
    private final AiSubscriptionAccess subscriptionAccess;

    @Override
    public AiConfigVO get() {
        int tenantId = currentTenantId();
        boolean allowed = subscriptionAccess.allowed(tenantId);
        SysAiConfigDO selected = find(tenantId);
        AiConfigVO vo = new AiConfigVO();
        vo.setPlatform(tenantId == AiConfigConstants.PLATFORM_TENANT_ID);
        vo.setSubscriptionAllowed(allowed);
        vo.setDefaultModels(AiConfigConstants.DEFAULT_MODELS);
        vo.setConfigured(selected != null);
        vo.setMode(selected != null ? selected.getMode() : AiConfigConstants.MODE_API);
        vo.setProvider(selected != null ? selected.getProvider() : AiConfigConstants.PROVIDER_CLAUDE);
        if (selected != null) {
            vo.setUpdateTime(selected.getUpdateTime());
            vo.setUpdateBy(selected.getUpdateBy());
        }
        Map<String, AiProfileVO> api = new LinkedHashMap<>();
        Map<String, AiProfileVO> subscription = new LinkedHashMap<>();
        for (String provider : AiConfigConstants.PROVIDERS) {
            api.put(provider, toProfileVO(profile(tenantId, AiConfigConstants.MODE_API, provider)));
            subscription.put(provider, toProfileVO(platformSubscription(provider)));
        }
        vo.setApiProfiles(api);
        if (allowed) {
            vo.setSubscriptionProfiles(subscription);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(SaveAiConfigRequest req) {
        int tenantId = currentTenantId();
        String mode = normalizeMode(req.getMode());
        String provider = normalizeProvider(req.getProvider());
        boolean platform = tenantId == AiConfigConstants.PLATFORM_TENANT_ID;
        if (AiConfigConstants.MODE_SUBSCRIPTION.equals(mode)) {
            if (!subscriptionAccess.allowed(tenantId)) {
                throw new BizException(ResultCode.PARAM_ERROR, "只能使用本公司 API Key");
            }
            // 平台可以先保存未登录服务商的订阅设置；租户只能选可用的
            if (!platform && !Boolean.TRUE.equals(available(loginStatus(), provider))) {
                throw new BizException(ResultCode.PARAM_ERROR, "该平台订阅暂不可用，请选择其他方式");
            }
            if (platform) {
                writeProfile(tenantId, mode, provider, req, false);
            }
        } else {
            writeProfile(tenantId, mode, provider, req, true);
        }
        SysAiConfigDO selected = find(tenantId);
        if (selected == null) {
            selected = new SysAiConfigDO();
            selected.setTenantId(tenantId);
            selected.setStatus(1);
            selected.setApiKey("");
            selected.setMode(mode);
            selected.setProvider(provider);
            mapper.insert(selected);
        } else {
            selected.setMode(mode);
            selected.setProvider(provider);
            mapper.updateById(selected);
        }
        log.info("AI 配置已保存，tenantId={}, mode={}, provider={}, keyChanged={}",
                tenantId, mode, provider, StringUtils.hasText(req.getApiKey()));
    }

    @Override
    public AiCallConfig resolveForCurrentTenant() {
        int tenantId = currentTenantId();
        SysAiConfigDO selected = find(tenantId);
        if (selected == null) {
            throw new AiNotConfiguredException();
        }
        String provider = selected.getProvider();
        if (AiConfigConstants.MODE_SUBSCRIPTION.equals(selected.getMode())) {
            if (tenantId != AiConfigConstants.PLATFORM_TENANT_ID
                    && (!subscriptionAccess.allowed(tenantId) || !Boolean.TRUE.equals(available(loginStatus(), provider)))) {
                throw new AiUnavailableException();
            }
            return build(AiConfigConstants.MODE_SUBSCRIPTION, provider, "", platformSubscription(provider));
        }
        SysAiProviderConfigDO row = profile(tenantId, AiConfigConstants.MODE_API, provider);
        String key = decryptKey(row);
        if (key.isEmpty()) {
            throw new AiNotConfiguredException();
        }
        return build(AiConfigConstants.MODE_API, provider, key, row);
    }

    @Override
    public boolean isConfiguredForCurrentTenant() {
        int tenantId = currentTenantId();
        SysAiConfigDO selected = find(tenantId);
        if (selected == null) {
            return false;
        }
        // 订阅与 resolveForCurrentTenant 同样要查登录，否则随后的 createAndSubmit 会在业务事务里抛 AiUnavailableException
        if (AiConfigConstants.MODE_SUBSCRIPTION.equals(selected.getMode())) {
            return tenantId == AiConfigConstants.PLATFORM_TENANT_ID
                    || (subscriptionAccess.allowed(tenantId)
                    && Boolean.TRUE.equals(available(loginStatus(), selected.getProvider())));
        }
        SysAiProviderConfigDO row = profile(tenantId, AiConfigConstants.MODE_API, selected.getProvider());
        return row != null && StringUtils.hasText(row.getApiKey());
    }

    @Override
    public AiConnectionTestVO test(SaveAiConfigRequest req) {
        return orchestratorClient.test(fromRequest(req));
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

    /** 保存这一种连接方式的设置；withKey 为 false 时不写 Key（订阅） */
    private void writeProfile(int tenantId, String mode, String provider, SaveAiConfigRequest req, boolean withKey) {
        SysAiProviderConfigDO row = profile(tenantId, mode, provider);
        boolean insert = row == null;
        if (insert) {
            row = new SysAiProviderConfigDO();
            row.setTenantId(tenantId);
            row.setMode(mode);
            row.setProvider(provider);
            row.setApiKey("");
            row.setStatus(1);
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

    private AiProfileVO toProfileVO(SysAiProviderConfigDO row) {
        AiProfileVO vo = new AiProfileVO();
        vo.setMaxWebSearches(AiConfigConstants.DEFAULT_MAX_WEB_SEARCHES);
        if (row == null) {
            return vo;
        }
        vo.setSaved(true);
        vo.setHasKey(StringUtils.hasText(row.getApiKey()));
        vo.setApiKeyMasked(vo.isHasKey() ? AiCallConfig.mask(aesUtils.decrypt(row.getApiKey())) : "");
        vo.setParseModel(row.getParseModel());
        vo.setTranslateModel(row.getTranslateModel());
        vo.setParseEffort(row.getParseEffort());
        vo.setTranslateEffort(row.getTranslateEffort());
        vo.setMaxWebSearches(row.getMaxWebSearches());
        return vo;
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
        if (!AiConfigConstants.MODE_API.equals(m) && !AiConfigConstants.MODE_SUBSCRIPTION.equals(m)) {
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

    private SysAiConfigDO find(int tenantId) {
        return mapper.selectOne(new LambdaQueryWrapper<SysAiConfigDO>().eq(SysAiConfigDO::getTenantId, tenantId));
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

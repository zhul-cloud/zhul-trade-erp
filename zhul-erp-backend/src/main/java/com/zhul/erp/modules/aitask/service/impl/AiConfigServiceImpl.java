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
import com.zhul.erp.modules.aitask.dto.AiModelListVO;
import com.zhul.erp.modules.aitask.dto.AiSubscriptionStatusVO;
import com.zhul.erp.modules.aitask.dto.SaveAiConfigRequest;
import com.zhul.erp.modules.aitask.entity.SysAiConfigDO;
import com.zhul.erp.modules.aitask.exception.AiNotConfiguredException;
import com.zhul.erp.modules.aitask.repository.SysAiConfigMapper;
import com.zhul.erp.modules.aitask.service.AiConfigService;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    private final SysAiConfigMapper mapper;
    private final AesUtils aesUtils;
    private final AiOrchestratorClient orchestratorClient;

    @Override
    public AiConfigVO get() {
        int tenantId = currentTenantId();
        SysAiConfigDO row = find(tenantId);
        AiConfigVO vo = new AiConfigVO();
        vo.setSubscriptionAllowed(tenantId == AiConfigConstants.PLATFORM_TENANT_ID);
        vo.setDefaultModels(AiConfigConstants.DEFAULT_MODELS);
        if (row == null) {
            vo.setConfigured(false);
            vo.setMode(AiConfigConstants.MODE_API);
            vo.setProvider(AiConfigConstants.PROVIDER_CLAUDE);
            vo.setMaxWebSearches(AiConfigConstants.DEFAULT_MAX_WEB_SEARCHES);
            vo.setParseModel("");
            vo.setTranslateModel("");
            vo.setParseEffort("");
            vo.setTranslateEffort("");
            vo.setApiKeyMasked("");
            return vo;
        }
        vo.setConfigured(true);
        vo.setMode(row.getMode());
        vo.setProvider(row.getProvider());
        vo.setHasKey(StringUtils.hasText(row.getApiKey()));
        vo.setApiKeyMasked(vo.isHasKey() ? AiCallConfig.mask(aesUtils.decrypt(row.getApiKey())) : "");
        vo.setParseModel(row.getParseModel());
        vo.setTranslateModel(row.getTranslateModel());
        vo.setParseEffort(row.getParseEffort());
        vo.setTranslateEffort(row.getTranslateEffort());
        vo.setMaxWebSearches(row.getMaxWebSearches());
        vo.setUpdateTime(row.getUpdateTime());
        vo.setUpdateBy(row.getUpdateBy());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(SaveAiConfigRequest req) {
        int tenantId = currentTenantId();
        String mode = normalizeMode(req.getMode(), tenantId);
        SysAiConfigDO row = find(tenantId);
        boolean insert = row == null;
        if (insert) {
            row = new SysAiConfigDO();
            row.setTenantId(tenantId);
            row.setStatus(1);
            row.setApiKey("");
        }
        row.setMode(mode);
        row.setProvider(normalizeProvider(req.getProvider()));
        if (StringUtils.hasText(req.getApiKey())) {
            row.setApiKey(aesUtils.encrypt(req.getApiKey().trim()));
        }
        row.setParseModel(trim(req.getParseModel()));
        row.setTranslateModel(trim(req.getTranslateModel()));
        row.setParseEffort(trim(req.getParseEffort()));
        row.setTranslateEffort(trim(req.getTranslateEffort()));
        row.setMaxWebSearches(req.getMaxWebSearches());
        if (insert) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
        log.info("AI 配置已保存，tenantId={}, mode={}, provider={}, keyChanged={}",
                tenantId, mode, row.getProvider(), StringUtils.hasText(req.getApiKey()));
    }

    @Override
    public AiCallConfig resolveForCurrentTenant() {
        SysAiConfigDO row = find(currentTenantId());
        if (row == null) {
            throw new AiNotConfiguredException();
        }
        String key = StringUtils.hasText(row.getApiKey()) ? aesUtils.decrypt(row.getApiKey()) : "";
        if (!AiConfigConstants.MODE_SUBSCRIPTION.equals(row.getMode()) && key.isEmpty()) {
            throw new AiNotConfiguredException();
        }
        return build(row.getMode(), row.getProvider(), key, row.getParseModel(), row.getTranslateModel(),
                row.getParseEffort(), row.getTranslateEffort(), row.getMaxWebSearches());
    }

    @Override
    public boolean isConfiguredForCurrentTenant() {
        SysAiConfigDO row = find(currentTenantId());
        return row != null
                && (AiConfigConstants.MODE_SUBSCRIPTION.equals(row.getMode()) || StringUtils.hasText(row.getApiKey()));
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
        if (currentTenantId() != AiConfigConstants.PLATFORM_TENANT_ID) {
            throw new BizException(ResultCode.FORBIDDEN, "只有平台租户能查看订阅登录状态");
        }
        return orchestratorClient.status();
    }

    /** 页面当前填写的值；Key 留空时用已保存的 Key（可能仍为空，由编排服务提示未填写） */
    private AiCallConfig fromRequest(SaveAiConfigRequest req) {
        int tenantId = currentTenantId();
        String key = trim(req.getApiKey());
        if (key.isEmpty()) {
            SysAiConfigDO row = find(tenantId);
            if (row != null && StringUtils.hasText(row.getApiKey())) {
                key = aesUtils.decrypt(row.getApiKey());
            }
        }
        return build(normalizeMode(req.getMode(), tenantId), normalizeProvider(req.getProvider()), key,
                req.getParseModel(), req.getTranslateModel(), req.getParseEffort(), req.getTranslateEffort(),
                req.getMaxWebSearches());
    }

    /** 补全默认值：服务商 claude、模型按服务商、思考强度 medium、最大搜索 20 */
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

    private String normalizeMode(String mode, int tenantId) {
        String m = StringUtils.hasText(mode) ? mode.trim() : AiConfigConstants.MODE_API;
        if (AiConfigConstants.MODE_SUBSCRIPTION.equals(m) && tenantId != AiConfigConstants.PLATFORM_TENANT_ID) {
            throw new BizException(ResultCode.PARAM_ERROR, "只能使用 API 方式，订阅方式仅供平台使用");
        }
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

    private static int currentTenantId() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : AiConfigConstants.PLATFORM_TENANT_ID;
    }
}

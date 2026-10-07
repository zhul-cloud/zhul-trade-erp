package com.zhul.erp.modules.quotation.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.entity.PricingAmountTierDO;
import com.zhul.erp.modules.quotation.entity.PricingConditionMarginDO;
import com.zhul.erp.modules.quotation.repository.PricingAmountTierMapper;
import com.zhul.erp.modules.quotation.repository.PricingConditionMarginMapper;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 读取定价策略：租户改过就用租户自己的，否则用平台默认（tenant_id=0）。
 * 品相毛利率、低值耗材分层各自整体判断「租户是否有自己的一套」；转人工提示与品牌清单按配置项逐个回退。
 */
@Component
@RequiredArgsConstructor
public class PricingSettings {

    private static final int PLATFORM = 0;

    private final PricingConditionMarginMapper marginMapper;
    private final PricingAmountTierMapper tierMapper;
    private final SysConfigMapper sysConfigMapper;
    private final ObjectMapper objectMapper;

    public PricingStrategy load(int tenantId) {
        Map<Integer, PricingStrategy.Margin> conditions = new LinkedHashMap<>();
        for (PricingConditionMarginDO r : marginRows(ownerOfMargins(tenantId))) {
            if (Objects.equals(r.getStatus(), 1)) {
                conditions.put(r.getItemCondition(), new PricingStrategy.Margin(r.getMarginRate(), r.getFloorRate()));
            }
        }
        List<PricingStrategy.Tier> tiers = tierRows(ownerOfTiers(tenantId)).stream()
                .filter(r -> Objects.equals(r.getStatus(), 1))
                .sorted(Comparator.comparing(PricingAmountTierDO::getMaxCost))
                .map(r -> new PricingStrategy.Tier(r.getMaxCost(), r.getMarginRate()))
                .toList();
        return new PricingStrategy(conditions, tiers,
                bool(tenantId, QuotationConstants.CONFIG_HINT_DISCONTINUED_URGENT),
                bool(tenantId, QuotationConstants.CONFIG_HINT_PREMIUM_BRAND),
                bool(tenantId, QuotationConstants.CONFIG_HINT_RETURNING_CUSTOMER),
                bool(tenantId, QuotationConstants.CONFIG_HINT_TO_CONFIRM),
                brands(tenantId));
    }

    /** 租户在表里有任何一行（含停用的）就视为有自己的一套 */
    public int ownerOfMargins(int tenantId) {
        return tenantId != PLATFORM && marginMapper.selectCount(new LambdaQueryWrapper<PricingConditionMarginDO>()
                .eq(PricingConditionMarginDO::getTenantId, tenantId)) > 0 ? tenantId : PLATFORM;
    }

    public int ownerOfTiers(int tenantId) {
        return tenantId != PLATFORM && tierMapper.selectCount(new LambdaQueryWrapper<PricingAmountTierDO>()
                .eq(PricingAmountTierDO::getTenantId, tenantId)) > 0 ? tenantId : PLATFORM;
    }

    public List<PricingConditionMarginDO> marginRows(int owner) {
        return marginMapper.selectList(new LambdaQueryWrapper<PricingConditionMarginDO>()
                .eq(PricingConditionMarginDO::getTenantId, owner));
    }

    public List<PricingAmountTierDO> tierRows(int owner) {
        return tierMapper.selectList(new LambdaQueryWrapper<PricingAmountTierDO>()
                .eq(PricingAmountTierDO::getTenantId, owner));
    }

    /** 写配置：租户第一次改时复制平台配置的名称与说明生成租户自己的一行 */
    public void setConfig(int tenantId, String key, String value) {
        SysConfigDO own = configRow(tenantId, key);
        if (own != null) {
            own.setConfigValue(value);
            sysConfigMapper.updateById(own);
            return;
        }
        SysConfigDO platform = configRow(PLATFORM, key);
        SysConfigDO row = new SysConfigDO();
        row.setTenantId(tenantId);
        row.setConfigKey(key);
        row.setConfigName(platform == null ? key : platform.getConfigName());
        row.setConfigValue(value);
        row.setConfigType(platform == null ? "STRING" : platform.getConfigType());
        row.setIsBuiltin(1);
        row.setIsEncrypted(0);
        row.setConfigGroup(QuotationConstants.CONFIG_GROUP);
        row.setRemark(platform == null ? "" : platform.getRemark());
        sysConfigMapper.insert(row);
    }

    public String toJson(List<String> brands) {
        try {
            return objectMapper.writeValueAsString(brands);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("品牌清单序列化失败", e);
        }
    }

    private List<String> brands(int tenantId) {
        String v = value(tenantId, QuotationConstants.CONFIG_PREMIUM_BRANDS);
        if (!StringUtils.hasText(v)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(v, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private boolean bool(int tenantId, String key) {
        return "true".equalsIgnoreCase(value(tenantId, key));
    }

    private String value(int tenantId, String key) {
        SysConfigDO own = tenantId == PLATFORM ? null : configRow(tenantId, key);
        SysConfigDO row = own != null ? own : configRow(PLATFORM, key);
        return row == null ? null : row.getConfigValue();
    }

    private SysConfigDO configRow(int tenantId, String key) {
        return sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, key)
                .eq(SysConfigDO::getTenantId, tenantId)
                .isNull(SysConfigDO::getDeletedAt)
                .last("LIMIT 1"));
    }
}

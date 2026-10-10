package com.zhul.erp.modules.inquiry.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

/** 询价相关系统设置：租户覆盖值优先，其次平台模板（tenant_id=0），都没有时用默认值 */
@Component
@RequiredArgsConstructor
public class SourcingSettings {

    private static final int DEFAULT_TIMEOUT_HOURS = 24;
    private static final int DEFAULT_URGENT_TIMEOUT_HOURS = 4;

    private final SysConfigMapper sysConfigMapper;

    public int timeoutHours(int tenantId, boolean urgent) {
        return urgent
                ? intValue(tenantId, InquiryConstants.CONFIG_URGENT_TIMEOUT_HOURS, DEFAULT_URGENT_TIMEOUT_HOURS)
                : intValue(tenantId, InquiryConstants.CONFIG_TIMEOUT_HOURS, DEFAULT_TIMEOUT_HOURS);
    }

    public boolean autoAssign(int tenantId) {
        return "true".equalsIgnoreCase(value(tenantId, InquiryConstants.CONFIG_AUTO_ASSIGN));
    }

    /** 写自动分配开关：平台账号改模板；租户第一次改时复制模板生成租户自己的设置 */
    public void setAutoAssign(int tenantId, boolean enabled) {
        String value = String.valueOf(enabled);
        SysConfigDO own = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, InquiryConstants.CONFIG_AUTO_ASSIGN)
                .eq(SysConfigDO::getTenantId, tenantId)
                .isNull(SysConfigDO::getDeletedAt)
                .last("LIMIT 1"));
        if (own != null) {
            own.setConfigValue(value);
            sysConfigMapper.updateById(own);
            return;
        }
        SysConfigDO row = new SysConfigDO();
        row.setTenantId(tenantId);
        row.setConfigKey(InquiryConstants.CONFIG_AUTO_ASSIGN);
        row.setConfigName("询价任务自动分配");
        row.setConfigValue(value);
        row.setConfigType("BOOLEAN");
        row.setIsBuiltin(1);
        row.setIsEncrypted(0);
        row.setConfigGroup("inquiry");
        row.setRemark("开启后新任务按分配规则自动分配");
        sysConfigMapper.insert(row);
    }

    private int intValue(int tenantId, String key, int fallback) {
        String v = value(tenantId, key);
        if (!StringUtils.hasText(v)) {
            return fallback;
        }
        try {
            int n = Integer.parseInt(v.trim());
            return n > 0 ? n : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String value(int tenantId, String key) {
        List<SysConfigDO> rows = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, key)
                .in(SysConfigDO::getTenantId, List.of(0, tenantId))
                .isNull(SysConfigDO::getDeletedAt));
        return rows.stream()
                .max(Comparator.comparing(SysConfigDO::getTenantId))
                .map(SysConfigDO::getConfigValue)
                .orElse(null);
    }
}

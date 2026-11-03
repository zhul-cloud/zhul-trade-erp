package com.zhul.erp.modules.masterdata.customerprofile.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** 经营等级参数：租户覆盖值优先，其次平台模板（tenant_id=0），填错或缺失时用默认值 */
@Component
@RequiredArgsConstructor
public class CustomerGradeSettings {

    static final String RECENCY = "customer.grade.recency-days";
    static final String FREQUENCY = "customer.grade.frequency-orders";
    static final String MONETARY = "customer.grade.monetary-usd";
    static final String WEIGHTS = "customer.grade.weights";
    static final String CUTOFFS = "customer.grade.cutoffs";
    static final String FOLLOW = "customer.grade.follow-days";

    private final SysConfigMapper sysConfigMapper;

    public RfmCalculator.Params params(int tenantId) {
        RfmCalculator.Params d = RfmCalculator.Params.defaults();
        return new RfmCalculator.Params(
                ints(value(tenantId, RECENCY), 4, d.recencyDays()),
                ints(value(tenantId, FREQUENCY), 4, d.frequency()),
                decimals(value(tenantId, MONETARY), 4, d.monetaryUsd()),
                decimals(value(tenantId, WEIGHTS), 3, d.weights()),
                decimals(value(tenantId, CUTOFFS), 3, d.cutoffs()),
                ints(value(tenantId, FOLLOW), 4, d.followDays()));
    }

    static int[] ints(String csv, int size, int[] fallback) {
        if (!StringUtils.hasText(csv)) {
            return fallback;
        }
        String[] parts = csv.split(",");
        if (parts.length != size) {
            return fallback;
        }
        int[] out = new int[size];
        try {
            for (int i = 0; i < size; i++) {
                out[i] = Integer.parseInt(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            return fallback;
        }
        return out;
    }

    static BigDecimal[] decimals(String csv, int size, BigDecimal[] fallback) {
        if (!StringUtils.hasText(csv)) {
            return fallback;
        }
        String[] parts = csv.split(",");
        if (parts.length != size) {
            return fallback;
        }
        BigDecimal[] out = new BigDecimal[size];
        try {
            for (int i = 0; i < size; i++) {
                out[i] = new BigDecimal(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            return fallback;
        }
        return out;
    }

    private String value(int tenantId, String key) {
        List<SysConfigDO> rows = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, key)
                .in(SysConfigDO::getTenantId, List.of(0, tenantId))
                .isNull(SysConfigDO::getDeletedAt));
        return rows.stream().max(Comparator.comparing(SysConfigDO::getTenantId)).map(SysConfigDO::getConfigValue).orElse(null);
    }
}

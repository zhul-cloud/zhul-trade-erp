package com.zhul.erp.modules.crm.followup.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 客户跟进系统参数：租户覆盖值优先，其次平台模板（tenant_id = 0），填错或缺失时用默认值 */
@Component
@RequiredArgsConstructor
public class FollowupSettings {

    private final SysConfigMapper sysConfigMapper;

    /** 一个租户的参数快照 */
    public record Params(List<Integer> quoteDays, int piIntervalDays, int nurtureDefaultDays, int nurtureQuoteDays,
                         int nurtureNoReplyTimes, List<BigDecimal> amountTiers) {

        public static Params defaults() {
            return new Params(List.of(1, 3, 7, 14), 2, 60, 14, 3,
                    List.of(new BigDecimal("200000"), new BigDecimal("50000"), new BigDecimal("10000")));
        }

        /** 金额档：3 最高，0 最低（没有金额也是 0）；只用于排序比较，不舍入存储 */
        public int tierOf(BigDecimal amountCny) {
            if (amountCny == null) {
                return 0;
            }
            for (int i = 0; i < amountTiers.size(); i++) {
                if (amountCny.compareTo(amountTiers.get(i)) >= 0) {
                    return amountTiers.size() - i;
                }
            }
            return 0;
        }
    }

    public Params params(int tenantId) {
        Map<String, String> v = values(tenantId);
        Params d = Params.defaults();
        return new Params(
                ascending(v.get(FollowupConstants.CFG_QUOTE_DAYS), d.quoteDays()),
                positive(v.get(FollowupConstants.CFG_PI_INTERVAL), d.piIntervalDays()),
                positive(v.get(FollowupConstants.CFG_NURTURE_DEFAULT), d.nurtureDefaultDays()),
                positive(v.get(FollowupConstants.CFG_NURTURE_QUOTE_DAYS), d.nurtureQuoteDays()),
                positive(v.get(FollowupConstants.CFG_NURTURE_NO_REPLY), d.nurtureNoReplyTimes()),
                descendingAmounts(v.get(FollowupConstants.CFG_AMOUNT_TIERS), d.amountTiers()));
    }

    static List<Integer> ascending(String csv, List<Integer> fallback) {
        if (!StringUtils.hasText(csv)) {
            return fallback;
        }
        List<Integer> out = new ArrayList<>(8);
        try {
            for (String p : csv.split(",")) {
                int n = Integer.parseInt(p.trim());
                if (n <= 0 || (!out.isEmpty() && n <= out.get(out.size() - 1))) {
                    return fallback;
                }
                out.add(n);
            }
        } catch (NumberFormatException e) {
            return fallback;
        }
        return out.isEmpty() ? fallback : List.copyOf(out);
    }

    static int positive(String s, int fallback) {
        if (!StringUtils.hasText(s)) {
            return fallback;
        }
        try {
            int n = Integer.parseInt(s.trim());
            return n > 0 ? n : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static List<BigDecimal> descendingAmounts(String csv, List<BigDecimal> fallback) {
        if (!StringUtils.hasText(csv)) {
            return fallback;
        }
        List<BigDecimal> out = new ArrayList<>(4);
        try {
            for (String p : csv.split(",")) {
                BigDecimal n = new BigDecimal(p.trim());
                if (n.signum() <= 0 || (!out.isEmpty() && n.compareTo(out.get(out.size() - 1)) >= 0)) {
                    return fallback;
                }
                out.add(n);
            }
        } catch (NumberFormatException e) {
            return fallback;
        }
        return out.isEmpty() ? fallback : List.copyOf(out);
    }

    private Map<String, String> values(int tenantId) {
        List<SysConfigDO> rows = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigDO>()
                .likeRight(SysConfigDO::getConfigKey, "sales.followup.")
                .in(SysConfigDO::getTenantId, List.of(0, tenantId))
                .isNull(SysConfigDO::getDeletedAt));
        return rows.stream().collect(Collectors.groupingBy(SysConfigDO::getConfigKey,
                Collectors.collectingAndThen(Collectors.maxBy(Comparator.comparing(SysConfigDO::getTenantId)),
                        o -> o.map(SysConfigDO::getConfigValue).orElse(""))));
    }
}

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

    /** 订单履约、售后评价的参数：快递承运商（大写）、快递 / 其他运输天数、评价提醒间隔与次数 */
    public record OrderParams(List<String> expressCarriers, int expressDays, int otherDays, int reviewDays, int reviewTimes) {

        public static OrderParams defaults() {
            return new OrderParams(List.of("DHL", "FEDEX", "UPS", "TNT", "EMS", "ARAMEX"), 5, 10, 7, 2);
        }

        /** 承运商去掉空格、转大写后以清单中任一项开头的算快递 */
        public boolean express(String carrier) {
            if (carrier == null) {
                return false;
            }
            String c = carrier.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
            return !c.isEmpty() && expressCarriers.stream().anyMatch(c::startsWith);
        }

        public int transitDays(String carrier) {
            return express(carrier) ? expressDays : otherDays;
        }
    }

    /** 客户经营的参数（第三期 design D4、D5）：预计采购提前天数、沉睡天数、安静天数、沉睡激活间隔、季度回顾间隔 */
    public record AccountParams(int aheadDays, int dormantDays, int dormantQuietDays, int dormantIntervalDays,
                                int reviewDays) {

        public static AccountParams defaults() {
            return new AccountParams(30, 180, 90, 60, 90);
        }
    }

    public AccountParams accountParams(int tenantId) {
        Map<String, String> v = values(tenantId);
        AccountParams d = AccountParams.defaults();
        return new AccountParams(positive(v.get(FollowupConstants.CFG_ACCOUNT_AHEAD), d.aheadDays()),
                positive(v.get(FollowupConstants.CFG_DORMANT_DAYS), d.dormantDays()),
                positive(v.get(FollowupConstants.CFG_DORMANT_QUIET), d.dormantQuietDays()),
                positive(v.get(FollowupConstants.CFG_DORMANT_INTERVAL), d.dormantIntervalDays()),
                positive(v.get(FollowupConstants.CFG_STRATEGIC_REVIEW), d.reviewDays()));
    }

    /** 主动开发触达序列：首次触达日为第 1 天，之后第几天提醒再跟（第四期 design D5） */
    public static final List<Integer> DEFAULT_DEVELOP_SEQUENCE = List.of(1, 4, 9, 16);

    public List<Integer> developSequence(int tenantId) {
        return ascending(values(tenantId).get(FollowupConstants.CFG_DEVELOP_SEQUENCE), DEFAULT_DEVELOP_SEQUENCE);
    }

    public OrderParams orderParams(int tenantId) {
        Map<String, String> v = values(tenantId);
        OrderParams d = OrderParams.defaults();
        return new OrderParams(carriers(v.get(FollowupConstants.CFG_EXPRESS_CARRIERS), d.expressCarriers()),
                positive(v.get(FollowupConstants.CFG_EXPRESS_DAYS), d.expressDays()),
                positive(v.get(FollowupConstants.CFG_OTHER_DAYS), d.otherDays()),
                positive(v.get(FollowupConstants.CFG_REVIEW_DAYS), d.reviewDays()),
                positive(v.get(FollowupConstants.CFG_REVIEW_TIMES), d.reviewTimes()));
    }

    static List<String> carriers(String csv, List<String> fallback) {
        if (!StringUtils.hasText(csv)) {
            return fallback;
        }
        List<String> out = java.util.Arrays.stream(csv.split(","))
                .map(x -> x.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT))
                .filter(StringUtils::hasText).toList();
        return out.isEmpty() ? fallback : out;
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

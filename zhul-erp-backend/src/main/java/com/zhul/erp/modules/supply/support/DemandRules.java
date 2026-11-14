package com.zhul.erp.modules.supply.support;

import com.zhul.erp.modules.supply.constants.SupplyConstants;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 需求等级、建议优先级、建议覆盖状态的计算（design 决策 4、5、6），纯函数，不读库。
 * 阈值先写死，上线后按实际数据再定要不要做成可配置。
 */
public final class DemandRules {

    private DemandRules() {
    }

    /** 近 90 天的有效信号次数（不含来源已取消、已忽略） */
    public record Counts(int inquiry, int order, int stockout, int lost, int lostSupply, int search, int research,
                         int observeSources) {
    }

    /** 全部有效信号是否出现过各类（等级按有没有算，不限 90 天） */
    public record Presence(boolean inquiry, boolean commercial, int observeCount, int observeSources) {
    }

    /** 需求等级：有订单或未成交报价为商业 → 有询盘为已验证 → 搜索词、调研两次以上或两个以上来源为重复 → 观察 */
    public static int level(Presence p) {
        if (p.commercial()) {
            return SupplyConstants.LEVEL_COMMERCIAL;
        }
        if (p.inquiry()) {
            return SupplyConstants.LEVEL_VERIFIED;
        }
        if (p.observeCount() >= 2 || p.observeSources() >= 2) {
            return SupplyConstants.LEVEL_REPEAT;
        }
        return SupplyConstants.LEVEL_OBSERVE;
    }

    public record Priority(int value, String rule) {
    }

    /** 建议优先级，命中的规则用于页面显示 */
    public static Priority priority(Counts c) {
        if (c.inquiry() >= 3) {
            return new Priority(SupplyConstants.PRIORITY_HIGH, "近 90 天询盘 " + c.inquiry() + " 次");
        }
        if (c.order() > 0 && c.stockout() > 0) {
            return new Priority(SupplyConstants.PRIORITY_HIGH, "有成交且出现过无货");
        }
        if (c.lostSupply() >= 2) {
            return new Priority(SupplyConstants.PRIORITY_HIGH, "因交期/物流未成交 " + c.lostSupply() + " 次");
        }
        if (c.inquiry() == 2) {
            return new Priority(SupplyConstants.PRIORITY_MID, "近 90 天询盘 2 次");
        }
        if (c.order() > 0) {
            return new Priority(SupplyConstants.PRIORITY_MID, "近 90 天有成交");
        }
        return new Priority(SupplyConstants.PRIORITY_LOW, "");
    }

    /** 证据距今天数在有效天数以内（第 N 天有效，第 N+1 天过期） */
    public static boolean evidenceValid(LocalDate evidenceDate, LocalDate today, int validDays) {
        return evidenceDate != null && ChronoUnit.DAYS.between(evidenceDate, today) <= validDays;
    }

    /**
     * 建议覆盖状态：有效期内有证据 → 可直接报价；有证据但过期 → 需确认；有主营供应商 → 有线索未验证；否则无渠道。
     * 「品牌 + 品类」级需求没有型号，调用方传 latestEvidence = null，只在后两档判断。
     */
    public static int suggestCoverage(LocalDate latestEvidence, LocalDate today, int validDays, int leadSuppliers) {
        if (latestEvidence != null) {
            return evidenceValid(latestEvidence, today, validDays)
                    ? SupplyConstants.COVER_READY : SupplyConstants.COVER_CONFIRM;
        }
        return leadSuppliers > 0 ? SupplyConstants.COVER_LEAD : SupplyConstants.COVER_NO_CHANNEL;
    }

    /** 推荐下一步日期（记一次处理）：无渠道、有线索未验证 3 天后，需确认 2 天后，可直接报价按证据到期前 5 天 */
    public static LocalDate suggestNextDate(int coverage, LocalDate evidenceDate, LocalDate today, int validDays) {
        if (coverage == SupplyConstants.COVER_READY && evidenceDate != null) {
            LocalDate d = evidenceDate.plusDays(validDays - 5L);
            return d.isAfter(today) ? d : today.plusDays(1);
        }
        return today.plusDays(coverage == SupplyConstants.COVER_CONFIRM ? 2 : 3);
    }

    public static String coverageName(int c) {
        return switch (c) {
            case SupplyConstants.COVER_NO_CHANNEL -> "无渠道";
            case SupplyConstants.COVER_LEAD -> "有线索未验证";
            case SupplyConstants.COVER_CONFIRM -> "需确认";
            case SupplyConstants.COVER_READY -> "可直接报价";
            default -> "未进入矩阵";
        };
    }

    public static String priorityName(int p) {
        return switch (p) {
            case SupplyConstants.PRIORITY_HIGH -> "高";
            case SupplyConstants.PRIORITY_MID -> "中";
            default -> "低";
        };
    }

    /** 比率：分母为 0 返回 null（页面显示「—」），保留一位小数，四舍五入 */
    public static java.math.BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return java.math.BigDecimal.valueOf(numerator * 100L)
                .divide(java.math.BigDecimal.valueOf(denominator), 1, java.math.RoundingMode.HALF_UP);
    }
}

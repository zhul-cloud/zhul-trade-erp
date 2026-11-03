package com.zhul.erp.modules.masterdata.customerprofile.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 经营等级 RFM（spec master-data/customer-profile「客户经营等级」），沿用《成交客户表》的规则：
 * R 距最近订单天数、F 近 12 个月订单数、M 近 12 个月销售额（美元）各 1–5 分，
 * 综合分数 = R×权重 + F×权重 + M×权重，HALF_UP 保留一位小数，按分界定 S/A/B/C。纯计算，不查库。
 */
public final class RfmCalculator {

    public static final int UNDEALT = 0;
    public static final int S = 1;
    public static final int C = 4;
    public static final List<String> GRADE_NAMES = List.of("未成交", "S", "A", "B", "C");

    /**
     * @param recencyDays  R 依次为 5/4/3/2 分的天数上限（含），超过最后一个为 1 分，默认 30,90,180,365
     * @param frequency    F 依次为 5/4/3/2 分的订单数下限，默认 13,6,3,1
     * @param monetaryUsd  M 依次为 5/4/3/2 分的金额下限（美元），默认 9000,4700,4000,1800
     * @param weights      R、F、M 权重，默认 0.3,0.4,0.3
     * @param cutoffs      S、A、B 的综合分数下限，默认 4.5,3.5,2.5
     * @param followDays   S、A、B、C 的建议跟进间隔（天），默认 7,14,30,60
     */
    public record Params(int[] recencyDays, int[] frequency, BigDecimal[] monetaryUsd, BigDecimal[] weights,
                         BigDecimal[] cutoffs, int[] followDays) {
        public static Params defaults() {
            return new Params(new int[] {30, 90, 180, 365}, new int[] {13, 6, 3, 1},
                    new BigDecimal[] {new BigDecimal("9000"), new BigDecimal("4700"), new BigDecimal("4000"), new BigDecimal("1800")},
                    new BigDecimal[] {new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("0.3")},
                    new BigDecimal[] {new BigDecimal("4.5"), new BigDecimal("3.5"), new BigDecimal("2.5")},
                    new int[] {7, 14, 30, 60});
        }
    }

    /** 原始值：daysSinceLast 为距最近一次订单的天数；ordersInYear、usdInYear 为近 12 个月 */
    public record Input(long daysSinceLast, int ordersInYear, BigDecimal usdInYear) {
    }

    public record Score(int r, int f, int m, BigDecimal total, int grade) {
    }

    private RfmCalculator() {
    }

    public static Score score(Input in, Params p) {
        int r = recency(in.daysSinceLast(), p.recencyDays());
        int f = frequency(in.ordersInYear(), p.frequency());
        int m = monetary(in.usdInYear() == null ? BigDecimal.ZERO : in.usdInYear(), p.monetaryUsd());
        BigDecimal total = p.weights()[0].multiply(BigDecimal.valueOf(r))
                .add(p.weights()[1].multiply(BigDecimal.valueOf(f)))
                .add(p.weights()[2].multiply(BigDecimal.valueOf(m)))
                .setScale(1, RoundingMode.HALF_UP);
        return new Score(r, f, m, total, grade(total, p.cutoffs()));
    }

    static int recency(long days, int[] limits) {
        for (int i = 0; i < limits.length; i++) {
            if (days <= limits[i]) {
                return 5 - i;
            }
        }
        return 1;
    }

    static int frequency(int orders, int[] floors) {
        for (int i = 0; i < floors.length; i++) {
            if (orders >= floors[i]) {
                return 5 - i;
            }
        }
        return 1;
    }

    static int monetary(BigDecimal usd, BigDecimal[] floors) {
        for (int i = 0; i < floors.length; i++) {
            if (usd.compareTo(floors[i]) >= 0) {
                return 5 - i;
            }
        }
        return 1;
    }

    static int grade(BigDecimal total, BigDecimal[] cutoffs) {
        for (int i = 0; i < cutoffs.length; i++) {
            if (total.compareTo(cutoffs[i]) >= 0) {
                return S + i;
            }
        }
        return C;
    }

    /** 最终等级 = 计算等级上调或下调一级（数字越小等级越高），不超出 S–C；未成交不调整 */
    public static int finalGrade(int calc, int adjust) {
        if (calc == UNDEALT) {
            return UNDEALT;
        }
        return Math.max(S, Math.min(C, calc - adjust));
    }

    public static int followDays(int grade, Params p) {
        return grade >= S && grade <= C ? p.followDays()[grade - 1] : 0;
    }
}

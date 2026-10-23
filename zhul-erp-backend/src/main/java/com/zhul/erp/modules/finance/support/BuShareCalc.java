package com.zhul.erp.modules.finance.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 公共费用按销售额比例分摊（spec system/business-unit「公共费用按销售额比例分摊」）：
 * <ol>
 *   <li>比例 = 事业部当月销售收入 ÷ 参与事业部销售收入合计；</li>
 *   <li>每个事业部不低于最低比例：低于的固定为最低比例，其余比例在其他事业部间按销售收入重新分，直到没有低于的；
 *       最低比例 × 事业部数 ≥ 100% 时平分；</li>
 *   <li>都没有销售收入时平分；</li>
 *   <li>金额 HALF_UP 两位，尾差计入比例最大的事业部。</li>
 * </ol>
 * 比例保持完整精度参与计算，显示时四位小数。
 */
public final class BuShareCalc {

    private static final MathContext MC = MathContext.DECIMAL128;

    private BuShareCalc() {
    }

    /**
     * @param revenue  参与事业部（按显示顺序）→ 当月销售收入（负数按 0）
     * @param minShare 最低比例（0.2 表示 20%）
     * @return 事业部 → 比例，合计为 1
     */
    public static Map<Integer, BigDecimal> shares(Map<Integer, BigDecimal> revenue, BigDecimal minShare) {
        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        int n = revenue.size();
        if (n == 0) {
            return out;
        }
        BigDecimal equal = BigDecimal.ONE.divide(BigDecimal.valueOf(n), MC);
        BigDecimal min = minShare == null ? BigDecimal.ZERO : minShare.max(BigDecimal.ZERO);
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : revenue.values()) {
            total = total.add(pos(v));
        }
        if (n == 1 || total.signum() == 0 || min.multiply(BigDecimal.valueOf(n)).compareTo(BigDecimal.ONE) >= 0) {
            revenue.keySet().forEach(k -> out.put(k, n == 1 ? BigDecimal.ONE : equal));
            return out;
        }
        Set<Integer> fixed = new HashSet<>();
        while (true) {
            BigDecimal remaining = BigDecimal.ONE.subtract(min.multiply(BigDecimal.valueOf(fixed.size())));
            BigDecimal rest = BigDecimal.ZERO;
            int others = 0;
            for (Map.Entry<Integer, BigDecimal> e : revenue.entrySet()) {
                if (!fixed.contains(e.getKey())) {
                    rest = rest.add(pos(e.getValue()));
                    others++;
                }
            }
            boolean changed = false;
            for (Map.Entry<Integer, BigDecimal> e : revenue.entrySet()) {
                if (fixed.contains(e.getKey())) {
                    out.put(e.getKey(), min);
                    continue;
                }
                BigDecimal r = rest.signum() == 0
                        ? remaining.divide(BigDecimal.valueOf(others), MC)
                        : remaining.multiply(pos(e.getValue()), MC).divide(rest, MC);
                if (r.compareTo(min) < 0) {
                    fixed.add(e.getKey());
                    changed = true;
                }
                out.put(e.getKey(), r);
            }
            if (!changed) {
                return out;
            }
        }
    }

    /** 按比例分金额：HALF_UP 两位，尾差计入比例最大的事业部（并列取先出现的） */
    public static Map<Integer, BigDecimal> split(BigDecimal amount, Map<Integer, BigDecimal> shares) {
        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        if (shares.isEmpty() || amount == null) {
            return out;
        }
        Integer largest = null;
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<Integer, BigDecimal> e : shares.entrySet()) {
            BigDecimal v = amount.multiply(e.getValue(), MC).setScale(2, RoundingMode.HALF_UP);
            out.put(e.getKey(), v);
            sum = sum.add(v);
            if (largest == null || e.getValue().compareTo(shares.get(largest)) > 0) {
                largest = e.getKey();
            }
        }
        out.merge(largest, amount.setScale(2, RoundingMode.HALF_UP).subtract(sum), BigDecimal::add);
        return out;
    }

    /** 显示用：四位小数（0.1951 即 19.51%） */
    public static BigDecimal display(BigDecimal share) {
        return share == null ? null : share.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal pos(BigDecimal v) {
        return v == null || v.signum() < 0 ? BigDecimal.ZERO : v;
    }
}

package com.zhul.erp.modules.crm.followup.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采购规律（第三期 design D3）：纯函数，输入一个客户近 3 年的订单，输出采购周期、预计下次采购日和说明文字。
 * 调用方负责只传未取消、不含售后补发的订单（历史迁移订单计入）。只作参考，界面上注明「系统推测」。
 */
public final class PurchasePattern {

    /** 相隔这么多天以内的订单算同一次采购 */
    static final int MERGE_DAYS = 7;
    /** 看最近几年的订单 */
    static final int YEARS = 3;
    /** 品牌、品类各取前几个 */
    static final int TOP = 3;

    private PurchasePattern() {
    }

    /** 一张订单：下单日期，以及明细的品牌、品类和本位币金额 */
    public record Order(LocalDate date, List<Line> lines) {
    }

    public record Line(String brand, String category, BigDecimal amountCny) {
    }

    /**
     * @param purchases 采购次数（合并 7 天内的订单后）
     * @param cycleDays 采购周期，次数少于 3 时为 0
     * @param nextDate  预计下次采购日，次数少于 3 时为 null
     * @param text      说明文字
     * @param brands    近 12 个月按金额排前 3 的品牌和品类，如「Siemens、ABB · PLC、变频器」
     */
    public record Result(int purchases, int cycleDays, LocalDate nextDate, String text, String brands) {
    }

    public static Result of(List<Order> orders, LocalDate today) {
        LocalDate from = today.minusYears(YEARS);
        List<LocalDate> dates = orders.stream().map(Order::date)
                .filter(d -> d != null && !d.isBefore(from) && !d.isAfter(today))
                .sorted().toList();
        List<LocalDate> purchases = new ArrayList<>(dates.size());
        LocalDate prev = null;
        for (LocalDate d : dates) {
            // 与上一张订单相隔 7 天以内的并入同一次采购，采购日取这次采购的第一张订单
            if (prev == null || ChronoUnit.DAYS.between(prev, d) > MERGE_DAYS) {
                purchases.add(d);
            }
            prev = d;
        }
        String brands = brands(orders, today);
        int n = purchases.size();
        if (n == 0) {
            return new Result(0, 0, null, "近 3 年没有订单", brands);
        }
        if (n == 1) {
            return new Result(1, 0, null, "数据不足", brands);
        }
        if (n == 2) {
            long gap = ChronoUnit.DAYS.between(purchases.get(0), purchases.get(1));
            return new Result(2, 0, null, "样本少：两次相隔 " + gap + " 天，仅供参考", brands);
        }
        List<Long> gaps = new ArrayList<>(n - 1);
        for (int i = 1; i < n; i++) {
            gaps.add(ChronoUnit.DAYS.between(purchases.get(i - 1), purchases.get(i)));
        }
        gaps.sort(Comparator.naturalOrder());
        int m = gaps.size();
        // 中位数：偶数个间隔取中间两个的平均，向下取整到天
        int cycle = (int) (m % 2 == 1 ? gaps.get(m / 2) : (gaps.get(m / 2 - 1) + gaps.get(m / 2)) / 2);
        LocalDate next = purchases.get(n - 1).plusDays(cycle);
        return new Result(n, cycle, next, "约每 " + cycle + " 天一次，下次约 " + next.getMonthValue() + " 月", brands);
    }

    /** 近 12 个月按本位币金额合计排前 3 的品牌和品类，空值不计；都没有时为空 */
    static String brands(List<Order> orders, LocalDate today) {
        LocalDate from = today.minusMonths(12);
        Map<String, BigDecimal> byBrand = new HashMap<>(16);
        Map<String, BigDecimal> byCategory = new HashMap<>(16);
        for (Order o : orders) {
            if (o.date() == null || o.date().isBefore(from) || o.date().isAfter(today) || o.lines() == null) {
                continue;
            }
            for (Line l : o.lines()) {
                BigDecimal a = l.amountCny() == null ? BigDecimal.ZERO : l.amountCny();
                if (l.brand() != null && !l.brand().isBlank()) {
                    byBrand.merge(l.brand().trim(), a, BigDecimal::add);
                }
                if (l.category() != null && !l.category().isBlank()) {
                    byCategory.merge(l.category().trim(), a, BigDecimal::add);
                }
            }
        }
        String b = top(byBrand);
        String c = top(byCategory);
        if (b.isEmpty() || c.isEmpty()) {
            return b + c;
        }
        return b + " · " + c;
    }

    private static String top(Map<String, BigDecimal> m) {
        return m.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP).map(Map.Entry::getKey).collect(Collectors.joining("、"));
    }
}

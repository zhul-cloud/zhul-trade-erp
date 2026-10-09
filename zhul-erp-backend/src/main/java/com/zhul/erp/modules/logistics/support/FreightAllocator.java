package com.zhul.erp.modules.logistics.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运费分摊（纯计算）：按权重把金额分成若干份。
 * 精度：中间过程保持完整精度，每份按 HALF_UP 保留两位小数；尾差放到权重最大的那一份（同权重取第一个），保证合计等于总额。
 * 权重全为 0 时按份数平均分。
 */
public final class FreightAllocator {

    private static final MathContext MC = MathContext.DECIMAL128;

    private FreightAllocator() {
    }

    /** 箱子：计费重与箱内各行（订单型号行 → 货值，人民币） */
    public record Box(Long boxId, Long outboundId, BigDecimal chargeable, Map<Long, BigDecimal> values) {
    }

    /** 一行分摊结果 */
    public record Share(Long outboundId, Long boxId, Long soItemId, BigDecimal amount) {
    }

    /** 体积重 = 长 × 宽 × 高 ÷ 体积系数；计费重 = max(毛重, 体积重)，完整精度 */
    public static BigDecimal chargeable(int length, int width, int height, BigDecimal grossWeight, BigDecimal divisor) {
        BigDecimal volume = BigDecimal.valueOf((long) length * width * height).divide(divisor, MC);
        return grossWeight.max(volume);
    }

    /** 按权重分 total，返回与 weights 等长的金额 */
    public static List<BigDecimal> split(BigDecimal total, List<BigDecimal> weights) {
        int n = weights.size();
        List<BigDecimal> out = new ArrayList<>(n);
        if (n == 0) {
            return out;
        }
        BigDecimal sum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean even = sum.signum() == 0;
        BigDecimal allocated = BigDecimal.ZERO;
        for (BigDecimal w : weights) {
            BigDecimal share = even ? total.divide(BigDecimal.valueOf(n), MC) : total.multiply(w).divide(sum, MC);
            BigDecimal rounded = share.setScale(2, RoundingMode.HALF_UP);
            out.add(rounded);
            allocated = allocated.add(rounded);
        }
        BigDecimal diff = total.setScale(2, RoundingMode.HALF_UP).subtract(allocated);
        if (diff.signum() != 0) {
            int max = 0;
            for (int i = 1; i < n; i++) {
                if (weights.get(i).compareTo(weights.get(max)) > 0) {
                    max = i;
                }
            }
            out.set(max, out.get(max).add(diff));
        }
        return out;
    }

    /** 按箱计费重分到箱，再按箱内货值分到行 */
    public static List<Share> toLines(BigDecimal total, List<Box> boxes) {
        List<Share> out = new ArrayList<>();
        List<BigDecimal> perBox = split(total, boxes.stream().map(Box::chargeable).toList());
        for (int b = 0; b < boxes.size(); b++) {
            Box box = boxes.get(b);
            List<Map.Entry<Long, BigDecimal>> lines = new ArrayList<>(box.values().entrySet());
            List<BigDecimal> perLine = split(perBox.get(b), lines.stream().map(Map.Entry::getValue).toList());
            for (int i = 0; i < lines.size(); i++) {
                out.add(new Share(box.outboundId(), box.boxId(), lines.get(i).getKey(), perLine.get(i)));
            }
        }
        return out;
    }

    /** 国内快递：先按出库单的计费重之和分到出库单，再在出库单内按箱、按货值分 */
    public static List<Share> byOutbound(BigDecimal total, List<Box> boxes) {
        Map<Long, List<Box>> groups = new LinkedHashMap<>();
        boxes.stream().sorted(Comparator.comparing(Box::outboundId))
                .forEach(b -> groups.computeIfAbsent(b.outboundId(), k -> new ArrayList<>()).add(b));
        List<Long> ids = new ArrayList<>(groups.keySet());
        List<BigDecimal> perOutbound = split(total, ids.stream()
                .map(id -> groups.get(id).stream().map(Box::chargeable).reduce(BigDecimal.ZERO, BigDecimal::add)).toList());
        List<Share> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            out.addAll(toLines(perOutbound.get(i), groups.get(ids.get(i))));
        }
        return out;
    }
}

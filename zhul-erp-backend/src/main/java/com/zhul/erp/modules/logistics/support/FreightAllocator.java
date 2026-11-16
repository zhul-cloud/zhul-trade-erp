package com.zhul.erp.modules.logistics.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 运费分摊（纯计算）：按权重把金额分成若干份。
 * 精度：中间过程保持完整精度，每份按 HALF_UP 保留两位小数；尾差放到权重最大的那一份（同权重取第一个），保证合计等于总额。
 * 权重全为 0 时按份数平均分。
 */
public final class FreightAllocator {

    private static final MathContext MC = MathContext.DECIMAL128;

    private FreightAllocator() {
    }

    /** 箱子：计费重与箱内各行。合并打包的箱子装有不同销售出库单的货，每行带所属出库单（openspec outbound-merge-pack） */
    public record Box(Long boxId, BigDecimal chargeable, List<Line> lines) {
    }

    /** 箱内一行：所属销售出库单、订单型号行与货值（人民币） */
    public record Line(Long outboundId, Long soItemId, BigDecimal value) {
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

    /**
     * 按箱计费重分到箱，再按箱内货值分到行；国内快递与国际运费都用这一种。
     * 每张出库单分到的运费 = 它的行之和；不合箱时与先按出库单分的结果相同，只是尾差可能落在不同的行。
     */
    public static List<Share> toLines(BigDecimal total, List<Box> boxes) {
        List<Share> out = new ArrayList<>();
        List<BigDecimal> perBox = split(total, boxes.stream().map(Box::chargeable).toList());
        for (int b = 0; b < boxes.size(); b++) {
            Box box = boxes.get(b);
            List<BigDecimal> perLine = split(perBox.get(b), box.lines().stream().map(Line::value).toList());
            for (int i = 0; i < box.lines().size(); i++) {
                Line l = box.lines().get(i);
                out.add(new Share(l.outboundId(), box.boxId(), l.soItemId(), perLine.get(i)));
            }
        }
        return out;
    }
}

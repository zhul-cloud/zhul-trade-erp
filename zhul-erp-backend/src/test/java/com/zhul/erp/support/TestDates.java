package com.zhul.erp.support;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 测试里与「今天」有关的取值（audit DOC-007）：单号、存储路径带日期，后端生成时按当时的日期。
 * 断言时现取，不在类加载时固定；用例开始前离午夜太近就等过零点，保证一个用例不跨天。
 */
public final class TestDates {

    /** 一个用例（含造数据与断言）预计的最长耗时；离午夜不到这么久就先等过零点 */
    private static final Duration MARGIN = Duration.ofSeconds(30);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private TestDates() {
    }

    /** 今天，yyyyMMdd（单号里的日期） */
    public static String today() {
        return LocalDate.now().format(DAY);
    }

    /** 本月，yyyyMM（存储路径里的月份） */
    public static String thisMonth() {
        return LocalDate.now().format(MONTH);
    }

    /** 离午夜不到 MARGIN 时等到零点后再开始，否则用例中途跨天，单号日期与断言对不上 */
    public static void awayFromDayEnd() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        Duration left = Duration.between(now, midnight);
        if (left.compareTo(MARGIN) >= 0) {
            return;
        }
        try {
            Thread.sleep(left.toMillis() + 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待跨过零点时被中断", e);
        }
    }
}

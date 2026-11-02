package com.zhul.erp.modules.aftersales.support;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 工作时间计算（spec sales/after-sales「处理时限」）：周一至周五 9:00–18:00（北京时间），不考虑法定节假日与调休。
 * 起点不在工作时间内时从下一个工作时间开始；1 个工作日 = 9 个工作小时。按分钟计算，无舍入。
 */
public final class WorkHours {

    private static final LocalTime START = LocalTime.of(9, 0);
    private static final LocalTime END = LocalTime.of(18, 0);

    private WorkHours() {
    }

    /** 从 from 起加 minutes 个工作分钟 */
    public static LocalDateTime add(LocalDateTime from, long minutes) {
        LocalDateTime c = align(from);
        long left = Math.max(0, minutes);
        while (true) {
            long avail = Duration.between(c, c.toLocalDate().atTime(END)).toMinutes();
            if (left <= avail) {
                return c.plusMinutes(left);
            }
            left -= avail;
            c = nextWorkdayStart(c.toLocalDate());
        }
    }

    /** 对齐到最近的工作时间点（本身在工作时间内则不变） */
    static LocalDateTime align(LocalDateTime t) {
        LocalDate d = t.toLocalDate();
        if (!workday(d) || !t.toLocalTime().isBefore(END)) {
            return nextWorkdayStart(d);
        }
        return t.toLocalTime().isBefore(START) ? d.atTime(START) : t;
    }

    private static LocalDateTime nextWorkdayStart(LocalDate d) {
        LocalDate n = d.plusDays(1);
        while (!workday(n)) {
            n = n.plusDays(1);
        }
        return n.atTime(START);
    }

    private static boolean workday(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY;
    }
}

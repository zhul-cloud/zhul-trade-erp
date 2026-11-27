package com.zhul.erp.modules.video.support;

import com.zhul.erp.common.exception.BizException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 夜间生成时段（北京时间），格式「HH:mm-HH:mm」，可以跨零点（如 20:00-07:00）。
 * 开始时刻算在时段内，结束时刻不算；开始与结束相同表示全天。
 */
public record VideoWindow(LocalTime start, LocalTime end) {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    public static VideoWindow parse(String text) {
        String[] parts = text == null ? new String[0] : text.trim().split("-");
        if (parts.length != 2) {
            throw new BizException("时段格式应为 20:00-07:00");
        }
        try {
            return new VideoWindow(LocalTime.parse(parts[0].trim(), HM), LocalTime.parse(parts[1].trim(), HM));
        } catch (DateTimeParseException e) {
            throw new BizException("时段格式应为 20:00-07:00");
        }
    }

    public boolean contains(LocalTime t) {
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !t.isBefore(start) && t.isBefore(end);
        }
        return !t.isBefore(start) || t.isBefore(end);
    }

    /** 下一个可以开始生成的时刻：现在在时段内就是现在，否则为下一次时段开始 */
    public LocalDateTime nextOpen(LocalDateTime now) {
        if (contains(now.toLocalTime())) {
            return now;
        }
        LocalDateTime today = now.toLocalDate().atTime(start);
        return today.isAfter(now) ? today : today.plusDays(1);
    }

    /** 这一轮时段的结束时刻（now 须在时段内），「当晚暂停」到这个时刻为止 */
    public LocalDateTime currentEnd(LocalDateTime now) {
        LocalDateTime today = now.toLocalDate().atTime(end);
        return today.isAfter(now) ? today : today.plusDays(1);
    }

    public String text() {
        return start.format(HM) + "-" + end.format(HM);
    }
}

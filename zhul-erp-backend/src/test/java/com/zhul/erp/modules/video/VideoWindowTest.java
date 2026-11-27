package com.zhul.erp.modules.video;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.video.support.VideoWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** spec「夜间批量生成」：时段边界与跨零点 */
class VideoWindowTest {

    private final VideoWindow night = VideoWindow.parse("20:00-07:00");

    @Test
    void boundaries_acrossMidnight() {
        assertFalse(night.contains(LocalTime.of(19, 59)));
        assertTrue(night.contains(LocalTime.of(20, 0)));
        assertTrue(night.contains(LocalTime.of(0, 0)));
        assertTrue(night.contains(LocalTime.of(6, 59)));
        assertFalse(night.contains(LocalTime.of(7, 0)));
        assertFalse(night.contains(LocalTime.of(12, 0)));
    }

    @Test
    void sameDayWindow_andAllDay() {
        VideoWindow day = VideoWindow.parse("09:00-18:00");
        assertFalse(day.contains(LocalTime.of(8, 59)));
        assertTrue(day.contains(LocalTime.of(9, 0)));
        assertFalse(day.contains(LocalTime.of(18, 0)));
        assertTrue(VideoWindow.parse("00:00-00:00").contains(LocalTime.of(13, 37)));
    }

    @Test
    void nextOpen_daytimeWaitsForEvening_nightIsNow() {
        LocalDateTime ten = LocalDateTime.of(2026, 11, 26, 10, 0);
        assertEquals(LocalDateTime.of(2026, 11, 26, 20, 0), night.nextOpen(ten));
        LocalDateTime late = LocalDateTime.of(2026, 11, 26, 23, 30);
        assertEquals(late, night.nextOpen(late));
        assertEquals(LocalDateTime.of(2026, 11, 27, 7, 0), night.currentEnd(late));
        assertEquals(LocalDateTime.of(2026, 11, 27, 7, 0), night.currentEnd(LocalDateTime.of(2026, 11, 27, 3, 0)));
    }

    @Test
    void invalidFormat_rejected() {
        assertThrows(BizException.class, () -> VideoWindow.parse("8点-7点"));
        assertThrows(BizException.class, () -> VideoWindow.parse("25:00-07:00"));
        assertThrows(BizException.class, () -> VideoWindow.parse(null));
        assertEquals("20:00-07:00", VideoWindow.parse(" 20:00 - 07:00 ").text());
    }
}

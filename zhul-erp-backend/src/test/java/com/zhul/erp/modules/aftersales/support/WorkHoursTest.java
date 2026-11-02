package com.zhul.erp.modules.aftersales.support;

import com.zhul.erp.modules.aftersales.constants.AfterSalesConstants;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** spec sales/after-sales「处理时限」：工作时间计算与截止时间选择（2026-11-06 为周五） */
class WorkHoursTest {

    private static LocalDateTime t(int day, int h, int m) {
        return LocalDateTime.of(2026, 11, day, h, m);
    }

    @Test
    void add_withinDay_acrossEvening_weekend_andNonWorkStart() {
        assertEquals(t(2, 11, 0), WorkHours.add(t(2, 9, 0), 120), "周一 9:00 + 2 小时");
        assertEquals(t(9, 10, 30), WorkHours.add(t(6, 17, 30), 120), "周五 17:30 + 2 个工作小时 → 下周一 10:30");
        assertEquals(t(9, 15, 0), WorkHours.add(t(6, 15, 0), 9 * 60), "1 个工作日跨周末");
        assertEquals(t(5, 14, 0), WorkHours.add(t(3, 14, 0), 18 * 60), "周二 14:00 + 2 个工作日 → 周四 14:00");
        assertEquals(t(9, 11, 0), WorkHours.add(t(7, 10, 0), 120), "周六起算从周一 9:00 开始");
        assertEquals(t(3, 11, 0), WorkHours.add(t(2, 20, 0), 120), "下班后起算从次日 9:00 开始");
        assertEquals(t(2, 11, 0), WorkHours.add(t(2, 7, 0), 120), "上班前起算从当天 9:00 开始");
        assertEquals(t(6, 18, 0), WorkHours.add(t(6, 9, 0), 9 * 60), "正好用完一天停在 18:00");
        assertEquals(t(2, 9, 0), WorkHours.add(t(1, 12, 0), 0), "零时长对齐到下一个工作时间");
    }

    private static AfterSalesDO open(String level, LocalDateTime received) {
        AfterSalesDO a = new AfterSalesDO();
        a.setStatus(AfterSalesConstants.AS_OPEN);
        a.setLevelCode(level);
        a.setReceivedAt(received);
        return a;
    }

    @Test
    void refresh_firstResponse_update_manual_waiting_closed() {
        AfterSalesDO s1 = open("S1", t(7, 10, 0));
        AfterSalesSla.refresh(s1);
        assertEquals(t(7, 11, 0), s1.getDueAt(), "S1 自然时间，周六也算");
        assertEquals(AfterSalesConstants.DUE_FIRST, s1.getDueType());

        AfterSalesDO pending = open(AfterSalesConstants.LEVEL_PENDING, t(6, 17, 30));
        AfterSalesSla.refresh(pending);
        assertEquals(t(9, 10, 30), pending.getDueAt(), "待确认按 S2");

        AfterSalesDO s3 = open("S3", t(2, 9, 0));
        s3.setRespondedAt(t(3, 14, 0));
        s3.setSlaStartAt(t(3, 14, 0));
        AfterSalesSla.refresh(s3);
        assertEquals(t(5, 14, 0), s3.getDueAt());
        assertEquals(AfterSalesConstants.DUE_UPDATE, s3.getDueType());

        s3.setNextUpdateAt(t(4, 15, 0));
        AfterSalesSla.refresh(s3);
        assertEquals(t(4, 15, 0), s3.getDueAt(), "手填更早以手填为准");
        assertEquals(AfterSalesConstants.DUE_MANUAL, s3.getDueType());
        s3.setNextUpdateAt(t(5, 14, 0));
        AfterSalesSla.refresh(s3);
        assertEquals(AfterSalesConstants.DUE_UPDATE, s3.getDueType(), "相等取等级截止");

        AfterSalesDO legacy = open("S2", t(2, 9, 0));
        legacy.setRespondedAt(t(2, 9, 0));
        AfterSalesSla.refresh(legacy);
        assertNull(legacy.getDueAt(), "升级前的存量单没有起算时间：只看手填");

        AfterSalesDO s5 = open("S5", t(2, 9, 0));
        s5.setRespondedAt(t(2, 10, 0));
        AfterSalesSla.refresh(s5);
        assertNull(s5.getDueAt(), "S5 响应后只看手填");
        assertEquals(AfterSalesConstants.DUE_NONE, s5.getDueType());

        AfterSalesDO waiting = open("S2", t(2, 9, 0));
        waiting.setStatus(AfterSalesConstants.AS_WAITING);
        AfterSalesSla.refresh(waiting);
        assertNull(waiting.getDueAt(), "待客户确认暂停");
        waiting.setNextUpdateAt(t(4, 10, 0));
        AfterSalesSla.refresh(waiting);
        assertEquals(t(4, 10, 0), waiting.getDueAt());

        waiting.setStatus(AfterSalesConstants.AS_CLOSED);
        AfterSalesSla.refresh(waiting);
        assertNull(waiting.getDueAt(), "已结案不提醒");
    }

    @Test
    void state_overdue_soon_normal() {
        LocalDateTime now = t(2, 14, 0);
        assertEquals("OVERDUE", AfterSalesSla.state(t(2, 13, 59), now));
        assertEquals("SOON", AfterSalesSla.state(t(2, 14, 40), now));
        assertEquals("NORMAL", AfterSalesSla.state(t(2, 15, 0), now));
        assertNull(AfterSalesSla.state(null, now));
    }
}

package com.zhul.erp.modules.crm.followup;

import com.zhul.erp.modules.crm.followup.support.AccountCadence;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 客户经营的系统提醒（第三期 design D4、D5）：等级节奏、预计采购、沉睡激活、季度回顾取最早的一个。
 */
class AccountCadenceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final int[] FOLLOW_DAYS = {30, 60, 90, 180};

    private static AccountCadence.Reminder of(int grade, LocalDate lastContact, LocalDate patternNext, LocalDate lastOrder,
                                              LocalDate dormantSince, boolean strategic, LocalDate reviewFrom) {
        return AccountCadence.of(new AccountCadence.Input(grade, FOLLOW_DAYS, lastContact, patternNext, 30, lastOrder, dormantSince,
                60, strategic, reviewFrom, 90), TODAY);
    }

    @Test
    void gradeCadenceFromLastContact() {
        AccountCadence.Reminder r = of(2, LocalDate.of(2026, 10, 1), null, null, null, false, null);
        assertEquals(LocalDate.of(2026, 11, 30), r.date());
        assertEquals("A 级每 60 天联系", r.reason());
        assertEquals("按 A 级节奏联系，问近期项目和备件计划", r.action());
    }

    @Test
    void gradeCadenceFromTodayWithoutContact() {
        assertEquals(TODAY.plusDays(180), of(4, null, null, null, null, false, null).date());
    }

    @Test
    void predictedPurchaseRemindsAheadWhenEarlier() {
        AccountCadence.Reminder r = of(2, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 10), null, null, false, null);
        assertEquals(LocalDate.of(2026, 11, 10), r.date(), "提前 30 天");
        assertEquals("预计 12 月采购，提前问计划", r.reason());
    }

    @Test
    void predictedPurchaseBeforeLastContactIsIgnored() {
        AccountCadence.Reminder r = of(2, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 20), null, null, false, null);
        assertEquals("A 级每 60 天联系", r.reason(), "这一轮已经联系过");
    }

    @Test
    void dormantRemindsAtLastReachedNode() {
        LocalDate lastOrder = LocalDate.of(2026, 1, 20);
        // 8/1 开始沉睡，每 60 天一个节点：9/30 已到达
        AccountCadence.Reminder r = of(4, null, null, lastOrder, LocalDate.of(2026, 8, 1), false, null);
        assertEquals(LocalDate.of(2026, 9, 30), r.date());
        assertEquals("沉睡 " + ChronoUnit.DAYS.between(lastOrder, TODAY) + " 天，发同品类现货清单激活", r.reason());
        assertEquals("发同品类现货清单或新到货，问近期计划", r.action());
    }

    @Test
    void dormantNodeMovesOnAfterContact() {
        AccountCadence.Reminder r = of(4, LocalDate.of(2026, 10, 10), null, LocalDate.of(2026, 1, 20), LocalDate.of(2026, 8, 1), false, null);
        assertEquals(LocalDate.of(2026, 11, 29), r.date(), "9/30 之后联系过，顺延到下一个节点");
    }

    @Test
    void strategicQuarterReviewWins() {
        LocalDate lastContact = LocalDate.of(2026, 10, 20);
        LocalDate reviewFrom = LocalDate.of(2026, 8, 10);
        AccountCadence.Reminder r = of(1, lastContact, null, null, null, true, reviewFrom);
        assertEquals(reviewFrom.plusDays(90), r.date());
        assertEquals("战略客户季度回顾", r.reason());
        assertEquals("S 级每 30 天联系", of(1, lastContact, null, null, null, false, reviewFrom).reason(), "不是战略客户不提醒回顾");
    }

    @Test
    void nothingToRemind() {
        assertNull(of(0, null, null, null, null, false, null));
    }
}

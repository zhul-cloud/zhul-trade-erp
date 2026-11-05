package com.zhul.erp.modules.crm.followup;

import com.zhul.erp.modules.crm.followup.support.DevelopCadence;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主动开发的系统提醒（第四期 design D5）：每个阶段的提醒、序列第 4 / 9 / 16 天、序列发完建议转培育。
 */
class DevelopCadenceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final List<Integer> SEQ = List.of(1, 4, 9, 16);
    private static final Map<String, String> D5 = Map.of("default", "换一个新价值点", "d4", "第 4 天：补充一个案例",
            "d9", "第 9 天：发现货清单", "d16", "第 16 天：收尾邮件");

    private static DevelopCadence.Reminder d5(LocalDate firstTouch) {
        return DevelopCadence.of(new DevelopCadence.Input("D5", false, null, firstTouch, SEQ, D5), TODAY);
    }

    @Test
    void stageRemindersUseStageSince() {
        LocalDate since = LocalDate.of(2026, 11, 1);
        for (String stage : List.of("D1", "D3", "D4", "D7")) {
            DevelopCadence.Reminder r = DevelopCadence.of(new DevelopCadence.Input(stage, false, since, null, SEQ, Map.of()), TODAY);
            assertEquals(since, r.date(), stage);
            assertFalse(r.action().isEmpty(), stage);
        }
    }

    @Test
    void d2RunningHasNoDateAndUnqualifiedSuggestsStop() {
        LocalDate since = LocalDate.of(2026, 11, 1);
        assertNull(DevelopCadence.of(new DevelopCadence.Input("D2", false, since, null, SEQ, Map.of()), TODAY).date());
        DevelopCadence.Reminder r = DevelopCadence.of(new DevelopCadence.Input("D2", true, since, null, SEQ, Map.of()), TODAY);
        assertEquals(since, r.date());
        assertEquals("资格不足，建议停止或培育", r.action());
    }

    @Test
    void d6WaitsForUser() {
        assertNull(DevelopCadence.of(new DevelopCadence.Input("D6", false, TODAY, TODAY, SEQ, Map.of()), TODAY).date());
    }

    @Test
    void justAfterFirstTouchRemindsDay4() {
        DevelopCadence.Reminder r = d5(TODAY);
        assertEquals(TODAY.plusDays(3), r.date());
        assertEquals(4, r.day());
        assertEquals("第 4 天：补充一个案例", r.action());
        assertFalse(r.suggestNurture());
    }

    @Test
    void day9NodeReached() {
        // 首次触达在 8 天前：今天是第 9 天
        DevelopCadence.Reminder r = d5(TODAY.minusDays(8));
        assertEquals(TODAY, r.date());
        assertEquals(9, r.day());
        assertEquals("第 9 天：发现货清单", r.action());
    }

    @Test
    void betweenNodesKeepsLastReachedNode() {
        // 第 12 天：第 9 天的节点已到（逾期），第 16 天还没到
        DevelopCadence.Reminder r = d5(TODAY.minusDays(11));
        assertEquals(9, r.day());
        assertEquals(TODAY.minusDays(3), r.date());
    }

    @Test
    void day16IsClosingEmail() {
        DevelopCadence.Reminder r = d5(TODAY.minusDays(15));
        assertEquals(16, r.day());
        assertEquals("第 16 天：收尾邮件", r.action());
        assertFalse(r.suggestNurture());
    }

    @Test
    void afterLastNodeSuggestsNurture() {
        DevelopCadence.Reminder r = d5(TODAY.minusDays(16));
        assertTrue(r.suggestNurture());
        assertEquals(DevelopCadence.SUGGEST_NURTURE, r.action());
        assertEquals(TODAY.minusDays(1), r.date());
    }

    @Test
    void nodeWithoutHintFallsBackToDefault() {
        DevelopCadence.Reminder r = DevelopCadence.of(new DevelopCadence.Input("D5", false, null, TODAY, List.of(1, 3, 7),
                Map.of("default", "换一个新价值点")), TODAY);
        assertEquals("第 3 天：换一个新价值点", r.action());
    }
}

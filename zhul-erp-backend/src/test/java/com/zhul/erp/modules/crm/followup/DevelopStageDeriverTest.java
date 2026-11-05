package com.zhul.erp.modules.crm.followup;

import com.zhul.erp.modules.crm.followup.support.DevelopStageDeriver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主动开发阶段推导（第四期 design D4）：D1–D7 每个阶段、背调中、资格不足、只进不退。
 */
class DevelopStageDeriverTest {

    private static DevelopStageDeriver.Result of(int potential, boolean running, Integer priority, boolean contactable,
                                                 int touches, boolean replied, boolean signal, String current) {
        return DevelopStageDeriver.derive(new DevelopStageDeriver.Input(potential, running, priority, contactable, touches,
                replied, signal, current));
    }

    @Test
    void newProspectWithoutResearchIsD1() {
        DevelopStageDeriver.Result r = of(0, false, null, false, 0, false, false, null);
        assertEquals("D1", r.stage());
        assertFalse(r.unqualified());
    }

    @Test
    void runningResearchIsD2NotUnqualified() {
        DevelopStageDeriver.Result r = of(0, true, null, true, 0, false, false, "D1");
        assertEquals("D2", r.stage());
        assertFalse(r.unqualified());
    }

    @Test
    void lowPotentialStaysInD2AsUnqualified() {
        DevelopStageDeriver.Result r = of(2, false, null, true, 0, false, false, "D1");
        assertEquals("D2", r.stage());
        assertTrue(r.unqualified());
    }

    @Test
    void lowPriorityReportIsUnqualified() {
        assertTrue(of(0, false, 3, true, 0, false, false, null).unqualified());
        assertTrue(of(0, false, 4, true, 0, false, false, null).unqualified());
    }

    @Test
    void qualifiedWithoutContactIsD3() {
        assertEquals("D3", of(3, false, null, false, 0, false, false, null).stage());
        assertEquals("D3", of(0, false, 2, false, 0, false, false, null).stage(), "背调优先级 1–2 也算合格");
    }

    @Test
    void qualifiedWithContactIsD4() {
        assertEquals("D4", of(4, false, null, true, 0, false, false, "D3").stage());
        assertEquals("D4", of(0, false, 1, true, 0, false, false, null).stage());
    }

    @Test
    void goodPriorityOutweighsLowStars() {
        // 星级 2 但背调建议重点跟进：按合格算
        DevelopStageDeriver.Result r = of(2, false, 1, true, 0, false, false, null);
        assertEquals("D4", r.stage());
        assertFalse(r.unqualified());
    }

    @Test
    void firstTouchIsD5EvenWhenUnqualified() {
        assertEquals("D5", of(0, false, null, false, 1, false, false, "D1").stage());
    }

    @Test
    void replyIsD6AndSignalIsD7() {
        assertEquals("D6", of(3, false, null, true, 2, true, false, "D5").stage());
        assertEquals("D7", of(3, false, null, true, 2, true, true, "D6").stage());
        assertEquals("D7", of(0, false, null, false, 0, false, true, null).stage(), "采购信号直接到 D7");
    }

    @Test
    void neverGoesBack() {
        // 联系人被删、背调重跑都不退回
        assertEquals("D5", of(0, true, null, false, 0, false, false, "D5").stage());
        assertEquals("D4", of(1, false, 4, false, 0, false, false, "D4").stage());
        assertFalse(of(1, false, 4, false, 0, false, false, "D4").unqualified(), "不在 D2 不算资格不足");
    }
}

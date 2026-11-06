package com.zhul.erp.modules.workbench.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 工作台卡片副标题里的金额：两位小数 HALF_UP，带千分位（audit UI-041） */
class BossWidgetsMoneyTest {

    @Test
    void zero() {
        assertEquals("0.00", BossWidgets.money(BigDecimal.ZERO));
    }

    @Test
    void groupsThousands() {
        assertEquals("4,564,683.02", BossWidgets.money(new BigDecimal("4564683.02")));
    }

    @Test
    void roundsHalfUp() {
        assertEquals("1,000.01", BossWidgets.money(new BigDecimal("1000.005")));
        assertEquals("1,000.00", BossWidgets.money(new BigDecimal("1000.004")));
    }

    @Test
    void negative() {
        assertEquals("-1,234.50", BossWidgets.money(new BigDecimal("-1234.5")));
    }

    @Test
    void veryLarge() {
        assertEquals("9,999,999,999,999,999.99", BossWidgets.money(new BigDecimal("9999999999999999.99")));
    }
}

package com.zhul.erp.modules.masterdata.customerprofile;

import com.zhul.erp.modules.masterdata.customerprofile.support.RfmCalculator;
import com.zhul.erp.modules.masterdata.customerprofile.support.RfmCalculator.Input;
import com.zhul.erp.modules.masterdata.customerprofile.support.RfmCalculator.Score;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 经营等级 RFM：期望值取自《成交客户表》的真实行，加边界值 */
class RfmCalculatorTest {

    private static final RfmCalculator.Params P = RfmCalculator.Params.defaults();

    private static Score score(long days, int orders, String usd) {
        return RfmCalculator.score(new Input(days, orders, new BigDecimal(usd)), P);
    }

    private static void check(Score s, int r, int f, int m, String total, String grade) {
        assertEquals(r, s.r(), "R");
        assertEquals(f, s.f(), "F");
        assertEquals(m, s.m(), "M");
        assertEquals(new BigDecimal(total), s.total(), "综合分数");
        assertEquals(grade, RfmCalculator.GRADE_NAMES.get(s.grade()), "等级");
    }

    @Test
    void sheetRows() {
        check(score(2, 20, "44764.44"), 5, 5, 5, "5.0", "S");
        check(score(7, 1, "320.00"), 5, 2, 1, "2.6", "B");
        check(score(10, 4, "4045.00"), 5, 3, 3, "3.6", "A");
        check(score(10, 1, "6178.00"), 5, 2, 4, "3.5", "A");
        check(score(56, 13, "9644.60"), 4, 5, 5, "4.7", "S");
        check(score(32, 1, "0"), 4, 2, 1, "2.3", "C");
        check(score(45, 2, "31242.50"), 4, 2, 5, "3.5", "A");
        check(score(400, 0, "0"), 1, 1, 1, "1.0", "C");
    }

    @Test
    void boundaries() {
        assertEquals(5, score(30, 1, "0").r());
        assertEquals(4, score(31, 1, "0").r());
        assertEquals(2, score(365, 1, "0").r());
        assertEquals(1, score(366, 1, "0").r());
        assertEquals(4, score(1, 12, "0").f());
        assertEquals(5, score(1, 13, "0").f());
        assertEquals(1, score(1, 1, "1799.99").m());
        assertEquals(2, score(1, 1, "1800.00").m());
        assertEquals(4, score(1, 1, "4700.00").m());
        assertEquals(5, score(1, 1, "9000.00").m());
    }

    @Test
    void adjustOneLevelWithinSToC() {
        assertEquals(2, RfmCalculator.finalGrade(3, 1), "B 上调为 A");
        assertEquals(4, RfmCalculator.finalGrade(3, -1), "B 下调为 C");
        assertEquals(1, RfmCalculator.finalGrade(1, 1), "S 不能再上调");
        assertEquals(4, RfmCalculator.finalGrade(4, -1), "C 不能再下调");
        assertEquals(0, RfmCalculator.finalGrade(0, 1), "未成交不调整");
        assertEquals(14, RfmCalculator.followDays(2, P));
        assertEquals(0, RfmCalculator.followDays(0, P));
    }
}

package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.zhul.erp.modules.inquiry.sourcing.service.impl.QuoteTextParser.Line;
import com.zhul.erp.modules.inquiry.sourcing.service.impl.QuoteTextParser.Model;
import com.zhul.erp.modules.inquiry.sourcing.service.impl.QuoteTextParser.Result;
import com.zhul.erp.modules.inquiry.sourcing.service.impl.QuoteTextParser.Status;
import com.zhul.erp.modules.inquiry.support.QuoteDictSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** spec inquiry/sourcing-quote「粘贴报价快速回填」的识别规则 */
class QuoteTextParserTest {

    private static final int NEW = 1;
    private static final int USED = 4;
    private static final int DISMANTLED = 5;
    private static final int IN_STOCK = 1;
    private static final int D3_5 = 4;
    private static final int W4_8 = 8;
    private static final int W8_PLUS = 9;

    private static final QuoteDictSnapshot DICTS = new QuoteDictSnapshot(
            dict(1, "全新原装", 2, "99新", 3, "翻新", 4, "二手", 5, "拆机件", 6, "国产替代", 7, "待确认"),
            dict(1, "现货", 2, "1-2天", 3, "2-3天", 4, "3-5天", 5, "5-7天", 6, "1-2周", 7, "2-4周", 8, "4-8周", 9, "8周以上"),
            dict(), dict());

    private static QuoteDictSnapshot.Dict dict(Object... kv) {
        Map<Integer, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            labels.put((Integer) kv[i], (String) kv[i + 1]);
        }
        return new QuoteDictSnapshot.Dict(labels, Set.copyOf(labels.keySet()));
    }

    private static List<Model> models(String... names) {
        java.util.ArrayList<Model> out = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            out.add(new Model((long) i + 1, names[i]));
        }
        return out;
    }

    private static void price(String expected, Line l) {
        assertEquals(0, new BigDecimal(expected).compareTo(l.unitPrice()), l.raw() + " → " + l.unitPrice());
    }

    @Test
    void sample1_quantitiesStockWeeksAndCommonNote() {
        Result r = QuoteTextParser.parse("""
                LXM32AD30N4，要 1 台    3140  现货
                LXM32AD18N4，要 2 台    2237 现货
                BMH1003P16A2A，要 1 台   2562  5周
                BMH0702P12A2A，要 2 台   3263  5周
                未税包邮  全新原装正品 假一罚万
                """, models("LXM32AD30N4", "LXM32AD18N4", "BMH1003P16A2A", "BMH0702P12A2A"), DICTS);
        assertEquals(4, r.lines().size());
        String[] prices = {"3140", "2237", "2562", "3263"};
        int[] leads = {IN_STOCK, IN_STOCK, W4_8, W4_8};
        for (int i = 0; i < 4; i++) {
            Line l = r.lines().get(i);
            assertEquals(Status.MATCHED, l.status());
            assertEquals((long) i + 1, l.itemId());
            price(prices[i], l);
            assertEquals(leads[i], l.leadTime(), l.raw());
            assertFalse(l.taxIncluded());
            assertEquals(NEW, l.condition());
            assertEquals("未税包邮  全新原装正品 假一罚万", l.note());
        }
    }

    @Test
    void sample2_priceAfterModel_digitsInModelIgnored() {
        Result r = QuoteTextParser.parse("""
                AX32-30-10-80单价120
                AX25-30-10-80单价89
                TA25DU-19M单价72
                TA25DU-14M单价65
                TA25DU-8.5M单价65
                """, models("AX32-30-10-80", "AX25-30-10-80", "TA25DU-19M", "TA25DU-14M", "TA25DU-8.5M"), DICTS);
        assertEquals(5, r.lines().size());
        String[] prices = {"120", "89", "72", "65", "65"};
        for (int i = 0; i < 5; i++) {
            assertEquals((long) i + 1, r.lines().get(i).itemId(), r.lines().get(i).raw());
            price(prices[i], r.lines().get(i));
        }
        assertEquals("", r.lines().get(0).note());
    }

    @Test
    void modelWrittenDifferently_taxIncludedDefaultRate() {
        Line l = QuoteTextParser.parse("6es72141ag400xb0 980含税", models("6ES7 214-1AG40-0XB0"), DICTS).lines().get(0);
        assertEquals(1L, l.itemId());
        price("980", l);
        assertTrue(l.taxIncluded());
        assertEquals(13, l.taxRate());
    }

    @Test
    void longestModelWins_andExplicitRate() {
        List<Line> lines = QuoteTextParser.parse("TA25DU-14M 含税3% ¥1,280.5 3-5天", models("TA25DU-1", "TA25DU-14M"), DICTS).lines();
        assertEquals(2L, lines.get(0).itemId());
        price("1280.50", lines.get(0));
        assertEquals(3, lines.get(0).taxRate());
        assertEquals(D3_5, lines.get(0).leadTime());
    }

    @Test
    void unmatchedAndNoPrice() {
        List<Line> lines = QuoteTextParser.parse("""
                BMH0701 拆机 1800
                LXM32AD18N4 另有一台二手
                """, models("LXM32AD18N4"), DICTS).lines();
        assertEquals(Status.UNMATCHED, lines.get(0).status());
        assertNull(lines.get(0).itemId());
        assertEquals(DISMANTLED, lines.get(0).condition());
        assertEquals(Status.NO_PRICE, lines.get(1).status());
        assertEquals(USED, lines.get(1).condition());
    }

    @Test
    void lineOverridesCommon_andLabeledPriceBeatsOriginal() {
        List<Line> lines = QuoteTextParser.parse("""
                A-1 原价 3500 现价 3140 含税
                B-2 x3 2000元 10周
                全部未税 现货
                """, models("A-1", "B-2"), DICTS).lines();
        price("3140", lines.get(0));
        assertTrue(lines.get(0).taxIncluded(), "行里写了含税，不被整段的未税覆盖");
        assertEquals(IN_STOCK, lines.get(0).leadTime(), "行里没写货期，套用整段的现货");
        price("2000", lines.get(1));
        assertFalse(lines.get(1).taxIncluded());
        assertEquals(W8_PLUS, lines.get(1).leadTime());
    }

    @Test
    void emptyText() {
        assertTrue(QuoteTextParser.parse("  \n ", models("A-1"), DICTS).lines().isEmpty());
        assertTrue(QuoteTextParser.parse(null, models("A-1"), DICTS).lines().isEmpty());
    }
}

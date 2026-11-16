package com.zhul.erp.modules.logistics;

import com.zhul.erp.modules.logistics.support.FreightAllocator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** spec logistics/freight-allocation：计费重、尾差、按货值、零值 */
class FreightAllocatorTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void chargeable_takesMaxOfGrossAndVolume() {
        assertEquals(0, d("30.00").compareTo(FreightAllocator.chargeable(55, 40, 30, d("30.00"), d("5000"))));
        assertEquals(0, d("13.2").compareTo(FreightAllocator.chargeable(55, 40, 30, d("10.00"), d("5000"))));
        assertEquals(0, d("11").compareTo(FreightAllocator.chargeable(55, 40, 30, d("10.00"), d("6000"))));
    }

    @Test
    void split_remainderGoesToLargest_andSumsExactly() {
        assertEquals(List.of(d("33.34"), d("33.33"), d("33.33")), FreightAllocator.split(d("100.00"), List.of(d("1"), d("1"), d("1"))));
        assertEquals(List.of(d("33.33"), d("33.34"), d("33.33")), FreightAllocator.split(d("100.00"), List.of(d("1"), d("1.0001"), d("1"))),
                "尾差放到权重最大的那一份");
        assertEquals(List.of(d("60.00"), d("20.00")), FreightAllocator.split(d("80.00"), List.of(d("30"), d("10"))));
        assertEquals(List.of(d("0.00"), d("0.00")), FreightAllocator.split(d("0"), List.of(d("3"), d("1"))), "运费为 0");
        assertEquals(List.of(d("5.00"), d("5.00")), FreightAllocator.split(d("10.00"), List.of(d("0"), d("0"))), "权重都为 0 时平均分");
    }

    private static FreightAllocator.Line l(long outboundId, long soItemId, String value) {
        return new FreightAllocator.Line(outboundId, soItemId, d(value));
    }

    @Test
    void lines_byBoxThenByValue() {
        List<FreightAllocator.Share> s = FreightAllocator.toLines(d("60.00"), List.of(
                new FreightAllocator.Box(1L, d("30"), List.of(l(7L, 11L, "400"), l(7L, 12L, "200")))));
        assertEquals(d("40.00"), s.get(0).amount());
        assertEquals(d("20.00"), s.get(1).amount());
    }

    /** 不合箱时按箱分摊，每张出库单分到的与先按出库单分相同（OB1 计费重 30、OB2 计费重 10，运费 80） */
    @Test
    void domestic_byBox_sameAsByOutboundWhenNotMixed() {
        List<FreightAllocator.Share> s = FreightAllocator.toLines(d("80.00"), List.of(
                new FreightAllocator.Box(1L, d("20"), List.of(l(1L, 11L, "100"))),
                new FreightAllocator.Box(2L, d("10"), List.of(l(1L, 12L, "100"))),
                new FreightAllocator.Box(3L, d("10"), List.of(l(2L, 21L, "100")))));
        assertEquals(d("60.00"), sum(s, 1L));
        assertEquals(d("20.00"), sum(s, 2L));
    }

    /** openspec outbound-merge-pack：合并打包的箱子装两张订单的货，箱的运费按货值分到两张出库单 */
    @Test
    void mergedBox_splitsToBothOutboundsByValue() {
        List<FreightAllocator.Share> s = FreightAllocator.toLines(d("40.00"), List.of(
                new FreightAllocator.Box(1L, d("30"), List.of(l(1L, 11L, "300"), l(2L, 21L, "100")))));
        assertEquals(d("30.00"), sum(s, 1L));
        assertEquals(d("10.00"), sum(s, 2L));
        assertEquals(List.of(1L, 1L), s.stream().map(FreightAllocator.Share::boxId).toList(), "两行都记在同一箱");
    }

    private static BigDecimal sum(List<FreightAllocator.Share> s, long outboundId) {
        return s.stream().filter(x -> x.outboundId() == outboundId).map(FreightAllocator.Share::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

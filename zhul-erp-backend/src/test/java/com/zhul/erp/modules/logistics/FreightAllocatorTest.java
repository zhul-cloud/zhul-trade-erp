package com.zhul.erp.modules.logistics;

import com.zhul.erp.modules.logistics.support.FreightAllocator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    void lines_byBoxThenByValue() {
        Map<Long, BigDecimal> v1 = new LinkedHashMap<>();
        v1.put(11L, d("400"));
        v1.put(12L, d("200"));
        List<FreightAllocator.Share> s = FreightAllocator.toLines(d("60.00"), List.of(new FreightAllocator.Box(1L, 7L, d("30"), v1)));
        assertEquals(d("40.00"), s.get(0).amount());
        assertEquals(d("20.00"), s.get(1).amount());
    }

    @Test
    void domestic_byOutboundFirst() {
        List<FreightAllocator.Share> s = FreightAllocator.byOutbound(d("80.00"), List.of(
                new FreightAllocator.Box(1L, 1L, d("20"), Map.of(11L, d("100"))),
                new FreightAllocator.Box(2L, 1L, d("10"), Map.of(12L, d("100"))),
                new FreightAllocator.Box(3L, 2L, d("10"), Map.of(21L, d("100")))));
        BigDecimal ob1 = s.stream().filter(x -> x.outboundId() == 1L).map(FreightAllocator.Share::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ob2 = s.stream().filter(x -> x.outboundId() == 2L).map(FreightAllocator.Share::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(d("60.00"), ob1);
        assertEquals(d("20.00"), ob2);
    }
}

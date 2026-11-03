package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.inquiry.customerinquiry.dto.InquiryModelLineVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InquiryModelSummaryTest {

    private static InquiryModelLineVO line(String brand, String model, String category) {
        InquiryModelLineVO l = new InquiryModelLineVO();
        l.setBrand(brand);
        l.setModel(model);
        l.setCategory(category);
        return l;
    }

    @Test
    void brandsAndCategories_rankedByCount_tieKeepsOrder() {
        List<InquiryModelLineVO> lines = List.of(line("ABB", "A1", "变频器"), line("Siemens", "S1", "PLC"),
                line("Siemens", "S2", "PLC"), line("Omron", "O1", "传感器"), line(" ", "X", ""));
        assertEquals(List.of("Siemens", "ABB", "Omron"), InquiryModelSummary.brands(lines));
        assertEquals(List.of("PLC", "变频器", "传感器"), InquiryModelSummary.categories(lines));
        assertEquals("A1", InquiryModelSummary.firstModel(lines));
    }

    @Test
    void empty_noModel() {
        assertNull(InquiryModelSummary.firstModel(List.of()));
        assertEquals(List.of(), InquiryModelSummary.brands(List.of()));
        assertEquals("B", InquiryModelSummary.firstModel(List.of(line("X", "", ""), line("Y", "B", ""))), "跳过空型号");
    }
}

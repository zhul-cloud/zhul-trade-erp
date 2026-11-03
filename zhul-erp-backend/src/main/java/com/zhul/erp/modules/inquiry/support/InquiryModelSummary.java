package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.inquiry.customerinquiry.dto.InquiryModelLineVO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 询盘列表的型号摘要：第一个型号，品牌、品类按型号数从多到少（一样多时按出现顺序） */
public final class InquiryModelSummary {

    private InquiryModelSummary() {
    }

    public static String firstModel(List<InquiryModelLineVO> lines) {
        return lines.stream().map(InquiryModelLineVO::getModel).filter(StringUtils::hasText).findFirst().orElse(null);
    }

    public static List<String> brands(List<InquiryModelLineVO> lines) {
        return ranked(lines, InquiryModelLineVO::getBrand);
    }

    public static List<String> categories(List<InquiryModelLineVO> lines) {
        return ranked(lines, InquiryModelLineVO::getCategory);
    }

    private static List<String> ranked(List<InquiryModelLineVO> lines, Function<InquiryModelLineVO, String> key) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (InquiryModelLineVO l : lines) {
            String k = key.apply(l);
            if (StringUtils.hasText(k)) {
                counts.merge(k.trim(), 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        // List.sort 是稳定排序，数量一样时保持出现顺序
        entries.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()));
        return entries.stream().map(Map.Entry::getKey).toList();
    }
}

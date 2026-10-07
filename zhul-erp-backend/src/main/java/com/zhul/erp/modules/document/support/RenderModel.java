package com.zhul.erp.modules.document.support;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 渲染数据：表头字段、明细行、费用行，键为占位符名（如 quotation.no、item.model）。
 * 数值用 BigDecimal / Integer，Excel 中整格是一个数值占位符时写成数字单元格，公式才能计算。
 */
public record RenderModel(Map<String, Object> header, List<Map<String, Object>> items, List<Map<String, Object>> fees) {

    public static String text(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal b) {
            return b.toPlainString();
        }
        return v.toString();
    }
}

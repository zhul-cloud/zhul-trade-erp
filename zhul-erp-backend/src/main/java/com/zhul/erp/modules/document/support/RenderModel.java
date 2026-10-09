package com.zhul.erp.modules.document.support;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 渲染数据：表头字段、明细行、费用行，键为占位符名（如 quotation.no、item.model）。
 * 数值用 BigDecimal / Integer，Excel 中整格是一个数值占位符时写成数字单元格，公式才能计算。
 */
public record RenderModel(Map<String, Object> header, List<Map<String, Object>> items, List<Map<String, Object>> fees) {

    /** 明细行标记：无货行（不报价）。Excel 中该行公式清空；文字报价固定输出 型号 品牌 数量 + ${item.noStockText} */
    public static final String NO_STOCK = "item.noStock";
    /** PL 明细行所属的箱（同一箱的行连续）；箱字段 ${box.*} 只在箱内第一行有值，渲染后按箱合并单元格 */
    public static final String BOX_GROUP = "box.group";
    public static final String NO_STOCK_TEXT = "item.noStockText";
    static final String NO_STOCK_LINE = "${item.model} ${item.brand} ${item.qty} ${" + NO_STOCK_TEXT + "}";

    public static String text(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal b) {
            return b.toPlainString();
        }
        return v.toString();
    }

    /** 发给客户的单据：明细行品牌按 brands（原文 → 输出名）替换，如「西门子」→ Siemens */
    public RenderModel withBrands(java.util.function.Function<java.util.Collection<String>, Map<String, String>> brands) {
        List<String> names = items.stream().map(r -> r.get("item.brand")).filter(String.class::isInstance).map(String.class::cast).toList();
        if (names.isEmpty()) {
            return this;
        }
        Map<String, String> mapped = brands.apply(names);
        for (Map<String, Object> r : items) {
            Object b = r.get("item.brand");
            if (b instanceof String text && mapped.containsKey(text)) {
                r.put("item.brand", mapped.get(text));
            }
        }
        return this;
    }
}

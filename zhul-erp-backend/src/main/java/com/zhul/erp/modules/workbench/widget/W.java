package com.zhul.erp.modules.workbench.widget;

import com.zhul.erp.modules.workbench.dto.WidgetData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/** 看板组件的小工具：期间、较上期变化、各种块的构造 */
final class W {

    private W() {
    }

    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 本月 1 日到今天 */
    static LocalDate monthStart() {
        return LocalDate.now().withDayOfMonth(1);
    }

    /** 上月 1 日 */
    static LocalDate lastMonthStart() {
        return monthStart().minusMonths(1);
    }

    /** 上月最后一天 */
    static LocalDate lastMonthEnd() {
        return monthStart().minusDays(1);
    }

    static BigDecimal num(Number n) {
        return n == null ? BigDecimal.ZERO : n instanceof BigDecimal b ? b : BigDecimal.valueOf(n.doubleValue());
    }

    /** 百分比变化：(本期 − 上期) ÷ 上期，HALF_UP 保留 1 位；上期为 0 时为空 */
    static BigDecimal pct(BigDecimal cur, BigDecimal prev) {
        if (prev == null || prev.signum() == 0 || cur == null) {
            return null;
        }
        return cur.subtract(prev).multiply(HUNDRED).divide(prev.abs(), 1, RoundingMode.HALF_UP);
    }

    /** 比率：分子 ÷ 分母 × 100，HALF_UP 保留 1 位；分母为 0 时为空 */
    static BigDecimal rate(Number part, Number whole) {
        BigDecimal w = num(whole);
        if (w.signum() == 0) {
            return null;
        }
        return num(part).multiply(HUNDRED).divide(w, 1, RoundingMode.HALF_UP);
    }

    static WidgetData.Metric metric(String label, Number value, String format, String hint) {
        WidgetData.Metric m = new WidgetData.Metric();
        m.setLabel(label);
        m.setValue(value == null ? null : num(value));
        m.setFormat(format);
        m.setHint(hint);
        return m;
    }

    /** 带较上月的变化：money 与 count 的变化用百分比，percent 的变化用百分点 */
    static WidgetData.Metric compare(String label, Number cur, Number prev, String format, boolean higherIsBetter) {
        WidgetData.Metric m = metric(label, cur, format, null);
        if ("percent".equals(format)) {
            if (cur != null && prev != null) {
                m.setDelta(num(cur).subtract(num(prev)).setScale(1, RoundingMode.HALF_UP));
                m.setDeltaFormat("points");
            }
        } else {
            m.setDelta(pct(num(cur), num(prev)));
            m.setDeltaFormat("percent");
        }
        m.setHigherIsBetter(higherIsBetter);
        return m;
    }

    static WidgetData.Metric link(WidgetData.Metric m, String link) {
        m.setLink(link);
        return m;
    }

    static WidgetData.Metric tone(WidgetData.Metric m, String hintTone) {
        m.setHintTone(hintTone);
        return m;
    }

    static WidgetData.Todo todo(String icon, String tone, String title, String desc, long count, boolean alert, String link) {
        WidgetData.Todo t = new WidgetData.Todo();
        t.setIcon(icon);
        t.setTone(tone);
        t.setTitle(title);
        t.setDesc(desc);
        t.setCount(count);
        t.setAlert(alert);
        t.setLink(link);
        return t;
    }

    static WidgetData.Block metrics(List<WidgetData.Metric> items) {
        WidgetData.Block b = new WidgetData.Block();
        b.setType("metrics");
        b.setMetrics(items);
        return b;
    }

    static WidgetData.Block metrics(WidgetData.Metric... items) {
        return metrics(new ArrayList<>(Arrays.asList(items)));
    }

    static WidgetData.Block todos(List<WidgetData.Todo> items) {
        WidgetData.Block b = new WidgetData.Block();
        b.setType("todos");
        b.setTodos(items);
        return b;
    }

    static WidgetData.Block table(String title, WidgetData.Table t) {
        WidgetData.Block b = new WidgetData.Block();
        b.setType("table");
        b.setTitle(title);
        b.setTable(t);
        return b;
    }

    static WidgetData.Block bars(WidgetData.Bars bars, String text) {
        WidgetData.Block b = new WidgetData.Block();
        b.setType("bars");
        b.setBars(bars);
        b.setText(text);
        return b;
    }

    static WidgetData.Block note(String text) {
        WidgetData.Block b = new WidgetData.Block();
        b.setType("note");
        b.setText(text);
        return b;
    }

    static WidgetData of(WidgetData.Block... blocks) {
        WidgetData d = new WidgetData();
        for (WidgetData.Block b : blocks) {
            if (b != null) {
                d.add(b);
            }
        }
        return d;
    }

    /** 有数据接口的组件 */
    static WorkbenchWidget widget(String key, List<String> menus, Supplier<Object> loader) {
        return PurchaserWidgets.widget(key, menus, loader);
    }
}

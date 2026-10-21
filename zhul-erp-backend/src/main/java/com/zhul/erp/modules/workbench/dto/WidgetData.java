package com.zhul.erp.modules.workbench.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 看板组件的通用数据：由若干块组成，前端按块的类型统一渲染（数字行、待办、表格、柱状图、漏斗、分段进度、说明）。
 * 新增组件只要后端按这些块组织数据、前端注册表加一行标题与图标，不必再写展示组件。
 * 数值保留原始精度，由前端按 format 格式化（money 人民币、percent 百分比、count 个数）。
 */
@Data
public class WidgetData {

    private List<Block> blocks = new ArrayList<>();

    public WidgetData add(Block b) {
        blocks.add(b);
        return this;
    }

    @Data
    public static class Block {
        /** metrics、todos、table、bars、funnel、progress、note */
        private String type;
        /** 块的小标题（可空） */
        private String title;
        private List<Metric> metrics;
        private List<Todo> todos;
        private Table table;
        private Bars bars;
        private List<Step> steps;
        private List<Progress> progress;
        /** note 的文字，或其他块下方的一行说明 */
        private String text;
        /** 块里的标签（如未结汇外币），显示为胶囊 */
        private List<String> tags;
    }

    /** 数字：值、格式、一行说明，可带较上期的变化与点击进入的地址 */
    @Data
    public static class Metric {
        private String label;
        private BigDecimal value;
        private String format = "count";
        private String hint;
        /** 说明的色调：green、orange、red，空为默认 */
        private String hintTone;
        /** 较上期的变化：percent 为百分比变化，count/money 为差值；为空不显示 */
        private BigDecimal delta;
        private String deltaFormat;
        /** 变大是好事（收入）还是坏事（费用） */
        private Boolean higherIsBetter = true;
        private String link;
    }

    /** 待办：图标、色调、名称、说明、数量，点击进入列表 */
    @Data
    public static class Todo {
        private String icon;
        private String tone;
        private String title;
        private String desc;
        private Long count;
        /** 数量大于 0 时用色调强调 */
        private Boolean alert = false;
        private String link;
    }

    @Data
    public static class Table {
        private List<Column> columns = new ArrayList<>();
        /** 每行的单元格：文字或数字（数字按列的 format 格式化） */
        private List<List<Object>> rows = new ArrayList<>();
        /** 每行点击进入的地址（可空） */
        private List<String> links;
        private String empty;
    }

    @Data
    public static class Column {
        private String title;
        /** l 左对齐、r 右对齐 */
        private String align = "l";
        private String format;

        public static Column of(String title, String align, String format) {
            Column c = new Column();
            c.setTitle(title);
            c.setAlign(align);
            c.setFormat(format);
            return c;
        }
    }

    @Data
    public static class Bars {
        private List<String> labels = new ArrayList<>();
        private List<BigDecimal> values = new ArrayList<>();
        private String format = "count";
        /** 最后一根柱子突出显示（本月 / 今天） */
        private Boolean highlightLast = true;
    }

    /** 漏斗的一步：名称、数量、与上一步的转化率（%） */
    @Data
    public static class Step {
        private String label;
        private BigDecimal value;
        private BigDecimal rate;
        private String link;
    }

    /** 分段进度：一行一个对象，按段累计 */
    @Data
    public static class Progress {
        private String label;
        private List<Part> parts = new ArrayList<>();
    }

    @Data
    public static class Part {
        private String label;
        private BigDecimal value;
        /** green、blue、gray、orange */
        private String tone;
    }
}

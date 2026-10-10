package com.zhul.erp.modules.product.content.support;

import lombok.Data;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 一个 Markdown 内容包的解析结果（不含商品匹配与写入判断） */
@Data
public class ContentDraft {
    private String brand;
    private String model;
    private String lang;
    /** 文件级错误：有则整个文件不能确认 */
    private List<String> errors = new ArrayList<>();
    /** 提示：跳过的空章节、不认识的章节、第三期章节等 */
    private List<String> notes = new ArrayList<>();
    private Map<ContentSection, Block> blocks = new EnumMap<>(ContentSection.class);

    @Data
    public static class Block {
        private ContentSection section;
        /** 章节原文（不含标题行），预览修改时整段替换 */
        private String raw;
        private String text;
        private List<SpecRow> specs = new ArrayList<>();
        private List<AppRow> apps = new ArrayList<>();
        private List<CompatRow> compat = new ArrayList<>();
        private List<FaqRow> faqs = new ArrayList<>();
        private List<DocRow> docs = new ArrayList<>();
        /** 块级错误：有则该块必须修改或跳过才能确认 */
        private List<String> errors = new ArrayList<>();

        public int count() {
            if (section.isText()) {
                return text == null || text.isEmpty() ? 0 : 1;
            }
            return specs.size() + apps.size() + compat.size() + faqs.size() + docs.size();
        }
    }

    public record SpecRow(String label, String value, String unit) {
    }

    public record AppRow(String icon, String title, String description) {
    }

    public record CompatRow(String model, String note) {
    }

    public record FaqRow(String question, String answer) {
    }

    public record DocRow(String title, String url) {
    }
}

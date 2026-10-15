package com.zhul.erp.modules.product.content.support;

import lombok.Data;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一个 Markdown 内容包的解析结果（不含商品匹配与写入判断） */
@Data
public class ContentDraft {
    private String brand;
    private String model;
    private String lang;
    /** 包类型：facts-资料包、marketing-营销包 */
    private String type;
    /** 营销包的平台编码（alibaba、mic、site、social），资料包为空 */
    private String platform;
    /** frontmatter 的 updated（资料日期），平台参考价用作取价日期 */
    private String updated;
    /** 规范版本：2 为商品内容规范 v2，空为旧格式 */
    private Integer schema;
    /** v2 营销文件拆出的部分：keywords（关键词库）或平台编码；资料包与旧格式为空 */
    private String part;
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
        /** 关键词库：已按档位、档内顺序排好并去重 */
        private List<KeywordRow> keywords = new ArrayList<>();
        /** 卖点，每条一行 */
        private List<String> points = new ArrayList<>();
        /** 物流、海关、产品识别、SEO 字段：字段键 → 规范化后的值 */
        private Map<String, String> fields = new LinkedHashMap<>();
        /** 备选标题 */
        private List<TitleRow> titles = new ArrayList<>();
        /** 产品属性、图片说明文字：两列 */
        private List<PairRow> pairs = new ArrayList<>();
        /** 关键未知字段、冲突与待确认 */
        private List<NoteRow> notesRows = new ArrayList<>();
        /** 来源索引 */
        private List<SourceRow> sources = new ArrayList<>();
        /** 选用关键词 */
        private List<String> picks = new ArrayList<>();
        /** 社媒帖子、视频方案 */
        private List<PostRow> posts = new ArrayList<>();
        /** 平台参考价（公开挂牌价） */
        private List<ListingRow> listings = new ArrayList<>();
        /** 块级错误：有则该块必须修改或跳过才能确认 */
        private List<String> errors = new ArrayList<>();
        /** 块级提示：去重、截断等（不影响确认） */
        private List<String> notes = new ArrayList<>();

        public int count() {
            if (section.isText()) {
                return text == null || text.isEmpty() ? 0 : 1;
            }
            return specs.size() + apps.size() + compat.size() + faqs.size() + docs.size() + keywords.size()
                    + points.size() + fields.size() + titles.size() + pairs.size() + notesRows.size() + sources.size()
                    + picks.size() + posts.size() + listings.size();
        }
    }

    /** 规格：v2 另有条件、证据状态、来源说明 */
    public record SpecRow(String label, String value, String unit, String condition, String evidence, String sourceNote) {
        public SpecRow(String label, String value, String unit) {
            this(label, value, unit, "", "", "");
        }

        public SpecRow withSourceNote(String note) {
            return new SpecRow(label, value, unit, condition, evidence, note);
        }
    }

    public record AppRow(String icon, String title, String description) {
    }

    public record CompatRow(String model, String note) {
    }

    public record FaqRow(String question, String answer) {
    }

    public record DocRow(String title, String url) {
    }

    /** tier：1-核心、2-主要、3-长尾；v2 另有分类、搜索意图、事实依据 */
    public record KeywordRow(String keyword, int tier, String category, String intent, String basis) {
        public KeywordRow(String keyword, int tier) {
            this(keyword, tier, "", "", "");
        }
    }

    /** 标题：第一条为推荐 */
    public record TitleRow(String title, String angle) {
    }

    public record PairRow(String name, String value) {
    }

    /** kind：1-关键未知字段、2-冲突与待确认 */
    public record NoteRow(int kind, String item, String status, String impact) {
    }

    public record SourceRow(String code, String name, String link) {
    }

    /** 平台参考价：价格类别、平台/网站、成色、美元价、来源原价、来源链接 */
    public record ListingRow(String category, String platform, String condition, java.math.BigDecimal priceUsd,
                             String sourcePrice, String linkTitle, String linkUrl) {
    }

    /** 社媒帖子（channel 为 LinkedIn、Facebook）或视频方案（channel 为 YouTube）；text 为原文 */
    public record PostRow(String channel, String label, String text) {
    }
}

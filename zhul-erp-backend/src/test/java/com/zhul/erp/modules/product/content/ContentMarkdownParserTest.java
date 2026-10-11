package com.zhul.erp.modules.product.content;

import com.zhul.erp.modules.product.content.support.ContentDraft;
import com.zhul.erp.modules.product.content.support.ContentMarkdownParser;
import com.zhul.erp.modules.product.content.support.ContentSection;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ContentMarkdownParserTest {

    private static String resource(String name) throws IOException {
        try (InputStream in = ContentMarkdownParserTest.class.getResourceAsStream("/product-content/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void llmWikiContentPackage() throws IOException {
        // llm-wiki 现有内容包资料和营销章节混在一起：按资料包解析只取资料章节，按营销包解析只取营销章节
        String raw = resource("llm-wiki-68561906A.md");
        ContentDraft d = ContentMarkdownParser.parse(raw.replace("model: 68561906A", "model: 68561906A\ntype: facts\nlang: zh"));

        assertThat(d.getErrors()).isEmpty();
        assertThat(d.getBrand()).isEqualTo("ABB");
        assertThat(d.getModel()).isEqualTo("68561906A");
        assertThat(d.getLang()).isEqualTo("zh");
        assertThat(d.getType()).isEqualTo("facts");
        assertThat(d.getBlocks().get(ContentSection.SUMMARY).getText())
                .isEqualTo("IGBT gate drive board with Fuji 6MBI225U-120 module, for ACS800/ACS880");
        assertThat(d.getBlocks()).doesNotContainKey(ContentSection.GEO).doesNotContainKey(ContentSection.DOCS)
                .doesNotContainKey(ContentSection.FAQ);
        assertThat(d.getBlocks().get(ContentSection.SPECS).getSpecs()).hasSize(7)
                .first().satisfies(r -> {
                    assertThat(r.label()).isEqualTo("Order code");
                    assertThat(r.value()).isEqualTo("68561906A");
                });
        assertThat(d.getBlocks().get(ContentSection.APPS).getApps()).hasSize(3)
                .first().satisfies(r -> assertThat(r.title()).isEqualTo("ACS800 general-purpose drives"));
        assertThat(d.getBlocks().get(ContentSection.COMPAT).getCompat()).hasSize(2);
        assertThat(d.getBlocks().values()).allSatisfy(b -> assertThat(b.getErrors()).isEmpty());
        assertThat(d.getNotes()).anyMatch(n -> n.contains("首屏定义块") && n.contains("不属于资料包"))
                .anyMatch(n -> n.startsWith("「FAQ") && n.contains("不属于资料包"))
                .anyMatch(n -> n.contains("价格") && n.contains("第三期"));

        ContentDraft m = ContentMarkdownParser.parse(raw.replace("model: 68561906A", "model: 68561906A\ntype: marketing\nplatform: site\nlang: en"));
        assertThat(m.getErrors()).isEmpty();
        assertThat(m.getBlocks()).containsOnlyKeys(ContentSection.FAQ);
        ContentDraft.Block faq = m.getBlocks().get(ContentSection.FAQ);
        assertThat(faq.getErrors()).isEmpty();
        assertThat(faq.getFaqs()).hasSize(7);
        assertThat(faq.getFaqs().get(0).question()).isEqualTo("What is the ABB 68561906A / AGDR-71C board used for?");
        assertThat(faq.getFaqs().get(0).answer()).startsWith("It's an IGBT gate driver board");
        assertThat(m.getNotes()).anyMatch(n -> n.contains("规格参数表") && n.contains("不属于独立站营销包"))
                .anyMatch(n -> n.contains("首屏定义块") && n.contains("没有内容"));
    }

    @Test
    void factsTemplateInChinese() {
        String md = """
                ---
                type: Facts
                brand: 西门子
                model: "6es7 214-1ag40-0xb0"
                lang: ZH   # 中文
                ---
                <!-- 商品信息 -->
                品牌：Siemens

                ## 规格参数表
                | 字段 | 内容 | 单位 |
                |---|---|---|
                | 工作电压 | 24 | V DC |

                ## 应用场景
                - 产线控制 | 用于小型产线

                ## 技术问答
                问：能用 TIA Portal 编程吗？
                答：可以，
                V13 及以上。

                ## 技术资料
                - [数据手册](https://example.com/a.pdf)

                ## SEO 标题
                西门子 6ES7214-1AG40-0XB0
                """;
        ContentDraft d = ContentMarkdownParser.parse(md);
        assertThat(d.getErrors()).isEmpty();
        assertThat(d.getType()).isEqualTo("facts");
        assertThat(d.getBrand()).isEqualTo("西门子");
        assertThat(d.getModel()).isEqualTo("6es7 214-1ag40-0xb0");
        assertThat(d.getLang()).isEqualTo("zh");
        assertThat(d.getBlocks().get(ContentSection.SPECS).getSpecs())
                .containsExactly(new ContentDraft.SpecRow("工作电压", "24", "V DC"));
        assertThat(d.getBlocks().get(ContentSection.APPS).getApps())
                .containsExactly(new ContentDraft.AppRow("", "产线控制", "用于小型产线"));
        assertThat(d.getBlocks().get(ContentSection.TECH_FAQ).getFaqs())
                .containsExactly(new ContentDraft.FaqRow("能用 TIA Portal 编程吗？", "可以， V13 及以上。"));
        assertThat(d.getBlocks().get(ContentSection.DOCS).getDocs())
                .containsExactly(new ContentDraft.DocRow("数据手册", "https://example.com/a.pdf"));
        assertThat(d.getBlocks()).doesNotContainKey(ContentSection.SEO_TITLE);
        assertThat(d.getNotes()).anyMatch(n -> n.contains("SEO 标题") && n.contains("不属于资料包"));
    }

    @Test
    void marketingTemplate() {
        String md = """
                ---
                type: marketing
                platform: site
                brand: Siemens
                model: 6ES7214-1AG40-0XB0
                lang: ru
                ---
                ## SEO 标题
                Купить 6ES7214-1AG40-0XB0

                ## 产品长描述
                Абзац 1
                Абзац 2

                ## 营销问答
                Вопрос: Есть в наличии?
                Ответ: Да.
                """;
        ContentDraft d = ContentMarkdownParser.parse(md);
        assertThat(d.getErrors()).isEmpty();
        assertThat(d.getBlocks().get(ContentSection.SEO_TITLE).getText()).isEqualTo("Купить 6ES7214-1AG40-0XB0");
        assertThat(d.getBlocks().get(ContentSection.LONG_DESC).getText()).isEqualTo("Абзац 1\nАбзац 2");
        assertThat(d.getBlocks().get(ContentSection.FAQ).getFaqs())
                .containsExactly(new ContentDraft.FaqRow("Есть в наличии?", "Да."));
    }

    @Test
    void typeAndLangRules() {
        String body = "\n---\n## SEO 标题\nabc\n";
        assertThat(ContentMarkdownParser.parse("---\nbrand: A\nmodel: B\nlang: en" + body).getErrors())
                .anyMatch(e -> e.contains("缺少包类型"));
        assertThat(ContentMarkdownParser.parse("---\ntype: seo\nbrand: A\nmodel: B\nlang: en" + body).getErrors())
                .anyMatch(e -> e.contains("包类型「seo」无效"));
        assertThat(ContentMarkdownParser.parse("---\ntype: facts\nbrand: A\nmodel: B\nlang: en" + body).getErrors())
                .anyMatch(e -> e.contains("资料包只能是中文"));
        assertThat(ContentMarkdownParser.parse("---\ntype: marketing\nplatform: site\nbrand: A\nmodel: B\nlang: zh" + body).getErrors())
                .anyMatch(e -> e.contains("独立站营销包只能是英文或俄文"));
        assertThat(ContentMarkdownParser.parse("---\ntype: marketing\nplatform: site\nbrand: A\nmodel: B" + body).getErrors())
                .anyMatch(e -> e.contains("缺少 lang"));
        // 营销包按平台：缺平台、无效平台、平台语言不符
        assertThat(ContentMarkdownParser.parse("---\ntype: marketing\nbrand: A\nmodel: B\nlang: en" + body).getErrors())
                .anyMatch(e -> e.contains("缺少平台"));
        assertThat(ContentMarkdownParser.parse("---\ntype: marketing\nplatform: amazon\nbrand: A\nmodel: B\nlang: en" + body)
                .getErrors()).anyMatch(e -> e.contains("平台「amazon」无效"));
        assertThat(ContentMarkdownParser.parse("---\ntype: marketing\nplatform: alibaba\nbrand: A\nmodel: B\nlang: ru" + body)
                .getErrors()).anyMatch(e -> e.contains("阿里国际站营销包只能是英文"));
        assertThat(ContentMarkdownParser.parse("---\ntype: marketing\nplatform: Social\nbrand: A\nmodel: B\nlang: en"
                + "\n---\n## LinkedIn\nhi\n").getErrors()).isEmpty();
    }

    @Test
    void fileAndBlockErrors() {
        String marketing = """
                ---
                type: marketing
                platform: site
                brand: ABB
                lang: de
                ---
                ## SEO 标题
                %s
                ## FAQ
                Q: no answer
                ## 社媒文案
                hello
                ## 随便写的
                abc
                """.formatted("T".repeat(121));
        ContentDraft d = ContentMarkdownParser.parse(marketing);
        assertThat(d.getErrors()).anyMatch(e -> e.contains("model")).anyMatch(e -> e.contains("独立站营销包只能是英文或俄文"));
        assertThat(d.getBlocks().get(ContentSection.SEO_TITLE).getErrors()).anyMatch(e -> e.contains("120"));
        assertThat(d.getBlocks().get(ContentSection.FAQ).getErrors()).anyMatch(e -> e.contains("A:"));
        assertThat(d.getNotes()).anyMatch(n -> n.contains("社媒文案") && n.contains("不认识"))
                .anyMatch(n -> n.contains("随便写的") && n.contains("不认识"));

        String facts = """
                ---
                type: facts
                brand: ABB
                model: X1
                lang: zh
                ---
                ## 规格参数表
                not a table
                | only-one |
                ## 技术资料
                - [x](javascript:alert(1))
                """;
        ContentDraft f = ContentMarkdownParser.parse(facts);
        assertThat(f.getErrors()).isEmpty();
        assertThat(f.getBlocks().get(ContentSection.SPECS).getErrors()).hasSize(2);
        assertThat(f.getBlocks().get(ContentSection.DOCS).getErrors()).hasSize(1);
    }

    private static String mkt(String platform, String body) {
        return "---\ntype: marketing\nplatform: " + platform + "\nbrand: Schneider\nmodel: LXM32AD30N4\nlang: en\n---\n" + body;
    }

    @Test
    void alibabaKeywordsAndSellingPoints() {
        ContentDraft d = ContentMarkdownParser.parse(mkt("alibaba", """
                ## 标题
                Schneider LXM32AD30N4 Lexium 32 Servo Drive 3kW

                ## SEO 标题
                不属于阿里

                ## 关键词库（核心词在前）
                | 关键词 | 档位 |
                |---|---|
                | servo drive in stock | 长尾 |
                | LXM32AD30N4 | 核心 |
                | 3kW servo drive | main |
                | Lexium 32 servo drive | Core |
                | lxm32ad30n4 | 长尾 |
                | LXM32 spare part | long-tail |
                | Schneider servo | 2 |

                ## 卖点
                - Genuine Schneider, factory sealed
                1. 3kW, 400V three-phase
                2) Safe Torque Off built in
                * Ships in 1-3 days
                • 12-month warranty
                6、Datasheet support
                """));
        assertThat(d.getErrors()).isEmpty();
        assertThat(d.getPlatform()).isEqualTo("alibaba");
        assertThat(d.getBlocks().get(ContentSection.TITLE).getText()).isEqualTo("Schneider LXM32AD30N4 Lexium 32 Servo Drive 3kW");
        assertThat(d.getBlocks()).doesNotContainKey(ContentSection.SEO_TITLE);
        assertThat(d.getNotes()).anyMatch(n -> n.contains("SEO 标题") && n.contains("不属于阿里国际站营销包"));
        ContentDraft.Block kw = d.getBlocks().get(ContentSection.KEYWORDS);
        assertThat(kw.getErrors()).isEmpty();
        // 档位优先、同档按表格顺序；重复（不分大小写）只留第一次
        assertThat(kw.getKeywords()).containsExactly(
                new ContentDraft.KeywordRow("LXM32AD30N4", 1),
                new ContentDraft.KeywordRow("Lexium 32 servo drive", 1),
                new ContentDraft.KeywordRow("3kW servo drive", 2),
                new ContentDraft.KeywordRow("Schneider servo", 2),
                new ContentDraft.KeywordRow("servo drive in stock", 3),
                new ContentDraft.KeywordRow("LXM32 spare part", 3));
        assertThat(kw.getNotes()).anyMatch(n -> n.contains("重复的 1 个") && n.contains("lxm32ad30n4"));
        ContentDraft.Block points = d.getBlocks().get(ContentSection.SELLING_POINTS);
        assertThat(points.getPoints()).containsExactly("Genuine Schneider, factory sealed", "3kW, 400V three-phase",
                "Safe Torque Off built in", "Ships in 1-3 days", "12-month warranty");
        assertThat(points.getNotes()).anyMatch(n -> n.contains("超过 5 条") && n.contains("忽略 1 条"));
    }

    @Test
    void keywordErrorsAndLimit() {
        ContentDraft.Block bad = ContentMarkdownParser.parseBlock(ContentSection.KEYWORDS, "| a | 重要 |\n| | 核心 |\n| b |");
        assertThat(bad.getErrors()).hasSize(3).anyMatch(e -> e.contains("档位须为核心、主要或长尾"))
                .anyMatch(e -> e.contains("缺少关键词"));
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 205; i++) {
            many.append("| kw").append(i).append(" | ").append(i < 5 ? "长尾" : "核心").append(" |\n");
        }
        ContentDraft.Block big = ContentMarkdownParser.parseBlock(ContentSection.KEYWORDS, many.toString());
        assertThat(big.getKeywords()).hasSize(200).allMatch(k -> k.tier() == 1);
        assertThat(big.getNotes()).anyMatch(n -> n.contains("只保留前 200 个") && n.contains("忽略 5 个"));
        assertThat(ContentMarkdownParser.parseBlock(ContentSection.KEYWORDS, "| " + "k".repeat(129) + " | 核心 |")
                .getErrors()).anyMatch(e -> e.contains("128"));
    }

    @Test
    void micAndSocialKeepRawText() {
        ContentDraft mic = ContentMarkdownParser.parse(mkt("mic", """
                ## 标题
                LXM32AD30N4 Servo Drive

                ## 关键词
                | LXM32AD30N4 | 核心 |

                ## 商品详情
                ### Overview
                <!-- 说明 -->
                The **Lexium 32** drive.

                | Spec | Value |
                |---|---|
                | Power | 3kW |

                ## 产品长描述
                不属于中国制造网
                """));
        assertThat(mic.getErrors()).isEmpty();
        assertThat(mic.getBlocks()).containsOnlyKeys(ContentSection.TITLE, ContentSection.KEYWORDS, ContentSection.DETAIL);
        assertThat(mic.getBlocks().get(ContentSection.DETAIL).getText())
                .isEqualTo("### Overview\n\nThe **Lexium 32** drive.\n\n| Spec | Value |\n|---|---|\n| Power | 3kW |");
        assertThat(mic.getNotes()).anyMatch(n -> n.contains("产品长描述") && n.contains("不属于中国制造网营销包"));

        ContentDraft social = ContentMarkdownParser.parse(mkt("social", """
                ## LinkedIn 帖子
                Direct-on-line starting slams a motor to full speed.

                ⚙️ 51.0A (Delta) / 30.0A (3-wire)
                ✅ UL Listed

                #AllenBradley #SoftStarter

                ## Facebook
                **150-C30NBD | In Stock**
                👉 DM us

                ## YouTube 视频描述
                📌 Timestamps:
                00:00 Front panel

                ## 小红书
                中文帖子
                """));
        assertThat(social.getErrors()).isEmpty();
        assertThat(social.getBlocks().get(ContentSection.LINKEDIN).getText()).isEqualTo(
                "Direct-on-line starting slams a motor to full speed.\n\n⚙️ 51.0A (Delta) / 30.0A (3-wire)\n✅ UL Listed\n\n"
                        + "#AllenBradley #SoftStarter");
        assertThat(social.getBlocks().get(ContentSection.FACEBOOK).getText()).isEqualTo("**150-C30NBD | In Stock**\n👉 DM us");
        assertThat(social.getBlocks().get(ContentSection.YOUTUBE).getText()).isEqualTo("📌 Timestamps:\n00:00 Front panel");
        assertThat(social.getNotes()).anyMatch(n -> n.contains("小红书") && n.contains("不认识"));
        assertThat(ContentMarkdownParser.parseBlock(ContentSection.LINKEDIN, "x".repeat(3001)).getErrors())
                .anyMatch(e -> e.contains("3000"));
    }

    @Test
    void missingFrontmatter() {
        ContentDraft d = ContentMarkdownParser.parse("## SEO 标题\nabc\n");
        assertThat(d.getErrors()).anyMatch(e -> e.contains("frontmatter"));
    }

    @Test
    void parseSingleBlock() {
        ContentDraft.Block b = ContentMarkdownParser.parseBlock(ContentSection.SPECS, "| A | 1 |\n| B | 2 |");
        assertThat(b.getSpecs()).hasSize(2);
        assertThat(ContentMarkdownParser.parseBlock(ContentSection.GEO, "（无）").count()).isZero();
    }
}

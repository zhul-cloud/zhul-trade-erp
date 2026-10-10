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
        // llm-wiki 现有内容包没有 lang，补上后应完整解析
        String md = resource("llm-wiki-68561906A.md").replace("model: 68561906A", "model: 68561906A\nlang: en");
        ContentDraft d = ContentMarkdownParser.parse(md);

        assertThat(d.getErrors()).isEmpty();
        assertThat(d.getBrand()).isEqualTo("ABB");
        assertThat(d.getModel()).isEqualTo("68561906A");
        assertThat(d.getLang()).isEqualTo("en");
        assertThat(d.getBlocks().get(ContentSection.SUMMARY).getText())
                .isEqualTo("IGBT gate drive board with Fuji 6MBI225U-120 module, for ACS800/ACS880");
        assertThat(d.getBlocks()).doesNotContainKey(ContentSection.GEO).doesNotContainKey(ContentSection.DOCS);
        assertThat(d.getBlocks().get(ContentSection.SPECS).getSpecs()).hasSize(7)
                .first().satisfies(r -> {
                    assertThat(r.label()).isEqualTo("Order code");
                    assertThat(r.value()).isEqualTo("68561906A");
                });
        assertThat(d.getBlocks().get(ContentSection.APPS).getApps()).hasSize(3)
                .first().satisfies(r -> assertThat(r.title()).isEqualTo("ACS800 general-purpose drives"));
        assertThat(d.getBlocks().get(ContentSection.COMPAT).getCompat()).hasSize(2);
        ContentDraft.Block faq = d.getBlocks().get(ContentSection.FAQ);
        assertThat(faq.getErrors()).isEmpty();
        assertThat(faq.getFaqs()).hasSize(7);
        assertThat(faq.getFaqs().get(0).question()).isEqualTo("What is the ABB 68561906A / AGDR-71C board used for?");
        assertThat(faq.getFaqs().get(0).answer()).startsWith("It's an IGBT gate driver board");
        assertThat(d.getBlocks().values()).allSatisfy(b -> assertThat(b.getErrors()).isEmpty());
        assertThat(d.getNotes()).anyMatch(n -> n.contains("首屏定义块") && n.contains("没有内容"))
                .anyMatch(n -> n.contains("价格") && n.contains("第三期"));
    }

    @Test
    void templateSectionsAndChinese() {
        String md = """
                ---
                brand: 西门子
                model: "6es7 214-1ag40-0xb0"
                lang: ZH   # 中文
                ---
                <!-- 商品信息 -->
                品牌：Siemens

                ## SEO 标题
                西门子 6ES7214-1AG40-0XB0 CPU 1214C

                ## SEO 描述
                原装现货，快速报价

                ## 产品长描述
                第一段
                第二段

                ## 规格参数表
                | 字段 | 内容 | 单位 |
                |---|---|---|
                | 工作电压 | 24 | V DC |

                ## 应用场景
                - 产线控制 | 用于小型产线

                ## FAQ
                问：有现货吗？
                答：有，
                24 小时发货。

                ## 技术资料
                - [Datasheet](https://example.com/a.pdf)
                """;
        ContentDraft d = ContentMarkdownParser.parse(md);
        assertThat(d.getErrors()).isEmpty();
        assertThat(d.getBrand()).isEqualTo("西门子");
        assertThat(d.getModel()).isEqualTo("6es7 214-1ag40-0xb0");
        assertThat(d.getLang()).isEqualTo("zh");
        assertThat(d.getBlocks().get(ContentSection.SEO_TITLE).getText()).isEqualTo("西门子 6ES7214-1AG40-0XB0 CPU 1214C");
        assertThat(d.getBlocks().get(ContentSection.LONG_DESC).getText()).isEqualTo("第一段\n第二段");
        assertThat(d.getBlocks().get(ContentSection.SPECS).getSpecs())
                .containsExactly(new ContentDraft.SpecRow("工作电压", "24", "V DC"));
        assertThat(d.getBlocks().get(ContentSection.APPS).getApps())
                .containsExactly(new ContentDraft.AppRow("", "产线控制", "用于小型产线"));
        assertThat(d.getBlocks().get(ContentSection.FAQ).getFaqs())
                .containsExactly(new ContentDraft.FaqRow("有现货吗？", "有， 24 小时发货。"));
        assertThat(d.getBlocks().get(ContentSection.DOCS).getDocs())
                .containsExactly(new ContentDraft.DocRow("Datasheet", "https://example.com/a.pdf"));
    }

    @Test
    void fileAndBlockErrors() {
        String md = """
                ---
                brand: ABB
                lang: de
                ---
                ## SEO 标题
                %s
                ## 规格参数表
                not a table
                | only-one |
                ## FAQ
                Q: no answer
                ## 技术资料
                - [x](javascript:alert(1))
                ## 社媒文案
                hello
                ## 随便写的
                abc
                """.formatted("T".repeat(121));
        ContentDraft d = ContentMarkdownParser.parse(md);
        assertThat(d.getErrors()).anyMatch(e -> e.contains("model")).anyMatch(e -> e.contains("de"));
        assertThat(d.getBlocks().get(ContentSection.SEO_TITLE).getErrors()).anyMatch(e -> e.contains("120"));
        assertThat(d.getBlocks().get(ContentSection.SPECS).getErrors()).hasSize(2);
        assertThat(d.getBlocks().get(ContentSection.FAQ).getErrors()).anyMatch(e -> e.contains("A:"));
        assertThat(d.getBlocks().get(ContentSection.DOCS).getErrors()).hasSize(1);
        assertThat(d.getNotes()).anyMatch(n -> n.contains("社媒文案") && n.contains("第三期"))
                .anyMatch(n -> n.contains("随便写的") && n.contains("不认识"));
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

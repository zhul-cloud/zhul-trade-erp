package com.zhul.erp.modules.product.content;

import com.zhul.erp.modules.product.content.support.ContentDraft;
import com.zhul.erp.modules.product.content.support.ContentSection;
import com.zhul.erp.modules.product.content.support.ContentV2Parser;
import com.zhul.erp.modules.product.content.support.FactFields;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 商品内容规范 v2 解析：样例为技能对 Delta VFD015M21A 的实际输出按 v2 整理 */
class ContentV2ParserTest {

    private static String resource(String name) throws IOException {
        try (InputStream in = ContentV2ParserTest.class.getResourceAsStream("/product-content/v2/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ContentDraft facts() throws IOException {
        List<ContentDraft> drafts = ContentV2Parser.parseAll(resource("facts-VFD015M21A.md"));
        assertThat(drafts).hasSize(1);
        return drafts.get(0);
    }

    @Test
    void factsSample() throws IOException {
        ContentDraft d = facts();
        assertThat(d.getErrors()).isEmpty();
        assertThat(d.getSchema()).isEqualTo(2);
        assertThat(d.getType()).isEqualTo("facts");
        assertThat(d.getBlocks().values()).allSatisfy(b -> assertThat(b.getErrors()).isEmpty());

        assertThat(d.getBlocks().get(ContentSection.IDENTITY).getFields())
                .containsEntry(ContentV2Parser.NAME_ZH, "台达 VFD-M 变频器")
                .containsEntry(ContentV2Parser.NAME_EN, "Delta VFD-M AC Motor Drive")
                .containsEntry(ContentV2Parser.SERIES, "VFD-M")
                .hasSize(3);
        assertThat(d.getBlocks().get(ContentSection.SUMMARY).getText()).startsWith("台达 VFD-M 系列 1.5 kW");

        List<ContentDraft.SpecRow> specs = d.getBlocks().get(ContentSection.SPECS).getSpecs();
        assertThat(specs).hasSize(7);
        assertThat(specs.get(3)).satisfies(r -> {
            assertThat(r.label()).isEqualTo("输入相数及电流");
            assertThat(r.value()).isEqualTo("单相 15.7 A；三相 9.0 A");
            assertThat(r.condition()).isEqualTo("两种输入条件分别列示；不可与输出电流互换");
            assertThat(r.evidence()).isEqualTo("实物标签确认，手册佐证");
            assertThat(r.sourceNote()).isEqualTo("S02、S03");
        });

        assertThat(d.getBlocks().get(ContentSection.UNKNOWNS).getNotesRows()).hasSize(4)
                .first().satisfies(n -> {
                    assertThat(n.kind()).isEqualTo(1);
                    assertThat(n.item()).isEqualTo("当前可售数量、成色、测试记录、保修");
                    assertThat(n.status()).isEqualTo("待卖方实时确认");
                    assertThat(n.impact()).isEqualTo("不写现货、全新原装、已测试或保修承诺");
                });
        assertThat(d.getBlocks().get(ContentSection.CONFLICTS).getNotesRows()).hasSize(4)
                .first().satisfies(n -> {
                    assertThat(n.kind()).isEqualTo(2);
                    assertThat(n.item()).isEqualTo("输入与输出电流不能混用。");
                    assertThat(n.impact()).startsWith("铭牌的单相输入 15.7 A");
                });

        assertThat(d.getBlocks().get(ContentSection.APPS).getApps()).hasSize(3).first().satisfies(a -> {
            assertThat(a.title()).isEqualTo("现有 VFD015M21A 备件采购");
            assertThat(a.description()).isEqualTo("寻找同型号设备；选型前提：比对完整铭牌、控制接线、参数备份及机器工况");
        });
        assertThat(d.getBlocks().get(ContentSection.COMPAT).getCompat()).hasSize(2);
        assertThat(d.getBlocks().get(ContentSection.TECH_FAQ).getFaqs()).hasSize(2);
        assertThat(d.getBlocks().get(ContentSection.DOCS).getDocs()).hasSize(2);
        assertThat(d.getBlocks().get(ContentSection.SOURCES).getSources()).hasSize(5).first().satisfies(s -> {
            assertThat(s.code()).isEqualTo("S01");
            assertThat(s.name()).isEqualTo("Delta 官方 VFD-M 系列页，系列生命周期");
            assertThat(s.link()).isEqualTo("https://www.deltaww.com/en-US/products/AC-Motor-Drives/98");
        });
        // 内部附录不解析
        assertThat(d.getNotes()).noneMatch(n -> n.contains("内部附录"));
    }

    @Test
    void specSourceCodesResolvedToNames() throws IOException {
        ContentDraft d = facts();
        ContentV2Parser.resolveSources(d);
        List<ContentDraft.SpecRow> specs = d.getBlocks().get(ContentSection.SPECS).getSpecs();
        assertThat(specs.get(2).sourceNote()).isEqualTo("S03 项目原始铭牌照片");
        assertThat(specs.get(3).sourceNote()).startsWith("S02 Delta 官方 VFD-M 中文手册").contains("；S03 项目原始铭牌照片");
    }

    @Test
    void marketingSampleSplitsIntoKeywordLibraryAndPlatforms() throws IOException {
        List<ContentDraft> drafts = ContentV2Parser.parseAll(resource("marketing-VFD015M21A.md"));
        assertThat(drafts).extracting(ContentDraft::getPart)
                .containsExactly("keywords", "alibaba", "mic", "site", "social");
        assertThat(drafts).allSatisfy(d -> {
            assertThat(d.getErrors()).isEmpty();
            assertThat(d.getBlocks().values()).allSatisfy(b -> assertThat(b.getErrors()).as(b.getSection().name()).isEmpty());
            assertThat(d.getLang()).isEqualTo("en");
        });

        ContentDraft kw = drafts.get(0);
        assertThat(kw.getPlatform()).isNull();
        List<ContentDraft.KeywordRow> library = kw.getBlocks().get(ContentSection.KEYWORD_LIBRARY).getKeywords();
        assertThat(library).isNotEmpty().first().satisfies(k -> {
            assertThat(k.keyword()).isEqualTo("Delta VFD015M21A");
            assertThat(k.tier()).isEqualTo(1);
            assertThat(k.category()).isNotEmpty();
            assertThat(k.intent()).isNotEmpty();
        });
        assertThat(library).anySatisfy(k -> {
            assertThat(k.keyword()).isEqualTo("VFD-M to ME300 or MS300 evaluation");
            assertThat(k.tier()).isEqualTo(3);
        });

        ContentDraft ali = drafts.get(1);
        assertThat(ali.getPlatform()).isEqualTo("alibaba");
        ContentDraft.Block title = ali.getBlocks().get(ContentSection.TITLE);
        assertThat(title.getText()).isEqualTo("Delta VFD015M21A VFD-M AC Motor Drive 1.5 kW 2 HP 200-240 V Input");
        assertThat(title.getTitles()).hasSize(4);
        assertThat(title.getTitles().get(1).angle()).isEqualTo("输出参数");
        assertThat(ali.getBlocks().get(ContentSection.KEYWORD_PICKS).getPicks()).contains("Delta VFD015M21A")
                .allSatisfy(p -> assertThat(library).anyMatch(k -> k.keyword().equalsIgnoreCase(p)));
        assertThat(ali.getBlocks().get(ContentSection.ATTRIBUTES).getPairs()).hasSize(7)
                .first().isEqualTo(new ContentDraft.PairRow("Brand / Model", "Delta / VFD015M21A"));
        assertThat(ali.getBlocks().get(ContentSection.SELLING_POINTS).getPoints()).hasSize(4);
        assertThat(ali.getBlocks().get(ContentSection.DETAIL).getText()).startsWith("Delta VFD015M21A is a VFD-M");
        assertThat(ali.getBlocks().get(ContentSection.FAQ).getFaqs()).hasSize(7).first().satisfies(f -> {
            assertThat(f.question()).isEqualTo("Is this the exact VFD015M21A model?");
            assertThat(f.answer()).startsWith("The offered specification");
        });
        assertThat(ali.getBlocks().get(ContentSection.CTA).getText()).startsWith("Send an inquiry");

        ContentDraft site = drafts.get(3);
        assertThat(site.getBlocks().get(ContentSection.SEO_FIELDS).getFields())
                .containsEntry(ContentV2Parser.SEO_TITLE, "Delta VFD015M21A VFD-M Drive")
                .containsEntry(ContentV2Parser.H1, "Delta VFD015M21A VFD-M AC Motor Drive")
                .containsEntry(ContentV2Parser.URL_SLUG, "delta-vfd015m21a-vfd-m-ac-motor-drive")
                .containsKey(ContentV2Parser.META_DESCRIPTION);
        assertThat(site.getBlocks().get(ContentSection.GEO).getText()).startsWith("Delta VFD015M21A is a VFD-M");
        assertThat(site.getBlocks().get(ContentSection.LONG_DESC).getText()).contains("**Specifications**");
        assertThat(site.getBlocks().get(ContentSection.FAQ).getFaqs()).hasSize(7);
        assertThat(site.getBlocks().get(ContentSection.ALT_TEXTS).getPairs()).hasSize(2)
                .first().satisfies(p -> assertThat(p.name()).isEqualTo("正面图"));

        ContentDraft social = drafts.get(4);
        List<ContentDraft.PostRow> posts = social.getBlocks().get(ContentSection.POSTS).getPosts();
        assertThat(posts).extracting(ContentDraft.PostRow::channel)
                .containsExactly("LinkedIn", "LinkedIn", "LinkedIn", "Facebook", "Facebook");
        assertThat(posts.get(0).label()).isEqualTo("产品识别");
        assertThat(posts.get(0).text()).contains("#Delta #VFDM #IndustrialDrives").contains("🔗 Get a quote");
        List<ContentDraft.PostRow> videos = social.getBlocks().get(ContentSection.VIDEOS).getPosts();
        assertThat(videos).hasSize(2);
        assertThat(videos.get(0).label()).startsWith("待拍摄方案");
        assertThat(videos.get(0).text()).contains("| 预计时间 |").contains("**Thumbnail text:**");
    }

    private static String marketing(String body) {
        return "---\nschema: 2\ntype: marketing\nbrand: Delta\nmodel: X1\nlang: en\n---\n\n# X1\n\n" + body;
    }

    @Test
    void appendixSkippedAndHeadingVariants() {
        List<ContentDraft> drafts = ContentV2Parser.parseAll(marketing("""
                ## 1. 阿里国际站 Alibaba.com

                ### Buyer benefits

                - Exact model

                ### Product details

                Body text.

                ### Alibaba inquiry CTA

                Send an inquiry.

                ### 奇怪的章节

                x

                ## 内部附录

                ## 中国制造网

                ### 标题

                Should not be parsed
                """));
        assertThat(drafts).hasSize(1);
        ContentDraft ali = drafts.get(0);
        assertThat(ali.getBlocks()).containsOnlyKeys(ContentSection.SELLING_POINTS, ContentSection.DETAIL, ContentSection.CTA);
        assertThat(ali.getNotes()).anyMatch(n -> n.contains("奇怪的章节"));
    }

    @Test
    void russianFileOnlyForSite() {
        List<ContentDraft> drafts = ContentV2Parser.parseAll(marketing("""
                ## 独立站

                ### 首屏定义块

                Привет.

                ## 阿里国际站

                ### 标题

                Title
                """).replace("lang: en", "lang: ru"));
        assertThat(drafts.get(0).getErrors()).isEmpty();
        assertThat(drafts.get(1).getErrors()).anyMatch(e -> e.contains("俄文只用于独立站"));
    }

    @Test
    void pendingValuesNotImportedAndMissingColumnsReported() {
        List<ContentDraft> drafts = ContentV2Parser.parseAll("""
                ---
                schema: 2
                type: facts
                brand: Delta
                model: X1
                lang: zh
                ---

                ## 规格参数

                | 事实编号 | 项目 | 数值及单位 | 条件/范围 | 证据状态 | 来源 |
                | --- | --- | --- | --- | --- | --- |
                | F01 | 净重 | [待确认] | | 待确认 | |
                | F02 | 输出电流 | 7.0 A | 额定 | 官方确认 | S01 |
                | F03 |

                ## 价格与采购参考

                USD 60
                """);
        ContentDraft d = drafts.get(0);
        ContentDraft.Block specs = d.getBlocks().get(ContentSection.SPECS);
        assertThat(specs.getSpecs()).extracting(ContentDraft.SpecRow::label).containsExactly("输出电流");
        assertThat(specs.getNotes()).anyMatch(n -> n.contains("净重"));
        assertThat(specs.getErrors()).anyMatch(e -> e.contains("事实编号"));
        assertThat(d.getNotes()).anyMatch(n -> n.contains("价格与采购参考") && n.contains("不导入"));
    }

    @Test
    void legacyFileStillParsedAsV1() {
        List<ContentDraft> drafts = ContentV2Parser.parseAll("""
                ---
                type: marketing
                platform: alibaba
                brand: Delta
                model: X1
                lang: en
                ---

                ## 标题

                Delta X1 drive
                """);
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).getSchema()).isNull();
        assertThat(drafts.get(0).getNotes().get(0)).contains("旧格式");
        assertThat(drafts.get(0).getBlocks().get(ContentSection.TITLE).getText()).isEqualTo("Delta X1 drive");
    }

    @Test
    void logisticsAndCustomsStillUseFactFields() {
        List<ContentDraft> drafts = ContentV2Parser.parseAll("""
                ---
                schema: 2
                type: facts
                brand: Delta
                model: X1
                lang: zh
                ---

                ## 海关信息

                | 字段 | 内容 |
                | --- | --- |
                | HS 编码（中国） | 8504.40.9999 |
                | 原产地 | 中国台湾 |
                """);
        assertThat(drafts.get(0).getBlocks().get(ContentSection.CUSTOMS).getFields())
                .containsEntry(FactFields.HS_CODE, "8504409999");
    }
}

package com.zhul.erp.modules.product.candidate.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WikiProductParserTest {

    private static String resource(String name) throws IOException {
        try (InputStream in = WikiProductParserTest.class.getResourceAsStream("/product-candidate/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void realWikiFile() throws IOException {
        WikiProductParser.Draft d = WikiProductParser.parse(resource("wiki-6EP3437-8SB00-0AY0.md"));
        assertThat(d.errors()).isEmpty();
        assertThat(d.brand()).isEqualTo("Siemens");
        assertThat(d.model()).isEqualTo("6EP3437-8SB00-0AY0");
        assertThat(d.categoryText()).isEqualTo("Power Supply PowerSupply（开关电源，新品类首次入库）");
        assertThat(d.productName()).isEqualTo("SITOP PSU8200系列开关电源");
        assertThat(d.description()).isEqualTo("SITOP PSU8200系列开关电源，3AC输入，DC24V/40A输出");
        assertThat(d.descriptionEn()).isEqualTo("Siemens SITOP PSU8200 Power Supply");
    }

    @Test
    void indexFileIsNotAProduct() {
        WikiProductParser.Draft d = WikiProductParser.parse("---\ntitle: 商品索引\n---\n\n# 商品索引\n\n| 品牌 | 型号 |\n");
        assertThat(d.errors()).anyMatch(e -> e.contains("缺少型号")).anyMatch(e -> e.contains("缺少品牌"));
        assertThat(WikiProductParser.parse("# 品牌品类矩阵\n\n内容").errors()).anyMatch(e -> e.contains("缺少 frontmatter"));
    }

    @Test
    void missingTableAndTitleSeparator() {
        WikiProductParser.Draft d = WikiProductParser.parse("""
                ---
                brand: "ABB"   # 品牌
                model: '3AFE68249457'
                category: ASiModule
                ---

                # ABB 3AFE68249457

                ## 规格参数
                | 品类 | 不在基本信息里 |
                """);
        assertThat(d.errors()).isEmpty();
        assertThat(d.brand()).isEqualTo("ABB");
        assertThat(d.model()).isEqualTo("3AFE68249457");
        assertThat(d.categoryText()).isEqualTo("ASi Module");
        assertThat(d.productName()).isNull();
        assertThat(d.description()).isNull();
    }

    @Test
    void placeholdersAreEmpty() {
        WikiProductParser.Draft d = WikiProductParser.parse("""
                ---
                brand: JAKON
                model: JK76
                category:
                ---
                # JAKON JK76 — [待确认]

                ## 基本信息
                | 字段 | 内容 |
                |------|------|
                | 品类 | — |
                | 中文产品名 | 数显计数器 |
                | 英文产品名 | [待确认] |
                """);
        assertThat(d.errors()).isEmpty();
        assertThat(d.categoryText()).isNull();
        assertThat(d.productName()).isEqualTo("数显计数器");
        assertThat(d.description()).isNull();
        assertThat(d.descriptionEn()).isNull();
    }

    @Test
    void camelCaseCategorySplit() {
        assertThat(WikiProductParser.categoryText("ServoAmplifier", null)).isEqualTo("Servo Amplifier");
        assertThat(WikiProductParser.categoryText("HMI", "HMI")).isEqualTo("HMI");
        assertThat(WikiProductParser.categoryText("IOModule", null)).isEqualTo("IO Module");
        assertThat(WikiProductParser.categoryText("DriveBoard", "DriveBoard（驱动器配件板卡）"))
                .isEqualTo("Drive Board DriveBoard（驱动器配件板卡）");
    }
}

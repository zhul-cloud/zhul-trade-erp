package com.zhul.erp.modules.document;

import com.zhul.erp.modules.document.support.DocumentConverter;
import com.zhul.erp.modules.document.support.Placeholders;
import com.zhul.erp.modules.document.support.RenderModel;
import com.zhul.erp.modules.document.support.SampleData;
import com.zhul.erp.modules.document.support.TemplateProblem;
import com.zhul.erp.modules.document.support.TextTemplateEngine;
import com.zhul.erp.modules.document.support.XlsxRenderer;
import com.zhul.erp.modules.document.support.XlsxTemplateInspector;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 模版解析、Excel 展开、文字报价、PDF / 图片压缩 */
class DocumentRenderTest {

    private static final String TEXT_V1 = "{{#items}}\n${item.model} ${item.brand} ${item.qty} ${quotation.currencySymbol}"
            + "${item.unitPriceShort} ${item.leadTimeEn} ${item.conditionEn} ${item.warranty} warranty time\n{{/items}}";

    private static byte[] builtin(String name) throws IOException {
        try (InputStream in = new ClassPathResource("document-template/" + name).getInputStream()) {
            return in.readAllBytes();
        }
    }

    @Test
    void builtinQuotationTemplatePassesInspection() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(builtin("quotation-v1.xlsx")))) {
            assertThat(XlsxTemplateInspector.hasPageNumber(wb)).isTrue();
            XlsxTemplateInspector.Layout layout = XlsxTemplateInspector.inspect(wb, true);
            assertThat(layout.problems()).isEmpty();
            assertThat(layout.itemRow()).isGreaterThan(0);
            assertThat(layout.feeRow()).isGreaterThan(layout.itemRow());
        }
    }

    @Test
    void inspectionReportsUnknownPlaceholderAndMissingItems() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet().createRow(2).createCell(1).setCellValue("${customer.nmae}");
            List<TemplateProblem> problems = XlsxTemplateInspector.inspect(wb, true).problems();
            assertThat(problems).extracting(TemplateProblem::toString)
                    .anyMatch(s -> s.startsWith("B3") && s.contains("${customer.nmae}"))
                    .anyMatch(s -> s.contains("没有找到明细行"));
        }
    }

    @Test
    void itemRowsExpandAndTotalsCoverAllRows() throws IOException {
        byte[] out = XlsxRenderer.render(builtin("quotation-v1.xlsx"), SampleData.quotation());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            String all = dump(sheet);
            assertThat(all).doesNotContain("${");
            assertThat(all).contains("6ES7214-1AG40-0XB0", "1756-L83E", "VPSH 61", "Shipping (DHL)", "QT20261004001");
            // 6120 + 268.5×2 + 85.9×4 + 65 = 7065.6
            assertThat(all).contains("7065.6");
            assertThat(sheet.getRepeatingRows()).isNotNull();
        }
    }

    @Test
    void emptyFeesRemovesFeeRowWithoutBreakingTotal() throws IOException {
        RenderModel sample = SampleData.quotation();
        Map<String, Object> header = new LinkedHashMap<>(sample.header());
        header.put("quotation.feeTotal", BigDecimal.ZERO);
        RenderModel noFee = new RenderModel(header, sample.items(), List.of());
        byte[] out = XlsxRenderer.render(builtin("quotation-v1.xlsx"), noFee);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out))) {
            String all = dump(wb.getSheetAt(0));
            assertThat(all).doesNotContain("${", "#REF").contains("7000.6");
        }
    }

    @Test
    void textQuoteOneLinePerModelWithTrailingZerosStripped() {
        assertThat(TextTemplateEngine.validate(TEXT_V1)).isEmpty();
        String text = TextTemplateEngine.render(TEXT_V1, SampleData.quotation());
        assertThat(text.split("\n")).containsExactly(
                "6ES7214-1AG40-0XB0 SIEMENS 2 $268.5 In stock New Original 1 year warranty time",
                "1756-L83E Allen-Bradley 1 $6120 1-2 weeks New Sealed 1 year warranty time",
                "VPSH 61 VEGA 4 $85.9 In stock Used 3 months warranty time");
    }

    @Test
    void textTemplateValidation() {
        assertThat(TextTemplateEngine.validate("${item.model}")).extracting(TemplateProblem::message)
                .anyMatch(m -> m.contains("{{#items}}"));
        assertThat(TextTemplateEngine.validate("{{#items}}${item.modle}{{/items}}")).extracting(TemplateProblem::message)
                .anyMatch(m -> m.contains("${item.modle}"));
        assertThat(TextTemplateEngine.validate("{{#items}}${item.model}")).isNotEmpty();
        assertThat(TextTemplateEngine.validate("  ")).isNotEmpty();
        assertThat(Placeholders.known("quotation.total")).isTrue();
    }

    /** 福唯报价模版（公司模版改造的占位符版）渲染 12 行：行数、合计公式、Logo 位置、行内合并、表头每页重复 */
    @Test
    void fouwellTemplateRendersTwelveRows() throws IOException {
        byte[] tpl;
        try (InputStream in = new ClassPathResource("document-template/fouwell-quotation-v2.xlsx").getInputStream()) {
            tpl = in.readAllBytes();
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(tpl))) {
            assertThat(XlsxTemplateInspector.inspect(wb, true).problems()).isEmpty();
            assertThat(XlsxTemplateInspector.hasPageNumber(wb)).isTrue();
        }
        byte[] out = XlsxRenderer.render(tpl, withItems(12));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            String all = dump(sheet);
            assertThat(all).doesNotContain("${").contains("Fuzhou Fouwell", "Shipping (DHL)", "QT20261004001");
            // 第 5~16 行为 12 行明细，第 17 行费用，第 18 行合计 = SUM(M5:M17)
            assertThat(sheet.getRow(4).getCell(5).getStringCellValue()).isEqualTo("6ES7214-1AG40-0XB0");
            assertThat(sheet.getRow(15).getCell(1).getNumericCellValue()).isEqualTo(12d);
            assertThat(sheet.getRow(16).getCell(2).getStringCellValue()).isEqualTo("Shipping (DHL)");
            assertThat(sheet.getRow(17).getCell(12).getCellFormula()).isEqualTo("SUM(M5:M17)");
            assertThat(sheet.getRow(15).getCell(12).getCellFormula()).isEqualTo("K16*L16");
            // Logo 在明细行之上，位置不变
            org.apache.poi.xssf.usermodel.XSSFClientAnchor logo = (org.apache.poi.xssf.usermodel.XSSFClientAnchor)
                    sheet.getDrawingPatriarch().getShapes().get(0).getAnchor();
            assertThat(logo.getRow1()).isEqualTo(1);
            // 每行的 C:E 合并都在
            long itemMerges = sheet.getMergedRegions().stream()
                    .filter(m -> m.getFirstColumn() == 2 && m.getLastColumn() == 4 && m.getFirstRow() >= 4 && m.getFirstRow() <= 16).count();
            assertThat(itemMerges).isEqualTo(13);
            assertThat(sheet.getRepeatingRows().formatAsString()).isEqualTo("4:4");
        }
    }

    /** 30 行明细：PDF ≤ 500KB，图片 ≤ 800KB 且宽度 ≤ 1600px；本机没有 LibreOffice 时跳过 */
    @Test
    void pdfAndImageStayWithinSizeLimits() throws IOException {
        DocumentConverter converter = new DocumentConverter("", System.getProperty("java.io.tmpdir") + "/zhul-document-test");
        Assumptions.assumeTrue(converter.available(), "本机未安装 LibreOffice");
        byte[] xlsx = XlsxRenderer.render(builtin("quotation-v1.xlsx"), withItems(30));
        byte[] pdf = converter.toPdf(xlsx);
        assertThat(pdf.length).isLessThanOrEqualTo(500 * 1024);
        byte[] jpg = converter.toJpeg(pdf, 1600);
        assertThat(jpg.length).isLessThanOrEqualTo(800 * 1024);
        assertThat(javax.imageio.ImageIO.read(new ByteArrayInputStream(jpg)).getWidth()).isLessThanOrEqualTo(1600);
        String dir = System.getProperty("zhul.render.out");
        if (dir != null) {
            java.nio.file.Files.write(java.nio.file.Path.of(dir, "sample.xlsx"), xlsx);
            java.nio.file.Files.write(java.nio.file.Path.of(dir, "sample.pdf"), pdf);
            java.nio.file.Files.write(java.nio.file.Path.of(dir, "sample.jpg"), jpg);
        }
    }

    /** 80 行明细分 2 页，第 2 页重复表头，页码为 Page 2 / 2 */
    @Test
    void longQuotationPaginatesWithRepeatedHeader() throws IOException {
        DocumentConverter converter = new DocumentConverter("", System.getProperty("java.io.tmpdir") + "/zhul-document-test");
        Assumptions.assumeTrue(converter.available(), "本机未安装 LibreOffice");
        byte[] pdf = converter.toPdf(XlsxRenderer.render(builtin("quotation-v1.xlsx"), withItems(80)));
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setStartPage(2);
            stripper.setEndPage(2);
            String page2 = stripper.getText(doc);
            assertThat(page2).contains("Model", "Brand", "Page 2 / 2");
        }
    }

    @Test
    void converterUnavailableGivesFriendlyError() {
        DocumentConverter converter = new DocumentConverter("/nonexistent/soffice", System.getProperty("java.io.tmpdir"));
        assertThat(converter.available()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> converter.toPdf(new byte[] {1}))
                .isInstanceOf(com.zhul.erp.common.exception.BizException.class)
                .hasMessageContaining("PDF / 图片暂时无法生成")
                .extracting("errorCode").isEqualTo("CONVERTER_UNAVAILABLE");
    }

    private static RenderModel withItems(int n) {
        RenderModel sample = SampleData.quotation();
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> m = new LinkedHashMap<>(sample.items().get(i % 3));
            m.put("item.no", i + 1);
            items.add(m);
        }
        return new RenderModel(sample.header(), items, sample.fees());
    }

    private static String dump(XSSFSheet sheet) {
        org.apache.poi.ss.usermodel.DataFormatter fmt = new org.apache.poi.ss.usermodel.DataFormatter();
        org.apache.poi.ss.usermodel.FormulaEvaluator ev = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        StringBuilder sb = new StringBuilder();
        for (Row row : sheet) {
            row.forEach(c -> {
                String v = c.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA
                        ? String.valueOf(ev.evaluate(c).getNumberValue()) : fmt.formatCellValue(c);
                sb.append(v).append('|');
            });
            sb.append('\n');
        }
        return sb.toString();
    }
}

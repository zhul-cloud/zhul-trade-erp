package com.zhul.erp.modules.sales;

import com.zhul.erp.modules.document.constants.DocTypes;
import com.zhul.erp.modules.document.support.RenderModel;
import com.zhul.erp.modules.document.support.SampleData;
import com.zhul.erp.modules.document.support.TemplateProblem;
import com.zhul.erp.modules.document.support.XlsxRenderer;
import com.zhul.erp.modules.document.support.XlsxTemplateInspector;
import com.zhul.erp.modules.quotation.support.QuotationRenderModels;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.BankSnapshotDTO;
import com.zhul.erp.modules.sales.dto.PartyDTO;
import com.zhul.erp.modules.sales.entity.PiFeeDO;
import com.zhul.erp.modules.sales.entity.PiItemDO;
import com.zhul.erp.modules.sales.entity.PiVersionDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.support.PiCalculator;
import com.zhul.erp.modules.sales.support.PiRenderModels;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** spec document/document-template「PI 模版占位符」与 sales/proforma-invoice「PI 导出」：福唯 PI V2 渲染 */
class PiRenderTest {

    private static byte[] resource(String path) throws Exception {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** 12 个型号 + 运费 + 手续费 + 5% 折扣 */
    private static RenderModel model() {
        ProformaInvoiceDO pi = new ProformaInvoiceDO();
        pi.setPiNo("FWPI20261006001");
        pi.setCurrencyCode("USD");
        pi.setExchangeRate(new BigDecimal("7.15"));
        List<PiItemDO> items = new ArrayList<>();
        for (int k = 1; k <= 12; k++) {
            PiItemDO i = new PiItemDO();
            i.setModel(k == 12 ? "6ES7214-1AG40-0XB0 + 6ES7231-4HD32-0XB0" : "MODEL-" + k);
            i.setQuantity(k);
            i.setUnitPrice(new BigDecimal("10.50"));
            i.setCostPrice(new BigDecimal("50"));
            i.setHsCode("8537109" + (k % 10));
            i.setOriginCountry(k == 12 ? "Germany (re-packed in Netherlands)" : "Germany");
            i.setItemCondition(0);
            i.setLeadTime(0);
            PiCalculator.applyLine(i, pi.getExchangeRate());
            items.add(i);
        }
        List<PiFeeDO> fees = new ArrayList<>();
        for (String[] f : new String[][] {{"Shipping Cost", "60"}, {"Bank Charge", "0"}}) {
            PiFeeDO fee = new PiFeeDO();
            fee.setFeeName(f[0]);
            fee.setAmount(new BigDecimal(f[1]));
            fees.add(fee);
        }
        PiVersionDO v = new PiVersionDO();
        v.setDiscountType(SalesConstants.DISCOUNT_PERCENT);
        v.setDiscountValue(new BigDecimal("5"));
        v.setPaymentTerm("T/T 100% in advance");
        PiCalculator.applyTotals(v, items, fees, pi.getExchangeRate());
        PartyDTO buyer = new PartyDTO();
        buyer.setName("ACROBOT TECHNOLOGIES PRIVATE LIMITED");
        buyer.setAddress("Plot 12, GIDC");
        buyer.setCity("Ahmedabad");
        buyer.setCountry("India");
        PartyDTO consignee = new PartyDTO();
        consignee.setName("ACROBOT Ahmedabad Warehouse");
        consignee.setCountry("India");
        BankSnapshotDTO bank = new BankSnapshotDTO();
        bank.setBankName("JPMorgan Chase Bank N.A., Singapore Branch");
        bank.setAccountNo("10141740757803");
        bank.setSwiftCode("CHASSGSGXXX");
        return PiRenderModels.build(pi, v, items, fees, buyer, consignee, bank, null, null,
                new QuotationRenderModels.Labels(Map.of(), Map.of(), Map.of(), Map.of()), java.time.LocalDate.of(2026, 10, 6));
    }

    @Test
    void fouwellV2_expandsItemsAndFees_totalFormula_imagesMoveDown_noCost() throws Exception {
        byte[] tpl = resource("document-template/fouwell-pi-v2.xlsx");
        List<Integer> before = pictureRows(tpl);
        byte[] out = XlsxRenderer.render(tpl, DocTypes.PI, model());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            XSSFFormulaEvaluator.evaluateAllFormulaCells(wb);
            DataFormatter fmt = new DataFormatter();
            // 明细 13~24 行，费用 25~27 行（运费、手续费、折扣），合计第 28 行
            assertThat(fmt.formatCellValue(sheet.getRow(12).getCell(2))).isEqualTo("MODEL-1");
            assertThat(fmt.formatCellValue(sheet.getRow(23).getCell(2))).startsWith("6ES7214-1AG40-0XB0 +");
            // 费用行序号接着型号行：运费 13、手续费 14、折扣 15
            assertThat(sheet.getRow(24).getCell(1).getNumericCellValue()).isEqualTo(13);
            assertThat(sheet.getRow(26).getCell(1).getNumericCellValue()).isEqualTo(15);
            // 原产国列（F）按最长内容加宽；型号列（C:E 合并）本来够宽，不变
            assertThat(sheet.getColumnWidth(5)).isGreaterThanOrEqualTo(("Germany (re-packed in Netherlands)".length() + 2) * 256);
            assertThat(sheet.getColumnWidth(2) + sheet.getColumnWidth(3) + sheet.getColumnWidth(4)).isEqualTo(templateWidth(2, 4));
            assertThat(fmt.formatCellValue(sheet.getRow(23).getCell(6))).isEqualTo("85371092");
            assertThat(sheet.getRow(24).getCell(2).getStringCellValue()).isEqualTo("Shipping Cost");
            assertThat(sheet.getRow(26).getCell(2).getStringCellValue()).isEqualTo("Discount");
            assertThat(sheet.getRow(26).getCell(9).getNumericCellValue()).isEqualTo(-40.95);
            assertThat(sheet.getRow(27).getCell(9).getCellFormula()).isEqualTo("SUM(J13:J27)");
            // 78 × 10.50 = 819.00；+60 −40.95 = 838.05
            assertThat(sheet.getRow(27).getCell(9).getNumericCellValue()).isEqualTo(838.05);
            assertThat(fmt.formatCellValue(sheet.getRow(6).getCell(3))).isEqualTo("Plot 12, GIDC, Ahmedabad, India");
            assertThat(fmt.formatCellValue(sheet.getRow(6).getCell(10))).isEqualTo("FWPI20261006001");
            assertThat(fmt.formatCellValue(sheet.getRow(19).getCell(7))).doesNotContain("${");
            StringBuilder all = new StringBuilder();
            for (Row r : sheet) {
                r.forEach(c -> all.append(fmt.formatCellValue(c)).append('|'));
            }
            assertThat(all.toString()).contains("10141740757803", "CHASSGSGXXX", "DESTINATION: India")
                    .doesNotContain("${", "Rev.", "50.00");
        }
        // 数字类占位符写成纯数字单元格，不残留内联文字（Excel / WPS 会显示残留的 ${item.no}）
        String xml = sheetXml(out);
        assertThat(xml).doesNotContain("${").doesNotContain("t=\"n\"><v>1.0</v><is>");
        // 公章、签名在明细之下：随 11 行明细 + 2 行费用下移 13 行；Logo 不动
        List<Integer> after = pictureRows(out);
        assertThat(after).containsExactly(before.get(0), before.get(1) + 13, before.get(2) + 13);
    }

    @Test
    void builtinV1AndSampleRender_unknownPlaceholderRejected() throws Exception {
        byte[] v1 = resource("document-template/pi-v1.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(v1))) {
            assertThat(XlsxTemplateInspector.inspect(wb, DocTypes.PI, true).problems()).isEmpty();
            // PI 占位符不能用在报价单模版里
            assertThat(XlsxTemplateInspector.inspect(wb, DocTypes.QUOTATION, true).problems()).isNotEmpty();
        }
        byte[] out = XlsxRenderer.render(v1, DocTypes.PI, SampleData.pi());
        assertThat(out.length).isGreaterThan(1000);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(v1))) {
            var cell = wb.getSheetAt(0).getRow(4).getCell(1);
            cell.setBlank();
            cell.setCellValue("Seller: ${seller.nmae}");
            List<TemplateProblem> problems = XlsxTemplateInspector.inspect(wb, DocTypes.PI, true).problems();
            assertThat(problems).hasSize(1);
            assertThat(problems.get(0).toString()).contains("B5", "不认识的占位符 ${seller.nmae}");
        }
    }

    private static int templateWidth(int from, int to) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(resource("document-template/fouwell-pi-v2.xlsx")))) {
            int w = 0;
            for (int c = from; c <= to; c++) {
                w += wb.getSheetAt(0).getColumnWidth(c);
            }
            return w;
        }
    }

    private static String sheetXml(byte[] xlsx) throws Exception {
        try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new ByteArrayInputStream(xlsx))) {
            for (var e = z.getNextEntry(); e != null; e = z.getNextEntry()) {
                if (e.getName().equals("xl/worksheets/sheet1.xml")) {
                    return new String(z.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalStateException("没有 sheet1.xml");
    }

    /** 图片起点行，从小到大 */
    private static List<Integer> pictureRows(byte[] xlsx) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            List<Integer> rows = new ArrayList<>();
            for (XSSFShape s : wb.getSheetAt(0).getDrawingPatriarch().getShapes()) {
                rows.add(((XSSFClientAnchor) s.getAnchor()).getRow1());
            }
            rows.sort(Integer::compareTo);
            return rows;
        }
    }
}

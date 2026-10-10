package com.zhul.erp.modules.sales;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** spec sales/proforma-invoice「议价测算」：导出与手工成本测算表格式一致的 Excel */
class PiBargainContractTest extends SalesContractSupport {

    @Test
    void exportBargainSheet_formatFormulasAndDiscount() throws Exception {
        long c = customer("Pinnacle Industrial Controls", "India");
        // 小计 USD 1,000：2 × 300（成本 1,500）+ 1 × 400（成本 2,400），成本合计 CNY 5,400
        long id = sentPi(quotationItemIds(quotation(c, 2, "USD", null, l("ASD-A2-2043-M", 2, "1500", "300"),
                l("ECMA-E11830RS", 1, "2400", "400"))), null);
        String piNo = jdbc.queryForObject("select pi_no from proforma_invoice where id = ?", String.class, id);

        MockHttpServletResponse res = perform(get(PI + "/" + id + "/bargain-export")
                .param("rate", "6.65").param("discountType", "2").param("discountValue", "50"), admin);
        assertEquals(200, res.getStatus());
        String disposition = URLDecoder.decode(res.getHeader("Content-Disposition"), StandardCharsets.UTF_8);
        assertTrue(disposition.contains("Pinnacle Industrial Controls_" + piNo + "_" + LocalDate.now() + ".xlsx"), disposition);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(res.getContentAsByteArray()))) {
            Sheet s = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            assertEquals("固定汇率：", s.getRow(0).getCell(0).getStringCellValue());
            assertEquals(6.65, s.getRow(0).getCell(1).getNumericCellValue(), 1e-9);
            assertEquals("型号", s.getRow(1).getCell(2).getStringCellValue());
            assertEquals("销售单价(USD)", s.getRow(1).getCell(6).getStringCellValue());
            assertEquals("D3*E3", s.getRow(2).getCell(5).getCellFormula());
            assertEquals("G3*$B$1", s.getRow(2).getCell(7).getCellFormula());
            assertEquals("Total:", s.getRow(4).getCell(2).getStringCellValue());
            assertEquals(332.50, s.getRow(4).getCell(11).getNumericCellValue(), 1e-9, "折扣 USD 50 × 6.65");
            XSSFFormulaEvaluator.evaluateAllFormulaCells(wb);
            assertEquals(5400, s.getRow(4).getCell(5).getNumericCellValue(), 1e-6, "成本合计");
            assertEquals(6650, s.getRow(4).getCell(8).getNumericCellValue(), 1e-6, "销售总价");
            assertEquals("14.52%", fmt.formatCellValue(s.getRow(4).getCell(13), wb.getCreationHelper().createFormulaEvaluator()), "让 50 后毛利率 14.5%（按让利后收入）");
        }

        // 没有采购成本价的 PI 不能测算
        long noCost = sentPi(quotationItemIds(quotation(c, 2, "USD", null, l("6ES7214", 1, "100", "50"))), null);
        jdbc.update("update proforma_invoice_item set cost_price = null where pi_id = ?", noCost);
        assertEquals("没有采购成本价的型号，无法测算",
                call(get(PI + "/" + noCost + "/bargain-export"), admin).path("message").asText());
    }
}

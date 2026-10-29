package com.zhul.erp.modules.inquiry.support;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 客户询盘表格按既有路径抽成文本，不需要 Spring 或数据库。 */
class SpreadsheetTextTest {

    @TempDir
    Path directory;

    @Test
    void extractsXlsModelsAndQuantity() throws Exception {
        try (Workbook workbook = new HSSFWorkbook()) {
            assertWorkbookText(workbook, "models.xls");
        }
    }

    @Test
    void extractsXlsxModelsAndQuantity() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            assertWorkbookText(workbook, "models.xlsx");
        }
    }

    @Test
    void preservesCsvTextAndChinese() throws Exception {
        String content = "型号,数量\n6ES7214-1AG40-0XB0,2\nE3Z-D61,10";
        Path file = Files.writeString(directory.resolve("models.csv"), content, StandardCharsets.UTF_8);
        assertThat(SpreadsheetText.extract(file)).isEqualTo(content);
    }

    private void assertWorkbookText(Workbook workbook, String name) throws Exception {
        Sheet sheet = workbook.createSheet("Models");
        sheet.createRow(0).createCell(0).setCellValue("型号");
        sheet.getRow(0).createCell(1).setCellValue("数量");
        sheet.createRow(1).createCell(0).setCellValue("6ES7214-1AG40-0XB0");
        sheet.getRow(1).createCell(1).setCellValue(2);
        sheet.createRow(2);  // 空行应被跳过
        Sheet second = workbook.createSheet("Sensors");
        second.createRow(0).createCell(0).setCellValue("E3Z-D61");
        second.getRow(0).createCell(1).setCellValue(10);
        Path file = directory.resolve(name);
        try (OutputStream output = Files.newOutputStream(file)) {
            workbook.write(output);
        }
        assertThat(SpreadsheetText.extract(file)).isEqualTo(
                "Sheet: Models\n型号\t数量\t\n6ES7214-1AG40-0XB0\t2\t\nSheet: Sensors\nE3Z-D61\t10");
    }
}

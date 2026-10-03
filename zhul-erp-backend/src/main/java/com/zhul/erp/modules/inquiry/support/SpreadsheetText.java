package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.common.exception.BizException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 把客户发来的 Excel / CSV 抽成纯文本交给 AI 解析：逐表逐行，单元格以制表符分隔，跳过空行 */
public final class SpreadsheetText {

    private static final int MAX_CHARS = 60_000;

    private SpreadsheetText() {
    }

    public static String extract(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        try {
            String text = name.endsWith(".csv") ? Files.readString(path, StandardCharsets.UTF_8) : workbook(path);
            return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
        } catch (IOException e) {
            throw new BizException("读取表格附件失败，请确认文件没有损坏", e);
        }
    }

    private static String workbook(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path); Workbook workbook = WorkbookFactory.create(in)) {
            DataFormatter formatter = new DataFormatter();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (sheet.getPhysicalNumberOfRows() == 0) {
                    continue;
                }
                sb.append("Sheet: ").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    StringBuilder line = new StringBuilder();
                    boolean hasContent = false;
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell).trim();
                        hasContent |= !value.isEmpty();
                        line.append(value).append('\t');
                    }
                    if (hasContent) {
                        sb.append(line).append('\n');
                    }
                }
            }
            return sb.toString().trim();
        }
    }
}

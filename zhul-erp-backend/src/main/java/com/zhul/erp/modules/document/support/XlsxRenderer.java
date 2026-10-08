package com.zhul.erp.modules.document.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.document.constants.DocTypes;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用 POI 按模版生成 Excel：表头占位符替换；明细行、费用行按实际行数展开（复制样式、行高、行内合并区域，
 * 行内公式按行号平移）；引用模版行的合计公式扩展到全部展开行；明细行之下的图片随之下移；表头行设为每页重复打印。
 */
public final class XlsxRenderer {

    /** 单元格引用：可选 $、列字母、可选 $、行号 */
    private static final Pattern CELL_REF = Pattern.compile("(?<![A-Za-z_$])(\\$?)([A-Z]{1,3})(\\$?)(\\d+)(?![\\d(])");
    /** 区域引用 A1:B2 */
    private static final Pattern AREA_REF = Pattern.compile("(\\$?[A-Z]{1,3}\\$?)(\\d+):(\\$?[A-Z]{1,3}\\$?)(\\d+)");

    private static final int MAX_COLUMN_CHARS = 60;

    private XlsxRenderer() {
    }

    public static byte[] render(byte[] template, RenderModel model) {
        return render(template, DocTypes.QUOTATION, model);
    }

    public static byte[] render(byte[] template, int docType, RenderModel model) {
        String typeName = DocTypes.NAMES.get(docType).split(" ")[0];
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            XlsxTemplateInspector.Layout layout = XlsxTemplateInspector.inspect(wb, docType, true);
            if (!layout.ok()) {
                throw new BizException(typeName + "模版有问题：" + layout.problems().get(0));
            }
            XSSFSheet sheet = wb.getSheetAt(0);
            int itemRow = layout.itemRow();
            int feeRow = layout.feeRow();
            List<Map<String, Object>> fees = numberFees(model);
            if (sheet.getRepeatingRows() == null && itemRow > 0) {
                sheet.setRepeatingRows(new CellRangeAddress(itemRow - 1, itemRow - 1, -1, -1));
            }
            // 先处理靠下的那一行，靠上那一行展开时会把它整体下移，引用随之更新
            if (feeRow > itemRow) {
                expand(sheet, feeRow, fees, model.header());
                expand(sheet, itemRow, model.items(), model.header());
            } else {
                expand(sheet, itemRow, model.items(), model.header());
                if (feeRow >= 0) {
                    expand(sheet, feeRow, fees, model.header());
                }
            }
            fillHeader(sheet, model.header());
            wb.setForceFormulaRecalculation(true);
            try {
                XSSFFormulaEvaluator.evaluateAllFormulaCells(wb);
            } catch (RuntimeException e) {
                // 管理员模版里有 POI 不支持的函数时，交给 Excel / LibreOffice 打开时再算
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(typeName + "模版无法打开，请检查是否为有效的 Excel 文件", e);
        }
    }

    /** 费用行的序号接着型号行往下编（如 9 个型号时运费为 10），对应占位符 ${fee.no} */
    private static List<Map<String, Object>> numberFees(RenderModel model) {
        List<Map<String, Object>> fees = new ArrayList<>(model.fees().size());
        int no = model.items().size();
        for (Map<String, Object> f : model.fees()) {
            Map<String, Object> copy = new LinkedHashMap<>(f);
            copy.put("fee.no", ++no);
            fees.add(copy);
        }
        return fees;
    }

    /** 把第 rowIdx 行展开成 rows.size() 行；没有数据时删除这一行 */
    static void expand(XSSFSheet sheet, int rowIdx, List<Map<String, Object>> rows, Map<String, Object> header) {
        int n = rows.size();
        if (n == 0) {
            removeRow(sheet, rowIdx);
            return;
        }
        if (n > 1) {
            int last = sheet.getLastRowNum();
            List<CellRangeAddress> rowMerges = mergesInRow(sheet, rowIdx);
            if (rowIdx < last) {
                sheet.shiftRows(rowIdx + 1, last, n - 1, true, false);
            }
            shiftPictures(sheet, rowIdx, n - 1);
            Row tpl = sheet.getRow(rowIdx);
            for (int k = 1; k < n; k++) {
                Row target = sheet.createRow(rowIdx + k);
                copyRow(tpl, target, rowIdx, rowIdx + k);
                for (CellRangeAddress m : rowMerges) {
                    sheet.addMergedRegion(new CellRangeAddress(rowIdx + k, rowIdx + k, m.getFirstColumn(), m.getLastColumn()));
                }
            }
            extendFormulas(sheet, rowIdx, rowIdx + n - 1);
        }
        for (int k = 0; k < n; k++) {
            Row row = sheet.getRow(rowIdx + k);
            boolean noStock = Boolean.TRUE.equals(rows.get(k).get(RenderModel.NO_STOCK));
            for (Cell cell : row) {
                // 无货行没有单价：行内公式（如 数量 × 单价）清空，避免 #VALUE!，合计求和时按空单元格处理
                if (noStock && cell.getCellType() == CellType.FORMULA) {
                    cell.setBlank();
                    continue;
                }
                fillCell(cell, rows.get(k), header);
            }
        }
        widenColumns(sheet, rowIdx, rowIdx + n - 1);
    }

    /**
     * 明细 / 费用行填完后，按内容估算显示宽度（中日韩字符按 2、其他按 1 个字符宽，留 2 个余量），
     * 列宽不够时加宽，单列最多 60 个字符宽；合并单元格按合并范围的总宽度判断，不够时加宽范围内最后一列。只加宽不缩窄。
     */
    static void widenColumns(Sheet sheet, int firstRow, int lastRow) {
        DataFormatter fmt = new DataFormatter();
        List<CellRangeAddress> merges = sheet.getMergedRegions();
        for (int r = firstRow; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.BLANK) {
                    continue;
                }
                String text = fmt.formatCellValue(cell);
                if (text.isEmpty() || text.contains("\n")) {
                    continue;
                }
                int first = cell.getColumnIndex();
                int last = first;
                for (CellRangeAddress m : merges) {
                    if (m.isInRange(cell)) {
                        first = m.getFirstColumn();
                        last = m.getLastColumn();
                        break;
                    }
                }
                int need = Math.min(displayWidth(text) + 2, MAX_COLUMN_CHARS) * 256;
                int have = 0;
                for (int c = first; c <= last; c++) {
                    have += sheet.getColumnWidth(c);
                }
                if (have < need) {
                    sheet.setColumnWidth(last, Math.min(sheet.getColumnWidth(last) + need - have, MAX_COLUMN_CHARS * 256));
                }
            }
        }
    }

    private static int displayWidth(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += text.charAt(i) > 0x2E80 ? 2 : 1;
        }
        return w;
    }

    private static void removeRow(XSSFSheet sheet, int rowIdx) {
        // 合计公式的区域若以这一行结尾，先收缩到上一行，避免删除后变成 #REF!
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.FORMULA) {
                    cell.setCellFormula(shrinkEnd(cell.getCellFormula(), rowIdx + 1));
                }
            }
        }
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            CellRangeAddress m = sheet.getMergedRegion(i);
            if (m.getFirstRow() == rowIdx && m.getLastRow() == rowIdx) {
                sheet.removeMergedRegion(i);
            }
        }
        Row row = sheet.getRow(rowIdx);
        if (row != null) {
            sheet.removeRow(row);
        }
        int last = sheet.getLastRowNum();
        if (rowIdx < last) {
            sheet.shiftRows(rowIdx + 1, last, -1, true, false);
            shiftPictures(sheet, rowIdx, -1);
        }
    }

    private static List<CellRangeAddress> mergesInRow(Sheet sheet, int rowIdx) {
        List<CellRangeAddress> list = new ArrayList<>();
        for (CellRangeAddress m : sheet.getMergedRegions()) {
            if (m.getFirstRow() == rowIdx && m.getLastRow() == rowIdx) {
                list.add(m);
            }
        }
        return list;
    }

    private static void copyRow(Row src, Row target, int srcIdx, int targetIdx) {
        target.setHeight(src.getHeight());
        for (Cell c : src) {
            Cell t = target.createCell(c.getColumnIndex());
            t.setCellStyle(c.getCellStyle());
            switch (c.getCellType()) {
                case STRING -> t.setCellValue(c.getStringCellValue());
                case NUMERIC -> t.setCellValue(c.getNumericCellValue());
                case BOOLEAN -> t.setCellValue(c.getBooleanCellValue());
                case FORMULA -> t.setCellFormula(moveRelativeRows(c.getCellFormula(), srcIdx + 1, targetIdx + 1));
                default -> {
                }
            }
        }
    }

    /** 行内公式（如 =I11*J11）复制到下一行时，相对引用的行号随之平移 */
    static String moveRelativeRows(String formula, int fromRow, int toRow) {
        Matcher m = CELL_REF.matcher(formula);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String rowAbs = m.group(3);
            int row = Integer.parseInt(m.group(4));
            String newRow = rowAbs.isEmpty() && row == fromRow ? String.valueOf(toRow) : m.group(4);
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + m.group(2) + rowAbs + newRow));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 模版行之外、以模版行结尾的区域（如 SUM(K11:K11)、SUM(J11:J12) 中的 J12）扩展到展开后的最后一行 */
    private static void extendFormulas(Sheet sheet, int tplRow, int lastRow) {
        for (Row row : sheet) {
            if (row.getRowNum() >= tplRow && row.getRowNum() <= lastRow) {
                continue;
            }
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.FORMULA) {
                    cell.setCellFormula(extendEnd(cell.getCellFormula(), tplRow + 1, lastRow + 1));
                }
            }
        }
    }

    static String extendEnd(String formula, int tplRow1, int lastRow1) {
        Matcher m = AREA_REF.matcher(formula);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int start = Integer.parseInt(m.group(2));
            int end = Integer.parseInt(m.group(4));
            String rep = m.group();
            if (end == tplRow1 && start <= tplRow1) {
                rep = m.group(1) + start + ":" + m.group(3) + lastRow1;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String shrinkEnd(String formula, int removedRow1) {
        Matcher m = AREA_REF.matcher(formula);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int start = Integer.parseInt(m.group(2));
            int end = Integer.parseInt(m.group(4));
            String rep = m.group();
            if (end == removedRow1 && start < removedRow1) {
                rep = m.group(1) + start + ":" + m.group(3) + (removedRow1 - 1);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** shiftRows 不会移动图片：锚点在 rowIdx 之下的图片按插入 / 删除的行数平移 */
    private static void shiftPictures(XSSFSheet sheet, int rowIdx, int delta) {
        XSSFDrawing drawing = sheet.getDrawingPatriarch();
        if (drawing == null) {
            return;
        }
        for (XSSFShape shape : drawing.getShapes()) {
            if (shape.getAnchor() instanceof XSSFClientAnchor a && a.getRow1() > rowIdx) {
                a.setRow1(a.getRow1() + delta);
                // 单格锚点（只有起点和尺寸，常见于公章、签名图片）没有终点
                if (a.getSize() == null) {
                    a.setRow2(a.getRow2() + delta);
                }
            }
        }
    }

    private static void fillHeader(Sheet sheet, Map<String, Object> header) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                fillCell(cell, header, header);
            }
        }
    }

    private static void fillCell(Cell cell, Map<String, Object> values, Map<String, Object> header) {
        if (cell.getCellType() != CellType.STRING) {
            return;
        }
        String text = cell.getStringCellValue();
        Matcher whole = Placeholders.TOKEN.matcher(text);
        if (whole.matches()) {
            String name = whole.group(1);
            Object v = values.containsKey(name) ? values.get(name) : header.get(name);
            // 先清空再写数字：模版里的内联文字单元格直接写数字会残留 <is> 原文，Excel / WPS 显示的是原文
            if (v instanceof BigDecimal b) {
                cell.setBlank();
                cell.setCellValue(b.doubleValue());
            } else if (v instanceof Integer i) {
                cell.setBlank();
                cell.setCellValue(i);
            } else if (values.containsKey(name) || header.containsKey(name)) {
                setText(cell, RenderModel.text(v));
            }
            return;
        }
        if (text.contains("${")) {
            String filled = TextTemplateEngine.fill(TextTemplateEngine.fill(text, values), header);
            if (!filled.equals(text)) {
                setText(cell, filled);
            }
        }
    }

    /** 先清空再写：内联字符串（inlineStr）单元格直接 setCellValue 时 POI 仍读写旧的内联值 */
    private static void setText(Cell cell, String text) {
        cell.setBlank();
        cell.setCellValue(text);
    }
}

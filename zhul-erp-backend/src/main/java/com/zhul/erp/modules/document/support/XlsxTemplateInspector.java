package com.zhul.erp.modules.document.support;

import com.zhul.erp.modules.document.constants.DocTypes;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * 扫描 xlsx 模版：找出明细行（含 ${item.…}）、费用行（含 ${fee.…}），并校验占位符。
 * 只看第一个工作表（单据只渲染第一个工作表）。
 */
public final class XlsxTemplateInspector {

    /** 扫描结果；行号从 0 开始，没有时为 -1 */
    public record Layout(int itemRow, int feeRow, List<TemplateProblem> problems) {
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    private XlsxTemplateInspector() {
    }

    public static Layout inspect(Workbook wb, boolean requireItems) {
        return inspect(wb, DocTypes.QUOTATION, requireItems);
    }

    public static Layout inspect(Workbook wb, int docType, boolean requireItems) {
        Sheet sheet = wb.getSheetAt(0);
        List<TemplateProblem> problems = new ArrayList<>();
        List<Integer> itemRows = new ArrayList<>();
        List<Integer> feeRows = new ArrayList<>();
        for (Row row : sheet) {
            boolean hasItem = false;
            boolean hasFee = false;
            for (Cell cell : row) {
                if (cell.getCellType() != CellType.STRING) {
                    continue;
                }
                Matcher m = Placeholders.TOKEN.matcher(cell.getStringCellValue());
                while (m.find()) {
                    String name = m.group(1);
                    String at = new CellReference(cell).formatAsString(false);
                    if (!Placeholders.known(docType, name)) {
                        problems.add(new TemplateProblem(at, "不认识的占位符 ${" + name + "}"));
                    } else if (name.startsWith(Placeholders.ITEM_PREFIX) || name.startsWith(Placeholders.BOX_PREFIX)) {
                        hasItem = true;
                    } else if (name.startsWith(Placeholders.FEE_PREFIX)) {
                        hasFee = true;
                    }
                }
            }
            if (hasItem) {
                itemRows.add(row.getRowNum());
            }
            if (hasFee) {
                feeRows.add(row.getRowNum());
            }
        }
        if (requireItems && itemRows.isEmpty()) {
            problems.add(new TemplateProblem("", docType == DocTypes.PL ? "PL 模版缺少明细行占位符：请在明细行写上 ${item.model} 等占位符"
                    : "没有找到明细行：请在型号行写上 ${item.model} 等占位符"));
        }
        if (itemRows.size() > 1) {
            problems.add(new TemplateProblem("第 " + rows(itemRows) + " 行", "明细占位符只能放在同一行"));
        }
        if (feeRows.size() > 1) {
            problems.add(new TemplateProblem("第 " + rows(feeRows) + " 行", "费用占位符只能放在同一行"));
        }
        if (!itemRows.isEmpty() && !feeRows.isEmpty() && itemRows.get(0).equals(feeRows.get(0))) {
            problems.add(new TemplateProblem("第 " + (itemRows.get(0) + 1) + " 行", "明细和费用不能放在同一行"));
        }
        return new Layout(itemRows.isEmpty() ? -1 : itemRows.get(0), feeRows.isEmpty() ? -1 : feeRows.get(0), problems);
    }

    /** 页眉页脚里是否有页码字段 &P */
    public static boolean hasPageNumber(Workbook wb) {
        Sheet sheet = wb.getSheetAt(0);
        StringBuilder sb = new StringBuilder();
        for (org.apache.poi.ss.usermodel.HeaderFooter hf : List.of(sheet.getHeader(), sheet.getFooter())) {
            sb.append(hf.getLeft()).append(hf.getCenter()).append(hf.getRight());
        }
        return sb.toString().contains("&P");
    }

    private static String rows(List<Integer> rows) {
        StringBuilder sb = new StringBuilder();
        for (int r : rows) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(r + 1);
        }
        return sb.toString();
    }
}

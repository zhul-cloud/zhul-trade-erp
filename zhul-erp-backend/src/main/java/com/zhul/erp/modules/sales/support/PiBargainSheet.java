package com.zhul.erp.modules.sales.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.sales.entity.PiItemDO;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * 议价测算表：沿用手工成本测算表的格式（04-成本核算/*.xlsx）——A1「固定汇率：」B1 汇率；第 2 行表头从 C 列开始；
 * 型号行与合计行写公式，合计行的「折扣」为测算让利折合 RMB（按收入比例分摊到有成本行的部分）。
 * 只列有采购成本价的型号，没有成本价的型号与费用不计入（与系统毛利口径一致）。
 */
public final class PiBargainSheet {

    private static final String[] HEADERS = {"型号", "数量", "成本单价(RMB)", "成本总价(RMB)", "销售单价(%s)", "销售单价(RMB)",
        "销售总价(RMB)", "总毛利", "让利", "折扣", "实际毛利", "实际毛利率"};
    private static final int[] WIDTHS = {24, 8, 14, 14, 14, 14, 14, 12, 10, 12, 12, 12};
    private static final int FIRST_COL = 2;

    private PiBargainSheet() {
    }

    /** items：PI 版本的型号行；discount：测算的整单折扣（原币）；rate：测算汇率 */
    public static byte[] build(List<PiItemDO> items, String currency, BigDecimal rate, BigDecimal discount) {
        List<PiItemDO> costed = items.stream().filter(i -> i.getCostPrice() != null).toList();
        if (costed.isEmpty()) {
            throw new BizException("没有采购成本价的型号，无法测算");
        }
        BigDecimal itemAmount = items.stream().map(PiItemDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costedRevenue = costed.stream().map(PiItemDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountCny = itemAmount.signum() == 0 ? BigDecimal.ZERO
                : discount.multiply(costedRevenue, MathContext.DECIMAL128).divide(itemAmount, MathContext.DECIMAL128)
                .multiply(rate, MathContext.DECIMAL128).setScale(2, RoundingMode.HALF_UP);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            DataFormat fmt = wb.createDataFormat();
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            head.setBorderBottom(BorderStyle.THIN);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(fmt.getFormat("#,##0.00"));
            CellStyle moneyBold = wb.createCellStyle();
            moneyBold.cloneStyleFrom(money);
            moneyBold.setFont(bold);
            CellStyle percent = wb.createCellStyle();
            percent.setDataFormat(fmt.getFormat("0.00%"));
            CellStyle percentBold = wb.createCellStyle();
            percentBold.cloneStyleFrom(percent);
            percentBold.setFont(bold);
            CellStyle rateStyle = wb.createCellStyle();
            rateStyle.setDataFormat(fmt.getFormat("0.000000"));

            Row r1 = sheet.createRow(0);
            r1.createCell(0).setCellValue("固定汇率：");
            Cell rc = r1.createCell(1);
            rc.setCellValue(rate.doubleValue());
            rc.setCellStyle(rateStyle);

            Row r2 = sheet.createRow(1);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = r2.createCell(FIRST_COL + i);
                c.setCellValue(String.format(HEADERS[i], currency));
                c.setCellStyle(head);
                sheet.setColumnWidth(FIRST_COL + i, WIDTHS[i] * 256);
            }
            int rowIdx = 2;
            for (PiItemDO i : costed) {
                int n = rowIdx + 1;
                Row r = sheet.createRow(rowIdx++);
                r.createCell(2).setCellValue(i.getModel());
                r.createCell(3).setCellValue(i.getQuantity());
                cell(r, 4, money).setCellValue(i.getCostPrice().doubleValue());
                cell(r, 5, money).setCellFormula("D" + n + "*E" + n);
                cell(r, 6, money).setCellValue(i.getUnitPrice().doubleValue());
                cell(r, 7, money).setCellFormula("G" + n + "*$B$1");
                cell(r, 8, money).setCellFormula("H" + n + "*D" + n);
                cell(r, 9, money).setCellFormula("I" + n + "-F" + n);
                cell(r, 12, money).setCellFormula("(I" + n + "+K" + n + ")-(F" + n + "+L" + n + ")");
                cell(r, 13, percent).setCellFormula("IF(I" + n + "=0,0,M" + n + "/I" + n + ")");
            }
            int last = rowIdx;
            int t = rowIdx + 1;
            Row total = sheet.createRow(rowIdx);
            Cell label = total.createCell(2);
            label.setCellValue("Total:");
            label.setCellStyle(head);
            cell(total, 5, moneyBold).setCellFormula("SUM(F3:F" + last + ")");
            cell(total, 8, moneyBold).setCellFormula("SUM(I3:I" + last + ")");
            cell(total, 9, moneyBold).setCellFormula("SUM(J3:J" + last + ")");
            cell(total, 10, moneyBold).setCellValue(0);
            cell(total, 11, moneyBold).setCellValue(discountCny.doubleValue());
            cell(total, 12, moneyBold).setCellFormula("(I" + t + "+K" + t + ")-(F" + t + "+L" + t + ")");
            // 实际毛利率按让利后的收入计算（与系统、议价测算一致；手工表原来除以让利前的销售总价）
            cell(total, 13, percentBold).setCellFormula("IF((I" + t + "+K" + t + "-L" + t + ")=0,0,M" + t + "/(I" + t + "+K" + t + "-L" + t + "))");
            if (costed.size() < items.size()) {
                sheet.createRow(rowIdx + 2).createCell(2)
                        .setCellValue("未计入没有采购成本价的型号 " + (items.size() - costed.size()) + " 个；运费、手续费不计入毛利");
            }
            wb.setForceFormulaRecalculation(true);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("议价测算表生成失败", e);
        }
    }

    private static Cell cell(Row r, int col, CellStyle style) {
        Cell c = r.createCell(col);
        c.setCellStyle(style);
        return c;
    }
}

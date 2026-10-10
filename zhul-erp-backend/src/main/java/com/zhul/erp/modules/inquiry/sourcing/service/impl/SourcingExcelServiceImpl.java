package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.storage.PrivateFileStorage;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.sourcing.dto.ImportConfirmFileRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.ImportConfirmRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.ImportConfirmRowRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.ImportFileVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.ImportRowVO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingImportDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskAssigneeDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingImportMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskAssigneeMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskMapper;
import com.zhul.erp.modules.inquiry.sourcing.service.MyTaskService;
import com.zhul.erp.modules.inquiry.sourcing.service.SourcingExcelService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import com.zhul.erp.modules.inquiry.support.QuoteDictSnapshot;
import com.zhul.erp.modules.inquiry.support.QuoteDicts;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class SourcingExcelServiceImpl implements SourcingExcelService {

    static final String META_SHEET = "_meta";
    static final String META_MARK = "zhul-sourcing";
    static final String META_VERSION = "1";
    static final String SHEET_INQUIRY = "①询盘单";
    static final String SHEET_QUOTE = "②询价单";
    /** ②询价单里写型号标识的隐藏列（F 列） */
    static final int ID_COLUMN = 5;
    private static final int MAX_FILES = 20;
    private static final String LINE = "─────────────────────────────";
    private static final Pattern NO_STOCK = Pattern.compile("无货|没货|缺货|断货");
    private static final String[][] SECTIONS = {{"淘宝", "1"}, {"1688", "2"}, {"闲鱼", "3"}};
    private static final String[] GUIDE = {
        "注：左侧为模版，请按实际询价结果填写！", "", "各字段填写规范如下：", "",
        "【型号】", "- 第一行型号已填好；同一型号多条报价，在下面空行继续填，型号可留空", "",
        "【单价】", "- 填写人民币单价，默认不含税，只填数字", "- 店家只给含税价时写「含税」，如：113含税；税率不是 13% 时写明，如：113含税3%", "- 没有货在单价里写「无货」", "",
        "【货况】", "- 从下拉中选择：%s", "",
        "【货期】", "- 从下拉中选择：%s", "",
        "【店铺名称】", "- 填写平台的店铺全称，便于追溯", "",
        "【备注】", "- 在每个平台下方「备注：」后填写价格异常、MOQ 等情况", "",
        "请勿删除或修改隐藏的列和工作表，否则系统无法识别"};

    private final SourcingTaskMapper taskMapper;
    private final SourcingTaskAssigneeMapper assigneeMapper;
    private final SourcingImportMapper importMapper;
    private final MyTaskService myTaskService;
    private final MyTaskServiceImpl myTaskImpl;
    private final SourcingProgress progress;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final PrivateFileStorage fileStorage;
    private final QuoteDicts quoteDicts;

    // ---------------------------------------------------------------- 下载

    @Override
    public Download download(List<Long> taskIds, boolean proxy) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new BizException("请选择要下载的任务");
        }
        List<SourcingTaskDO> tasks = new ArrayList<>();
        for (Long id : taskIds.stream().distinct().toList()) {
            tasks.add(proxy ? downloadableTask(id) : myTaskService.myTask(id));
        }
        if (tasks.size() == 1) {
            SourcingTaskDO t = tasks.get(0);
            return new Download(fileName(t), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook(t));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            Map<String, Integer> used = new HashMap<>();
            for (SourcingTaskDO t : tasks) {
                String name = fileName(t);
                int n = used.merge(name, 1, Integer::sum);
                zip.putNextEntry(new ZipEntry(n == 1 ? name : name.replace(".xlsx", "（" + n + "）.xlsx")));
                zip.write(workbook(t));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException("打包询价包失败", e);
        }
        return new Download("询价包（" + tasks.size() + " 个任务）.zip", "application/zip", out.toByteArray());
    }

    /** 文件名沿用习惯：询盘单{字母} · 品牌 · 品类[ · 型号].xlsx */
    String fileName(SourcingTaskDO t) {
        String letter = t.getTaskCode().length() > 13 ? t.getTaskCode().substring(13) : "";
        StringBuilder sb = new StringBuilder("询盘单").append(letter).append(" · ").append(t.getBrand());
        if (StringUtils.hasText(t.getCategory())) {
            sb.append(" · ").append(t.getCategory());
        }
        List<InquiryItemDO> items = myTaskImpl.taskItems(t.getId());
        if (items.size() == 1) {
            sb.append(" · ").append(items.get(0).getConfirmedModel());
        }
        return sb.toString().replaceAll("[\\\\/:*?\"<>|]", "_") + ".xlsx";
    }

    byte[] workbook(SourcingTaskDO task) {
        List<InquiryItemDO> items = myTaskImpl.taskItems(task.getId());
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            inquirySheet(wb, task, items, bold);
            quoteSheet(wb, items, bold);
            metaSheet(wb, task, items);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成询价包失败", e);
        }
    }

    private void inquirySheet(Workbook wb, SourcingTaskDO task, List<InquiryItemDO> items, CellStyle bold) {
        Sheet s = wb.createSheet(SHEET_INQUIRY);
        s.setColumnWidth(0, 90 * 256);
        List<String> lines = new ArrayList<>();
        String letter = task.getTaskCode().length() > 13 ? task.getTaskCode().substring(13) : "";
        String title = "询盘单 " + letter + " · " + task.getBrand() + (StringUtils.hasText(task.getCategory()) ? " · " + task.getCategory() : "")
                + "（" + items.size() + "条）";
        lines.add(LINE);
        lines.add(title);
        lines.add(LINE);
        QuoteDictSnapshot.Dict lifecycles = quoteDicts.snapshot().lifecycles();
        for (InquiryItemDO i : items) {
            lines.add("型号：" + i.getConfirmedModel() + lifecycleSuffix(i, lifecycles));
            lines.add("数量：" + i.getQuantity() + (StringUtils.hasText(i.getUnit()) ? i.getUnit() : ""));
            if (StringUtils.hasText(i.getDescription())) {
                lines.add("描述：" + i.getDescription());
            }
            lines.add("状态：" + lifecycleLabel(i, lifecycles));
            lines.add("难度：" + difficultyLabel(i.getDifficulty()));
        }
        lines.add("");
        lines.add("💬 询价模版");
        for (InquiryItemDO i : items) {
            lines.add(StringUtils.hasText(i.getInquiryScript()) ? i.getInquiryScript() : MyTaskServiceImpl.defaultScript(i));
        }
        lines.add("");
        lines.add(LINE);
        lines.add("🔍 货源直达 · " + title);
        lines.add(LINE);
        for (InquiryItemDO i : items) {
            lines.add(i.getConfirmedModel() + lifecycleSuffix(i, lifecycles));
            int n = 1;
            for (String kw : myTaskImpl.keywords(i)) {
                String q = URLEncoder.encode(kw, StandardCharsets.UTF_8);
                lines.add(circled(n++) + " " + kw);
                lines.add("https://s.taobao.com/search?q=" + q);
                lines.add("https://s.1688.com/selloffer/offer_search.htm?keywords=" + q);
                lines.add("https://www.goofish.com/search?q=" + q);
            }
        }
        lines.add("↑ 按序打开，有结果即停止，继续询价；无结果换下一条。");
        for (int r = 0; r < lines.size(); r++) {
            Cell c = s.createRow(r).createCell(0);
            c.setCellValue(lines.get(r));
            if (r == 1) {
                c.setCellStyle(bold);
            }
        }
    }

    private void quoteSheet(Workbook wb, List<InquiryItemDO> items, CellStyle bold) {
        Sheet s = wb.createSheet(SHEET_QUOTE);
        int[] widths = {26, 12, 12, 14, 24};
        for (int i = 0; i < widths.length; i++) {
            s.setColumnWidth(i, widths[i] * 256);
        }
        s.setColumnWidth(6, 56 * 256);
        int r = 0;
        for (String[] section : SECTIONS) {
            Row head = s.createRow(r++);
            head.createCell(0).setCellValue("【" + section[0] + "】询价结果汇总");
            head.getCell(0).setCellStyle(bold);
            Row cols = s.createRow(r++);
            String[] names = {"型号", "单价", "货况", "货期", "店铺名称"};
            for (int i = 0; i < names.length; i++) {
                cols.createCell(i).setCellValue(names[i]);
                cols.getCell(i).setCellStyle(bold);
            }
            for (InquiryItemDO i : items) {
                Row first = s.createRow(r++);
                first.createCell(0).setCellValue(i.getConfirmedModel());
                first.createCell(ID_COLUMN).setCellValue(String.valueOf(i.getId()));
                Row extra = s.createRow(r++);
                extra.createCell(ID_COLUMN).setCellValue(String.valueOf(i.getId()));
            }
            s.createRow(r++).createCell(0).setCellValue("备注：");
            r++;
        }
        QuoteDictSnapshot dicts = quoteDicts.snapshot();
        String[] conditions = dicts.conditions().enabled().stream().map(dicts.conditions()::label).toArray(String[]::new);
        String[] leadTimes = dicts.leadTimes().enabled().stream().map(dicts.leadTimes()::label).toArray(String[]::new);
        dropdown(s, r, 2, conditions, "货况");
        dropdown(s, r, 3, leadTimes, "货期");
        for (int i = 0; i < GUIDE.length; i++) {
            Row row = s.getRow(i) != null ? s.getRow(i) : s.createRow(i);
            String text = GUIDE[i].contains("%s")
                    ? String.format(GUIDE[i], String.join(" / ", "【货况】".equals(GUIDE[i - 1]) ? conditions : leadTimes)) : GUIDE[i];
            row.createCell(6).setCellValue(text);
        }
        s.setColumnHidden(ID_COLUMN, true);
    }

    /** 货况、货期列限定只能从字典里选；字典为空时不加限制 */
    private static void dropdown(Sheet s, int lastRow, int col, String[] values, String name) {
        if (values.length == 0 || lastRow < 1) {
            return;
        }
        DataValidationHelper helper = s.getDataValidationHelper();
        DataValidation v = helper.createValidation(helper.createExplicitListConstraint(values),
                new CellRangeAddressList(0, lastRow - 1, col, col));
        v.setShowErrorBox(true);
        v.createErrorBox(name + "不在可选值内", "请从下拉中选择" + name + "，可选值由系统字典维护");
        s.addValidationData(v);
    }

    private void metaSheet(Workbook wb, SourcingTaskDO task, List<InquiryItemDO> items) {
        Sheet s = wb.createSheet(META_SHEET);
        String[][] head = {{META_MARK, META_VERSION}, {"tenant", String.valueOf(task.getTenantId())},
            {"task", String.valueOf(task.getId())}, {"code", task.getTaskCode()}};
        for (int r = 0; r < head.length; r++) {
            Row row = s.createRow(r);
            row.createCell(0).setCellValue(head[r][0]);
            row.createCell(1).setCellValue(head[r][1]);
        }
        for (int i = 0; i < items.size(); i++) {
            Row row = s.createRow(head.length + 1 + i);
            row.createCell(0).setCellValue(String.valueOf(items.get(i).getId()));
            row.createCell(1).setCellValue(items.get(i).getConfirmedModel());
        }
        s.protectSheet(UUID.randomUUID().toString());
        wb.setSheetVisibility(wb.getSheetIndex(s), SheetVisibility.VERY_HIDDEN);
    }

    /** 停产型号在型号行后提示；名称取字典，标记按码值固定（2-停产红色） */
    private static String lifecycleSuffix(InquiryItemDO i, QuoteDictSnapshot.Dict lifecycles) {
        if (i.getLifecycle() != null && i.getLifecycle() == InquiryConstants.LIFECYCLE_DISCONTINUED) {
            String name = lifecycleName(i.getLifecycle(), lifecycles);
            return StringUtils.hasText(i.getReplacementModel()) ? "（🔴 " + name + " → 替代：" + i.getReplacementModel() + "）" : "（🔴 " + name + "）";
        }
        return "";
    }

    private static String lifecycleLabel(InquiryItemDO i, QuoteDictSnapshot.Dict lifecycles) {
        int code = i.getLifecycle() == null ? InquiryConstants.LIFECYCLE_UNKNOWN : i.getLifecycle();
        String name = lifecycleName(code, lifecycles);
        return switch (code) {
            case InquiryConstants.LIFECYCLE_ACTIVE -> "🟢 " + name;
            case InquiryConstants.LIFECYCLE_DISCONTINUED -> StringUtils.hasText(i.getReplacementModel())
                    ? "🔴 " + name + " → 替代：" + i.getReplacementModel() : "🔴 " + name;
            default -> "⚪ " + name;
        };
    }

    /** 字典里没有该码值（被删或未初始化）时退回待查 */
    private static String lifecycleName(int code, QuoteDictSnapshot.Dict lifecycles) {
        String name = lifecycles.label(code);
        return StringUtils.hasText(name) ? name : "待查";
    }

    private static String difficultyLabel(Integer d) {
        if (d == null) {
            return "—";
        }
        return switch (d) {
            case 1 -> "🟢 简单";
            case 2 -> "🟡 中等";
            case 3 -> "🔴 困难";
            default -> "—";
        };
    }

    private static String circled(int n) {
        return n >= 1 && n <= 10 ? String.valueOf((char) ('①' + n - 1)) : n + ".";
    }

    // ---------------------------------------------------------------- 导入

    @Override
    public List<ImportFileVO> preview(List<MultipartFile> files, boolean proxy) {
        if (files == null || files.isEmpty()) {
            throw new BizException("请选择要导入的询价包");
        }
        if (files.size() > MAX_FILES) {
            throw new BizException("一次最多导入 " + MAX_FILES + " 个文件");
        }
        List<ImportFileVO> result = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            ImportFileVO vo = new ImportFileVO();
            vo.setFileName(file.getOriginalFilename());
            vo.setRows(List.of());
            try {
                PrivateFileStorage.StoredFile stored = fileStorage.store(InquiryConstants.IMPORT_MODULE, tenantId(), file,
                        InquiryConstants.IMPORT_EXTS, InquiryConstants.IMPORT_MAX_BYTES, "只能导入从系统下载的询价包（.xlsx）");
                vo.setFileKey(stored.fileKey());
                parseInto(fileStorage.resolveOwned(InquiryConstants.IMPORT_MODULE, stored.fileKey(), tenantId()), vo, proxy);
            } catch (BizException e) {
                vo.setRejectReason(e.getMessage());
            }
            result.add(vo);
        }
        return result;
    }

    void parseInto(Path path, ImportFileVO vo, boolean proxy) {
        try (InputStream in = Files.newInputStream(path); Workbook wb = new XSSFWorkbook(in)) {
            Long taskId = metaTaskId(wb);
            if (taskId == null) {
                throw new BizException("找不到任务编号，可能不是从系统下载的询价包");
            }
            SourcingTaskDO task = taskForImport(taskId, proxy);
            Long assignee = importAssignee(task, proxy);
            vo.setTaskId(task.getId());
            vo.setTaskCode(task.getTaskCode());
            vo.setBrand(task.getBrand());
            vo.setCategory(task.getCategory());
            vo.setAssigneeId(assignee);
            vo.setAssigneeName(lookups.userNames(List.of(assignee)).get(assignee));
            Sheet sheet = wb.getSheet(SHEET_QUOTE);
            if (sheet == null) {
                throw new BizException("找不到「②询价单」工作表");
            }
            vo.setRows(parseRows(sheet, myTaskImpl.taskItems(task.getId()), quoteDicts.snapshot()));
        } catch (IOException e) {
            throw new BizException("文件已损坏或不是 Excel 文件", e);
        }
    }

    private Long metaTaskId(Workbook wb) {
        Sheet meta = wb.getSheet(META_SHEET);
        DataFormatter f = new DataFormatter();
        if (meta == null || meta.getRow(0) == null || !META_MARK.equals(f.formatCellValue(meta.getRow(0).getCell(0)))) {
            return null;
        }
        String tenant = meta.getRow(1) == null ? "" : f.formatCellValue(meta.getRow(1).getCell(1));
        String task = meta.getRow(2) == null ? "" : f.formatCellValue(meta.getRow(2).getCell(1));
        if (!String.valueOf(tenantId()).equals(tenant) || !task.matches("\\d+")) {
            return null;
        }
        return Long.parseLong(task);
    }

    /** 逐分区读取已填写的行；单价为空且其余各列也为空的模板空行跳过 */
    List<ImportRowVO> parseRows(Sheet sheet, List<InquiryItemDO> items, QuoteDictSnapshot dicts) {
        Map<Long, InquiryItemDO> byId = new HashMap<>();
        Map<String, InquiryItemDO> byModel = new HashMap<>();
        for (InquiryItemDO i : items) {
            byId.put(i.getId(), i);
            byModel.putIfAbsent(i.getModelKey(), i);
        }
        DataFormatter f = new DataFormatter();
        List<ImportRowVO> rows = new ArrayList<>();
        Integer channel = null;
        String channelName = null;
        InquiryItemDO current = null;
        List<ImportRowVO> sectionRows = new ArrayList<>();
        Map<Long, Boolean> listedInSection = new LinkedHashMap<>();
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            String a = cell(f, row, 0);
            if (a.startsWith("【") && a.contains("】")) {
                channelName = a.substring(1, a.indexOf('】'));
                channel = channelOf(channelName);
                current = null;
                sectionRows = new ArrayList<>();
                listedInSection = new LinkedHashMap<>();
                continue;
            }
            if (channel == null || "型号".equals(a)) {
                continue;
            }
            if (a.startsWith("备注")) {
                String note = a.replaceFirst("^备注[:：]?", "").trim();
                applySectionNote(note, sectionRows, listedInSection, byId, channel, channelName, rows, r);
                channel = null;
                continue;
            }
            String idText = cell(f, row, ID_COLUMN);
            InquiryItemDO item = idText.matches("\\d+") ? byId.get(Long.parseLong(idText)) : null;
            if (item == null && StringUtils.hasText(a)) {
                item = byModel.get(PriceKeys.model(a));
            }
            if (item == null && !StringUtils.hasText(a)) {
                item = current;
            }
            if (item != null) {
                current = item;
                listedInSection.putIfAbsent(item.getId(), Boolean.TRUE);
            }
            String price = cell(f, row, 1);
            String condition = cell(f, row, 2);
            String lead = cell(f, row, 3);
            String shop = cell(f, row, 4);
            if (!StringUtils.hasText(price) && !StringUtils.hasText(condition) && !StringUtils.hasText(lead) && !StringUtils.hasText(shop)) {
                continue;
            }
            ImportRowVO vo = new ImportRowVO();
            vo.setPosition(channelName + " 区第 " + (r + 1) + " 行");
            vo.setChannel(channel);
            vo.setShopName(shop);
            vo.setRawLeadTime(lead);
            vo.setRawPrice(price);
            vo.setRawCondition(condition);
            vo.setNote("");
            List<String> problems = new ArrayList<>();
            if (item == null) {
                vo.setModel(a);
                problems.add("型号不在这个任务里");
            } else {
                vo.setItemId(item.getId());
                vo.setModel(item.getConfirmedModel());
            }
            if (NO_STOCK.matcher(price).find()) {
                vo.setNoStock(true);
            } else {
                vo.setNoStock(false);
                TaxedPrice tp = parseTaxedPrice(price);
                vo.setTaxIncluded(tp.taxIncluded());
                vo.setTaxRate(tp.taxIncluded() ? tp.rate() : null);
                if (tp.price() == null) {
                    problems.add(StringUtils.hasText(price) ? "单价不是数字" : "没有填单价");
                } else {
                    vo.setUnitPrice(tp.price());
                }
                if (tp.taxIncluded() && !dicts.taxRates().enabled().contains(tp.rate())) {
                    problems.add("税率不在可选值内");
                }
            }
            Integer cond = dicts.conditionOf(condition);
            if (cond == null) {
                problems.add("货况不在可选值内");
                vo.setItemCondition(0);
            } else {
                vo.setItemCondition(cond);
            }
            Integer leadTime = dicts.leadTimeOf(lead);
            if (leadTime == null) {
                problems.add("货期不在可选值内");
                vo.setLeadTime(0);
            } else {
                vo.setLeadTime(leadTime);
            }
            vo.setProblems(problems);
            rows.add(vo);
            sectionRows.add(vo);
        }
        return rows;
    }

    /** 分区备注写入该分区本次各行；分区里一条报价都没有、备注写着无货时，按无货记录列出的型号 */
    private static void applySectionNote(String note, List<ImportRowVO> sectionRows, Map<Long, Boolean> listed,
                                         Map<Long, InquiryItemDO> byId, Integer channel, String channelName,
                                         List<ImportRowVO> rows, int r) {
        if (!StringUtils.hasText(note)) {
            return;
        }
        sectionRows.forEach(v -> v.setNote(note));
        if (sectionRows.isEmpty() && NO_STOCK.matcher(note).find()) {
            for (Long id : listed.keySet()) {
                ImportRowVO vo = new ImportRowVO();
                vo.setPosition(channelName + " 区第 " + (r + 1) + " 行（备注）");
                vo.setItemId(id);
                vo.setModel(byId.get(id).getConfirmedModel());
                vo.setChannel(channel);
                vo.setShopName("");
                vo.setRawPrice("");
                vo.setNoStock(true);
                vo.setItemCondition(0);
                vo.setRawCondition("");
                vo.setLeadTime(0);
                vo.setRawLeadTime("");
                vo.setNote(note);
                vo.setProblems(List.of());
                rows.add(vo);
            }
        }
    }

    record TaxedPrice(BigDecimal price, boolean taxIncluded, Integer rate) {
    }

    /** 依次匹配：「含税3%」「含3%税」「含税」；更具体的写法放前面，避免税率被漏掉 */
    static final Pattern TAXED = Pattern.compile("含税\\s*(\\d{1,2})\\s*%|含\\s*(\\d{1,2})\\s*%?\\s*税|含税");

    /**
     * 单价单元格可以写含税价：「113含税」「含税113」「113 含税3%」「113（含3%税）」。
     * 识别出含税时税率取文字里的百分数，没写按 13%；去掉含税字样后再按普通单价解析。
     */
    static TaxedPrice parseTaxedPrice(String raw) {
        String s = raw == null ? "" : raw;
        Matcher m = TAXED.matcher(s);
        if (!m.find()) {
            return new TaxedPrice(parsePrice(s), false, null);
        }
        String rate = m.group(1) != null ? m.group(1) : m.group(2);
        Integer r = rate == null ? InquiryConstants.DEFAULT_TAX_RATE : (int) Math.round(Double.parseDouble(rate));
        String rest = (s.substring(0, m.start()) + s.substring(m.end())).replaceAll("[()（）]", "");
        return new TaxedPrice(parsePrice(rest), true, r);
    }

    static BigDecimal parsePrice(String raw) {
        String s = raw == null ? "" : raw.replaceAll("[¥￥元,，\\s]", "");
        if (!s.matches("\\d+(\\.\\d+)?")) {
            return null;
        }
        return new BigDecimal(s);
    }

    private static Integer channelOf(String name) {
        for (String[] s : SECTIONS) {
            if (s[0].equals(name)) {
                return Integer.parseInt(s[1]);
            }
        }
        return InquiryConstants.CHANNEL_OTHER;
    }

    private static String cell(DataFormatter f, Row row, int col) {
        if (row == null) {
            return "";
        }
        Cell c = row.getCell(col);
        return c == null ? "" : f.formatCellValue(c).trim();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void confirm(ImportConfirmRequest req, boolean proxy) {
        Long me = currentUser.resolve();
        for (ImportConfirmFileRequest file : req.getFiles()) {
            SourcingTaskDO task = taskForImport(file.getTaskId(), proxy);
            progress.lock(task.getCustomerInquiryId());
            Long assignee = importAssignee(task, proxy);
            SourcingImportDO record = new SourcingImportDO();
            record.setTenantId(task.getTenantId());
            record.setTaskId(task.getId());
            record.setFileName(file.getFileName() == null ? "" : file.getFileName());
            String key = file.getFileKey() == null ? "" : file.getFileKey();
            if (StringUtils.hasText(key)) {
                fileStorage.resolveOwned(InquiryConstants.IMPORT_MODULE, key, task.getTenantId());
            }
            record.setFileKey(key);
            record.setImportedBy(me == null ? 0L : me);
            record.setOnBehalfOf(assignee);
            record.setRowCount(file.getRows().size());
            importMapper.insert(record);
            List<MyTaskService.QuoteDraft> drafts = new ArrayList<>(file.getRows().size());
            for (ImportConfirmRowRequest row : file.getRows()) {
                boolean noStock = Boolean.TRUE.equals(row.getNoStock());
                drafts.add(new MyTaskService.QuoteDraft(row.getItemId(), noStock, row.getChannel(), row.getShopName(), null,
                        noStock ? null : row.getUnitPrice(), !noStock && Boolean.TRUE.equals(row.getTaxIncluded()), row.getTaxRate(),
                        row.getItemCondition(), row.getLeadTime(), row.getNote(), false));
            }
            myTaskService.writeQuotes(task, assignee, drafts, true, InquiryConstants.ENTRY_IMPORT, record.getId());
            myTaskService.afterSubmit(task, assignee, file.getRows().stream().map(ImportConfirmRowRequest::getItemId).distinct().toList());
        }
    }

    /** 本人导入只能导入自己的任务；代他人导入可导入任何询价中或已回价的任务 */
    private SourcingTaskDO taskForImport(Long taskId, boolean proxy) {
        if (!proxy) {
            try {
                return myTaskService.myTask(taskId);
            } catch (BizException e) {
                throw new BizException("该询价任务未分配给你");
            }
        }
        return anyTask(taskId);
    }

    /** 代下载：待分配的任务也可以先下载，交给兼职采购；已取消的不行 */
    private SourcingTaskDO downloadableTask(Long taskId) {
        SourcingTaskDO task = taskMapper.selectById(taskId);
        if (task == null || task.getDeletedAt() != null || !Objects.equals(task.getTenantId(), tenantId())
                || task.getStatus() == InquiryConstants.TASK_CANCELLED) {
            throw new BizException("询价任务不存在或已取消");
        }
        return task;
    }

    private SourcingTaskDO anyTask(Long taskId) {
        SourcingTaskDO task = downloadableTask(taskId);
        if (task.getStatus() == InquiryConstants.TASK_UNASSIGNED) {
            throw new BizException("该任务还没有分配给采购，请先分配再导入");
        }
        return task;
    }

    /** 询价人：本人在该任务上有效时记本人，否则记该任务第一位有效采购 */
    private Long importAssignee(SourcingTaskDO task, boolean proxy) {
        Long me = currentUser.resolve();
        List<SourcingTaskAssigneeDO> active = assigneeMapper.selectList(new LambdaQueryWrapper<SourcingTaskAssigneeDO>()
                .eq(SourcingTaskAssigneeDO::getTaskId, task.getId())
                .eq(SourcingTaskAssigneeDO::getActive, 1)
                .isNull(SourcingTaskAssigneeDO::getDeletedAt)
                .orderByAsc(SourcingTaskAssigneeDO::getId));
        if (active.stream().anyMatch(a -> a.getAssigneeId().equals(me))) {
            return me;
        }
        if (!proxy || active.isEmpty()) {
            throw new BizException(proxy ? "该任务当前没有分配给任何人" : "该询价任务未分配给你");
        }
        return active.get(0).getAssigneeId();
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}

package com.zhul.erp.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.finance.dto.OrderCostVO;
import com.zhul.erp.modules.finance.service.OrderCostService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.dto.LedgerRowVO;
import com.zhul.erp.modules.inventory.dto.PeriodCogsVO;
import com.zhul.erp.modules.inventory.dto.PeriodVO;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.entity.InvCostAdjustDO;
import com.zhul.erp.modules.inventory.entity.InvCostAdjustItemDO;
import com.zhul.erp.modules.inventory.entity.InvMoveDO;
import com.zhul.erp.modules.inventory.entity.InvPeriodCogsDO;
import com.zhul.erp.modules.inventory.entity.InvPeriodDO;
import com.zhul.erp.modules.inventory.entity.InvPeriodLedgerDO;
import com.zhul.erp.modules.inventory.entity.InvWarehouseDO;
import com.zhul.erp.modules.inventory.repository.InvBatchMapper;
import com.zhul.erp.modules.inventory.repository.InvCostAdjustItemMapper;
import com.zhul.erp.modules.inventory.repository.InvCostAdjustMapper;
import com.zhul.erp.modules.inventory.repository.InvMoveMapper;
import com.zhul.erp.modules.inventory.repository.InvPeriodCogsMapper;
import com.zhul.erp.modules.inventory.repository.InvPeriodLedgerMapper;
import com.zhul.erp.modules.inventory.repository.InvPeriodMapper;
import com.zhul.erp.modules.inventory.repository.InvWarehouseMapper;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存月结（spec finance/period-close）：按月份结账、反结账，结账时保存库存台账与销售成本快照。
 * <ul>
 *   <li>月份范围：库存上线当月（最早一笔流水的业务日期）到上个月；只能结已过完的月份，并按顺序结；</li>
 *   <li>结账锁定由 {@link InventoryPeriods} 在过账层检查；成本调整总是记在调整当天所在月份，不受影响；</li>
 *   <li>快照：台账每个仓库 × 商品一行，另存全部仓库合计（warehouse_id = 0，调入调出抵消）；销售成本按订单型号行 ×
 *       出运单（出运结转）与成本调整单（当月的已出运部分调整）。反结账把快照软删除（保留记录）。</li>
 * </ul>
 * 事务边界：结账、反结账各一个事务（READ_COMMITTED），先锁住该月份的月结行，快照与状态一起提交。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryPeriodService {

    private static final List<String> SALES = List.of(InvConstants.BIZ_SALES_OUT, InvConstants.BIZ_SALES_OUT_UNDO);

    private final InvPeriodMapper periodMapper;
    private final InvPeriodLedgerMapper ledgerMapper;
    private final InvPeriodCogsMapper cogsMapper;
    private final InvMoveMapper moveMapper;
    private final InvBatchMapper batchMapper;
    private final InvCostAdjustMapper adjustMapper;
    private final InvCostAdjustItemMapper adjustItemMapper;
    private final InvWarehouseMapper warehouseMapper;
    private final SalesOrderItemMapper soItemMapper;
    private final SalesOrderMapper soMapper;
    private final InventoryQueryService queries;
    private final InventoryProducts products;
    private final OrderCostService orderCost;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;
    private final JdbcTemplate jdbc;

    // ---------------------------------------------------------------- 列表与详情

    /** 库存上线当月到上个月的各月份，月份倒序 */
    public List<PeriodVO> list() {
        YearMonth first = firstMonth();
        if (first == null) {
            return List.of();
        }
        YearMonth last = YearMonth.now().minusMonths(1);
        Map<String, InvPeriodDO> rows = periodMapper.selectList(scoped()).stream()
                .collect(Collectors.toMap(InvPeriodDO::getPeriodMonth, Function.identity(), (a, b) -> a));
        String latestClosed = latestClosed(rows.values());
        List<PeriodVO> out = new ArrayList<>();
        for (YearMonth m = last; !m.isBefore(first); m = m.minusMonths(1)) {
            out.add(toVo(m, rows.get(m.toString()), rows, latestClosed));
        }
        return out;
    }

    public PeriodVO detail(String month) {
        YearMonth m = parse(month);
        Map<String, InvPeriodDO> rows = periodMapper.selectList(scoped()).stream()
                .collect(Collectors.toMap(InvPeriodDO::getPeriodMonth, Function.identity(), (a, b) -> a));
        PeriodVO v = toVo(m, rows.get(m.toString()), rows, latestClosed(rows.values()));
        List<LedgerRowVO> ledger = queries.ledgerAll(m.toString());
        v.setOpeningAmount(ledger.stream().map(LedgerRowVO::getOpeningAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        v.setInAmount(ledger.stream().map(LedgerRowVO::getInAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        v.setOutAmount(ledger.stream().map(LedgerRowVO::getOutAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        if (!Objects.equals(v.getStatus(), InvConstants.PERIOD_CLOSED)) {
            v.setReminders(reminders(m));
        }
        return v;
    }

    private PeriodVO toVo(YearMonth m, InvPeriodDO p, Map<String, InvPeriodDO> rows, String latestClosed) {
        PeriodVO v = new PeriodVO();
        v.setMonth(m.toString());
        boolean closed = p != null && Objects.equals(p.getStatus(), InvConstants.PERIOD_CLOSED);
        v.setStatus(closed ? InvConstants.PERIOD_CLOSED : InvConstants.PERIOD_OPEN);
        v.setStatusName(closed ? "已结账" : "未结账");
        if (closed) {
            v.setClosingAmount(p.getClosingAmount());
            v.setCogsAmount(p.getCogsAmount());
            v.setAdjustAmount(p.getAdjustAmount());
        } else {
            Totals t = liveTotals(m);
            v.setClosingAmount(t.closing());
            v.setCogsAmount(t.cogs());
            v.setAdjustAmount(t.adjust());
        }
        if (p != null) {
            v.setClosedByName(closed ? p.getClosedByName() : "");
            v.setClosedAt(closed ? p.getClosedAt() : null);
            v.setReopenedByName(p.getReopenedByName());
            v.setReopenedAt(p.getReopenedAt());
            v.setReopenReason(p.getReopenReason());
        }
        String block = closed ? "已结账" : closeBlock(m, rows);
        v.setClosable(block == null);
        v.setCloseBlock(block);
        v.setReopenable(closed && m.toString().equals(latestClosed));
        return v;
    }

    /** 不能结账的原因；能结时为空 */
    private String closeBlock(YearMonth m, Map<String, InvPeriodDO> rows) {
        if (!m.isBefore(YearMonth.now())) {
            return m + " 还没过完";
        }
        YearMonth prev = m.minusMonths(1);
        YearMonth first = firstMonth();
        if (first != null && !prev.isBefore(first)) {
            InvPeriodDO p = rows.get(prev.toString());
            if (p == null || !Objects.equals(p.getStatus(), InvConstants.PERIOD_CLOSED)) {
                return "请先结 " + prev;
            }
        }
        return null;
    }

    private static String latestClosed(java.util.Collection<InvPeriodDO> rows) {
        return rows.stream().filter(p -> Objects.equals(p.getStatus(), InvConstants.PERIOD_CLOSED))
                .map(InvPeriodDO::getPeriodMonth).max(Comparator.naturalOrder()).orElse(null);
    }

    /** 月份的库存台账（已结账读快照） */
    public PageResult<LedgerRowVO> ledger(String month, com.zhul.erp.modules.inventory.dto.LedgerQuery q) {
        q.setMonth(parse(month).toString());
        return queries.ledger(q);
    }

    /** 月份的销售成本（已结账读快照）；关键词匹配订单号、型号、单号 */
    public PageResult<PeriodCogsVO> cogs(String month, String keyword, Integer page, Integer pageSize) {
        YearMonth m = parse(month);
        InvPeriodDO p = closedPeriod(m);
        List<InvPeriodCogsDO> rows = p != null ? cogsMapper.selectList(new LambdaQueryWrapper<InvPeriodCogsDO>()
                .eq(InvPeriodCogsDO::getTenantId, PiStore.tenantId())
                .eq(InvPeriodCogsDO::getPeriodId, p.getId())
                .isNull(InvPeriodCogsDO::getDeletedAt)
                .orderByAsc(InvPeriodCogsDO::getId)) : cogsRows(m);
        List<PeriodCogsVO> vos = toCogsVos(rows);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toLowerCase();
            vos = vos.stream().filter(v -> (v.getSoNo() + " " + v.getModel() + " " + v.getDocNo()).toLowerCase().contains(kw)).toList();
        }
        int size = pageSize == null || pageSize <= 0 ? 20 : pageSize;
        int pg = Math.max(1, page == null ? 1 : page);
        int from = Math.min(vos.size(), (pg - 1) * size);
        return PageResult.of((long) vos.size(), vos.subList(from, Math.min(vos.size(), from + size)));
    }

    /** 导出月份详情：库存台账（全部仓库合计）与销售成本两张表；已结账为快照 */
    public byte[] export(String month) {
        YearMonth m = parse(month);
        List<LedgerRowVO> ledger = queries.ledgerAll(m.toString());
        List<PeriodCogsVO> cogs = cogs(m.toString(), null, 1, Integer.MAX_VALUE).getRecords();
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet a = wb.createSheet("库存台账");
            row(a, 0, "品牌", "型号", "品类", "期初数量", "期初金额", "入库数量", "入库金额", "出库数量", "出库金额", "成本调整",
                    "期末数量", "期末金额");
            int r = 1;
            for (LedgerRowVO v : ledger) {
                row(a, r++, v.getBrand(), v.getModel(), v.getCategory(), v.getOpeningQty(), v.getOpeningAmount(), v.getInQty(),
                        v.getInAmount(), v.getOutQty(), v.getOutAmount(), v.getAdjustAmount(), v.getClosingQty(), v.getClosingAmount());
            }
            org.apache.poi.ss.usermodel.Sheet b = wb.createSheet("销售成本");
            row(b, 0, "类型", "订单号", "品牌", "型号", "单号", "日期", "数量", "其中系统外入库", "货款", "开票", "采购费用", "质量索赔",
                    "销售成本");
            r = 1;
            for (PeriodCogsVO v : cogs) {
                row(b, r++, v.getKindName(), v.getSoNo(), v.getBrand(), v.getModel(), v.getDocNo(), String.valueOf(v.getBizDate()),
                        v.getQuantity(), v.getSystemQty(), v.getAmountGoods(), v.getAmountInvoice(), v.getAmountFee(), v.getAmountClaim(),
                        v.getAmountCny());
            }
            wb.write(bos);
            return bos.toByteArray();
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException("库存月结导出失败", ex);
        }
    }

    private static void row(org.apache.poi.ss.usermodel.Sheet sheet, int index, Object... values) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            Object v = values[i];
            if (v instanceof Number n) {
                row.createCell(i).setCellValue(n.doubleValue());
            } else {
                row.createCell(i).setCellValue(v == null ? "" : v.toString());
            }
        }
    }

    // ---------------------------------------------------------------- 结账与反结账

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public PeriodVO close(String month) {
        YearMonth m = parse(month);
        InvPeriodDO p = lockOrCreate(m);
        if (Objects.equals(p.getStatus(), InvConstants.PERIOD_CLOSED)) {
            throw new BizException(m + " 已结账");
        }
        Map<String, InvPeriodDO> rows = periodMapper.selectList(scoped()).stream()
                .collect(Collectors.toMap(InvPeriodDO::getPeriodMonth, Function.identity(), (a, b) -> a));
        String block = closeBlock(m, rows);
        if (block != null) {
            throw new BizException(block);
        }
        int tenant = PiStore.tenantId();
        // 台账快照：全部仓库合计 + 各仓库
        BigDecimal closing = BigDecimal.ZERO.setScale(2);
        for (LedgerRowVO r : queries.liveLedgerAll(m, null)) {
            ledgerMapper.insert(ledgerRow(tenant, p, 0L, r));
            closing = closing.add(r.getClosingAmount());
        }
        for (InvWarehouseDO w : warehouseMapper.selectList(new LambdaQueryWrapper<InvWarehouseDO>()
                .eq(InvWarehouseDO::getTenantId, tenant))) {
            for (LedgerRowVO r : queries.liveLedgerAll(m, w.getId())) {
                ledgerMapper.insert(ledgerRow(tenant, p, w.getId(), r));
            }
        }
        BigDecimal cogs = BigDecimal.ZERO.setScale(2);
        for (InvPeriodCogsDO c : cogsRows(m)) {
            c.setPeriodId(p.getId());
            cogsMapper.insert(c);
            cogs = cogs.add(c.getAmountCny());
        }
        Long operator = currentUser.resolve();
        p.setStatus(InvConstants.PERIOD_CLOSED);
        p.setClosedBy(operator);
        p.setClosedByName(operatorName(operator));
        p.setClosedAt(LocalDateTime.now());
        p.setClosingAmount(closing);
        p.setCogsAmount(cogs);
        p.setAdjustAmount(adjustTotal(m));
        periodMapper.updateById(p);
        logService.recordOperateLog(InvConstants.MENU_PERIODS, "结账", null, Map.of("month", m.toString(),
                "closingAmount", closing, "cogsAmount", cogs, "adjustAmount", p.getAdjustAmount()));
        log.info("库存月结已结账，month={}, closingAmount={}, cogsAmount={}", m, closing, cogs);
        return detail(m.toString());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public PeriodVO reopen(String month, String reason) {
        YearMonth m = parse(month);
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw new BizException("请填写反结账原因");
        }
        InvPeriodDO p = lockOrCreate(m);
        if (!Objects.equals(p.getStatus(), InvConstants.PERIOD_CLOSED)) {
            throw new BizException(m + " 还没结账");
        }
        String latest = latestClosed(periodMapper.selectList(scoped()));
        if (!m.toString().equals(latest)) {
            throw new BizException("只能反结最近一个已结账月份（" + latest + "）");
        }
        LocalDateTime now = LocalDateTime.now();
        ledgerMapper.update(null, new LambdaUpdateWrapper<InvPeriodLedgerDO>()
                .eq(InvPeriodLedgerDO::getTenantId, PiStore.tenantId())
                .eq(InvPeriodLedgerDO::getPeriodId, p.getId())
                .isNull(InvPeriodLedgerDO::getDeletedAt)
                .set(InvPeriodLedgerDO::getDeletedAt, now)
                .set(InvPeriodLedgerDO::getUpdateTime, now));
        cogsMapper.update(null, new LambdaUpdateWrapper<InvPeriodCogsDO>()
                .eq(InvPeriodCogsDO::getTenantId, PiStore.tenantId())
                .eq(InvPeriodCogsDO::getPeriodId, p.getId())
                .isNull(InvPeriodCogsDO::getDeletedAt)
                .set(InvPeriodCogsDO::getDeletedAt, now)
                .set(InvPeriodCogsDO::getUpdateTime, now));
        Long operator = currentUser.resolve();
        p.setStatus(InvConstants.PERIOD_OPEN);
        p.setReopenedBy(operator);
        p.setReopenedByName(operatorName(operator));
        p.setReopenedAt(now);
        p.setReopenReason(why.length() > 200 ? why.substring(0, 200) : why);
        periodMapper.updateById(p);
        logService.recordOperateLog(InvConstants.MENU_PERIODS, "反结账", Map.of("month", m.toString(), "status", "已结账"),
                Map.of("month", m.toString(), "status", "未结账", "reason", p.getReopenReason()));
        log.info("库存月结已反结账，month={}, reason={}", m, p.getReopenReason());
        return detail(m.toString());
    }

    /** 锁住某月份的月结行（没有时新建一行未结账的） */
    private InvPeriodDO lockOrCreate(YearMonth m) {
        int tenant = PiStore.tenantId();
        InvPeriodDO p = periodMapper.selectOne(scoped().eq(InvPeriodDO::getPeriodMonth, m.toString()).last("FOR UPDATE"));
        if (p != null) {
            return p;
        }
        jdbc.update("INSERT IGNORE INTO inv_period (tenant_id, period_month, status) VALUES (?, ?, ?)", tenant, m.toString(),
                InvConstants.PERIOD_OPEN);
        return periodMapper.selectOne(scoped().eq(InvPeriodDO::getPeriodMonth, m.toString()).last("FOR UPDATE"));
    }

    private static InvPeriodLedgerDO ledgerRow(int tenant, InvPeriodDO p, long warehouseId, LedgerRowVO r) {
        InvPeriodLedgerDO x = new InvPeriodLedgerDO();
        x.setTenantId(tenant);
        x.setPeriodId(p.getId());
        x.setPeriodMonth(p.getPeriodMonth());
        x.setWarehouseId(warehouseId);
        x.setProductId(r.getProductId());
        x.setOpeningQty(r.getOpeningQty());
        x.setOpeningAmount(r.getOpeningAmount());
        x.setInQty(r.getInQty());
        x.setInAmount(r.getInAmount());
        x.setOutQty(r.getOutQty());
        x.setOutAmount(r.getOutAmount());
        x.setAdjustAmount(r.getAdjustAmount());
        x.setClosingQty(r.getClosingQty());
        x.setClosingAmount(r.getClosingAmount());
        x.setCurrencyCode("CNY");
        return x;
    }

    // ---------------------------------------------------------------- 计算

    private record Totals(BigDecimal closing, BigDecimal cogs, BigDecimal adjust) {
    }

    private Totals liveTotals(YearMonth m) {
        BigDecimal closing = jdbc.queryForObject("SELECT COALESCE(SUM(amount_cny), 0) FROM inv_move WHERE tenant_id = ?"
                + " AND op_type NOT IN (7, 8) AND biz_date < ?", BigDecimal.class, PiStore.tenantId(), java.sql.Date.valueOf(m.plusMonths(1).atDay(1)));
        BigDecimal cogs = cogsRows(m).stream().map(InvPeriodCogsDO::getAmountCny).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Totals(money(closing), money(cogs), adjustTotal(m));
    }

    /** 当月成本调整单合计（在库 + 销售成本 + 其他出库） */
    private BigDecimal adjustTotal(YearMonth m) {
        return money(adjustMapper.selectList(new LambdaQueryWrapper<InvCostAdjustDO>()
                        .eq(InvCostAdjustDO::getTenantId, PiStore.tenantId())
                        .eq(InvCostAdjustDO::getPeriodMonth, m.toString())
                        .isNull(InvCostAdjustDO::getDeletedAt)).stream()
                .map(a -> a.getStockAmount().add(a.getCogsAmount()).add(a.getOtherAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /**
     * 某月的销售成本明细（未保存）：业务日期在该月的销售出库流水（扣撤销）按型号行 × 出运单合计；
     * 所属月份为该月的成本调整单里已出运部分按型号行 × 调整单合计。金额为正数的成本，质量索赔为负数。
     */
    List<InvPeriodCogsDO> cogsRows(YearMonth m) {
        int tenant = PiStore.tenantId();
        List<InvMoveDO> moves = moveMapper.selectList(new LambdaQueryWrapper<InvMoveDO>()
                .eq(InvMoveDO::getTenantId, tenant)
                .eq(InvMoveDO::getTargetType, InvConstants.TARGET_SO_ITEM)
                .in(InvMoveDO::getBizType, SALES)
                .in(InvMoveDO::getOpType, List.of(InvConstants.OP_IN, InvConstants.OP_OUT))
                .ge(InvMoveDO::getBizDate, m.atDay(1))
                .lt(InvMoveDO::getBizDate, m.plusMonths(1).atDay(1))
                .orderByAsc(InvMoveDO::getBizDate, InvMoveDO::getId));
        Set<Long> system = moves.isEmpty() ? Set.of() : batchMapper.selectBatchIds(moves.stream().map(InvMoveDO::getBatchId).distinct().toList())
                .stream().filter(b -> Objects.equals(b.getSourceType(), InvConstants.BATCH_SYSTEM)).map(InvBatchDO::getId)
                .collect(Collectors.toSet());
        Map<String, InvPeriodCogsDO> out = new LinkedHashMap<>();
        for (InvMoveDO mv : moves) {
            InvPeriodCogsDO c = out.computeIfAbsent("S|" + mv.getTargetId() + "|" + mv.getDocId(), k -> cogsRow(tenant, m,
                    InvConstants.COGS_SHIPPED, mv.getTargetId(), mv.getProductId(), mv.getDocId(), mv.getDocNo(), mv.getBizDate()));
            int q = -mv.getQuantity();
            c.setQuantity(c.getQuantity() + q);
            if (system.contains(mv.getBatchId())) {
                c.setSystemQty(c.getSystemQty() + q);
            }
            c.setAmountGoods(c.getAmountGoods().subtract(nz(mv.getAmountGoods())));
            c.setAmountInvoice(c.getAmountInvoice().subtract(nz(mv.getAmountInvoice())));
            c.setAmountFee(c.getAmountFee().subtract(nz(mv.getAmountFee())));
            c.setAmountClaim(c.getAmountClaim().subtract(nz(mv.getAmountClaim())));
            c.setAmountCny(c.getAmountCny().subtract(nz(mv.getAmountCny())));
        }
        Map<Long, InvCostAdjustDO> adjusts = adjustMapper.selectList(new LambdaQueryWrapper<InvCostAdjustDO>()
                        .eq(InvCostAdjustDO::getTenantId, tenant)
                        .eq(InvCostAdjustDO::getPeriodMonth, m.toString())
                        .isNull(InvCostAdjustDO::getDeletedAt))
                .stream().collect(Collectors.toMap(InvCostAdjustDO::getId, Function.identity()));
        if (!adjusts.isEmpty()) {
            for (InvCostAdjustItemDO i : adjustItemMapper.selectList(new LambdaQueryWrapper<InvCostAdjustItemDO>()
                    .eq(InvCostAdjustItemDO::getTenantId, tenant)
                    .in(InvCostAdjustItemDO::getAdjustId, adjusts.keySet())
                    .eq(InvCostAdjustItemDO::getPart, InvConstants.PART_SHIPPED)
                    .isNotNull(InvCostAdjustItemDO::getSoItemId)
                    .isNull(InvCostAdjustItemDO::getDeletedAt)
                    .orderByAsc(InvCostAdjustItemDO::getId))) {
                InvCostAdjustDO a = adjusts.get(i.getAdjustId());
                InvPeriodCogsDO c = out.computeIfAbsent("A|" + i.getSoItemId() + "|" + a.getId(), k -> cogsRow(tenant, m,
                        InvConstants.COGS_ADJUST, i.getSoItemId(), i.getProductId(), a.getId(), a.getAdjustNo(), a.getAdjustDate()));
                c.setQuantity(c.getQuantity() + i.getQuantity());
                c.setAmountGoods(c.getAmountGoods().add(i.getAmountGoods()));
                c.setAmountInvoice(c.getAmountInvoice().add(i.getAmountInvoice()));
                c.setAmountFee(c.getAmountFee().add(i.getAmountFee()));
                c.setAmountClaim(c.getAmountClaim().add(i.getAmountClaim()));
                c.setAmountCny(c.getAmountCny().add(i.getAmountCny()));
            }
        }
        return out.values().stream().filter(c -> c.getQuantity() != 0 || c.getAmountCny().signum() != 0).toList();
    }

    private static InvPeriodCogsDO cogsRow(int tenant, YearMonth m, int kind, Long soItemId, Long productId, Long docId, String docNo,
                                           LocalDate date) {
        InvPeriodCogsDO c = new InvPeriodCogsDO();
        c.setTenantId(tenant);
        c.setPeriodMonth(m.toString());
        c.setKind(kind);
        c.setSoItemId(soItemId);
        c.setProductId(productId);
        c.setDocId(docId);
        c.setDocNo(Objects.requireNonNullElse(docNo, ""));
        c.setBizDate(date);
        c.setQuantity(0);
        c.setSystemQty(0);
        c.setCurrencyCode("CNY");
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        c.setAmountGoods(zero);
        c.setAmountInvoice(zero);
        c.setAmountFee(zero);
        c.setAmountClaim(zero);
        c.setAmountCny(zero);
        return c;
    }

    private List<PeriodCogsVO> toCogsVos(List<InvPeriodCogsDO> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, SalesOrderItemDO> soItems = soItemMapper.selectBatchIds(rows.stream().map(InvPeriodCogsDO::getSoItemId).distinct().toList())
                .stream().collect(Collectors.toMap(SalesOrderItemDO::getId, Function.identity()));
        List<Long> soIds = soItems.values().stream().map(SalesOrderItemDO::getSoId).distinct().toList();
        Map<Long, SalesOrderDO> sos = soIds.isEmpty() ? Map.of() : soMapper.selectBatchIds(soIds).stream()
                .collect(Collectors.toMap(SalesOrderDO::getId, Function.identity()));
        Map<Long, InventoryProducts.Label> labels = products.labels(rows.stream().map(InvPeriodCogsDO::getProductId).toList());
        return rows.stream().map(r -> {
            PeriodCogsVO v = new PeriodCogsVO();
            SalesOrderItemDO si = soItems.get(r.getSoItemId());
            SalesOrderDO so = si == null ? null : sos.get(si.getSoId());
            InventoryProducts.Label l = labels.getOrDefault(r.getProductId(), InventoryProducts.Label.NONE);
            v.setKind(r.getKind());
            v.setKindName(r.getKind() == InvConstants.COGS_SHIPPED ? "出运结转" : "成本调整");
            v.setSoId(so == null ? null : so.getId());
            v.setSoNo(so == null ? "" : so.getSoNo());
            v.setSoItemId(r.getSoItemId());
            v.setProductId(r.getProductId());
            v.setModel(l.model());
            v.setBrand(l.brand());
            v.setCategory(l.category());
            v.setDocType(r.getKind() == InvConstants.COGS_SHIPPED ? "SH" : "CA");
            v.setDocId(r.getDocId());
            v.setDocNo(r.getDocNo());
            v.setBizDate(r.getBizDate());
            v.setQuantity(r.getQuantity());
            v.setSystemQty(r.getSystemQty());
            v.setAmountGoods(r.getAmountGoods());
            v.setAmountInvoice(r.getAmountInvoice());
            v.setAmountFee(r.getAmountFee());
            v.setAmountClaim(r.getAmountClaim());
            v.setAmountCny(r.getAmountCny());
            return v;
        }).toList();
    }

    /**
     * 结账前的提醒（不阻止结账）：到该月底还没关联商品的有效采购入库行、退货入库行（不在库存里），
     * 当月有出运但成本还「未结算」的订单数。
     */
    List<String> reminders(YearMonth m) {
        int tenant = PiStore.tenantId();
        java.sql.Date end = java.sql.Date.valueOf(m.plusMonths(1).atDay(1));
        List<String> out = new ArrayList<>();
        Integer gr = jdbc.queryForObject("SELECT COUNT(*) FROM purchase_receipt_item i JOIN purchase_receipt r ON r.id = i.receipt_id"
                + " AND r.tenant_id = i.tenant_id WHERE i.tenant_id = ? AND r.status = 1 AND r.deleted_at IS NULL AND i.deleted_at IS NULL"
                + " AND i.product_id IS NULL AND r.received_date < ?", Integer.class, tenant, end);
        if (gr != null && gr > 0) {
            out.add(gr + " 条采购入库行还没关联商品，这些货不在库存里");
        }
        Integer rr = jdbc.queryForObject("SELECT COUNT(*) FROM return_receipt_item i JOIN return_receipt r ON r.id = i.receipt_id"
                + " AND r.tenant_id = i.tenant_id WHERE i.tenant_id = ? AND r.status IN (2, 3) AND r.deleted_at IS NULL"
                + " AND i.deleted_at IS NULL AND i.product_id IS NULL AND r.received_date < ?", Integer.class, tenant, end);
        if (rr != null && rr > 0) {
            out.add(rr + " 条退货入库行还没关联商品，这些货不在库存里");
        }
        List<Long> soItemIds = cogsRows(m).stream().map(InvPeriodCogsDO::getSoItemId).distinct().toList();
        if (!soItemIds.isEmpty()) {
            List<Long> soIds = soItemMapper.selectBatchIds(soItemIds).stream().map(SalesOrderItemDO::getSoId).distinct().toList();
            Map<Long, OrderCostVO> costs = soIds.isEmpty() ? Map.of() : orderCost.of(soMapper.selectBatchIds(soIds));
            long unsettled = costs.values().stream().filter(c -> !Boolean.TRUE.equals(c.getSettled())).count();
            if (unsettled > 0) {
                out.add(unsettled + " 张当月出运的订单成本还未结算（收款或运费没定），结账后变化记到之后的月份");
            }
        }
        int system = cogsRows(m).stream().mapToInt(InvPeriodCogsDO::getSystemQty).sum();
        if (system > 0) {
            out.add("当月出运里有 " + system + " 件来自系统外入库，成本为 0；订单成本按采购订单单价计");
        }
        return out;
    }

    // ---------------------------------------------------------------- 工具

    /** 库存上线当月：最早一笔流水的业务日期；没有流水时为空 */
    private YearMonth firstMonth() {
        LocalDate d = jdbc.queryForObject("SELECT MIN(biz_date) FROM inv_move WHERE tenant_id = ?", LocalDate.class, PiStore.tenantId());
        return d == null ? null : YearMonth.from(d);
    }

    private InvPeriodDO closedPeriod(YearMonth m) {
        return periodMapper.selectOne(scoped().eq(InvPeriodDO::getPeriodMonth, m.toString())
                .eq(InvPeriodDO::getStatus, InvConstants.PERIOD_CLOSED));
    }

    private LambdaQueryWrapper<InvPeriodDO> scoped() {
        return new LambdaQueryWrapper<InvPeriodDO>()
                .eq(InvPeriodDO::getTenantId, PiStore.tenantId())
                .isNull(InvPeriodDO::getDeletedAt);
    }

    private static YearMonth parse(String month) {
        try {
            return InventoryPeriods.parse(month == null ? "" : month.trim());
        } catch (DateTimeParseException e) {
            throw new BizException("月份格式应为 YYYY-MM", e);
        }
    }

    private String operatorName(Long operator) {
        return operator == null ? "系统" : Objects.requireNonNullElse(lookups.userNames(List.of(operator)).get(operator), "");
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

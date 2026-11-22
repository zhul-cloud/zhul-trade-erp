package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.finance.dto.SaveVoucherAccountsRequest;
import com.zhul.erp.modules.finance.dto.VoucherAccountVO;
import com.zhul.erp.modules.finance.dto.VoucherMonthDetailVO;
import com.zhul.erp.modules.finance.dto.VoucherMonthVO;
import com.zhul.erp.modules.finance.dto.VoucherSupplierVO;
import com.zhul.erp.modules.finance.entity.VoucherAccountDO;
import com.zhul.erp.modules.finance.entity.VoucherDO;
import com.zhul.erp.modules.finance.entity.VoucherEntryDO;
import com.zhul.erp.modules.finance.entity.VoucherMonthDO;
import com.zhul.erp.modules.finance.repository.VoucherAccountMapper;
import com.zhul.erp.modules.finance.repository.VoucherEntryMapper;
import com.zhul.erp.modules.finance.repository.VoucherMapper;
import com.zhul.erp.modules.finance.repository.VoucherMonthMapper;
import com.zhul.erp.modules.finance.support.VoucherRules;
import com.zhul.erp.modules.finance.support.VoucherRules.Action;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.dto.LedgerRowVO;
import com.zhul.erp.modules.inventory.entity.InvCostAdjustDO;
import com.zhul.erp.modules.inventory.entity.InvCostAdjustItemDO;
import com.zhul.erp.modules.inventory.entity.InvMoveDO;
import com.zhul.erp.modules.inventory.entity.InvPeriodDO;
import com.zhul.erp.modules.inventory.repository.InvCostAdjustItemMapper;
import com.zhul.erp.modules.inventory.repository.InvCostAdjustMapper;
import com.zhul.erp.modules.inventory.repository.InvMoveMapper;
import com.zhul.erp.modules.inventory.repository.InvPeriodMapper;
import com.zhul.erp.modules.inventory.service.InventoryQueryService;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 业务凭证（spec finance/business-voucher）：库存月结结账时按单据生成凭证，与库存账核对，按 T+ 凭证导入模板导出。
 * <ul>
 *   <li>一张凭证 = 一张来源单据的一次库存动作（流水按「单据类型 + 单据 + 业务类型 + 业务日期」分组），成本调整一张调整单一张；</li>
 *   <li>金额取当月库存流水与成本调整明细（月份结账后不再变化），只做加减，不再舍入；</li>
 *   <li>分录记业务项，T+ 科目编码在导出时按科目设置取，改科目不用重新生成。</li>
 * </ul>
 * 事务边界：生成、作废在库存月结的结账、反结账事务里执行（同一事务提交）；手动生成、导出、改设置各自一个事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherService {

    private static final String MENU = "/finance/vouchers";
    private static final String CNY = "CNY";
    private static final int ACTIVE = 1;
    private static final int VOIDED = 2;

    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper entryMapper;
    private final VoucherMonthMapper monthMapper;
    private final VoucherAccountMapper accountMapper;
    private final InvMoveMapper moveMapper;
    private final InvCostAdjustMapper adjustMapper;
    private final InvCostAdjustItemMapper adjustItemMapper;
    private final InvPeriodMapper periodMapper;
    private final InventoryQueryService inventoryQueries;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;
    private final JdbcTemplate jdbc;

    // ---------------------------------------------------------------- 生成与作废

    /** 结账时生成该月凭证（在结账事务内调用） */
    @Transactional(rollbackFor = Exception.class)
    public int generate(YearMonth m, Long periodId) {
        int tenant = PiStore.tenantId();
        List<Draft> drafts = build(m);
        Long operator = currentUser.resolve();
        VoucherMonthDO month = new VoucherMonthDO();
        month.setTenantId(tenant);
        month.setPeriodId(periodId);
        month.setPeriodMonth(m.toString());
        month.setGeneratedBy(operator);
        month.setGeneratedByName(userName(operator));
        month.setGeneratedAt(LocalDateTime.now());
        month.setExportCount(0);
        monthMapper.insert(month);
        for (Draft d : drafts) {
            VoucherDO v = new VoucherDO();
            v.setTenantId(tenant);
            v.setPeriodId(periodId);
            v.setPeriodMonth(m.toString());
            v.setCategory(d.category);
            v.setSourceDocType(d.docType);
            v.setSourceDocId(d.docId);
            v.setSourceDocNo(d.docNo);
            v.setBizType(d.bizType);
            v.setVoucherDate(d.date);
            v.setSummary(d.summary());
            v.setCurrencyCode(CNY);
            v.setDebitTotal(d.total(VoucherRules.DR));
            v.setCreditTotal(d.total(VoucherRules.CR));
            v.setStatus(ACTIVE);
            voucherMapper.insert(v);
            int no = 0;
            for (Map.Entry<LineKey, BigDecimal> e : d.lines().entrySet()) {
                VoucherEntryDO en = new VoucherEntryDO();
                en.setTenantId(tenant);
                en.setVoucherId(v.getId());
                en.setLineNo(++no);
                en.setItemKey(e.getKey().item());
                en.setPartnerType(e.getKey().partner() == null ? VoucherRules.PARTNER_NONE : VoucherRules.PARTNER_SUPPLIER);
                en.setPartnerId(e.getKey().partner());
                en.setDirection(e.getKey().side());
                en.setCurrencyCode(CNY);
                en.setAmount(e.getValue());
                entryMapper.insert(en);
            }
        }
        log.info("业务凭证已生成，month={}, count={}", m, drafts.size());
        return drafts.size();
    }

    /** 反结账时作废该月凭证（在反结账事务内调用），返回作废前是否导出过 */
    @Transactional(rollbackFor = Exception.class)
    public boolean voidMonth(YearMonth m) {
        int tenant = PiStore.tenantId();
        VoucherMonthDO month = activeMonth(m);
        if (month == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = activeVouchers(m).stream().map(VoucherDO::getId).toList();
        if (!ids.isEmpty()) {
            entryMapper.update(null, new LambdaUpdateWrapper<VoucherEntryDO>()
                    .eq(VoucherEntryDO::getTenantId, tenant)
                    .in(VoucherEntryDO::getVoucherId, ids)
                    .isNull(VoucherEntryDO::getDeletedAt)
                    .set(VoucherEntryDO::getDeletedAt, now)
                    .set(VoucherEntryDO::getUpdateTime, now));
            voucherMapper.update(null, new LambdaUpdateWrapper<VoucherDO>()
                    .eq(VoucherDO::getTenantId, tenant)
                    .in(VoucherDO::getId, ids)
                    .set(VoucherDO::getStatus, VOIDED)
                    .set(VoucherDO::getDeletedAt, now)
                    .set(VoucherDO::getUpdateTime, now));
        }
        monthMapper.update(null, new LambdaUpdateWrapper<VoucherMonthDO>()
                .eq(VoucherMonthDO::getTenantId, tenant)
                .eq(VoucherMonthDO::getId, month.getId())
                .set(VoucherMonthDO::getDeletedAt, now)
                .set(VoucherMonthDO::getUpdateTime, now));
        boolean exported = nz(month.getExportCount()) > 0;
        log.info("业务凭证已作废，month={}, count={}, exported={}", m, ids.size(), exported);
        return exported;
    }

    /** 该月凭证是否导出过（反结账确认框提示用） */
    public boolean exported(YearMonth m) {
        VoucherMonthDO month = activeMonth(m);
        return month != null && nz(month.getExportCount()) > 0;
    }

    /** 上线前已结账、还没有凭证的月份：手动生成 */
    @Transactional(rollbackFor = Exception.class)
    public VoucherMonthDetailVO generateManually(String month) {
        YearMonth m = parse(month);
        // 锁住月结行：与结账、反结账、并发的手动生成串行
        List<Long> locked = jdbc.queryForList("SELECT id FROM inv_period WHERE tenant_id = ? AND period_month = ? FOR UPDATE",
                Long.class, PiStore.tenantId(), m.toString());
        InvPeriodDO p = locked.isEmpty() ? null : periodMapper.selectById(locked.get(0));
        if (p == null || !Objects.equals(p.getStatus(), InvConstants.PERIOD_CLOSED)) {
            throw new BizException(m + " 还没结账，结账时会自动生成凭证");
        }
        if (activeMonth(m) != null) {
            throw new BizException(m + " 已经有凭证了");
        }
        int count = generate(m, p.getId());
        logService.recordOperateLog(MENU, "生成凭证", null, Map.of("month", m.toString(), "count", count));
        return detail(month);
    }

    // ---------------------------------------------------------------- 汇总规则

    private record LineKey(String item, Long partner, int side) {
    }

    /** 一张凭证的草稿：分录按「业务项 + 往来单位 + 方向」合并 */
    private static final class Draft {
        final int category;
        final String docType;
        final Long docId;
        final String docNo;
        final String bizType;
        final LocalDate date;
        String action;
        final Set<Long> partners = new LinkedHashSet<>();
        String partnerText = "";
        private final Map<LineKey, BigDecimal> lines = new LinkedHashMap<>();

        Draft(int category, String docType, Long docId, String docNo, String bizType, LocalDate date, String action) {
            this.category = category;
            this.docType = docType;
            this.docId = docId;
            this.docNo = docNo;
            this.bizType = bizType;
            this.date = date;
            this.action = action;
        }

        void add(String item, Long partner, int side, BigDecimal amount) {
            lines.merge(new LineKey(item, partner, side), amount, BigDecimal::add);
            if (partner != null) {
                partners.add(partner);
            }
        }

        /** 借方在前；合并后为 0 的行不列 */
        Map<LineKey, BigDecimal> lines() {
            Map<LineKey, BigDecimal> out = new LinkedHashMap<>();
            lines.entrySet().stream()
                    .filter(e -> e.getValue().signum() != 0)
                    .sorted(Comparator.comparingInt(e -> e.getKey().side()))
                    .forEach(e -> out.put(e.getKey(), e.getValue()));
            return out;
        }

        BigDecimal total(int side) {
            return lines.entrySet().stream().filter(e -> e.getKey().side() == side).map(Map.Entry::getValue)
                    .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        }

        String summary() {
            return (action + " " + docNo + (partnerText.isEmpty() ? "" : " " + partnerText)).trim();
        }
    }

    /** 按当月流水与成本调整明细生成凭证草稿，按日期、单号排序 */
    private List<Draft> build(YearMonth m) {
        int tenant = PiStore.tenantId();
        LocalDate start = m.atDay(1);
        LocalDate end = m.plusMonths(1).atDay(1);
        List<InvMoveDO> moves = moveMapper.selectList(new LambdaQueryWrapper<InvMoveDO>()
                .select(InvMoveDO::getId, InvMoveDO::getDocType, InvMoveDO::getDocId, InvMoveDO::getDocNo, InvMoveDO::getBizType,
                        InvMoveDO::getBizDate, InvMoveDO::getBatchId, InvMoveDO::getAmountCny)
                .eq(InvMoveDO::getTenantId, tenant)
                .ge(InvMoveDO::getBizDate, start)
                .lt(InvMoveDO::getBizDate, end)
                .in(InvMoveDO::getOpType, List.of(InvConstants.OP_IN, InvConstants.OP_OUT))
                .in(InvMoveDO::getBizType, VoucherRules.ACTIONS.keySet()));
        List<InvCostAdjustDO> adjusts = adjustMapper.selectList(new LambdaQueryWrapper<InvCostAdjustDO>()
                .eq(InvCostAdjustDO::getTenantId, tenant)
                .eq(InvCostAdjustDO::getPeriodMonth, m.toString())
                .isNull(InvCostAdjustDO::getDeletedAt));
        List<InvCostAdjustItemDO> adjustItems = adjusts.isEmpty() ? List.of()
                : adjustItemMapper.selectList(new LambdaQueryWrapper<InvCostAdjustItemDO>()
                .eq(InvCostAdjustItemDO::getTenantId, tenant)
                .in(InvCostAdjustItemDO::getAdjustId, adjusts.stream().map(InvCostAdjustDO::getId).toList())
                .isNull(InvCostAdjustItemDO::getDeletedAt));

        Set<Long> batchIds = new LinkedHashSet<>();
        Set<Long> returnIds = new LinkedHashSet<>();
        Set<Long> outboundIds = new LinkedHashSet<>();
        for (InvMoveDO mv : moves) {
            batchIds.add(mv.getBatchId());
            collectDoc(mv.getDocType(), mv.getDocId(), returnIds, outboundIds);
        }
        for (InvCostAdjustItemDO i : adjustItems) {
            batchIds.add(i.getBatchId());
            if (Objects.equals(i.getPart(), InvConstants.PART_OTHER)) {
                collectDoc(i.getDocType(), i.getDocId(), returnIds, outboundIds);
            }
        }
        Map<Long, Long> batchSupplier = batchSuppliers(batchIds);
        Map<Long, Long> returnSupplier = idMap("SELECT id, supplier_id FROM purchase_return WHERE tenant_id = ? AND id IN ", returnIds);
        Map<Long, Long> outboundType = idMap("SELECT id, outbound_type FROM inv_other_outbound WHERE tenant_id = ? AND id IN ",
                outboundIds);

        List<Draft> drafts = new ArrayList<>();
        // 库存动作：一张单据的一次动作一张
        Map<List<Object>, Draft> byAction = new LinkedHashMap<>();
        for (InvMoveDO mv : moves) {
            Action a = VoucherRules.ACTIONS.get(mv.getBizType());
            Draft d = byAction.computeIfAbsent(List.of(mv.getDocType(), mv.getDocId(), mv.getBizType(), mv.getBizDate()),
                    k -> new Draft(a.category(), mv.getDocType(), mv.getDocId(), mv.getDocNo(), mv.getBizType(), mv.getBizDate(),
                            a.action()));
            // 库存商品记在动作的常规一侧：借方侧金额 = 库存增加额，贷方侧金额 = 库存减少额；反向动作因此为负（红字）
            BigDecimal amount = a.stockSide() == VoucherRules.DR ? mv.getAmountCny() : mv.getAmountCny().negate();
            int counterSide = a.stockSide() == VoucherRules.DR ? VoucherRules.CR : VoucherRules.DR;
            String counter = a.counter();
            if (counter == null) {
                int type = outboundType.getOrDefault(mv.getDocId(), (long) InvConstants.OO_SCRAP).intValue();
                counter = VoucherRules.otherOut(type);
                d.action = a.action() + " · " + InvConstants.OO_TYPE_NAMES.getOrDefault(type, "");
            }
            Long partner = null;
            if (a.supplier()) {
                partner = "PR".equals(mv.getDocType()) ? returnSupplier.get(mv.getDocId()) : batchSupplier.get(mv.getBatchId());
            }
            d.add(VoucherRules.STOCK, null, a.stockSide(), amount);
            d.add(counter, partner, counterSide, amount);
        }
        drafts.addAll(byAction.values());
        // 成本调整：一张调整单一张
        Map<Long, List<InvCostAdjustItemDO>> itemsByAdjust = adjustItems.stream()
                .collect(Collectors.groupingBy(InvCostAdjustItemDO::getAdjustId, LinkedHashMap::new, Collectors.toList()));
        for (InvCostAdjustDO a : adjusts) {
            Draft d = new Draft(VoucherRules.CAT_ADJUST, "CA", a.getId(), a.getAdjustNo(), InvConstants.BIZ_COST_ADJUST,
                    a.getAdjustDate(), "成本调整");
            String offset = VoucherRules.adjOffset(a.getReason());
            for (InvCostAdjustItemDO i : itemsByAdjust.getOrDefault(a.getId(), List.of())) {
                BigDecimal amount = i.getAmountCny();
                if (Objects.equals(i.getPart(), InvConstants.PART_STOCK)) {
                    d.add(VoucherRules.STOCK, null, VoucherRules.DR, amount);
                } else if (Objects.equals(i.getPart(), InvConstants.PART_SHIPPED)) {
                    d.add(VoucherRules.COGS, null, VoucherRules.DR, amount);
                } else if ("PR".equals(i.getDocType())) {
                    d.add(VoucherRules.AP, returnSupplier.get(i.getDocId()), VoucherRules.DR, amount);
                } else {
                    int type = outboundType.getOrDefault(i.getDocId(), (long) InvConstants.OO_SCRAP).intValue();
                    d.add(VoucherRules.otherOut(type), null, VoucherRules.DR, amount);
                }
                d.add(offset, batchSupplier.get(i.getBatchId()), VoucherRules.CR, amount);
            }
            d.partnerText = InvConstants.CA_REASON_NAMES.getOrDefault(a.getReason(), "");
            drafts.add(d);
        }
        drafts.removeIf(d -> d.lines().isEmpty());
        // 摘要里的往来单位：一个供应商写名称，多个写「第一个等」
        Set<Long> partners = new LinkedHashSet<>();
        drafts.forEach(d -> partners.addAll(d.partners));
        Map<Long, String> names = supplierNames(partners);
        for (Draft d : drafts) {
            String supplier = d.partners.isEmpty() ? "" : names.getOrDefault(d.partners.iterator().next(), "")
                    + (d.partners.size() > 1 ? " 等" : "");
            d.partnerText = (d.partnerText + " " + supplier).trim();
            BigDecimal dr = d.total(VoucherRules.DR);
            BigDecimal cr = d.total(VoucherRules.CR);
            if (dr.compareTo(cr) != 0) {
                throw new IllegalStateException("业务凭证借贷不平：" + d.summary() + " 借 " + dr + " 贷 " + cr);
            }
        }
        drafts.sort(Comparator.comparing((Draft d) -> d.date).thenComparing(d -> d.docNo)
                .thenComparing(d -> d.bizType));
        return drafts;
    }

    private static void collectDoc(String docType, Long docId, Set<Long> returnIds, Set<Long> outboundIds) {
        if (docId == null) {
            return;
        }
        if ("PR".equals(docType)) {
            returnIds.add(docId);
        } else if ("OO".equals(docType)) {
            outboundIds.add(docId);
        }
    }

    /** 批次 → 采购订单的供应商（退货入库、系统外入库、期初批次没有） */
    private Map<Long, Long> batchSuppliers(Collection<Long> batchIds) {
        List<Long> ids = batchIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        int tenant = PiStore.tenantId();
        List<Object> args = new ArrayList<>(List.of(tenant, tenant, tenant));
        args.addAll(ids);
        Map<Long, Long> out = new HashMap<>();
        jdbc.query("SELECT b.id, po.supplier_id FROM inv_batch b"
                        + " JOIN purchase_order_item pi ON pi.id = b.po_item_id AND pi.tenant_id = ?"
                        + " JOIN purchase_order po ON po.id = pi.po_id AND po.tenant_id = ?"
                        + " WHERE b.tenant_id = ? AND b.id IN (" + marks(ids.size()) + ")",
                rs -> {
                    long s = rs.getLong(2);
                    if (!rs.wasNull() && s > 0) {
                        out.put(rs.getLong(1), s);
                    }
                }, args.toArray());
        return out;
    }

    private Map<Long, Long> idMap(String sqlPrefix, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Object> args = new ArrayList<>();
        args.add(PiStore.tenantId());
        args.addAll(ids);
        Map<Long, Long> out = new HashMap<>();
        jdbc.query(sqlPrefix + "(" + marks(ids.size()) + ")", rs -> {
            long v = rs.getLong(2);
            if (!rs.wasNull()) {
                out.put(rs.getLong(1), v);
            }
        }, args.toArray());
        return out;
    }

    private Map<Long, String> supplierNames(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        suppliers(ids).forEach(s -> out.put(s.getSupplierId(), s.getSupplierName()));
        return out;
    }

    private List<VoucherSupplierVO> suppliers(Collection<Long> ids) {
        List<Long> list = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (list.isEmpty()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        args.add(PiStore.tenantId());
        args.addAll(list);
        return jdbc.query("SELECT id, name, supplier_code, tplus_partner_code FROM supplier WHERE tenant_id = ? AND id IN ("
                + marks(list.size()) + ") ORDER BY name", (rs, i) -> {
            VoucherSupplierVO v = new VoucherSupplierVO();
            v.setSupplierId(rs.getLong("id"));
            v.setSupplierName(rs.getString("name"));
            v.setSupplierCode(rs.getString("supplier_code"));
            v.setPartnerCode(rs.getString("tplus_partner_code"));
            return v;
        }, args.toArray());
    }

    // ---------------------------------------------------------------- 月份列表与详情

    /** 已结账月份，月份倒序 */
    public List<VoucherMonthVO> months() {
        int tenant = PiStore.tenantId();
        List<InvPeriodDO> closed = periodMapper.selectList(new LambdaQueryWrapper<InvPeriodDO>()
                .eq(InvPeriodDO::getTenantId, tenant)
                .eq(InvPeriodDO::getStatus, InvConstants.PERIOD_CLOSED)
                .orderByDesc(InvPeriodDO::getPeriodMonth));
        Map<String, VoucherMonthDO> monthRows = monthMapper.selectList(new LambdaQueryWrapper<VoucherMonthDO>()
                        .eq(VoucherMonthDO::getTenantId, tenant)
                        .isNull(VoucherMonthDO::getDeletedAt))
                .stream().collect(Collectors.toMap(VoucherMonthDO::getPeriodMonth, Function.identity(), (a, b) -> a));
        Map<String, Object[]> stats = new HashMap<>();
        jdbc.query("SELECT period_month, COUNT(*), SUM(debit_total) FROM fin_voucher WHERE tenant_id = ? AND status = ?"
                        + " AND deleted_at IS NULL GROUP BY period_month",
                rs -> {
                    stats.put(rs.getString(1), new Object[]{rs.getInt(2), rs.getBigDecimal(3)});
                }, tenant, ACTIVE);
        List<VoucherMonthVO> out = new ArrayList<>();
        for (InvPeriodDO p : closed) {
            VoucherMonthVO v = new VoucherMonthVO();
            v.setMonth(p.getPeriodMonth());
            v.setCurrencyCode(CNY);
            VoucherMonthDO row = monthRows.get(p.getPeriodMonth());
            v.setGenerated(row != null);
            if (row != null) {
                Object[] s = stats.getOrDefault(p.getPeriodMonth(), new Object[]{0, BigDecimal.ZERO});
                v.setVoucherCount((Integer) s[0]);
                v.setDebitTotal(money((BigDecimal) s[1]));
                v.setReconDiff(recon(YearMonth.parse(p.getPeriodMonth())).getDiff());
                v.setExportCount(nz(row.getExportCount()));
                v.setExportedByName(row.getExportedByName());
                v.setExportedAt(row.getExportedAt());
            }
            out.add(v);
        }
        return out;
    }

    public VoucherMonthDetailVO detail(String month) {
        YearMonth m = parse(month);
        InvPeriodDO p = closedPeriod(m);
        VoucherMonthDO row = activeMonth(m);
        VoucherMonthDetailVO v = new VoucherMonthDetailVO();
        v.setMonth(m.toString());
        v.setCurrencyCode(CNY);
        v.setGenerated(row != null);
        v.setRecon(recon(m));
        v.setPeriodCogsAmount(money(p.getCogsAmount()));
        v.setCategoryCounts(new LinkedHashMap<>());
        v.setMissingItems(List.of());
        v.setVouchers(List.of());
        v.setDebitTotal(money(null));
        v.setReturnInAmount(money(null));
        if (row == null) {
            return v;
        }
        v.setGeneratedByName(row.getGeneratedByName());
        v.setGeneratedAt(row.getGeneratedAt());
        v.setExportCount(nz(row.getExportCount()));
        v.setExportedByName(row.getExportedByName());
        v.setExportedAt(row.getExportedAt());
        List<VoucherDO> vouchers = activeVouchers(m);
        Map<Long, List<VoucherEntryDO>> entries = entries(vouchers);
        Map<String, String[]> accounts = accountMap();
        Set<Long> partnerIds = new LinkedHashSet<>();
        entries.values().forEach(l -> l.forEach(e -> partnerIds.add(e.getPartnerId())));
        Map<Long, String> names = supplierNames(partnerIds);
        Set<String> missing = new LinkedHashSet<>();
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        BigDecimal debit = BigDecimal.ZERO.setScale(2);
        BigDecimal returnIn = BigDecimal.ZERO.setScale(2);
        List<VoucherMonthDetailVO.Voucher> list = new ArrayList<>();
        for (VoucherDO d : vouchers) {
            VoucherMonthDetailVO.Voucher x = new VoucherMonthDetailVO.Voucher();
            x.setId(d.getId());
            x.setVoucherDate(d.getVoucherDate());
            x.setCategory(d.getCategory());
            x.setCategoryName(VoucherRules.CATEGORY_NAMES.get(d.getCategory()));
            x.setSourceDocType(d.getSourceDocType());
            x.setSourceDocId(d.getSourceDocId());
            x.setSourceDocNo(d.getSourceDocNo());
            x.setBizType(d.getBizType());
            x.setSummary(d.getSummary());
            splitSummary(x);
            x.setDebitTotal(d.getDebitTotal());
            List<VoucherMonthDetailVO.Entry> es = new ArrayList<>();
            for (VoucherEntryDO e : entries.getOrDefault(d.getId(), List.of())) {
                VoucherMonthDetailVO.Entry y = new VoucherMonthDetailVO.Entry();
                y.setItemKey(e.getItemKey());
                y.setItemName(VoucherRules.itemLabel(e.getItemKey()));
                String[] acc = accounts.get(e.getItemKey());
                y.setAccountCode(acc == null ? "" : acc[0]);
                y.setAccountName(acc == null ? "" : acc[1]);
                if (!StringUtils.hasText(y.getAccountCode())) {
                    missing.add(y.getItemName());
                }
                y.setPartnerId(e.getPartnerId());
                y.setPartnerName(e.getPartnerId() == null ? null : names.get(e.getPartnerId()));
                if (e.getDirection() == VoucherRules.DR) {
                    y.setDebit(e.getAmount());
                } else {
                    y.setCredit(e.getAmount());
                }
                es.add(y);
            }
            x.setEntries(es);
            list.add(x);
            counts.merge(d.getCategory(), 1, Integer::sum);
            debit = debit.add(d.getDebitTotal());
            if (InvConstants.BIZ_RETURN_IN.equals(d.getBizType()) || InvConstants.BIZ_RETURN_IN_UNDO.equals(d.getBizType())) {
                returnIn = returnIn.add(d.getDebitTotal());
            }
        }
        v.setVouchers(list);
        v.setCategoryCounts(counts);
        v.setDebitTotal(debit);
        v.setReturnInAmount(returnIn);
        v.setMissingItems(new ArrayList<>(missing));
        return v;
    }

    /** 摘要 = 动作 单号 往来单位；拆开给前端把单号做成链接 */
    private static void splitSummary(VoucherMonthDetailVO.Voucher x) {
        String s = x.getSummary();
        String no = x.getSourceDocNo();
        int at = StringUtils.hasText(no) ? s.indexOf(" " + no) : -1;
        if (at < 0) {
            x.setAction(s);
            x.setPartnerText("");
            return;
        }
        x.setAction(s.substring(0, at));
        x.setPartnerText(s.substring(at + no.length() + 1).trim());
    }

    /** 期初 + 凭证里库存商品净额 + 不生成凭证的变动 = 期末；期初、期末取库存月结（全部仓库合计，已结账读快照） */
    VoucherMonthDetailVO.Recon recon(YearMonth m) {
        int tenant = PiStore.tenantId();
        List<LedgerRowVO> ledger = inventoryQueries.ledgerAll(m.toString());
        BigDecimal opening = ledger.stream().map(LedgerRowVO::getOpeningAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal closing = ledger.stream().map(LedgerRowVO::getClosingAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal stockNet = jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN e.direction = 1 THEN e.amount ELSE -e.amount END), 0)"
                        + " FROM fin_voucher_entry e JOIN fin_voucher v ON v.id = e.voucher_id AND v.tenant_id = ?"
                        + " WHERE e.tenant_id = ? AND e.deleted_at IS NULL AND e.item_key = ?"
                        + " AND v.period_month = ? AND v.status = ? AND v.deleted_at IS NULL",
                BigDecimal.class, tenant, tenant, VoucherRules.STOCK, m.toString(), ACTIVE);
        Map<String, BigDecimal> noVoucher = new LinkedHashMap<>();
        VoucherRules.NO_VOUCHER.keySet().stream().sorted().forEach(k -> noVoucher.put(k, BigDecimal.ZERO));
        List<Object> args = new ArrayList<>(List.of(tenant, java.sql.Date.valueOf(m.atDay(1)),
                java.sql.Date.valueOf(m.plusMonths(1).atDay(1))));
        args.addAll(VoucherRules.NO_VOUCHER.keySet());
        jdbc.query("SELECT biz_type, SUM(amount_cny) FROM inv_move WHERE tenant_id = ? AND biz_date >= ? AND biz_date < ?"
                        + " AND op_type NOT IN (7, 8) AND biz_type IN (" + marks(VoucherRules.NO_VOUCHER.size()) + ") GROUP BY biz_type",
                rs -> {
                    noVoucher.put(rs.getString(1), rs.getBigDecimal(2));
                }, args.toArray());
        VoucherMonthDetailVO.Recon r = new VoucherMonthDetailVO.Recon();
        r.setOpeningAmount(money(opening));
        r.setStockNetAmount(money(stockNet));
        r.setClosingAmount(money(closing));
        List<VoucherMonthDetailVO.NoVoucherItem> items = new ArrayList<>();
        BigDecimal nv = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : noVoucher.entrySet()) {
            VoucherMonthDetailVO.NoVoucherItem i = new VoucherMonthDetailVO.NoVoucherItem();
            i.setBizType(e.getKey());
            i.setName(VoucherRules.NO_VOUCHER.get(e.getKey()));
            i.setAmount(money(e.getValue()));
            items.add(i);
            nv = nv.add(i.getAmount());
        }
        r.setNoVoucherItems(items);
        r.setNoVoucherAmount(money(nv));
        r.setDiff(r.getOpeningAmount().add(r.getStockNetAmount()).add(r.getNoVoucherAmount()).subtract(r.getClosingAmount()));
        return r;
    }

    // ---------------------------------------------------------------- 科目设置

    public List<VoucherAccountVO> accounts() {
        Map<String, String[]> map = accountMap();
        List<VoucherAccountVO> out = new ArrayList<>();
        for (VoucherRules.Item i : VoucherRules.ITEMS) {
            VoucherAccountVO v = new VoucherAccountVO();
            v.setItemKey(i.key());
            v.setGroup(i.group());
            v.setItemName(i.name());
            String[] acc = map.get(i.key());
            v.setAccountCode(acc[0]);
            v.setAccountName(acc[1]);
            out.add(v);
        }
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<VoucherAccountVO> saveAccounts(SaveVoucherAccountsRequest req) {
        int tenant = PiStore.tenantId();
        Map<String, String[]> before = accountMap();
        Map<String, VoucherAccountDO> rows = accountMapper.selectList(new LambdaQueryWrapper<VoucherAccountDO>()
                        .eq(VoucherAccountDO::getTenantId, tenant))
                .stream().collect(Collectors.toMap(VoucherAccountDO::getItemKey, Function.identity(), (a, b) -> a));
        Map<String, String> changedBefore = new LinkedHashMap<>();
        Map<String, String> changedAfter = new LinkedHashMap<>();
        for (SaveVoucherAccountsRequest.Item i : req.getItems()) {
            if (VoucherRules.item(i.getItemKey()) == null) {
                throw new BizException("没有这个业务项：" + i.getItemKey());
            }
            String code = trim(i.getAccountCode());
            String name = trim(i.getAccountName());
            String[] old = before.get(i.getItemKey());
            if (old[0].equals(code) && old[1].equals(name)) {
                continue;
            }
            VoucherAccountDO row = rows.get(i.getItemKey());
            if (row == null) {
                row = new VoucherAccountDO();
                row.setTenantId(tenant);
                row.setItemKey(i.getItemKey());
                row.setStatus(1);
            }
            row.setAccountCode(code);
            row.setAccountName(name);
            if (row.getId() == null) {
                accountMapper.insert(row);
            } else {
                accountMapper.updateById(row);
            }
            String label = VoucherRules.itemLabel(i.getItemKey());
            changedBefore.put(label, (old[0] + " " + old[1]).trim());
            changedAfter.put(label, (code + " " + name).trim());
        }
        if (!changedAfter.isEmpty()) {
            logService.recordOperateLog(MENU, "科目设置", changedBefore, changedAfter);
        }
        return accounts();
    }

    /** 凭证里出现过的供应商（可按名称、编码搜索） */
    public List<VoucherSupplierVO> voucherSuppliers(String keyword) {
        int tenant = PiStore.tenantId();
        List<Long> ids = jdbc.queryForList("SELECT DISTINCT e.partner_id FROM fin_voucher_entry e"
                + " JOIN fin_voucher v ON v.id = e.voucher_id AND v.tenant_id = ? AND v.status = ? AND v.deleted_at IS NULL"
                + " WHERE e.tenant_id = ? AND e.partner_type = ? AND e.partner_id IS NOT NULL AND e.deleted_at IS NULL",
                Long.class, tenant, ACTIVE, tenant, VoucherRules.PARTNER_SUPPLIER);
        String k = trim(keyword).toLowerCase();
        return suppliers(ids).stream()
                .filter(s -> k.isEmpty() || nzs(s.getSupplierName()).toLowerCase().contains(k)
                        || nzs(s.getSupplierCode()).toLowerCase().contains(k))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public VoucherSupplierVO saveSupplierCode(Long supplierId, String partnerCode) {
        List<VoucherSupplierVO> found = suppliers(List.of(supplierId));
        if (found.isEmpty()) {
            throw new BizException("供应商不存在");
        }
        String code = trim(partnerCode);
        String old = nzs(found.get(0).getPartnerCode());
        jdbc.update("UPDATE supplier SET tplus_partner_code = ?, update_time = NOW() WHERE tenant_id = ? AND id = ?",
                code, PiStore.tenantId(), supplierId);
        logService.recordOperateLog(MENU, "设置往来单位编码", Map.of("supplier", found.get(0).getSupplierName(), "code", old),
                Map.of("supplier", found.get(0).getSupplierName(), "code", code));
        return suppliers(List.of(supplierId)).get(0);
    }

    /** 业务项 → {科目编码, 科目名称}：会计改过的取设置，没改过的取默认 */
    private Map<String, String[]> accountMap() {
        Map<String, String[]> out = new LinkedHashMap<>();
        VoucherRules.ITEMS.forEach(i -> out.put(i.key(), new String[]{i.defaultCode(), i.defaultName()}));
        accountMapper.selectList(new LambdaQueryWrapper<VoucherAccountDO>()
                        .eq(VoucherAccountDO::getTenantId, PiStore.tenantId()))
                .forEach(a -> out.put(a.getItemKey(), new String[]{nzs(a.getAccountCode()), nzs(a.getAccountName())}));
        return out;
    }

    // ---------------------------------------------------------------- 导出

    /**
     * 导出一个月份的全部凭证（T+ 凭证导入格式）。有业务项没设科目时不能导出；重复导出或核对不相符时需要 confirmed。
     */
    @Transactional(rollbackFor = Exception.class)
    public byte[] export(String month, boolean confirmed) {
        YearMonth m = parse(month);
        closedPeriod(m);
        VoucherMonthDO row = activeMonth(m);
        if (row == null) {
            throw new BizException(m + " 还没有凭证，请先生成凭证");
        }
        VoucherMonthDetailVO d = detail(month);
        if (!d.getMissingItems().isEmpty()) {
            throw BizException.of("VOUCHER_ACCOUNT_MISSING", "请先在科目设置里填写：" + String.join("、", d.getMissingItems()),
                    Map.of("items", d.getMissingItems()));
        }
        List<String> reasons = new ArrayList<>();
        if (nz(row.getExportCount()) > 0) {
            reasons.add("上次由 " + nzs(row.getExportedByName()) + " 在 " + row.getExportedAt().toString().replace('T', ' ')
                    .substring(0, 16) + " 导出，重复导入会在 T+ 里重复记账");
        }
        if (d.getRecon().getDiff().signum() != 0) {
            reasons.add("凭证与库存账核对差 " + d.getRecon().getDiff().toPlainString());
        }
        if (!reasons.isEmpty() && !confirmed) {
            throw BizException.of("VOUCHER_EXPORT_CONFIRM", String.join("；", reasons), Map.of("reasons", reasons));
        }
        byte[] file = tplusWorkbook(d);
        Long operator = currentUser.resolve();
        monthMapper.update(null, new LambdaUpdateWrapper<VoucherMonthDO>()
                .eq(VoucherMonthDO::getTenantId, PiStore.tenantId())
                .eq(VoucherMonthDO::getId, row.getId())
                .set(VoucherMonthDO::getExportCount, nz(row.getExportCount()) + 1)
                .set(VoucherMonthDO::getExportedBy, operator)
                .set(VoucherMonthDO::getExportedByName, userName(operator))
                .set(VoucherMonthDO::getExportedAt, LocalDateTime.now())
                .set(VoucherMonthDO::getUpdateTime, LocalDateTime.now()));
        logService.recordOperateLog(MENU, "导出凭证", null, Map.of("month", m.toString(), "count", d.getVouchers().size(),
                "exportCount", nz(row.getExportCount()) + 1));
        return file;
    }

    /**
     * T+ 凭证导入表。列以用户 T+ 版本的「凭证导入模板」为准（openspec inventory-vouchers design Open Questions），
     * 模板确定前先按常用列：凭证日期、凭证字、凭证号、摘要、科目编码、科目名称、借方金额、贷方金额、往来单位编码、往来单位名称。
     */
    private byte[] tplusWorkbook(VoucherMonthDetailVO d) {
        Map<Long, VoucherSupplierVO> sup = suppliers(d.getVouchers().stream().flatMap(v -> v.getEntries().stream())
                .map(VoucherMonthDetailVO.Entry::getPartnerId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(VoucherSupplierVO::getSupplierId, Function.identity()));
        String[] head = {"凭证日期", "凭证字", "凭证号", "摘要", "科目编码", "科目名称", "借方金额", "贷方金额", "往来单位编码", "往来单位名称"};
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("凭证");
            Row h = sheet.createRow(0);
            for (int i = 0; i < head.length; i++) {
                h.createCell(i).setCellValue(head[i]);
            }
            int r = 1;
            int no = 0;
            for (VoucherMonthDetailVO.Voucher v : d.getVouchers()) {
                no++;
                for (VoucherMonthDetailVO.Entry e : v.getEntries()) {
                    Row row = sheet.createRow(r++);
                    row.createCell(0).setCellValue(v.getVoucherDate().toString());
                    row.createCell(1).setCellValue("记");
                    row.createCell(2).setCellValue(no);
                    row.createCell(3).setCellValue(v.getSummary());
                    row.createCell(4).setCellValue(e.getAccountCode());
                    row.createCell(5).setCellValue(e.getAccountName());
                    if (e.getDebit() != null) {
                        row.createCell(6).setCellValue(e.getDebit().doubleValue());
                    }
                    if (e.getCredit() != null) {
                        row.createCell(7).setCellValue(e.getCredit().doubleValue());
                    }
                    VoucherSupplierVO s = e.getPartnerId() == null ? null : sup.get(e.getPartnerId());
                    if (s != null) {
                        row.createCell(8).setCellValue(StringUtils.hasText(s.getPartnerCode()) ? s.getPartnerCode() : nzs(s.getSupplierCode()));
                        row.createCell(9).setCellValue(nzs(s.getSupplierName()));
                    }
                }
            }
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("生成业务凭证 Excel 失败", e);
        }
    }

    // ---------------------------------------------------------------- 工具

    private VoucherMonthDO activeMonth(YearMonth m) {
        return monthMapper.selectOne(new LambdaQueryWrapper<VoucherMonthDO>()
                .eq(VoucherMonthDO::getTenantId, PiStore.tenantId())
                .eq(VoucherMonthDO::getPeriodMonth, m.toString())
                .isNull(VoucherMonthDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    private List<VoucherDO> activeVouchers(YearMonth m) {
        return voucherMapper.selectList(new LambdaQueryWrapper<VoucherDO>()
                .eq(VoucherDO::getTenantId, PiStore.tenantId())
                .eq(VoucherDO::getPeriodMonth, m.toString())
                .eq(VoucherDO::getStatus, ACTIVE)
                .isNull(VoucherDO::getDeletedAt)
                .orderByAsc(VoucherDO::getVoucherDate)
                .orderByAsc(VoucherDO::getId));
    }

    private Map<Long, List<VoucherEntryDO>> entries(List<VoucherDO> vouchers) {
        if (vouchers.isEmpty()) {
            return Map.of();
        }
        return entryMapper.selectList(new LambdaQueryWrapper<VoucherEntryDO>()
                        .eq(VoucherEntryDO::getTenantId, PiStore.tenantId())
                        .in(VoucherEntryDO::getVoucherId, vouchers.stream().map(VoucherDO::getId).toList())
                        .isNull(VoucherEntryDO::getDeletedAt)
                        .orderByAsc(VoucherEntryDO::getLineNo))
                .stream().collect(Collectors.groupingBy(VoucherEntryDO::getVoucherId));
    }

    private InvPeriodDO closedPeriod(YearMonth m) {
        InvPeriodDO p = periodMapper.selectOne(new LambdaQueryWrapper<InvPeriodDO>()
                .eq(InvPeriodDO::getTenantId, PiStore.tenantId())
                .eq(InvPeriodDO::getPeriodMonth, m.toString())
                .last("LIMIT 1"));
        if (p == null || !Objects.equals(p.getStatus(), InvConstants.PERIOD_CLOSED)) {
            throw new BizException(m + " 还没结账");
        }
        return p;
    }

    private static YearMonth parse(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BizException("月份格式应为 YYYY-MM", e);
        }
    }

    private String userName(Long operator) {
        return operator == null ? "系统" : Objects.requireNonNullElse(lookups.userNames(List.of(operator)).get(operator), "");
    }

    private static String marks(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String nzs(String v) {
        return v == null ? "" : v;
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }
}

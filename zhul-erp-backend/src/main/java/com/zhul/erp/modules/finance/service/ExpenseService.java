package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.ExpenseQuery;
import com.zhul.erp.modules.finance.dto.ExpenseVO;
import com.zhul.erp.modules.finance.dto.PayableVO;
import com.zhul.erp.modules.finance.dto.SaveExpenseRequest;
import com.zhul.erp.modules.finance.dto.SoItemOptionVO;
import com.zhul.erp.modules.finance.entity.ExpenseAllocationDO;
import com.zhul.erp.modules.finance.entity.ExpenseDO;
import com.zhul.erp.modules.finance.entity.ReimbursementDO;
import com.zhul.erp.modules.finance.entity.ReimbursementLineDO;
import com.zhul.erp.modules.finance.repository.ExpenseAllocationMapper;
import com.zhul.erp.modules.finance.repository.ExpenseMapper;
import com.zhul.erp.modules.finance.repository.ReimbursementLineMapper;
import com.zhul.erp.modules.finance.repository.ReimbursementMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.ExchangeRateService;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.system.service.BusinessUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 费用单（spec finance/expense）：偶发成本，分摊到订单型号；公司对公生成应付，员工垫付进入垫付人的待报销。
 * 已付款或已进报销单后只能改名称、说明与分摊。
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final String MENU = "费用单";
    private static final String CNY = "CNY";
    private static final int MAX_PAGE_SIZE = 100;

    private final BusinessUnitService businessUnitService;
    private final ExpenseMapper expenseMapper;
    private final ExpenseAllocationMapper allocationMapper;
    private final ReimbursementLineMapper lineMapper;
    private final ReimbursementMapper reimbursementMapper;
    private final PayableSyncService sync;
    private final PayableQueryService payableQuery;
    private final SalesOrderMapper soMapper;
    private final SalesOrderItemMapper soItemMapper;
    private final SupplierMapper supplierMapper;
    private final ExchangeRateService exchangeRateService;
    private final DocumentNumberService documentNumberService;
    private final AttachmentService attachments;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;
    private final com.zhul.erp.modules.system.service.DictItemService dictItemService;

    public static final String DICT_CATEGORY = "expense_category";
    public static final String CATEGORY_ORDER = "ORDER";
    public static final String CATEGORY_OTHER = "OTHER";

    /** 费用类别：编码 → 名称（启用的） */
    public Map<String, String> categoryNames() {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        dictItemService.listByDictType(DICT_CATEGORY).stream().filter(d -> Objects.equals(d.getStatus(), 1))
                .forEach(d -> m.put(d.getItemCode(), d.getItemName()));
        return m;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ExpenseVO create(SaveExpenseRequest req) {
        // 先校验再取编号，校验不过不占用流水号
        if (req.getAllocations() != null && !req.getAllocations().isEmpty()) {
            BigDecimal sum = req.getAllocations().stream().map(x -> x.getAmount().setScale(2, RoundingMode.HALF_UP))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(req.getAmount().setScale(2, RoundingMode.HALF_UP)) != 0) {
                throw new BizException("分摊合计须等于费用金额");
            }
        }
        if (req.getMethod() != null && req.getMethod() == FinanceConstants.METHOD_ADVANCE
                && (req.getAttachmentIds() == null || req.getAttachmentIds().isEmpty())) {
            throw new BizException("员工垫付的费用请上传凭证");
        }
        ExpenseDO e = new ExpenseDO();
        e.setTenantId(PiStore.tenantId());
        e.setExpenseNo(documentNumberService.next(DocumentType.FE));
        e.setStatus(FinanceConstants.FE_VALID);
        e.setVoidReason("");
        fill(e, req, false);
        e.setBusinessUnitId(unitOf(req));
        expenseMapper.insert(e);
        saveAllocations(e, req.getAllocations());
        attachments.sync(AttachmentService.EXPENSE, e.getId(), req.getAttachmentIds());
        requireProof(e);
        sync.syncExpense(e.getId());
        logService.recordOperateLog(MENU, "新建费用单", null, Map.of("expenseNo", e.getExpenseNo(), "name", e.getName(),
                "amount", e.getCurrencyCode() + " " + e.getAmount()));
        return detail(e.getId());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ExpenseVO update(Long id, SaveExpenseRequest req) {
        ExpenseDO e = lock(id);
        if (e.getStatus() == FinanceConstants.FE_VOID) {
            throw new BizException("费用单已作废");
        }
        String before = e.getCurrencyCode() + " " + e.getAmount();
        boolean locked = locked(e);
        fill(e, req, locked);
        expenseMapper.updateById(e);
        // 事业部可在「公共」与某事业部之间改，为空也要写回
        expenseMapper.update(null, new LambdaUpdateWrapper<ExpenseDO>()
                .set(ExpenseDO::getBusinessUnitId, unitOf(req)).eq(ExpenseDO::getId, e.getId()));
        saveAllocations(e, req.getAllocations());
        attachments.sync(AttachmentService.EXPENSE, e.getId(), req.getAttachmentIds());
        requireProof(e);
        sync.syncExpense(e.getId());
        logService.recordOperateLog(MENU, "修改费用单", Map.of("expenseNo", e.getExpenseNo(), "amount", before),
                Map.of("expenseNo", e.getExpenseNo(), "amount", e.getCurrencyCode() + " " + e.getAmount()));
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ExpenseVO voidExpense(Long id, String reason) {
        ExpenseDO e = lock(id);
        if (e.getStatus() == FinanceConstants.FE_VOID) {
            throw new BizException("费用单已作废");
        }
        if (locked(e)) {
            throw new BizException(Objects.equals(e.getMethod(), FinanceConstants.METHOD_COMPANY)
                    ? "已经付款，不能作废" : "已在报销单里，不能作废");
        }
        e.setStatus(FinanceConstants.FE_VOID);
        e.setVoidReason(reason.trim());
        expenseMapper.updateById(e);
        sync.syncExpense(id);
        logService.recordOperateLog(MENU, "作废费用单", Map.of("expenseNo", e.getExpenseNo(), "status", "有效"),
                Map.of("expenseNo", e.getExpenseNo(), "status", "已作废", "reason", reason.trim()));
        return detail(id);
    }

    public PageResult<ExpenseVO> page(ExpenseQuery q) {
        int size = q.getPageSize() == null ? 20 : Math.min(Math.max(q.getPageSize(), 1), MAX_PAGE_SIZE);
        int page = q.getPage() == null ? 1 : Math.max(q.getPage(), 1);
        LambdaQueryWrapper<ExpenseDO> w = filter(q);
        w.orderByDesc(ExpenseDO::getUpdateTime).orderByDesc(ExpenseDO::getId);
        Page<ExpenseDO> p = expenseMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getTotal(), toVos(p.getRecords()));
    }

    /** 筛选范围内（默认本月）有效费用的人民币合计 */
    public BigDecimal monthTotal(ExpenseQuery q) {
        ExpenseQuery scope = q == null ? new ExpenseQuery() : q;
        if (scope.getDateFrom() == null && scope.getDateTo() == null) {
            LocalDate today = LocalDate.now();
            scope.setDateFrom(today.withDayOfMonth(1));
            scope.setDateTo(today.withDayOfMonth(today.lengthOfMonth()));
        }
        scope.setShowVoid(false);
        return expenseMapper.selectList(filter(scope).select(ExpenseDO::getAmountCny)).stream()
                .map(ExpenseDO::getAmountCny).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public ExpenseVO detail(Long id) {
        ExpenseDO e = expenseMapper.selectOne(new LambdaQueryWrapper<ExpenseDO>()
                .eq(ExpenseDO::getId, id)
                .eq(ExpenseDO::getTenantId, PiStore.tenantId())
                .isNull(ExpenseDO::getDeletedAt));
        if (e == null) {
            throw new BizException("费用单不存在");
        }
        return toVos(List.of(e)).get(0);
    }

    /** 分摊时选订单型号：按订单号或型号搜索最近的订单 */
    public List<SoItemOptionVO> soItems(String keyword) {
        LambdaQueryWrapper<SalesOrderDO> w = new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getTenantId, PiStore.tenantId())
                .isNull(SalesOrderDO::getDeletedAt)
                .orderByDesc(SalesOrderDO::getId)
                .last("LIMIT 20");
        if (StringUtils.hasText(keyword)) {
            w.like(SalesOrderDO::getSoNo, keyword.trim());
        }
        Map<Long, SalesOrderDO> orders = soMapper.selectList(w).stream()
                .collect(Collectors.toMap(SalesOrderDO::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        if (orders.isEmpty()) {
            return List.of();
        }
        List<SoItemOptionVO> out = new ArrayList<>();
        for (SalesOrderItemDO i : soItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                .in(SalesOrderItemDO::getSoId, orders.keySet())
                .isNull(SalesOrderItemDO::getDeletedAt)
                .orderByDesc(SalesOrderItemDO::getSoId)
                .orderByAsc(SalesOrderItemDO::getLineNo))) {
            SoItemOptionVO o = new SoItemOptionVO();
            o.setSoId(i.getSoId());
            o.setSoNo(orders.get(i.getSoId()).getSoNo());
            o.setSoItemId(i.getId());
            o.setLineNo(i.getLineNo());
            o.setModel(i.getModel());
            o.setBrand(i.getBrand());
            o.setQuantity(i.getQuantity());
            out.add(o);
        }
        return out;
    }

    /** 已付款（公司对公）或已进有效报销单（员工垫付） */
    boolean locked(ExpenseDO e) {
        if (Objects.equals(e.getMethod(), FinanceConstants.METHOD_COMPANY)) {
            PayableVO p = payableQuery.bySource(FinanceConstants.SOURCE_EXPENSE, e.getId());
            return p != null && (p.getPaidAmount().signum() != 0 || p.getCreditAmount().signum() > 0);
        }
        return activeLine(e.getId()) != null;
    }

    private ReimbursementLineDO activeLine(Long expenseId) {
        return lineMapper.selectOne(new LambdaQueryWrapper<ReimbursementLineDO>()
                .eq(ReimbursementLineDO::getTenantId, PiStore.tenantId())
                .eq(ReimbursementLineDO::getSourceType, FinanceConstants.ITEM_EXPENSE)
                .eq(ReimbursementLineDO::getSourceId, expenseId)
                .eq(ReimbursementLineDO::getReleaseSeq, 0L)
                .isNull(ReimbursementLineDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** locked 时只改名称、说明（分摊另行保存），金额、收款方、付款方式保持 */
    private void fill(ExpenseDO e, SaveExpenseRequest req, boolean locked) {
        if (req.getOccurDate().isAfter(LocalDate.now())) {
            throw new BizException("发生日期不能晚于今天");
        }
        e.setName(req.getName().trim());
        e.setRemark(req.getRemark() == null ? "" : req.getRemark().trim());
        boolean linked = req.getAllocations() != null && !req.getAllocations().isEmpty();
        String category = StringUtils.hasText(req.getCategory()) ? req.getCategory().trim() : linked ? CATEGORY_ORDER : CATEGORY_OTHER;
        if (categoryNames().get(category) == null) {
            throw new BizException("费用类别不存在");
        }
        e.setCategory(category);
        e.setAmortizeMonths(req.getAmortizeMonths() == null ? 1 : req.getAmortizeMonths());
        if (locked) {
            BigDecimal amount = req.getAmount().setScale(2, RoundingMode.HALF_UP);
            boolean changed = amount.compareTo(e.getAmount()) != 0 || !Objects.equals(req.getMethod(), e.getMethod())
                    || !Objects.equals(req.getCurrencyCode(), e.getCurrencyCode()) || !samePayee(e, req);
            if (changed) {
                throw new BizException(Objects.equals(e.getMethod(), FinanceConstants.METHOD_COMPANY)
                        ? "已经付款，不能修改金额与收款方" : "已在报销单里，不能修改金额与收款方");
            }
            return;
        }
        e.setOccurDate(req.getOccurDate());
        fillPayee(e, req);
        e.setCurrencyCode(req.getCurrencyCode());
        BigDecimal rate = CNY.equals(req.getCurrencyCode()) ? BigDecimal.ONE : exchangeRateService.require(req.getCurrencyCode()).rate();
        e.setExchangeRate(rate);
        e.setAmount(req.getAmount().setScale(2, RoundingMode.HALF_UP));
        e.setAmountCny(e.getAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP));
        int method = req.getMethod();
        if (method != FinanceConstants.METHOD_COMPANY && method != FinanceConstants.METHOD_ADVANCE) {
            throw new BizException("付款方式无效");
        }
        e.setMethod(method);
        e.setAdvanceUserId(method == FinanceConstants.METHOD_ADVANCE
                ? (req.getAdvanceUserId() != null ? req.getAdvanceUserId() : currentUser.resolve()) : null);
        if (method == FinanceConstants.METHOD_ADVANCE && e.getAdvanceUserId() == null) {
            throw new BizException("请选择垫付人");
        }
    }

    private boolean samePayee(ExpenseDO e, SaveExpenseRequest req) {
        ExpenseDO probe = new ExpenseDO();
        fillPayee(probe, req);
        return Objects.equals(probe.getPayeeType(), e.getPayeeType()) && Objects.equals(probe.getSupplierId(), e.getSupplierId())
                && Objects.equals(probe.getShopName(), e.getShopName()) && Objects.equals(probe.getPayeeName(), e.getPayeeName());
    }

    private void fillPayee(ExpenseDO e, SaveExpenseRequest req) {
        int type = req.getPayeeType();
        e.setPayeeType(type);
        e.setSupplierId(null);
        e.setShopPlatform("");
        e.setShopName("");
        switch (type) {
            case FinanceConstants.PAYEE_SUPPLIER, FinanceConstants.PAYEE_FORWARDER -> {
                SupplierDO s = req.getSupplierId() == null ? null : supplierMapper.selectById(req.getSupplierId());
                if (s == null || !Objects.equals(s.getTenantId(), PiStore.tenantId())) {
                    throw new BizException("请选择收款方");
                }
                e.setSupplierId(s.getId());
                e.setPayeeName(StringUtils.hasText(s.getShortName()) ? s.getShortName() : s.getName());
            }
            case FinanceConstants.PAYEE_SHOP -> {
                Integer ch = req.getShopChannel();
                if (ch == null || ch == PurchaseConstants.CHANNEL_SUPPLIER || !PurchaseConstants.CHANNEL_NAMES.containsKey(ch)
                        || !StringUtils.hasText(req.getShopName())) {
                    throw new BizException("请选择平台并填写店铺名称");
                }
                e.setShopPlatform(String.valueOf(ch));
                e.setShopName(cut(req.getShopName().trim(), 128));
                e.setPayeeName(cut(PurchaseConstants.CHANNEL_NAMES.get(ch) + " · " + e.getShopName(), 128));
            }
            case FinanceConstants.PAYEE_OTHER -> {
                if (!StringUtils.hasText(req.getPayeeName())) {
                    throw new BizException("请填写收款方名称");
                }
                e.setPayeeName(cut(req.getPayeeName().trim(), 128));
            }
            default -> throw new BizException("收款方类型无效");
        }
    }

    /** 分摊：整份替换；合计须等于金额；人民币按费用单汇率折算，尾差放最后一行 */
    /**
     * 所属事业部（spec finance/expense「费用归属事业部」）：关联了订单型号的跟随订单，分摊行的订单须属于同一事业部；
     * 不关联的取所选事业部，没选为「公共」。公司没有事业部时为空。
     */
    private Integer unitOf(SaveExpenseRequest req) {
        if (req.getAllocations() == null || req.getAllocations().isEmpty()) {
            return businessUnitService.forOptional(req.getBusinessUnitId());
        }
        List<Long> itemIds = req.getAllocations().stream().map(SaveExpenseRequest.Alloc::getSoItemId).distinct().toList();
        Set<Long> soIds = soItemMapper.selectBatchIds(itemIds).stream().map(SalesOrderItemDO::getSoId).collect(Collectors.toSet());
        Set<Integer> units = soIds.isEmpty() ? Set.of() : soMapper.selectBatchIds(soIds).stream()
                .map(SalesOrderDO::getBusinessUnitId).collect(Collectors.toCollection(HashSet::new));
        if (units.size() > 1) {
            throw new BizException("分摊的订单须属于同一个事业部");
        }
        return units.isEmpty() ? null : units.iterator().next();
    }

    private void saveAllocations(ExpenseDO e, List<SaveExpenseRequest.Alloc> rows) {
        List<SaveExpenseRequest.Alloc> list = rows == null ? List.of() : rows;
        Set<Long> seen = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (SaveExpenseRequest.Alloc a : list) {
            if (!seen.add(a.getSoItemId())) {
                throw new BizException("同一个订单型号只能分摊一次");
            }
            sum = sum.add(a.getAmount().setScale(2, RoundingMode.HALF_UP));
        }
        if (!list.isEmpty() && sum.compareTo(e.getAmount()) != 0) {
            throw new BizException("分摊合计须等于费用金额");
        }
        Map<Long, SalesOrderItemDO> items = list.isEmpty() ? Map.of()
                : soItemMapper.selectBatchIds(seen).stream()
                .filter(i -> Objects.equals(i.getTenantId(), PiStore.tenantId()) && i.getDeletedAt() == null)
                .collect(Collectors.toMap(SalesOrderItemDO::getId, Function.identity()));
        if (items.size() != seen.size()) {
            throw new BizException("订单型号不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        allocationMapper.selectList(new LambdaQueryWrapper<ExpenseAllocationDO>()
                        .eq(ExpenseAllocationDO::getExpenseId, e.getId())
                        .isNull(ExpenseAllocationDO::getDeletedAt))
                .forEach(old -> {
                    old.setDeletedAt(now);
                    allocationMapper.updateById(old);
                });
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < list.size(); i++) {
            SaveExpenseRequest.Alloc a = list.get(i);
            BigDecimal amount = a.getAmount().setScale(2, RoundingMode.HALF_UP);
            BigDecimal cny = i == list.size() - 1 ? e.getAmountCny().subtract(assigned)
                    : amount.multiply(e.getExchangeRate()).setScale(2, RoundingMode.HALF_UP);
            assigned = assigned.add(cny);
            ExpenseAllocationDO row = new ExpenseAllocationDO();
            row.setTenantId(e.getTenantId());
            row.setExpenseId(e.getId());
            row.setSoItemId(a.getSoItemId());
            row.setSoId(items.get(a.getSoItemId()).getSoId());
            row.setAmount(amount);
            row.setAmountCny(cny);
            allocationMapper.insert(row);
        }
    }

    /** 员工垫付的费用单要有凭证（报销时作为截图） */
    private void requireProof(ExpenseDO e) {
        if (Objects.equals(e.getMethod(), FinanceConstants.METHOD_ADVANCE)
                && attachments.listOf(AttachmentService.EXPENSE, e.getId()).isEmpty()) {
            throw new BizException("员工垫付的费用请上传凭证");
        }
    }

    private ExpenseDO lock(Long id) {
        ExpenseDO e = expenseMapper.selectOne(new LambdaQueryWrapper<ExpenseDO>()
                .eq(ExpenseDO::getId, id)
                .eq(ExpenseDO::getTenantId, PiStore.tenantId())
                .isNull(ExpenseDO::getDeletedAt)
                .last("FOR UPDATE"));
        if (e == null) {
            throw new BizException("费用单不存在");
        }
        return e;
    }

    private LambdaQueryWrapper<ExpenseDO> filter(ExpenseQuery q) {
        LambdaQueryWrapper<ExpenseDO> w = new LambdaQueryWrapper<ExpenseDO>()
                .eq(ExpenseDO::getTenantId, PiStore.tenantId())
                .isNull(ExpenseDO::getDeletedAt);
        if (!Boolean.TRUE.equals(q.getShowVoid())) {
            w.eq(ExpenseDO::getStatus, FinanceConstants.FE_VALID);
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String k = q.getKeyword().trim();
            w.and(x -> x.like(ExpenseDO::getExpenseNo, k).or().like(ExpenseDO::getName, k).or().like(ExpenseDO::getPayeeName, k));
        }
        if (q.getDateFrom() != null) {
            w.ge(ExpenseDO::getOccurDate, q.getDateFrom());
        }
        if (q.getDateTo() != null) {
            w.le(ExpenseDO::getOccurDate, q.getDateTo());
        }
        if (q.getMethod() != null) {
            w.eq(ExpenseDO::getMethod, q.getMethod());
        }
        if (q.getBusinessUnitId() != null) {
            if (q.getBusinessUnitId() == 0) {
                w.isNull(ExpenseDO::getBusinessUnitId);
            } else {
                w.eq(ExpenseDO::getBusinessUnitId, q.getBusinessUnitId());
            }
        }
        if (StringUtils.hasText(q.getCategory())) {
            w.eq(ExpenseDO::getCategory, q.getCategory().trim());
        }
        if (q.getLinked() != null) {
            w.apply((q.getLinked() ? "" : "NOT ") + "EXISTS (SELECT 1 FROM fin_expense_allocation a WHERE a.expense_id = fin_expense.id "
                    + "AND a.deleted_at IS NULL)");
        }
        if (StringUtils.hasText(q.getSoNo())) {
            w.apply("id IN (SELECT a.expense_id FROM fin_expense_allocation a JOIN sales_order s ON s.id = a.so_id "
                    + "WHERE a.deleted_at IS NULL AND s.so_no LIKE CONCAT('%', {0}, '%'))", q.getSoNo().trim());
        }
        return w;
    }

    private List<ExpenseVO> toVos(List<ExpenseDO> rows) {
        Map<String, String> categories = categoryNames();
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(ExpenseDO::getId).toList();
        Map<Long, List<ExpenseAllocationDO>> allocs = allocationMapper.selectList(new LambdaQueryWrapper<ExpenseAllocationDO>()
                        .in(ExpenseAllocationDO::getExpenseId, ids)
                        .isNull(ExpenseAllocationDO::getDeletedAt)
                        .orderByAsc(ExpenseAllocationDO::getId))
                .stream().collect(Collectors.groupingBy(ExpenseAllocationDO::getExpenseId));
        Set<Long> itemIds = new HashSet<>();
        Set<Long> soIds = new HashSet<>();
        allocs.values().forEach(l -> l.forEach(a -> {
            itemIds.add(a.getSoItemId());
            soIds.add(a.getSoId());
        }));
        Map<Long, SalesOrderItemDO> items = itemIds.isEmpty() ? Map.of()
                : soItemMapper.selectBatchIds(itemIds).stream().collect(Collectors.toMap(SalesOrderItemDO::getId, Function.identity()));
        Map<Long, String> soNos = soIds.isEmpty() ? Map.of()
                : soMapper.selectBatchIds(soIds).stream().collect(Collectors.toMap(SalesOrderDO::getId, SalesOrderDO::getSoNo));
        Map<Long, PayableVO> pays = payableQuery.statusBySources(FinanceConstants.SOURCE_EXPENSE, ids);
        Map<Long, String> users = lookups.userNames(rows.stream().map(ExpenseDO::getAdvanceUserId).toList());
        Map<Long, List<AttachmentVO>> files = attachments.list(AttachmentService.EXPENSE, ids);
        Map<Long, ReimbursementLineDO> lines = lineMapper.selectList(new LambdaQueryWrapper<ReimbursementLineDO>()
                        .eq(ReimbursementLineDO::getTenantId, PiStore.tenantId())
                        .eq(ReimbursementLineDO::getSourceType, FinanceConstants.ITEM_EXPENSE)
                        .in(ReimbursementLineDO::getSourceId, ids)
                        .eq(ReimbursementLineDO::getReleaseSeq, 0L)
                        .isNull(ReimbursementLineDO::getDeletedAt))
                .stream().collect(Collectors.toMap(ReimbursementLineDO::getSourceId, Function.identity(), (a, b) -> a));
        Map<Long, ReimbursementDO> bxs = lines.isEmpty() ? Map.of()
                : reimbursementMapper.selectBatchIds(lines.values().stream().map(ReimbursementLineDO::getReimbursementId).toList())
                .stream().collect(Collectors.toMap(ReimbursementDO::getId, Function.identity()));
        List<ExpenseVO> out = new ArrayList<>();
        for (ExpenseDO e : rows) {
            ExpenseVO vo = new ExpenseVO();
            vo.setId(e.getId());
            vo.setExpenseNo(e.getExpenseNo());
            vo.setName(e.getName());
            vo.setBusinessUnitId(e.getBusinessUnitId());
            vo.setCategory(e.getCategory());
            vo.setCategoryName(categories.getOrDefault(e.getCategory(), e.getCategory()));
            vo.setAmortizeMonths(e.getAmortizeMonths());
            vo.setOccurDate(e.getOccurDate());
            vo.setPayeeType(e.getPayeeType());
            vo.setSupplierId(e.getSupplierId());
            vo.setShopPlatform(e.getShopPlatform());
            vo.setShopName(e.getShopName());
            vo.setPayeeName(e.getPayeeName());
            vo.setCurrencyCode(e.getCurrencyCode());
            vo.setAmount(e.getAmount());
            vo.setExchangeRate(e.getExchangeRate());
            vo.setAmountCny(e.getAmountCny());
            vo.setMethod(e.getMethod());
            vo.setAdvanceUserId(e.getAdvanceUserId());
            vo.setAdvanceUserName(users.get(e.getAdvanceUserId()));
            vo.setRemark(e.getRemark());
            vo.setStatus(e.getStatus());
            vo.setVoidReason(e.getVoidReason());
            PayableVO pay = pays.get(e.getId());
            if (pay != null) {
                vo.setPayableId(pay.getId());
                vo.setPayStatus(pay.getStatus());
                vo.setPaidAmount(pay.getPaidAmount());
            }
            ReimbursementLineDO line = lines.get(e.getId());
            ReimbursementDO bx = line == null ? null : bxs.get(line.getReimbursementId());
            if (bx != null) {
                vo.setReimburseNo(bx.getReimburseNo());
                vo.setReimburseStatus(bx.getStatus());
            }
            vo.setLocked(Objects.equals(e.getMethod(), FinanceConstants.METHOD_COMPANY)
                    ? pay != null && (pay.getPaidAmount().signum() != 0 || pay.getCreditAmount().signum() > 0) : bx != null);
            for (ExpenseAllocationDO a : allocs.getOrDefault(e.getId(), List.of())) {
                ExpenseVO.Alloc av = new ExpenseVO.Alloc();
                SalesOrderItemDO item = items.get(a.getSoItemId());
                av.setSoId(a.getSoId());
                av.setSoNo(soNos.get(a.getSoId()));
                av.setSoItemId(a.getSoItemId());
                av.setModel(item == null ? null : item.getModel());
                av.setBrand(item == null ? null : item.getBrand());
                av.setAmount(a.getAmount());
                av.setAmountCny(a.getAmountCny());
                vo.getAllocations().add(av);
            }
            vo.setLinked(!vo.getAllocations().isEmpty());
            vo.setAttachments(PayableQueryService.refs(files.getOrDefault(e.getId(), List.of())));
            vo.setCreateTime(e.getCreateTime());
            vo.setCreateBy(e.getCreateBy());
            vo.setUpdateTime(e.getUpdateTime());
            vo.setUpdateBy(e.getUpdateBy());
            out.add(vo);
        }
        return out;
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}

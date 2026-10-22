package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.FxPendingVO;
import com.zhul.erp.modules.finance.dto.FxSettlementQuery;
import com.zhul.erp.modules.finance.dto.FxSettlementVO;
import com.zhul.erp.modules.finance.dto.SaveFxSettlementRequest;
import com.zhul.erp.modules.finance.entity.FxSettlementAllocationDO;
import com.zhul.erp.modules.finance.entity.FxSettlementDO;
import com.zhul.erp.modules.finance.repository.FxSettlementAllocationMapper;
import com.zhul.erp.modules.finance.repository.FxSettlementMapper;
import com.zhul.erp.modules.finance.support.FxRates;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.ReceiptRowVO;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.sales.support.ReceiptRows;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.entity.BankAccountDO;
import com.zhul.erp.modules.system.repository.BankAccountMapper;
import com.zhul.erp.modules.system.service.BankAccountService;
import com.zhul.erp.modules.system.service.BusinessUnitService;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 结汇单（spec finance/fx-settlement）：按到账日期先进先出把结汇金额分摊到同币种未结完的外币到账；
 * 到账人民币 = 已结汇部分按结汇汇率 + 未结汇部分按估算汇率（最近一张有效结汇单的汇率）。
 * 金额 HALF_UP 2 位、汇率 6 位；分摊的人民币尾差放在最后一笔。
 * 事务边界：登记 / 作废结汇单、分摊（或撤回）、该币种未结完到账的人民币重算在同一事务内；
 * 并发：对该币种待结汇的到账行加锁（与到账作废互斥），登记带幂等键。
 */
@Service
@RequiredArgsConstructor
public class FxSettlementService {

    private static final String MENU = "结汇单";
    private static final List<Integer> OPEN = List.of(SalesConstants.SETTLE_NONE, SalesConstants.SETTLE_PARTIAL);

    private final FxSettlementMapper settlementMapper;
    private final FxSettlementAllocationMapper allocationMapper;
    private final PaymentReceiptMapper receiptMapper;
    private final BankAccountService bankAccountService;
    private final BankAccountMapper bankAccountMapper;
    private final DocumentNumberService documentNumberService;
    private final LogService logService;
    private final FxRates fxRates;
    private final ReceiptRows receiptRows;
    private final BusinessUnitService businessUnitService;

    // ---------------------------------------------------------------- 登记

    @Transactional(rollbackFor = Exception.class)
    public FxSettlementVO create(SaveFxSettlementRequest req) {
        FxSettlementDO dup = byKey(req.getIdempotencyKey());
        if (dup != null) {
            return detail(dup.getId());
        }
        String currency = req.getCurrencyCode().trim().toUpperCase(Locale.ROOT);
        if (FxRates.CNY.equals(currency)) {
            throw new BizException("人民币到账不需要结汇");
        }
        if (req.getSettleDate().isAfter(LocalDate.now())) {
            throw new BizException("结汇日期不能晚于今天");
        }
        BankAccountDO bank = bankAccountService.requireEnabled(req.getBankAccountId());
        if (!FxRates.CNY.equals(bank.getCurrencyCode())) {
            throw new BizException("入账账户须为人民币账户");
        }
        BigDecimal amount = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal amountCny = req.getAmountCny().setScale(2, RoundingMode.HALF_UP);
        // 事业部：只分摊本事业部的到账；没选时取入账账户的事业部
        Integer unit = businessUnitService.enabled()
                ? businessUnitService.require(req.getBusinessUnitId() != null ? req.getBusinessUnitId() : bank.getBusinessUnitId())
                : null;
        List<PaymentReceiptDO> open = lockOpen(unit, currency);
        BigDecimal balance = open.stream().map(FxSettlementService::unsettled).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(balance) > 0) {
            String left = currency + " " + String.format("%,.2f", balance);
            throw new BizException(unit == null ? "结汇金额超过待结汇余额 " + left
                    : businessUnitService.names().get(unit) + "可结汇的" + currency + "只有 " + left);
        }
        BigDecimal rate = amountCny.divide(amount, 6, RoundingMode.HALF_UP);

        FxSettlementDO s = new FxSettlementDO();
        s.setTenantId(PiStore.tenantId());
        s.setSettleNo(documentNumberService.next(DocumentType.JH));
        s.setBusinessUnitId(unit);
        s.setCurrencyCode(currency);
        s.setSettleDate(req.getSettleDate());
        s.setAmount(amount);
        s.setAmountCny(amountCny);
        s.setExchangeRate(rate);
        s.setBankAccountId(bank.getId());
        s.setRemark(req.getRemark() == null ? "" : req.getRemark().trim());
        s.setMigrated(0);
        s.setStatus(FinanceConstants.FX_VALID);
        s.setVoidReason("");
        s.setVoidedBy("");
        s.setIdempotencyKey(req.getIdempotencyKey());
        settlementMapper.insert(s);

        // 先进先出：按到账日期、登记先后分摊；人民币按汇率折算，尾差放在最后一笔
        BigDecimal left = amount;
        BigDecimal cnyLeft = amountCny;
        List<PaymentReceiptDO> touched = new ArrayList<>();
        int sort = 1;
        for (PaymentReceiptDO r : open) {
            if (left.signum() == 0) {
                break;
            }
            BigDecimal take = left.min(unsettled(r));
            if (take.signum() == 0) {
                continue;
            }
            left = left.subtract(take);
            BigDecimal cny = left.signum() == 0 ? cnyLeft : take.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            cnyLeft = cnyLeft.subtract(cny);
            FxSettlementAllocationDO a = new FxSettlementAllocationDO();
            a.setTenantId(s.getTenantId());
            a.setSettlementId(s.getId());
            a.setReceiptId(r.getId());
            a.setAmount(take);
            a.setAmountCny(cny);
            a.setSortOrder(sort++);
            allocationMapper.insert(a);
            r.setSettledAmount(r.getSettledAmount().add(take));
            touched.add(r);
        }
        refresh(open, rate);
        logService.recordOperateLog(MENU, "登记结汇单", null, Map.of("settleNo", s.getSettleNo(), "amount", currency + " " + amount,
                "amountCny", amountCny.toPlainString(), "rate", rate.toPlainString(), "receipts", touched.size()));
        return detail(s.getId());
    }

    // ---------------------------------------------------------------- 作废

    @Transactional(rollbackFor = Exception.class)
    public FxSettlementVO voidSettlement(Long id, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw new BizException("请填写作废原因");
        }
        FxSettlementDO s = settlementMapper.selectOne(new LambdaQueryWrapper<FxSettlementDO>()
                .eq(FxSettlementDO::getId, id)
                .eq(FxSettlementDO::getTenantId, PiStore.tenantId())
                .isNull(FxSettlementDO::getDeletedAt)
                .last("FOR UPDATE"));
        if (s == null) {
            throw new BizException("结汇单不存在");
        }
        if (s.getStatus() == FinanceConstants.FX_VOID) {
            throw new BizException("结汇单已作废");
        }
        if (Objects.equals(s.getMigrated(), 1)) {
            throw new BizException("迁移生成的结汇单不能作废");
        }
        FxSettlementDO latest = fxRates.latest(s.getBusinessUnitId(), s.getCurrencyCode());
        if (latest != null && !latest.getId().equals(s.getId())) {
            throw new BizException("请先作废之后的结汇单 " + latest.getSettleNo());
        }
        List<FxSettlementAllocationDO> allocs = allocations(List.of(s.getId()));
        Map<Long, PaymentReceiptDO> receipts = lockReceipts(allocs.stream().map(FxSettlementAllocationDO::getReceiptId).toList());
        LocalDateTime now = LocalDateTime.now();
        for (FxSettlementAllocationDO a : allocs) {
            PaymentReceiptDO r = receipts.get(a.getReceiptId());
            r.setSettledAmount(r.getSettledAmount().subtract(a.getAmount()));
            a.setDeletedAt(now);
            allocationMapper.updateById(a);
        }
        s.setStatus(FinanceConstants.FX_VOID);
        s.setVoidReason(why.length() > 200 ? why.substring(0, 200) : why);
        s.setVoidedBy(SecurityUtils.getCurrentUsername());
        s.setVoidedAt(now);
        settlementMapper.updateById(s);
        // 作废后估算汇率回到上一张有效结汇单（或系统汇率）
        Set<PaymentReceiptDO> all = new LinkedHashSet<>(receipts.values());
        all.addAll(lockOpen(s.getBusinessUnitId(), s.getCurrencyCode()));
        refresh(all, fxRates.estimate(s.getBusinessUnitId(), s.getCurrencyCode()));
        logService.recordOperateLog(MENU, "作废结汇单", Map.of("settleNo", s.getSettleNo(), "status", "有效"),
                Map.of("settleNo", s.getSettleNo(), "status", "已作废", "reason", s.getVoidReason()));
        return detail(id);
    }

    // ---------------------------------------------------------------- 人民币重算

    /**
     * 按已结汇分摊与估算汇率重算到账的人民币：
     * 实收人民币 = 已结汇人民币 + 未结汇部分 × 估算汇率；到账汇率 = 实收人民币 ÷ 实收（6 位）；毛额折人民币按到账汇率。
     */
    private void refresh(Collection<PaymentReceiptDO> rows, BigDecimal estimate) {
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> settledCny = new HashMap<>();
        for (FxSettlementAllocationDO a : allocationMapper.selectList(new LambdaQueryWrapper<FxSettlementAllocationDO>()
                .in(FxSettlementAllocationDO::getReceiptId, rows.stream().map(PaymentReceiptDO::getId).toList())
                .isNull(FxSettlementAllocationDO::getDeletedAt))) {
            settledCny.merge(a.getReceiptId(), a.getAmountCny(), BigDecimal::add);
        }
        for (PaymentReceiptDO r : rows) {
            BigDecimal net = r.getNetAmount();
            BigDecimal settled = r.getSettledAmount();
            BigDecimal cny = settledCny.getOrDefault(r.getId(), BigDecimal.ZERO)
                    .add(net.subtract(settled).multiply(estimate)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal rate = net.signum() == 0 ? estimate : cny.divide(net, 6, RoundingMode.HALF_UP);
            boolean done = net.signum() > 0 && settled.compareTo(net) >= 0;
            r.setSettleStatus(done ? SalesConstants.SETTLE_DONE
                    : settled.signum() > 0 ? SalesConstants.SETTLE_PARTIAL : SalesConstants.SETTLE_NONE);
            r.setRateSource(done ? SalesConstants.RATE_SETTLED : SalesConstants.RATE_ESTIMATE);
            r.setExchangeRate(rate);
            r.setNetAmountCny(cny);
            r.setAmountCny(r.getAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP));
            receiptMapper.updateById(r);
        }
    }

    // ---------------------------------------------------------------- 查询

    public PageResult<FxSettlementVO> page(FxSettlementQuery q) {
        LambdaQueryWrapper<FxSettlementDO> w = new LambdaQueryWrapper<FxSettlementDO>()
                .eq(FxSettlementDO::getTenantId, PiStore.tenantId())
                .isNull(FxSettlementDO::getDeletedAt);
        if (!Boolean.TRUE.equals(q.getIncludeVoid())) {
            w.eq(FxSettlementDO::getStatus, FinanceConstants.FX_VALID);
        }
        if (q.getBusinessUnitId() != null) {
            w.eq(FxSettlementDO::getBusinessUnitId, q.getBusinessUnitId());
        }
        if (StringUtils.hasText(q.getCurrencyCode())) {
            w.eq(FxSettlementDO::getCurrencyCode, q.getCurrencyCode().trim().toUpperCase(Locale.ROOT));
        }
        if (q.getDateFrom() != null) {
            w.ge(FxSettlementDO::getSettleDate, q.getDateFrom());
        }
        if (q.getDateTo() != null) {
            w.le(FxSettlementDO::getSettleDate, q.getDateTo());
        }
        w.orderByDesc(FxSettlementDO::getSettleDate).orderByDesc(FxSettlementDO::getId);
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        Page<FxSettlementDO> page = settlementMapper.selectPage(new Page<>(Math.max(1, q.getPage() == null ? 1 : q.getPage()), size), w);
        return PageResult.of(page.getTotal(), toVos(page.getRecords(), false));
    }

    public FxSettlementVO detail(Long id) {
        FxSettlementDO s = settlementMapper.selectById(id);
        if (s == null || s.getDeletedAt() != null || !Objects.equals(s.getTenantId(), PiStore.tenantId())) {
            throw new BizException("结汇单不存在");
        }
        return toVos(List.of(s), true).get(0);
    }

    /** 各事业部、各外币的待结汇：余额、估算汇率与按先进先出排序的到账 */
    public List<FxPendingVO> pending() {
        List<PaymentReceiptDO> rows = receiptMapper.selectList(openWrapper(null, null, false));
        Map<String, List<PaymentReceiptDO>> byPool = rows.stream()
                .collect(Collectors.groupingBy(FxSettlementService::poolKey, LinkedHashMap::new, Collectors.toList()));
        Map<Long, ReceiptRowVO> views = receiptRows.of(rows).stream().collect(Collectors.toMap(ReceiptRowVO::getId, Function.identity()));
        List<FxPendingVO> out = new ArrayList<>();
        byPool.keySet().stream().sorted().forEach(key -> {
            List<PaymentReceiptDO> list = byPool.get(key);
            Integer unit = list.get(0).getBusinessUnitId();
            String currency = list.get(0).getCurrencyCode();
            FxSettlementDO latest = fxRates.latest(unit, currency);
            FxPendingVO vo = new FxPendingVO();
            vo.setBusinessUnitId(unit);
            vo.setCurrencyCode(currency);
            vo.setBalance(list.stream().map(FxSettlementService::unsettled).reduce(BigDecimal.ZERO, BigDecimal::add));
            vo.setEstimateRate(fxRates.estimate(unit, currency));
            vo.setLatestSettleNo(latest == null ? null : latest.getSettleNo());
            vo.setLatestSettleDate(latest == null ? null : latest.getSettleDate());
            vo.setEstimateCny(vo.getBalance().multiply(vo.getEstimateRate()).setScale(2, RoundingMode.HALF_UP));
            vo.setReceipts(list.stream().map(r -> views.get(r.getId())).toList());
            out.add(vo);
        });
        return out;
    }

    private List<FxSettlementVO> toVos(List<FxSettlementDO> rows, boolean withAllocations) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<FxSettlementAllocationDO> allocs = allocations(rows.stream().map(FxSettlementDO::getId).toList());
        Map<Long, List<FxSettlementAllocationDO>> bySettlement = allocs.stream()
                .collect(Collectors.groupingBy(FxSettlementAllocationDO::getSettlementId));
        Map<Long, ReceiptRowVO> receipts = !withAllocations || allocs.isEmpty() ? Map.of()
                : receiptRows.of(receiptMapper.selectBatchIds(allocs.stream().map(FxSettlementAllocationDO::getReceiptId).distinct().toList()))
                .stream().collect(Collectors.toMap(ReceiptRowVO::getId, Function.identity()));
        Set<Integer> bankIds = rows.stream().map(FxSettlementDO::getBankAccountId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, BankAccountDO> banks = bankIds.isEmpty() ? Map.of()
                : bankAccountMapper.selectBatchIds(bankIds).stream().collect(Collectors.toMap(BankAccountDO::getId, b -> b));
        Map<String, Long> latestIds = new HashMap<>();
        rows.stream().map(s -> poolKey(s.getBusinessUnitId(), s.getCurrencyCode())).distinct().forEach(k -> {
            FxSettlementDO any = rows.stream().filter(s -> poolKey(s.getBusinessUnitId(), s.getCurrencyCode()).equals(k)).findFirst().orElseThrow();
            FxSettlementDO l = fxRates.latest(any.getBusinessUnitId(), any.getCurrencyCode());
            if (l != null) {
                latestIds.put(k, l.getId());
            }
        });
        List<FxSettlementVO> out = new ArrayList<>(rows.size());
        for (FxSettlementDO s : rows) {
            FxSettlementVO vo = new FxSettlementVO();
            vo.setId(s.getId());
            vo.setSettleNo(s.getSettleNo());
            vo.setBusinessUnitId(s.getBusinessUnitId());
            vo.setCurrencyCode(s.getCurrencyCode());
            vo.setSettleDate(s.getSettleDate());
            vo.setAmount(s.getAmount());
            vo.setAmountCny(s.getAmountCny());
            vo.setExchangeRate(s.getExchangeRate());
            vo.setBankAccountId(s.getBankAccountId());
            BankAccountDO b = s.getBankAccountId() == null ? null : banks.get(s.getBankAccountId());
            vo.setBankAccountName(b == null ? null : b.getBankName() + " " + BankAccountService.mask(b.getAccountNo()));
            vo.setRemark(s.getRemark());
            vo.setMigrated(Objects.equals(s.getMigrated(), 1));
            vo.setStatus(s.getStatus());
            vo.setVoidReason(s.getVoidReason());
            vo.setVoidedBy(s.getVoidedBy());
            vo.setVoidedAt(s.getVoidedAt());
            List<FxSettlementAllocationDO> mine = bySettlement.getOrDefault(s.getId(), List.of());
            vo.setAllocationCount(mine.size());
            vo.setLatest(s.getId().equals(latestIds.get(poolKey(s.getBusinessUnitId(), s.getCurrencyCode()))));
            if (withAllocations) {
                vo.setAllocations(mine.stream().map(a -> {
                    FxSettlementVO.Allocation x = new FxSettlementVO.Allocation();
                    x.setAmount(a.getAmount());
                    x.setAmountCny(a.getAmountCny());
                    x.setReceipt(receipts.get(a.getReceiptId()));
                    return x;
                }).toList());
            }
            vo.setCreateTime(s.getCreateTime());
            vo.setCreateBy(s.getCreateBy());
            vo.setUpdateTime(s.getUpdateTime());
            vo.setUpdateBy(s.getUpdateBy());
            out.add(vo);
        }
        return out;
    }

    // ---------------------------------------------------------------- 工具

    private static BigDecimal unsettled(PaymentReceiptDO r) {
        return r.getNetAmount().subtract(r.getSettledAmount());
    }

    /** 先进先出池：事业部 + 币种（公司没有事业部时只按币种） */
    private static String poolKey(PaymentReceiptDO r) {
        return poolKey(r.getBusinessUnitId(), r.getCurrencyCode());
    }

    private static String poolKey(Integer unit, String currency) {
        return (unit == null ? "" : String.format("%010d", unit)) + "/" + currency;
    }

    /** 某事业部某币种（currency 为空时全部池子）还没结完的有效到账，按先进先出排序 */
    private LambdaQueryWrapper<PaymentReceiptDO> openWrapper(Integer unit, String currency, boolean byUnit) {
        LambdaQueryWrapper<PaymentReceiptDO> w = new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getTenantId, PiStore.tenantId())
                .eq(PaymentReceiptDO::getKind, SalesConstants.KIND_RECEIPT)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt)
                .in(PaymentReceiptDO::getSettleStatus, OPEN);
        if (currency != null) {
            w.eq(PaymentReceiptDO::getCurrencyCode, currency);
        }
        if (byUnit) {
            w.eq(unit != null, PaymentReceiptDO::getBusinessUnitId, unit).isNull(unit == null, PaymentReceiptDO::getBusinessUnitId);
        }
        return w.orderByAsc(PaymentReceiptDO::getReceiptDate).orderByAsc(PaymentReceiptDO::getId);
    }

    private List<PaymentReceiptDO> lockOpen(Integer unit, String currency) {
        return receiptMapper.selectList(openWrapper(unit, currency, true).last("FOR UPDATE"));
    }

    private Map<Long, PaymentReceiptDO> lockReceipts(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return receiptMapper.selectList(new LambdaQueryWrapper<PaymentReceiptDO>()
                        .in(PaymentReceiptDO::getId, ids)
                        .orderByAsc(PaymentReceiptDO::getId)
                        .last("FOR UPDATE"))
                .stream().collect(Collectors.toMap(PaymentReceiptDO::getId, Function.identity()));
    }

    private List<FxSettlementAllocationDO> allocations(List<Long> settlementIds) {
        return allocationMapper.selectList(new LambdaQueryWrapper<FxSettlementAllocationDO>()
                .in(FxSettlementAllocationDO::getSettlementId, settlementIds)
                .isNull(FxSettlementAllocationDO::getDeletedAt)
                .orderByAsc(FxSettlementAllocationDO::getSortOrder));
    }

    private FxSettlementDO byKey(String key) {
        return settlementMapper.selectOne(new LambdaQueryWrapper<FxSettlementDO>()
                .eq(FxSettlementDO::getTenantId, PiStore.tenantId())
                .eq(FxSettlementDO::getIdempotencyKey, key)
                .last("LIMIT 1"));
    }
}

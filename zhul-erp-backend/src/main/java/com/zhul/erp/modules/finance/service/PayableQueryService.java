package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.PayableQuery;
import com.zhul.erp.modules.finance.dto.PayableStatsVO;
import com.zhul.erp.modules.finance.dto.PayableVO;
import com.zhul.erp.modules.finance.dto.PayeeOptionVO;
import com.zhul.erp.modules.finance.entity.PayableDO;
import com.zhul.erp.modules.finance.entity.PayableInstallmentDO;
import com.zhul.erp.modules.finance.entity.PaymentAllocationDO;
import com.zhul.erp.modules.finance.entity.PaymentDO;
import com.zhul.erp.modules.finance.repository.PayableInstallmentMapper;
import com.zhul.erp.modules.finance.repository.PayableMapper;
import com.zhul.erp.modules.finance.repository.PaymentAllocationMapper;
import com.zhul.erp.modules.finance.repository.PaymentMapper;
import com.zhul.erp.modules.masterdata.support.PaymentTerms;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.entity.BankAccountDO;
import com.zhul.erp.modules.system.repository.BankAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 应付查询（spec finance/accounts-payable「应付单页」）：列表、统计、详情、按来源查、登记付款的可选项 */
@Service
@RequiredArgsConstructor
public class PayableQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PayableMapper payableMapper;
    private final PayableInstallmentMapper installmentMapper;
    private final PaymentAllocationMapper allocationMapper;
    private final PaymentMapper paymentMapper;
    private final PurchaseOrderMapper orderMapper;
    private final BankAccountMapper bankAccountMapper;
    private final AttachmentService attachments;

    public PageResult<PayableVO> page(PayableQuery q) {
        int size = q.getPageSize() == null ? 20 : Math.min(Math.max(q.getPageSize(), 1), MAX_PAGE_SIZE);
        int page = q.getPage() == null ? 1 : Math.max(q.getPage(), 1);
        LambdaQueryWrapper<PayableDO> w = base();
        LocalDate today = LocalDate.now();
        if (!Boolean.TRUE.equals(q.getShowClosed())) {
            w.and(x -> x.ne(PayableDO::getStatus, FinanceConstants.STATUS_CLOSED).or().gt(PayableDO::getCreditAmount, 0));
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String k = q.getKeyword().trim();
            w.and(x -> x.like(PayableDO::getPayableNo, k).or().like(PayableDO::getPayeeName, k).or().like(PayableDO::getSourceNo, k));
        }
        if (q.getPayeeType() != null) {
            w.eq(PayableDO::getPayeeType, q.getPayeeType());
        }
        if (q.getStatus() != null) {
            w.eq(PayableDO::getStatus, q.getStatus());
        }
        if (StringUtils.hasText(q.getCurrencyCode())) {
            w.eq(PayableDO::getCurrencyCode, q.getCurrencyCode());
        }
        if (Boolean.TRUE.equals(q.getHasCredit())) {
            w.gt(PayableDO::getCreditAmount, 0);
        }
        String due = q.getDue() == null ? "" : q.getDue();
        switch (due) {
            case "OVERDUE" -> w.lt(PayableDO::getNextDueDate, today);
            case "SOON" -> w.between(PayableDO::getNextDueDate, today, today.plusDays(FinanceConstants.DUE_SOON_DAYS));
            case "MONTH" -> w.between(PayableDO::getNextDueDate, today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
            case "WAITING" -> w.isNull(PayableDO::getNextDueDate)
                    .in(PayableDO::getStatus, FinanceConstants.STATUS_UNPAID, FinanceConstants.STATUS_PARTIAL);
            default -> {
                // 不按到期筛选
            }
        }
        if ("DUE".equals(q.getSort()) || !due.isEmpty()) {
            w.orderByAsc(PayableDO::getNextDueDate).orderByDesc(PayableDO::getId);
        } else {
            w.orderByDesc(PayableDO::getUpdateTime).orderByDesc(PayableDO::getId);
        }
        Page<PayableDO> p = payableMapper.selectPage(new Page<>(page, size), w);
        List<PayableVO> rows = p.getRecords().stream().map(d -> toVo(d, null)).toList();
        fillTerms(rows);
        return PageResult.of(p.getTotal(), rows);
    }

    /** 顶部统计：按期次算未付，外币按应付汇率折人民币 */
    public PayableStatsVO stats() {
        LocalDate today = LocalDate.now();
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        LocalDate monthStart = today.withDayOfMonth(1);
        List<PayableDO> open = payableMapper.selectList(base()
                .and(x -> x.in(PayableDO::getStatus, FinanceConstants.STATUS_UNPAID, FinanceConstants.STATUS_PARTIAL)
                        .or().gt(PayableDO::getCreditAmount, 0)));
        PayableStatsVO vo = new PayableStatsVO();
        if (open.isEmpty()) {
            return vo;
        }
        Map<Long, List<PayableInstallmentDO>> insts = installmentsOf(open.stream().map(PayableDO::getId).toList());
        for (PayableDO p : open) {
            BigDecimal rate = p.getExchangeRate() == null ? BigDecimal.ONE : p.getExchangeRate();
            BigDecimal month = BigDecimal.ZERO;
            BigDecimal overdue = BigDecimal.ZERO;
            BigDecimal soon = BigDecimal.ZERO;
            if (p.getStatus() != FinanceConstants.STATUS_CLOSED) {
                for (PayableInstallmentDO i : insts.getOrDefault(p.getId(), List.of())) {
                    BigDecimal unpaid = i.getAmount().subtract(i.getPaidAmount()).max(BigDecimal.ZERO);
                    if (unpaid.signum() == 0 || i.getDueDate() == null) {
                        continue;
                    }
                    if (i.getDueDate().isBefore(today)) {
                        overdue = overdue.add(unpaid);
                    } else if (!i.getDueDate().isAfter(today.plusDays(FinanceConstants.DUE_SOON_DAYS))) {
                        soon = soon.add(unpaid);
                    }
                    if (!i.getDueDate().isBefore(monthStart) && !i.getDueDate().isAfter(monthEnd)) {
                        month = month.add(unpaid);
                    }
                }
            }
            if (month.signum() > 0) {
                vo.setMonthDue(vo.getMonthDue().add(cny(month, rate)));
                vo.setMonthDueCount(vo.getMonthDueCount() + 1);
            }
            if (overdue.signum() > 0) {
                vo.setOverdue(vo.getOverdue().add(cny(overdue, rate)));
                vo.setOverdueCount(vo.getOverdueCount() + 1);
            }
            if (soon.signum() > 0) {
                vo.setSoon(vo.getSoon().add(cny(soon, rate)));
                vo.setSoonCount(vo.getSoonCount() + 1);
            }
            if (p.getCreditAmount() != null && p.getCreditAmount().signum() > 0) {
                vo.setCredit(vo.getCredit().add(cny(p.getCreditAmount(), rate)));
                vo.setCreditCount(vo.getCreditCount() + 1);
            }
        }
        return vo;
    }

    public PayableVO detail(Long id) {
        PayableDO p = payableMapper.selectOne(base().eq(PayableDO::getId, id));
        if (p == null) {
            throw new BizException("应付单不存在");
        }
        return fullVo(p);
    }

    /** 采购订单、运费账单的付款面板；还没有应付单时返回空 */
    public PayableVO bySource(int sourceType, Long sourceId) {
        PayableDO p = payableMapper.selectOne(base().eq(PayableDO::getSourceType, sourceType).eq(PayableDO::getSourceId, sourceId));
        return p == null ? null : fullVo(p);
    }

    /** 列表的付款状态：来源 ID → 应付单（不含明细） */
    public Map<Long, PayableVO> statusBySources(int sourceType, Collection<Long> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return Map.of();
        }
        return payableMapper.selectList(base().eq(PayableDO::getSourceType, sourceType).in(PayableDO::getSourceId, sourceIds))
                .stream().collect(Collectors.toMap(PayableDO::getSourceId, d -> toVo(d, null), (a, b) -> a));
    }

    /** 登记付款的可选收款方：有未付或欠款的 */
    public List<PayeeOptionVO> payees(String keyword) {
        LambdaQueryWrapper<PayableDO> w = base().and(x -> x.in(PayableDO::getStatus, FinanceConstants.STATUS_UNPAID,
                FinanceConstants.STATUS_PARTIAL).or().gt(PayableDO::getCreditAmount, 0));
        if (StringUtils.hasText(keyword)) {
            w.like(PayableDO::getPayeeName, keyword.trim());
        }
        Map<String, PayeeOptionVO> out = new LinkedHashMap<>();
        for (PayableDO p : payableMapper.selectList(w.orderByAsc(PayableDO::getPayeeName))) {
            String key = payeeKey(p.getPayeeType(), p.getSupplierId(), p.getShopPlatform(), p.getShopName(), p.getPayeeName())
                    + "|" + p.getCurrencyCode();
            PayeeOptionVO o = out.computeIfAbsent(key, k -> {
                PayeeOptionVO n = new PayeeOptionVO();
                n.setKey(k);
                n.setAnchorId(p.getId());
                n.setPayeeType(p.getPayeeType());
                n.setSupplierId(p.getSupplierId());
                n.setShopPlatform(p.getShopPlatform());
                n.setShopName(p.getShopName());
                n.setPayeeName(p.getPayeeName());
                n.setCurrencyCode(p.getCurrencyCode());
                n.setUnpaid(BigDecimal.ZERO);
                n.setCredit(BigDecimal.ZERO);
                return n;
            });
            if (p.getStatus() != FinanceConstants.STATUS_CLOSED) {
                o.setUnpaid(o.getUnpaid().add(p.getAmount().subtract(p.getPaidAmount()).max(BigDecimal.ZERO)));
            }
            o.setCredit(o.getCredit().add(p.getCreditAmount()));
        }
        return new ArrayList<>(out.values());
    }

    /** 某收款方、某币种有未付或欠款的应付单（含付款计划），按到期先后 */
    public List<PayableVO> openOf(Long payableId) {
        PayableDO anchor = payableMapper.selectOne(base().eq(PayableDO::getId, payableId));
        if (anchor == null) {
            throw new BizException("应付单不存在");
        }
        List<PayableDO> list = samePayee(anchor).stream()
                .filter(p -> p.getCreditAmount().signum() > 0 || p.getStatus() == FinanceConstants.STATUS_UNPAID
                        || p.getStatus() == FinanceConstants.STATUS_PARTIAL)
                .sorted(Comparator.comparing((PayableDO p) -> p.getNextDueDate() == null ? LocalDate.MAX : p.getNextDueDate())
                        .thenComparing(PayableDO::getId))
                .toList();
        Map<Long, List<PayableInstallmentDO>> insts = installmentsOf(list.stream().map(PayableDO::getId).toList());
        return list.stream().map(p -> toVo(p, insts.getOrDefault(p.getId(), List.of()))).toList();
    }

    /** 同一收款方（类型 + 供应商或店铺）、同一币种的应付单（不含已删除） */
    public List<PayableDO> samePayee(PayableDO anchor) {
        LambdaQueryWrapper<PayableDO> w = base()
                .eq(PayableDO::getPayeeType, anchor.getPayeeType())
                .eq(PayableDO::getCurrencyCode, anchor.getCurrencyCode());
        if (anchor.getPayeeType() == FinanceConstants.PAYEE_SHOP) {
            w.eq(PayableDO::getShopPlatform, anchor.getShopPlatform()).eq(PayableDO::getShopName, anchor.getShopName());
        } else if (anchor.getPayeeType() == FinanceConstants.PAYEE_OTHER) {
            w.eq(PayableDO::getPayeeName, anchor.getPayeeName());
        } else {
            w.eq(PayableDO::getSupplierId, anchor.getSupplierId());
        }
        return payableMapper.selectList(w);
    }

    static String payeeKey(Integer type, Long supplierId, String platform, String shop, String name) {
        if (type == FinanceConstants.PAYEE_SHOP) {
            return type + ":" + platform + ":" + shop;
        }
        return type == FinanceConstants.PAYEE_OTHER ? type + ":" + name : type + ":" + supplierId;
    }

    private LambdaQueryWrapper<PayableDO> base() {
        return new LambdaQueryWrapper<PayableDO>()
                .eq(PayableDO::getTenantId, PiStore.tenantId())
                .isNull(PayableDO::getDeletedAt);
    }

    private PayableVO fullVo(PayableDO p) {
        PayableVO vo = toVo(p, installmentsOf(List.of(p.getId())).getOrDefault(p.getId(), List.of()));
        fillTerms(List.of(vo));
        vo.setPayments(paymentLines(p.getId()));
        return vo;
    }

    /** 采购订单的付款条件文字（列表、详情显示） */
    private void fillTerms(List<PayableVO> rows) {
        List<Long> poIds = rows.stream().filter(r -> r.getSourceType() == FinanceConstants.SOURCE_PURCHASE_ORDER)
                .map(PayableVO::getSourceId).toList();
        if (poIds.isEmpty()) {
            return;
        }
        Map<Long, String> terms = orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrderDO>()
                        .select(PurchaseOrderDO::getId, PurchaseOrderDO::getPaymentTerms)
                        .in(PurchaseOrderDO::getId, poIds))
                .stream().collect(Collectors.toMap(PurchaseOrderDO::getId, o -> termsText(o.getPaymentTerms()), (a, b) -> a));
        rows.forEach(r -> r.setPaymentTerms(terms.get(r.getSourceId())));
    }

    static String termsText(String json) {
        return PaymentTerms.fromJson(json).stream()
                .map(t -> t.getPercent().stripTrailingZeros().toPlainString() + "% " + triggerName(t.getTrigger(),
                        t.getDays() == null ? 0 : t.getDays()))
                .collect(Collectors.joining(" · "));
    }

    static String triggerName(int trigger, int days) {
        if (trigger == FinanceConstants.TRIGGER_STATEMENT) {
            return "对账确认";
        }
        if (trigger == PaymentTerms.AFTER_RECEIPT && days > 0) {
            return "入库后 " + days + " 天";
        }
        return PaymentTerms.TRIGGER_NAMES.getOrDefault(trigger, "");
    }

    PayableVO toVo(PayableDO p, List<PayableInstallmentDO> insts) {
        PayableVO vo = new PayableVO();
        vo.setId(p.getId());
        vo.setPayableNo(p.getPayableNo());
        vo.setPayeeType(p.getPayeeType());
        vo.setSupplierId(p.getSupplierId());
        vo.setShopPlatform(p.getShopPlatform());
        vo.setShopName(p.getShopName());
        vo.setPayeeName(p.getPayeeName());
        vo.setSourceType(p.getSourceType());
        vo.setSourceId(p.getSourceId());
        vo.setSourceNo(p.getSourceNo());
        vo.setCurrencyCode(p.getCurrencyCode());
        vo.setAmount(p.getAmount());
        vo.setExchangeRate(p.getExchangeRate());
        vo.setAmountCny(p.getAmountCny());
        vo.setPaidAmount(p.getPaidAmount());
        vo.setPaidCny(p.getPaidCny());
        boolean closed = p.getStatus() == FinanceConstants.STATUS_CLOSED;
        vo.setUnpaidAmount(closed ? BigDecimal.ZERO : p.getAmount().subtract(p.getPaidAmount()).max(BigDecimal.ZERO));
        vo.setCreditAmount(p.getCreditAmount());
        BigDecimal rate = p.getExchangeRate() == null ? BigDecimal.ONE : p.getExchangeRate();
        vo.setExchangeDiff("CNY".equals(p.getCurrencyCode()) ? BigDecimal.ZERO
                : p.getPaidCny().subtract(cny(p.getPaidAmount().min(p.getAmount()).max(BigDecimal.ZERO), rate)));
        vo.setStatus(p.getStatus());
        vo.setNextDueDate(p.getNextDueDate());
        LocalDate today = LocalDate.now();
        if (!closed && vo.getUnpaidAmount().signum() > 0) {
            Due d = due(p.getNextDueDate(), today);
            vo.setDueState(d == null ? "WAITING" : d.state());
            vo.setDueDays(d == null ? null : d.days());
        }
        vo.setCreateTime(p.getCreateTime());
        vo.setCreateBy(p.getCreateBy());
        vo.setUpdateTime(p.getUpdateTime());
        vo.setUpdateBy(p.getUpdateBy());
        if (insts != null) {
            for (PayableInstallmentDO i : insts) {
                PayableVO.Installment iv = new PayableVO.Installment();
                iv.setSeq(i.getSeq());
                iv.setRatio(i.getRatio());
                iv.setTriggerType(i.getTriggerType());
                iv.setOffsetDays(i.getOffsetDays());
                iv.setTriggerName(triggerName(i.getTriggerType(), i.getOffsetDays() == null ? 0 : i.getOffsetDays()));
                iv.setAmount(i.getAmount());
                iv.setDueDate(i.getDueDate());
                iv.setPaidAmount(i.getPaidAmount());
                iv.setStatus(i.getStatus());
                if (!closed && i.getAmount().compareTo(i.getPaidAmount()) > 0) {
                    Due d = due(i.getDueDate(), today);
                    iv.setDueState(d == null ? "WAITING" : d.state());
                    iv.setDueDays(d == null ? null : d.days());
                }
                vo.getInstallments().add(iv);
            }
        }
        return vo;
    }

    private record Due(String state, int days) {
    }

    /** 已逾期（days 为逾期天数）、即将到期（days 为剩余天数）、未到期（NORMAL）；到期日为空返回 null */
    private static Due due(LocalDate date, LocalDate today) {
        if (date == null) {
            return null;
        }
        long diff = ChronoUnit.DAYS.between(today, date);
        if (diff < 0) {
            return new Due("OVERDUE", (int) -diff);
        }
        return new Due(diff <= FinanceConstants.DUE_SOON_DAYS ? "SOON" : "NORMAL", (int) diff);
    }

    private Map<Long, List<PayableInstallmentDO>> installmentsOf(List<Long> payableIds) {
        if (payableIds.isEmpty()) {
            return Map.of();
        }
        return installmentMapper.selectList(new LambdaQueryWrapper<PayableInstallmentDO>()
                        .in(PayableInstallmentDO::getPayableId, payableIds)
                        .isNull(PayableInstallmentDO::getDeletedAt)
                        .orderByAsc(PayableInstallmentDO::getSeq))
                .stream().collect(Collectors.groupingBy(PayableInstallmentDO::getPayableId));
    }

    /** 与本单有关的付款单（含作废的，前端可以隐藏） */
    private List<PayableVO.PaymentLine> paymentLines(Long payableId) {
        List<PaymentAllocationDO> allocs = allocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationDO>()
                .and(w -> w.eq(PaymentAllocationDO::getPayableId, payableId).or().eq(PaymentAllocationDO::getCreditPayableId, payableId))
                .orderByAsc(PaymentAllocationDO::getId));
        if (allocs.isEmpty()) {
            return List.of();
        }
        Map<Long, PaymentDO> payments = paymentMapper.selectBatchIds(allocs.stream().map(PaymentAllocationDO::getPaymentId).distinct().toList())
                .stream().collect(Collectors.toMap(PaymentDO::getId, Function.identity()));
        Set<Long> otherIds = new java.util.HashSet<>();
        allocs.forEach(a -> {
            otherIds.add(a.getPayableId());
            if (a.getCreditPayableId() != null) {
                otherIds.add(a.getCreditPayableId());
            }
        });
        Map<Long, String> nos = payableMapper.selectBatchIds(otherIds).stream()
                .collect(Collectors.toMap(PayableDO::getId, PayableDO::getPayableNo));
        Map<Long, String> accounts = accountNames(payments.values().stream().map(PaymentDO::getBankAccountId).toList());
        Map<Long, List<AttachmentVO>> files = attachments.list(PaymentService.OWNER_TYPE, payments.keySet());
        List<PayableVO.PaymentLine> out = new ArrayList<>();
        for (PaymentAllocationDO a : allocs) {
            PaymentDO pay = payments.get(a.getPaymentId());
            if (pay == null) {
                continue;
            }
            boolean into = payableId.equals(a.getPayableId());
            int kind = a.getFromCredit() == null ? 0 : a.getFromCredit();
            PayableVO.PaymentLine l = new PayableVO.PaymentLine();
            l.setPaymentId(pay.getId());
            l.setPaymentNo(pay.getPaymentNo());
            l.setKind(pay.getKind());
            l.setRelation(into ? kind : 3);
            l.setMethod(pay.getMethod());
            l.setAccountName(accounts.get(pay.getBankAccountId()));
            l.setAdvanceUser(pay.getAdvanceUser());
            l.setPayDate(pay.getPayDate());
            l.setAmount(a.getAmount());
            l.setAmountCny(a.getAmountCny());
            l.setOtherPayableNo(into ? (a.getCreditPayableId() == null ? null : nos.get(a.getCreditPayableId())) : nos.get(a.getPayableId()));
            l.setVoided(pay.getVoidAt() != null);
            l.setCreateBy(pay.getCreateBy());
            l.setAttachments(refs(files.getOrDefault(pay.getId(), List.of())));
            out.add(l);
        }
        return out;
    }

    static List<PayableVO.AttachmentRef> refs(List<AttachmentVO> list) {
        return list.stream().map(f -> {
            PayableVO.AttachmentRef r = new PayableVO.AttachmentRef();
            r.setId(f.getId());
            r.setFileName(f.getFileName());
            r.setKind(f.getKind());
            return r;
        }).toList();
    }

    /** 公司账户显示名：银行 · 户名 ****尾号 */
    Map<Long, String> accountNames(Collection<Long> ids) {
        List<Long> list = ids.stream().filter(Objects::nonNull).distinct().toList();
        // 可变 Map：调用方会用可能为空的账户 ID 取值
        Map<Long, String> out = new HashMap<>();
        if (list.isEmpty()) {
            return out;
        }
        for (BankAccountDO b : bankAccountMapper.selectBatchIds(list)) {
            String no = b.getAccountNo() == null ? "" : b.getAccountNo().trim();
            String tail = no.length() > 4 ? no.substring(no.length() - 4) : no;
            out.put(b.getId().longValue(), b.getBankName() + " · " + b.getAccountName() + (tail.isEmpty() ? "" : " ****" + tail));
        }
        return out;
    }

    static BigDecimal cny(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}

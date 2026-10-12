package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.PaymentQuery;
import com.zhul.erp.modules.finance.dto.PaymentStatsVO;
import com.zhul.erp.modules.finance.dto.PaymentVO;
import com.zhul.erp.modules.finance.dto.ReceivedRefundRequest;
import com.zhul.erp.modules.finance.dto.RefundRequest;
import com.zhul.erp.modules.finance.dto.RegisterPaymentRequest;
import com.zhul.erp.modules.finance.entity.PayableDO;
import com.zhul.erp.modules.finance.entity.PaymentAllocationDO;
import com.zhul.erp.modules.finance.entity.PaymentDO;
import com.zhul.erp.modules.finance.repository.PayableMapper;
import com.zhul.erp.modules.finance.repository.PaymentAllocationMapper;
import com.zhul.erp.modules.finance.repository.PaymentMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.entity.BankAccountDO;
import com.zhul.erp.modules.system.repository.BankAccountMapper;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 付款单（spec finance/supplier-payment）：登记付款（公司对公、员工垫付）、欠款抵扣、供应商退款、作废。
 * 登记前按 ID 升序锁住涉及的应付单再校验；幂等键保证重复提交只生成一笔。
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** 付款截图的附件归属类型 */
    public static final String OWNER_TYPE = AttachmentService.PAYMENT;
    private static final String MENU = "付款单";
    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentMapper paymentMapper;
    private final PaymentAllocationMapper allocationMapper;
    private final PayableMapper payableMapper;
    private final PayableSyncService sync;
    private final PayableQueryService query;
    private final BankAccountMapper bankAccountMapper;
    private final DocumentNumberService documentNumberService;
    private final AttachmentService attachments;
    private final PermissionChecker perm;
    private final LogService logService;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public PaymentVO register(RegisterPaymentRequest req) {
        PaymentDO dup = byKey(req.getIdempotencyKey());
        if (dup != null) {
            return toVo(dup);
        }
        int method = req.getMethod();
        if (method == FinanceConstants.METHOD_COMPANY) {
            if (!perm.has(FinanceConstants.PERM_REGISTER)) {
                throw new BizException("没有「付款登记」权限，公司对公付款由财务登记");
            }
            requireAccount(req.getBankAccountId());
        } else if (method == FinanceConstants.METHOD_ADVANCE) {
            if (!perm.has(FinanceConstants.PERM_ADVANCE) && !perm.has(FinanceConstants.PERM_REGISTER)) {
                throw new BizException("没有「垫付登记」权限");
            }
        } else {
            throw new BizException("付款方式无效");
        }
        if (req.getPayDate().isAfter(LocalDate.now())) {
            throw new BizException("付款日期不能晚于今天");
        }
        List<RegisterPaymentRequest.Line> targets = merge(req.getAllocations());
        List<RegisterPaymentRequest.Line> credits = merge(req.getCredits());
        TreeSet<Long> ids = new TreeSet<>();
        targets.forEach(l -> ids.add(l.getPayableId()));
        credits.forEach(l -> ids.add(l.getPayableId()));
        Map<Long, PayableDO> locked = lock(ids);
        PayableDO anchor = locked.get(targets.get(0).getPayableId());
        for (PayableDO p : locked.values()) {
            if (!samePayee(anchor, p)) {
                throw new BizException("只能核销同一收款方、同一币种的应付单");
            }
        }
        BigDecimal amount = scale(req.getAmount());
        BigDecimal creditTotal = BigDecimal.ZERO;
        for (RegisterPaymentRequest.Line c : credits) {
            PayableDO src = locked.get(c.getPayableId());
            if (scale(c.getAmount()).compareTo(src.getCreditAmount()) > 0) {
                throw new BizException("应付单 " + src.getPayableNo() + " 的欠款只有 " + src.getCreditAmount() + "，请刷新后重试");
            }
            creditTotal = creditTotal.add(scale(c.getAmount()));
        }
        BigDecimal allocTotal = BigDecimal.ZERO;
        for (RegisterPaymentRequest.Line t : targets) {
            PayableDO p = locked.get(t.getPayableId());
            if (p.getStatus() == FinanceConstants.STATUS_CLOSED) {
                throw new BizException("应付单 " + p.getPayableNo() + " 已关闭，不能再核销");
            }
            BigDecimal unpaid = p.getAmount().subtract(p.getPaidAmount()).max(BigDecimal.ZERO);
            if (scale(t.getAmount()).compareTo(unpaid) > 0) {
                throw new BizException("应付单 " + p.getPayableNo() + " 未付金额已变化，请刷新后重试");
            }
            allocTotal = allocTotal.add(scale(t.getAmount()));
        }
        if (allocTotal.compareTo(amount.add(creditTotal)) != 0) {
            throw new BizException("核销合计须等于付款金额加欠款抵扣");
        }
        if (amount.add(creditTotal).signum() <= 0) {
            throw new BizException("付款金额须大于 0");
        }
        BigDecimal amountCny = cnyOf(anchor.getCurrencyCode(), amount, req.getAmountCny());

        PaymentDO pay = new PaymentDO();
        pay.setTenantId(PiStore.tenantId());
        pay.setPaymentNo(documentNumberService.next(DocumentType.PY));
        pay.setKind(FinanceConstants.KIND_PAYMENT);
        copyPayee(anchor, pay);
        pay.setMethod(method);
        pay.setBankAccountId(method == FinanceConstants.METHOD_COMPANY ? req.getBankAccountId() : null);
        pay.setAdvanceUser(method == FinanceConstants.METHOD_ADVANCE ? SecurityUtils.getCurrentUsername() : "");
        pay.setPayDate(req.getPayDate());
        pay.setCurrencyCode(anchor.getCurrencyCode());
        pay.setAmount(amount);
        pay.setAmountCny(amountCny);
        pay.setActualRate(rateOf(amount, amountCny));
        pay.setCreditUsed(creditTotal);
        pay.setRemark(req.getRemark() == null ? "" : req.getRemark().trim());
        pay.setReimburseStatus(method == FinanceConstants.METHOD_ADVANCE ? FinanceConstants.REIMBURSE_PENDING
                : FinanceConstants.REIMBURSE_NA);
        pay.setIdempotencyKey(req.getIdempotencyKey());
        paymentMapper.insert(pay);
        allocate(pay, targets, credits);
        attachments.attach(OWNER_TYPE, pay.getId(), req.getAttachmentIds());
        ids.forEach(sync::refresh);
        logService.recordOperateLog(MENU, "登记付款", null, Map.of("paymentNo", pay.getPaymentNo(), "payee", pay.getPayeeName(),
                "amount", pay.getCurrencyCode() + " " + amount, "method", method == FinanceConstants.METHOD_COMPANY ? "公司对公" : "员工垫付"));
        return toVo(paymentMapper.selectById(pay.getId()));
    }

    /** 按顺序把「实付 + 各笔欠款抵扣」填到各应付单；实付部分按实际汇率折人民币，尾差给最后一笔 */
    private void allocate(PaymentDO pay, List<RegisterPaymentRequest.Line> targets, List<RegisterPaymentRequest.Line> credits) {
        record Source(Long creditPayableId, BigDecimal left) {
        }
        Deque<Source> sources = new ArrayDeque<>();
        if (pay.getAmount().signum() > 0) {
            sources.add(new Source(null, pay.getAmount()));
        }
        credits.forEach(c -> sources.add(new Source(c.getPayableId(), scale(c.getAmount()))));
        List<PaymentAllocationDO> actual = new ArrayList<>();
        for (RegisterPaymentRequest.Line t : targets) {
            BigDecimal need = scale(t.getAmount());
            while (need.signum() > 0 && !sources.isEmpty()) {
                Source s = sources.poll();
                BigDecimal take = need.min(s.left());
                PaymentAllocationDO a = new PaymentAllocationDO();
                a.setTenantId(pay.getTenantId());
                a.setPaymentId(pay.getId());
                a.setPayableId(t.getPayableId());
                a.setAmount(take);
                a.setFromCredit(s.creditPayableId() == null ? 0 : 1);
                a.setCreditPayableId(s.creditPayableId());
                a.setAmountCny(BigDecimal.ZERO);
                if (s.creditPayableId() == null) {
                    actual.add(a);
                }
                allocationMapper.insert(a);
                need = need.subtract(take);
                if (s.left().compareTo(take) > 0) {
                    sources.push(new Source(s.creditPayableId(), s.left().subtract(take)));
                }
            }
        }
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < actual.size(); i++) {
            PaymentAllocationDO a = actual.get(i);
            BigDecimal c = i == actual.size() - 1 ? pay.getAmountCny().subtract(assigned)
                    : a.getAmount().multiply(pay.getActualRate()).setScale(2, RoundingMode.HALF_UP);
            assigned = assigned.add(c);
            a.setAmountCny(c);
            allocationMapper.updateById(a);
        }
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public PaymentVO refund(RefundRequest req) {
        PaymentDO dup = byKey(req.getIdempotencyKey());
        if (dup != null) {
            return toVo(dup);
        }
        if (!perm.has(FinanceConstants.PERM_REGISTER)) {
            throw new BizException("没有「付款登记」权限");
        }
        requireAccount(req.getBankAccountId());
        if (req.getRefundDate().isAfter(LocalDate.now())) {
            throw new BizException("退款日期不能晚于今天");
        }
        PayableDO p = lock(new TreeSet<>(List.of(req.getPayableId()))).get(req.getPayableId());
        BigDecimal amount = scale(req.getAmount());
        if (amount.compareTo(p.getCreditAmount()) > 0) {
            throw new BizException("退款不能超过欠款 " + p.getCreditAmount());
        }
        BigDecimal amountCny = cnyOf(p.getCurrencyCode(), amount, req.getAmountCny());
        PaymentDO pay = new PaymentDO();
        pay.setTenantId(PiStore.tenantId());
        pay.setPaymentNo(documentNumberService.next(DocumentType.PY));
        pay.setKind(FinanceConstants.KIND_REFUND);
        copyPayee(p, pay);
        pay.setMethod(FinanceConstants.METHOD_COMPANY);
        pay.setBankAccountId(req.getBankAccountId());
        pay.setAdvanceUser("");
        pay.setPayDate(req.getRefundDate());
        pay.setCurrencyCode(p.getCurrencyCode());
        pay.setAmount(amount);
        pay.setAmountCny(amountCny);
        pay.setActualRate(rateOf(amount, amountCny));
        pay.setCreditUsed(BigDecimal.ZERO);
        pay.setRemark(req.getRemark() == null ? "" : req.getRemark().trim());
        pay.setReimburseStatus(FinanceConstants.REIMBURSE_NA);
        pay.setIdempotencyKey(req.getIdempotencyKey());
        paymentMapper.insert(pay);
        PaymentAllocationDO a = new PaymentAllocationDO();
        a.setTenantId(pay.getTenantId());
        a.setPaymentId(pay.getId());
        a.setPayableId(p.getId());
        a.setAmount(amount);
        a.setAmountCny(amountCny);
        a.setFromCredit(2);
        allocationMapper.insert(a);
        attachments.attach(OWNER_TYPE, pay.getId(), req.getAttachmentIds());
        sync.refresh(p.getId());
        logService.recordOperateLog(MENU, "登记供应商退款", null, Map.of("paymentNo", pay.getPaymentNo(), "payable", p.getPayableNo(),
                "amount", pay.getCurrencyCode() + " " + amount));
        return toVo(paymentMapper.selectById(pay.getId()));
    }

    /**
     * 员工登记已收到供应商退款（③b）：只能对本人垫付过、有欠款的应付单登记；冲减欠款，成为本人待报销的负数项。
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public PaymentVO refundReceived(ReceivedRefundRequest req, boolean advancedByMe) {
        PaymentDO dup = byKey(req.getIdempotencyKey());
        if (dup != null) {
            return toVo(dup);
        }
        if (!advancedByMe) {
            throw new BizException("只能对你垫付过的应付单登记已收到退款");
        }
        if (req.getReceivedDate().isAfter(LocalDate.now())) {
            throw new BizException("收到日期不能晚于今天");
        }
        PayableDO p = lock(new TreeSet<>(List.of(req.getPayableId()))).get(req.getPayableId());
        BigDecimal amount = scale(req.getAmount());
        if (amount.compareTo(p.getCreditAmount()) > 0) {
            throw new BizException("退款不能超过欠款 " + p.getCreditAmount());
        }
        BigDecimal amountCny = cnyOf(p.getCurrencyCode(), amount, req.getAmountCny());
        PaymentDO pay = new PaymentDO();
        pay.setTenantId(PiStore.tenantId());
        pay.setPaymentNo(documentNumberService.next(DocumentType.PY));
        pay.setKind(FinanceConstants.KIND_REFUND);
        copyPayee(p, pay);
        pay.setMethod(FinanceConstants.METHOD_ADVANCE);
        pay.setAdvanceUser(SecurityUtils.getCurrentUsername());
        pay.setPayDate(req.getReceivedDate());
        pay.setCurrencyCode(p.getCurrencyCode());
        pay.setAmount(amount);
        pay.setAmountCny(amountCny);
        pay.setActualRate(rateOf(amount, amountCny));
        pay.setCreditUsed(BigDecimal.ZERO);
        pay.setRemark(req.getRemark() == null ? "" : req.getRemark().trim());
        pay.setReimburseStatus(FinanceConstants.REIMBURSE_PENDING);
        pay.setIdempotencyKey(req.getIdempotencyKey());
        paymentMapper.insert(pay);
        PaymentAllocationDO a = new PaymentAllocationDO();
        a.setTenantId(pay.getTenantId());
        a.setPaymentId(pay.getId());
        a.setPayableId(p.getId());
        a.setAmount(amount);
        a.setAmountCny(amountCny);
        a.setFromCredit(2);
        allocationMapper.insert(a);
        attachments.attach(OWNER_TYPE, pay.getId(), req.getAttachmentIds());
        sync.refresh(p.getId());
        logService.recordOperateLog(MENU, "登记已收到退款", null, Map.of("paymentNo", pay.getPaymentNo(), "payable", p.getPayableNo(),
                "amount", pay.getCurrencyCode() + " " + amount));
        return toVo(paymentMapper.selectById(pay.getId()));
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public PaymentVO voidPayment(Long id, String reason) {
        PaymentDO pay = visible(id);
        if (pay.getVoidAt() != null) {
            throw new BizException("付款单已作废");
        }
        if (Objects.equals(pay.getKind(), FinanceConstants.KIND_REIMBURSE)) {
            throw new BizException("报销付款请在报销单上撤销已付");
        }
        if (!hasVoidRight(pay)) {
            throw new BizException("只有登记人或有「付款登记」权限的人可以作废");
        }
        List<PaymentAllocationDO> allocs = allocationsOf(List.of(id)).getOrDefault(id, List.of());
        TreeSet<Long> ids = new TreeSet<>();
        allocs.forEach(a -> {
            ids.add(a.getPayableId());
            if (a.getCreditPayableId() != null) {
                ids.add(a.getCreditPayableId());
            }
        });
        lock(ids);
        pay = paymentMapper.selectOne(new LambdaQueryWrapper<PaymentDO>().eq(PaymentDO::getId, id).last("FOR UPDATE"));
        if (pay.getVoidAt() != null) {
            throw new BizException("付款单已作废");
        }
        if (Objects.equals(pay.getReimburseStatus(), FinanceConstants.REIMBURSE_CLAIMED)) {
            throw new BizException("这笔垫付已进入报销单，不能作废");
        }
        LocalDateTime now = LocalDateTime.now();
        pay.setVoidAt(now);
        pay.setVoidBy(SecurityUtils.getCurrentUsername());
        pay.setVoidReason(reason.trim());
        paymentMapper.updateById(pay);
        allocationMapper.update(null, new LambdaUpdateWrapper<PaymentAllocationDO>()
                .eq(PaymentAllocationDO::getPaymentId, id)
                .isNull(PaymentAllocationDO::getDeletedAt)
                .set(PaymentAllocationDO::getDeletedAt, now)
                .set(PaymentAllocationDO::getUpdateTime, now)
                .set(PaymentAllocationDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
        ids.forEach(sync::refresh);
        logService.recordOperateLog(MENU, "作废付款", Map.of("paymentNo", pay.getPaymentNo(), "status", "有效"),
                Map.of("paymentNo", pay.getPaymentNo(), "status", "已作废", "reason", reason.trim()));
        return toVo(pay);
    }

    public PageResult<PaymentVO> page(PaymentQuery q) {
        int size = q.getPageSize() == null ? 20 : Math.min(Math.max(q.getPageSize(), 1), MAX_PAGE_SIZE);
        int page = q.getPage() == null ? 1 : Math.max(q.getPage(), 1);
        LambdaQueryWrapper<PaymentDO> w = filter(q);
        if (!Boolean.TRUE.equals(q.getShowVoid())) {
            w.isNull(PaymentDO::getVoidAt);
        }
        w.orderByDesc(PaymentDO::getUpdateTime).orderByDesc(PaymentDO::getId);
        Page<PaymentDO> p = paymentMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getTotal(), toVos(p.getRecords()));
    }

    /** 付款人民币合计：没有选日期时取本月；只算有效的付款（不含退款） */
    public PaymentStatsVO stats(PaymentQuery q) {
        PaymentQuery scope = q == null ? new PaymentQuery() : q;
        if (scope.getDateFrom() == null && scope.getDateTo() == null) {
            LocalDate today = LocalDate.now();
            scope.setDateFrom(today.withDayOfMonth(1));
            scope.setDateTo(today.withDayOfMonth(today.lengthOfMonth()));
        }
        LambdaQueryWrapper<PaymentDO> w = filter(scope).isNull(PaymentDO::getVoidAt)
                .in(PaymentDO::getKind, FinanceConstants.KIND_PAYMENT, FinanceConstants.KIND_REIMBURSE)
                .select(PaymentDO::getKind, PaymentDO::getMethod, PaymentDO::getAmountCny);
        PaymentStatsVO vo = new PaymentStatsVO();
        for (PaymentDO p : paymentMapper.selectList(w)) {
            // 报销付款单独统计，不计入付款合计（垫付时已经算过一次）
            if (p.getKind() == FinanceConstants.KIND_REIMBURSE) {
                vo.setReimburse(vo.getReimburse().add(p.getAmountCny()));
                continue;
            }
            vo.setTotal(vo.getTotal().add(p.getAmountCny()));
            if (p.getMethod() == FinanceConstants.METHOD_ADVANCE) {
                vo.setAdvance(vo.getAdvance().add(p.getAmountCny()));
            } else {
                vo.setCompany(vo.getCompany().add(p.getAmountCny()));
            }
            vo.setCount(vo.getCount() + 1);
        }
        return vo;
    }

    private LambdaQueryWrapper<PaymentDO> filter(PaymentQuery q) {
        LambdaQueryWrapper<PaymentDO> w = new LambdaQueryWrapper<PaymentDO>()
                .eq(PaymentDO::getTenantId, PiStore.tenantId())
                .isNull(PaymentDO::getDeletedAt);
        if (StringUtils.hasText(q.getKeyword())) {
            String k = q.getKeyword().trim();
            w.and(x -> x.like(PaymentDO::getPaymentNo, k).or().like(PaymentDO::getPayeeName, k));
        }
        if (q.getDateFrom() != null) {
            w.ge(PaymentDO::getPayDate, q.getDateFrom());
        }
        if (q.getDateTo() != null) {
            w.le(PaymentDO::getPayDate, q.getDateTo());
        }
        if (q.getMethod() != null) {
            w.eq(PaymentDO::getMethod, q.getMethod());
        }
        if (StringUtils.hasText(q.getAdvanceUser())) {
            w.eq(PaymentDO::getAdvanceUser, q.getAdvanceUser().trim());
        }
        if (StringUtils.hasText(q.getCurrencyCode())) {
            w.eq(PaymentDO::getCurrencyCode, q.getCurrencyCode());
        }
        if (q.getKind() != null) {
            w.eq(PaymentDO::getKind, q.getKind());
        }
        return w;
    }

    private PaymentDO visible(Long id) {
        PaymentDO p = paymentMapper.selectOne(new LambdaQueryWrapper<PaymentDO>()
                .eq(PaymentDO::getId, id)
                .eq(PaymentDO::getTenantId, PiStore.tenantId())
                .isNull(PaymentDO::getDeletedAt));
        if (p == null) {
            throw new BizException("付款单不存在");
        }
        return p;
    }

    private boolean canVoid(PaymentDO p) {
        return p.getVoidAt() == null && !Objects.equals(p.getReimburseStatus(), FinanceConstants.REIMBURSE_CLAIMED)
                && !Objects.equals(p.getKind(), FinanceConstants.KIND_REIMBURSE) && hasVoidRight(p);
    }

    /** 登记人本人或有付款登记权限的人 */
    private boolean hasVoidRight(PaymentDO p) {
        return Objects.equals(p.getCreateBy(), SecurityUtils.getCurrentUsername()) || perm.has(FinanceConstants.PERM_REGISTER);
    }

    private PaymentDO byKey(String key) {
        return paymentMapper.selectOne(new LambdaQueryWrapper<PaymentDO>()
                .eq(PaymentDO::getTenantId, PiStore.tenantId())
                .eq(PaymentDO::getIdempotencyKey, key));
    }

    /** 按 ID 升序逐个加锁，避免并发登记互相等待成环 */
    private Map<Long, PayableDO> lock(TreeSet<Long> ids) {
        Map<Long, PayableDO> out = new LinkedHashMap<>();
        for (Long id : ids) {
            PayableDO p = payableMapper.selectOne(new LambdaQueryWrapper<PayableDO>()
                    .eq(PayableDO::getId, id)
                    .eq(PayableDO::getTenantId, PiStore.tenantId())
                    .isNull(PayableDO::getDeletedAt)
                    .last("FOR UPDATE"));
            if (p == null) {
                throw new BizException("应付单不存在");
            }
            out.put(id, p);
        }
        return out;
    }

    private void requireAccount(Long id) {
        if (id == null) {
            throw new BizException("请选择付款账户");
        }
        BankAccountDO b = bankAccountMapper.selectById(id);
        if (b == null || !Objects.equals(b.getTenantId(), PiStore.tenantId())) {
            throw new BizException("付款账户不存在");
        }
    }

    private static boolean samePayee(PayableDO a, PayableDO b) {
        if (!Objects.equals(a.getPayeeType(), b.getPayeeType()) || !Objects.equals(a.getCurrencyCode(), b.getCurrencyCode())) {
            return false;
        }
        if (a.getPayeeType() == FinanceConstants.PAYEE_SHOP) {
            return Objects.equals(a.getShopPlatform(), b.getShopPlatform()) && Objects.equals(a.getShopName(), b.getShopName());
        }
        return a.getPayeeType() == FinanceConstants.PAYEE_OTHER ? Objects.equals(a.getPayeeName(), b.getPayeeName())
                : Objects.equals(a.getSupplierId(), b.getSupplierId());
    }

    private static void copyPayee(PayableDO from, PaymentDO to) {
        to.setPayeeType(from.getPayeeType());
        to.setSupplierId(from.getSupplierId());
        to.setShopPlatform(from.getShopPlatform());
        to.setShopName(from.getShopName());
        to.setPayeeName(from.getPayeeName());
    }

    /** 同一张应付单的多行合并 */
    private static List<RegisterPaymentRequest.Line> merge(List<RegisterPaymentRequest.Line> lines) {
        Map<Long, BigDecimal> m = new LinkedHashMap<>();
        if (lines != null) {
            lines.forEach(l -> m.merge(l.getPayableId(), scale(l.getAmount()), BigDecimal::add));
        }
        return m.entrySet().stream().map(e -> {
            RegisterPaymentRequest.Line l = new RegisterPaymentRequest.Line();
            l.setPayableId(e.getKey());
            l.setAmount(e.getValue());
            return l;
        }).toList();
    }

    /** 人民币付款的实付人民币等于付款金额；外币必须填写 */
    private static BigDecimal cnyOf(String currency, BigDecimal amount, BigDecimal input) {
        if ("CNY".equals(currency)) {
            return amount;
        }
        if (amount.signum() == 0) {
            return BigDecimal.ZERO;
        }
        if (input == null || input.signum() <= 0) {
            throw new BizException("外币付款请填写实付人民币");
        }
        return scale(input);
    }

    private static BigDecimal rateOf(BigDecimal amount, BigDecimal cny) {
        return amount.signum() == 0 ? BigDecimal.ONE : cny.divide(amount, 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private Map<Long, List<PaymentAllocationDO>> allocationsOf(List<Long> paymentIds) {
        if (paymentIds.isEmpty()) {
            return Map.of();
        }
        return allocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationDO>()
                        .in(PaymentAllocationDO::getPaymentId, paymentIds)
                        .orderByAsc(PaymentAllocationDO::getId))
                .stream().collect(Collectors.groupingBy(PaymentAllocationDO::getPaymentId));
    }

    private PaymentVO toVo(PaymentDO p) {
        return toVos(List.of(p)).get(0);
    }

    private List<PaymentVO> toVos(List<PaymentDO> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(PaymentDO::getId).toList();
        Map<Long, List<PaymentAllocationDO>> allocs = allocationsOf(ids);
        List<Long> payableIds = new ArrayList<>();
        allocs.values().forEach(l -> l.forEach(a -> {
            payableIds.add(a.getPayableId());
            if (a.getCreditPayableId() != null) {
                payableIds.add(a.getCreditPayableId());
            }
        }));
        Map<Long, PayableDO> payables = payableIds.isEmpty() ? Map.of()
                : payableMapper.selectBatchIds(payableIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(PayableDO::getId, Function.identity()));
        Map<Long, String> accounts = query.accountNames(rows.stream().map(PaymentDO::getBankAccountId).toList());
        Map<Long, List<AttachmentVO>> files = attachments.list(OWNER_TYPE, ids);
        List<PaymentVO> out = new ArrayList<>(rows.size());
        for (PaymentDO p : rows) {
            PaymentVO vo = new PaymentVO();
            vo.setId(p.getId());
            vo.setPaymentNo(p.getPaymentNo());
            vo.setKind(p.getKind());
            vo.setPayeeType(p.getPayeeType());
            vo.setPayeeName(p.getPayeeName());
            vo.setMethod(p.getMethod());
            vo.setBankAccountId(p.getBankAccountId());
            vo.setAccountName(accounts.get(p.getBankAccountId()));
            vo.setAdvanceUser(p.getAdvanceUser());
            vo.setReimburseStatus(p.getReimburseStatus());
            vo.setPayDate(p.getPayDate());
            vo.setCurrencyCode(p.getCurrencyCode());
            vo.setAmount(p.getAmount());
            vo.setAmountCny(p.getAmountCny());
            vo.setActualRate(p.getActualRate());
            vo.setCreditUsed(p.getCreditUsed());
            vo.setRemark(p.getRemark());
            vo.setVoided(p.getVoidAt() != null);
            vo.setVoidAt(p.getVoidAt());
            vo.setVoidBy(p.getVoidBy());
            vo.setVoidReason(p.getVoidReason());
            vo.setCanVoid(canVoid(p));
            vo.setCreateTime(p.getCreateTime());
            vo.setCreateBy(p.getCreateBy());
            vo.setUpdateTime(p.getUpdateTime());
            vo.setUpdateBy(p.getUpdateBy());
            Map<String, PaymentVO.Allocation> merged = new LinkedHashMap<>();
            for (PaymentAllocationDO a : allocs.getOrDefault(p.getId(), List.of())) {
                PayableDO target = payables.get(a.getPayableId());
                PayableDO src = a.getCreditPayableId() == null ? null : payables.get(a.getCreditPayableId());
                String key = a.getPayableId() + ":" + a.getFromCredit() + ":" + a.getCreditPayableId();
                PaymentVO.Allocation v = merged.computeIfAbsent(key, k -> {
                    PaymentVO.Allocation n = new PaymentVO.Allocation();
                    n.setPayableId(a.getPayableId());
                    n.setPayableNo(target == null ? "" : target.getPayableNo());
                    n.setSourceNo(target == null ? "" : target.getSourceNo());
                    n.setFromCredit(a.getFromCredit());
                    n.setCreditPayableNo(src == null ? null : src.getPayableNo());
                    n.setAmount(BigDecimal.ZERO);
                    return n;
                });
                v.setAmount(v.getAmount().add(a.getAmount()));
            }
            vo.setAllocations(new ArrayList<>(merged.values()));
            vo.setAttachments(PayableQueryService.refs(files.getOrDefault(p.getId(), List.of())));
            out.add(vo);
        }
        return out;
    }
}

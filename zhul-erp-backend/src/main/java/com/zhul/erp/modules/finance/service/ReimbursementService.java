package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.AdvanceItemVO;
import com.zhul.erp.modules.finance.dto.PayReimbursementRequest;
import com.zhul.erp.modules.finance.dto.ProofRequest;
import com.zhul.erp.modules.finance.dto.RefundReminderVO;
import com.zhul.erp.modules.finance.dto.ReimbursementQuery;
import com.zhul.erp.modules.finance.dto.ReimbursementVO;
import com.zhul.erp.modules.finance.dto.SubmitReimbursementRequest;
import com.zhul.erp.modules.finance.entity.ExpenseDO;
import com.zhul.erp.modules.finance.entity.PayableDO;
import com.zhul.erp.modules.finance.entity.PaymentAllocationDO;
import com.zhul.erp.modules.finance.entity.PaymentDO;
import com.zhul.erp.modules.finance.entity.ReimbursementDO;
import com.zhul.erp.modules.finance.entity.ReimbursementLineDO;
import com.zhul.erp.modules.finance.repository.ExpenseMapper;
import com.zhul.erp.modules.finance.repository.PayableMapper;
import com.zhul.erp.modules.finance.repository.PaymentAllocationMapper;
import com.zhul.erp.modules.finance.repository.PaymentMapper;
import com.zhul.erp.modules.finance.repository.ReimbursementLineMapper;
import com.zhul.erp.modules.finance.repository.ReimbursementMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.entity.CourierWaybillDO;
import com.zhul.erp.modules.logistics.repository.CourierWaybillMapper;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.entity.BankAccountDO;
import com.zhul.erp.modules.system.repository.BankAccountMapper;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.ReceivingDiscrepancyDO;
import com.zhul.erp.modules.warehouse.repository.ReceivingDiscrepancyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 报销（spec finance/reimbursement）：待报销项按五种来源实时汇总（垫付人是本人、有效、还没进有效报销单），
 * 员工勾选提交报销单，提交后、付款前可以撤回；财务登记已付（同时记一条报销付款单），可以撤销已付。
 */
@Service
@RequiredArgsConstructor
public class ReimbursementService {

    private static final String MENU = "报销单";
    private static final int MAX_PAGE_SIZE = 100;

    private final ReimbursementMapper reimbursementMapper;
    private final ReimbursementLineMapper lineMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentAllocationMapper allocationMapper;
    private final PayableMapper payableMapper;
    private final ExpenseMapper expenseMapper;
    private final CourierWaybillMapper waybillMapper;
    private final ReceivingDiscrepancyMapper discrepancyMapper;
    private final PurchaseOrderMapper orderMapper;
    private final SupplierMapper supplierMapper;
    private final BankAccountMapper bankAccountMapper;
    private final PayableQueryService payableQuery;
    private final DocumentNumberService documentNumberService;
    private final AttachmentService attachments;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final PermissionChecker perm;
    private final LogService logService;

    private record Me(Long id, String username) {
    }

    private Me me() {
        Long id = currentUser.resolve();
        if (id == null) {
            throw new BizException("请先登录");
        }
        return new Me(id, SecurityUtils.getCurrentUsername());
    }

    // ---------------------------------------------------------------- 待报销

    public List<AdvanceItemVO> pending() {
        Me me = me();
        List<AdvanceItemVO> all = collect(me);
        Set<String> claimed = activeKeys(all);
        return all.stream().filter(i -> !claimed.contains(key(i.getSourceType(), i.getSourceId())))
                .sorted(Comparator.comparing((AdvanceItemVO i) -> i.getOccurDate() == null ? LocalDate.MIN : i.getOccurDate())
                        .thenComparing(AdvanceItemVO::getSourceType))
                .toList();
    }

    /** 本人五种来源的全部有效垫付（不管是否已报销） */
    private List<AdvanceItemVO> collect(Me me) {
        int tenant = PiStore.tenantId();
        List<AdvanceItemVO> out = new ArrayList<>();
        List<PaymentDO> pays = paymentMapper.selectList(new LambdaQueryWrapper<PaymentDO>()
                .eq(PaymentDO::getTenantId, tenant)
                .eq(PaymentDO::getMethod, FinanceConstants.METHOD_ADVANCE)
                .eq(PaymentDO::getAdvanceUser, me.username())
                .in(PaymentDO::getKind, FinanceConstants.KIND_PAYMENT, FinanceConstants.KIND_REFUND)
                .isNull(PaymentDO::getVoidAt)
                .isNull(PaymentDO::getDeletedAt));
        Map<Long, String> paySources = paymentSources(pays.stream().map(PaymentDO::getId).toList());
        Map<Long, List<AttachmentVO>> payFiles = attachments.list(AttachmentService.PAYMENT, pays.stream().map(PaymentDO::getId).toList());
        for (PaymentDO p : pays) {
            boolean refund = p.getKind() == FinanceConstants.KIND_REFUND;
            AdvanceItemVO i = item(refund ? FinanceConstants.ITEM_REFUND : FinanceConstants.ITEM_PAYMENT, p.getId(), p.getPaymentNo(),
                    p.getPayDate(), (refund ? "收到退款 · " : "") + p.getPayeeName() + sourceText(paySources.get(p.getId())),
                    refund ? p.getAmountCny().negate() : p.getAmountCny());
            proof(i, AttachmentService.PAYMENT, p.getId(), payFiles, false);
            out.add(i);
        }
        List<CourierWaybillDO> waybills = waybillMapper.selectList(new LambdaQueryWrapper<CourierWaybillDO>()
                .eq(CourierWaybillDO::getTenantId, tenant)
                .eq(CourierWaybillDO::getPayerId, me.id())
                .eq(CourierWaybillDO::getStatus, LogisticsConstants.WAYBILL_VALID)
                .gt(CourierWaybillDO::getFreight, BigDecimal.ZERO)
                .isNull(CourierWaybillDO::getDeletedAt));
        Map<Long, String> forwarders = supplierNames(waybills.stream().map(CourierWaybillDO::getForwarderId).toList());
        Map<Long, List<AttachmentVO>> wbFiles = attachments.list(AttachmentService.COURIER_PROOF,
                waybills.stream().map(CourierWaybillDO::getId).toList());
        for (CourierWaybillDO w : waybills) {
            AdvanceItemVO i = item(FinanceConstants.ITEM_COURIER, w.getId(), w.getTrackingNo(), w.getSentDate(),
                    "国内快递 · " + w.getCarrier() + " " + w.getTrackingNo()
                            + (forwarders.get(w.getForwarderId()) == null ? "" : " · 发" + forwarders.get(w.getForwarderId())),
                    w.getFreight());
            proof(i, AttachmentService.COURIER_PROOF, w.getId(), wbFiles, true);
            out.add(i);
        }
        List<ReceivingDiscrepancyDO> diffs = discrepancyMapper.selectList(new LambdaQueryWrapper<ReceivingDiscrepancyDO>()
                .eq(ReceivingDiscrepancyDO::getTenantId, tenant)
                .eq(ReceivingDiscrepancyDO::getReturnPayerId, me.id())
                .eq(ReceivingDiscrepancyDO::getStatus, WarehouseConstants.DIFF_HANDLED)
                .gt(ReceivingDiscrepancyDO::getReturnFreight, BigDecimal.ZERO)
                .isNull(ReceivingDiscrepancyDO::getDeletedAt));
        Map<Long, String> poNos = diffs.isEmpty() ? Map.of()
                : orderMapper.selectBatchIds(diffs.stream().map(ReceivingDiscrepancyDO::getPoId).distinct().toList()).stream()
                .collect(Collectors.toMap(PurchaseOrderDO::getId, p -> p.getPoNo() == null ? "" : p.getPoNo()));
        Map<Long, List<AttachmentVO>> diffFiles = attachments.list(AttachmentService.RETURN_PROOF,
                diffs.stream().map(ReceivingDiscrepancyDO::getId).toList());
        for (ReceivingDiscrepancyDO d : diffs) {
            AdvanceItemVO i = item(FinanceConstants.ITEM_RETURN_FREIGHT, d.getId(), poNos.getOrDefault(d.getPoId(), ""),
                    d.getHandledAt() == null ? null : d.getHandledAt().toLocalDate(),
                    "退货运费 · " + d.getModel() + " " + WarehouseConstants.DIFF_TYPE_NAMES.get(d.getType()) + " " + d.getQuantity()
                            + (StringUtils.hasText(d.getReturnTrackingNo()) ? " · " + nz(d.getReturnCarrier()) + " " + d.getReturnTrackingNo() : ""),
                    d.getReturnFreight());
            proof(i, AttachmentService.RETURN_PROOF, d.getId(), diffFiles, true);
            out.add(i);
        }
        List<ExpenseDO> expenses = expenseMapper.selectList(new LambdaQueryWrapper<ExpenseDO>()
                .eq(ExpenseDO::getTenantId, tenant)
                .eq(ExpenseDO::getMethod, FinanceConstants.METHOD_ADVANCE)
                .eq(ExpenseDO::getAdvanceUserId, me.id())
                .eq(ExpenseDO::getStatus, FinanceConstants.FE_VALID)
                .isNull(ExpenseDO::getDeletedAt));
        Map<Long, List<AttachmentVO>> feFiles = attachments.list(AttachmentService.EXPENSE, expenses.stream().map(ExpenseDO::getId).toList());
        for (ExpenseDO e : expenses) {
            AdvanceItemVO i = item(FinanceConstants.ITEM_EXPENSE, e.getId(), e.getExpenseNo(), e.getOccurDate(),
                    "费用单 · " + e.getName() + " · " + e.getPayeeName(), e.getAmountCny());
            proof(i, AttachmentService.EXPENSE, e.getId(), feFiles, false);
            out.add(i);
        }
        return out;
    }

    /** 付款核销到的来源单号（采购订单号），用于说明 */
    private Map<Long, String> paymentSources(List<Long> paymentIds) {
        if (paymentIds.isEmpty()) {
            return Map.of();
        }
        List<PaymentAllocationDO> allocs = allocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationDO>()
                .in(PaymentAllocationDO::getPaymentId, paymentIds)
                .isNull(PaymentAllocationDO::getDeletedAt));
        Map<Long, String> nos = allocs.isEmpty() ? Map.of()
                : payableMapper.selectBatchIds(allocs.stream().map(PaymentAllocationDO::getPayableId).distinct().toList()).stream()
                .collect(Collectors.toMap(PayableDO::getId, PayableDO::getSourceNo));
        Map<Long, String> out = new HashMap<>();
        for (PaymentAllocationDO a : allocs) {
            out.merge(a.getPaymentId(), nos.getOrDefault(a.getPayableId(), ""), (x, y) -> x.contains(y) ? x : x + "、" + y);
        }
        return out;
    }

    private static String sourceText(String s) {
        return StringUtils.hasText(s) ? " · " + s : "";
    }

    private static AdvanceItemVO item(int type, Long id, String no, LocalDate date, String desc, BigDecimal amount) {
        AdvanceItemVO i = new AdvanceItemVO();
        i.setSourceType(type);
        i.setSourceId(id);
        i.setSourceNo(no == null ? "" : no);
        i.setOccurDate(date);
        i.setDescription(desc.length() > 255 ? desc.substring(0, 255) : desc);
        i.setAmount(amount);
        return i;
    }

    private static void proof(AdvanceItemVO i, String ownerType, Long ownerId, Map<Long, List<AttachmentVO>> files, boolean uploadable) {
        i.setProofOwnerType(ownerType);
        i.setProofOwnerId(ownerId);
        i.setProofs(PayableQueryService.refs(files.getOrDefault(ownerId, List.of())));
        i.setProofUploadable(uploadable);
    }

    private static String key(int type, Long id) {
        return type + ":" + id;
    }

    /** 这些来源里已进有效报销单的 */
    private Set<String> activeKeys(List<AdvanceItemVO> items) {
        if (items.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        Map<Integer, List<Long>> byType = items.stream().collect(Collectors.groupingBy(AdvanceItemVO::getSourceType,
                Collectors.mapping(AdvanceItemVO::getSourceId, Collectors.toList())));
        byType.forEach((type, ids) -> lineMapper.selectList(new LambdaQueryWrapper<ReimbursementLineDO>()
                        .select(ReimbursementLineDO::getSourceType, ReimbursementLineDO::getSourceId)
                        .eq(ReimbursementLineDO::getTenantId, PiStore.tenantId())
                        .eq(ReimbursementLineDO::getSourceType, type)
                        .in(ReimbursementLineDO::getSourceId, ids)
                        .eq(ReimbursementLineDO::getReleaseSeq, 0L)
                        .isNull(ReimbursementLineDO::getDeletedAt))
                .forEach(l -> out.add(key(l.getSourceType(), l.getSourceId()))));
        return out;
    }

    /** 补传截图：只能给本人的快递、退货运费补传 */
    @Transactional(rollbackFor = Exception.class)
    public AdvanceItemVO addProof(ProofRequest req) {
        Me me = me();
        AdvanceItemVO target = collect(me).stream()
                .filter(i -> i.getSourceType().equals(req.getSourceType()) && i.getSourceId().equals(req.getSourceId()))
                .findFirst().orElseThrow(() -> new BizException("不是你的待报销项"));
        if (!target.isProofUploadable()) {
            throw new BizException("这一项的截图在原单据上");
        }
        attachments.attach(target.getProofOwnerType(), target.getSourceId(), req.getAttachmentIds());
        return collect(me).stream()
                .filter(i -> i.getSourceType().equals(req.getSourceType()) && i.getSourceId().equals(req.getSourceId()))
                .findFirst().orElse(target);
    }

    // ---------------------------------------------------------------- 提交与撤回

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ReimbursementVO submit(SubmitReimbursementRequest req) {
        Me me = me();
        Map<String, AdvanceItemVO> pending = pending().stream()
                .collect(Collectors.toMap(i -> key(i.getSourceType(), i.getSourceId()), Function.identity()));
        List<AdvanceItemVO> chosen = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SubmitReimbursementRequest.Item it : req.getItems()) {
            String k = key(it.getSourceType(), it.getSourceId());
            if (!seen.add(k)) {
                continue;
            }
            AdvanceItemVO i = pending.get(k);
            if (i == null) {
                throw new BizException("有的项已经在其他报销单里或不是你的待报销项，请刷新");
            }
            if (i.getProofs().isEmpty()) {
                throw new BizException(i.getDescription() + " 没有截图，请补传");
            }
            chosen.add(i);
        }
        BigDecimal total = chosen.stream().map(AdvanceItemVO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw new BizException("报销合计须大于 0");
        }
        ReimbursementDO bx = new ReimbursementDO();
        bx.setTenantId(PiStore.tenantId());
        bx.setReimburseNo(documentNumberService.next(DocumentType.BX));
        bx.setApplicantId(me.id());
        bx.setApplicantName(lookups.userNames(List.of(me.id())).getOrDefault(me.id(), me.username()));
        bx.setSubmittedAt(LocalDateTime.now());
        bx.setLineCount(chosen.size());
        bx.setTotalAmount(total);
        bx.setStatus(FinanceConstants.BX_PENDING);
        bx.setWithdrawReason("");
        bx.setPaidBy("");
        bx.setPayRemark("");
        reimbursementMapper.insert(bx);
        try {
            for (AdvanceItemVO i : chosen) {
                ReimbursementLineDO l = new ReimbursementLineDO();
                l.setTenantId(bx.getTenantId());
                l.setReimbursementId(bx.getId());
                l.setSourceType(i.getSourceType());
                l.setSourceId(i.getSourceId());
                l.setSourceNo(i.getSourceNo());
                l.setOccurDate(i.getOccurDate());
                l.setDescription(i.getDescription());
                l.setAmount(i.getAmount());
                l.setReleaseSeq(0L);
                lineMapper.insert(l);
            }
        } catch (DuplicateKeyException e) {
            throw new BizException("有的项已经在其他报销单里，请刷新", e);
        }
        markPayments(chosen, FinanceConstants.REIMBURSE_CLAIMED);
        logService.recordOperateLog(MENU, "提交报销单", null, Map.of("reimburseNo", bx.getReimburseNo(), "items", chosen.size(),
                "total", "CNY " + total));
        return detail(bx.getId());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ReimbursementVO withdraw(Long id, String reason) {
        Me me = me();
        ReimbursementDO bx = lock(id);
        if (!Objects.equals(bx.getApplicantId(), me.id())) {
            throw new BizException("只有提交人可以撤回");
        }
        if (bx.getStatus() != FinanceConstants.BX_PENDING) {
            throw new BizException(bx.getStatus() == FinanceConstants.BX_PAID ? "已报销，不能撤回" : "报销单已撤回");
        }
        bx.setStatus(FinanceConstants.BX_WITHDRAWN);
        bx.setWithdrawReason(reason.trim());
        reimbursementMapper.updateById(bx);
        List<ReimbursementLineDO> lines = linesOf(id);
        for (ReimbursementLineDO l : lines) {
            l.setReleaseSeq(l.getId());
            lineMapper.updateById(l);
        }
        markPayments(lines.stream().map(l -> item(l.getSourceType(), l.getSourceId(), "", null, "", BigDecimal.ZERO)).toList(),
                FinanceConstants.REIMBURSE_PENDING);
        logService.recordOperateLog(MENU, "撤回报销单", Map.of("reimburseNo", bx.getReimburseNo(), "status", "待付款"),
                Map.of("reimburseNo", bx.getReimburseNo(), "status", "已撤回", "reason", reason.trim()));
        return detail(id);
    }

    /** 采购货款、收到退款两种来源的付款单：进报销单为 2，撤回为 1 */
    private void markPayments(List<AdvanceItemVO> items, int status) {
        List<Long> ids = items.stream()
                .filter(i -> i.getSourceType() == FinanceConstants.ITEM_PAYMENT || i.getSourceType() == FinanceConstants.ITEM_REFUND)
                .map(AdvanceItemVO::getSourceId).toList();
        for (Long pid : ids) {
            PaymentDO p = paymentMapper.selectById(pid);
            p.setReimburseStatus(status);
            paymentMapper.updateById(p);
        }
    }

    // ---------------------------------------------------------------- 财务付款

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ReimbursementVO pay(Long id, PayReimbursementRequest req) {
        requirePayer();
        ReimbursementDO bx = lock(id);
        if (bx.getStatus() != FinanceConstants.BX_PENDING) {
            throw new BizException(bx.getStatus() == FinanceConstants.BX_PAID ? "已登记过已付" : "报销单已撤回");
        }
        if (req.getPayDate().isAfter(LocalDate.now())) {
            throw new BizException("付款日期不能晚于今天");
        }
        int method = req.getMethod() == null ? FinanceConstants.METHOD_COMPANY : req.getMethod();
        if (method == FinanceConstants.METHOD_COMPANY) {
            BankAccountDO account = req.getBankAccountId() == null ? null : bankAccountMapper.selectById(req.getBankAccountId());
            if (account == null || !Objects.equals(account.getTenantId(), PiStore.tenantId())) {
                throw new BizException(req.getBankAccountId() == null ? "请选择付款账户" : "付款账户不存在");
            }
        } else if (method == FinanceConstants.METHOD_PRIVATE) {
            if (!StringUtils.hasText(req.getPayerAccount())) {
                throw new BizException("私对私请填写付款个人账户");
            }
        } else {
            throw new BizException("付款方式无效");
        }
        String payer = method == FinanceConstants.METHOD_PRIVATE ? req.getPayerAccount().trim() : "";
        Long accountId = method == FinanceConstants.METHOD_COMPANY ? req.getBankAccountId() : null;
        attachments.attach(AttachmentService.REIMBURSE, bx.getId(), req.getAttachmentIds());
        PaymentDO p = new PaymentDO();
        p.setTenantId(bx.getTenantId());
        p.setPaymentNo(documentNumberService.next(DocumentType.PY));
        p.setKind(FinanceConstants.KIND_REIMBURSE);
        p.setPayeeType(FinanceConstants.PAYEE_EMPLOYEE);
        p.setShopPlatform("");
        p.setShopName("");
        p.setPayeeName(bx.getApplicantName());
        p.setMethod(method);
        p.setBankAccountId(accountId);
        p.setPayerAccount(payer);
        p.setAdvanceUser("");
        p.setPayDate(req.getPayDate());
        p.setCurrencyCode("CNY");
        p.setAmount(bx.getTotalAmount());
        p.setAmountCny(bx.getTotalAmount());
        p.setActualRate(BigDecimal.ONE);
        p.setCreditUsed(BigDecimal.ZERO);
        p.setRemark("报销单 " + bx.getReimburseNo());
        p.setReimburseStatus(FinanceConstants.REIMBURSE_NA);
        p.setIdempotencyKey("BX-" + bx.getId() + "-" + System.nanoTime());
        paymentMapper.insert(p);
        bx.setStatus(FinanceConstants.BX_PAID);
        bx.setPayMethod(method);
        bx.setPayAccountId(accountId);
        bx.setPayPayerAccount(payer);
        bx.setPayDate(req.getPayDate());
        bx.setPaidBy(SecurityUtils.getCurrentUsername());
        bx.setPaidAt(LocalDateTime.now());
        bx.setPayRemark(req.getRemark() == null ? "" : req.getRemark().trim());
        bx.setPaymentId(p.getId());
        reimbursementMapper.updateById(bx);
        logService.recordOperateLog(MENU, "登记报销已付", null, Map.of("reimburseNo", bx.getReimburseNo(), "paymentNo", p.getPaymentNo(),
                "total", "CNY " + bx.getTotalAmount()));
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ReimbursementVO unpay(Long id, String reason) {
        requirePayer();
        ReimbursementDO bx = lock(id);
        if (bx.getStatus() != FinanceConstants.BX_PAID) {
            throw new BizException("报销单还没登记已付");
        }
        if (bx.getPaymentId() != null) {
            PaymentDO p = paymentMapper.selectById(bx.getPaymentId());
            if (p != null && p.getVoidAt() == null) {
                p.setVoidAt(LocalDateTime.now());
                p.setVoidBy(SecurityUtils.getCurrentUsername());
                p.setVoidReason("撤销报销已付：" + reason.trim());
                paymentMapper.updateById(p);
            }
        }
        bx.setStatus(FinanceConstants.BX_PENDING);
        bx.setPayAccountId(null);
        bx.setPayDate(null);
        bx.setPaidBy("");
        bx.setPaidAt(null);
        bx.setPayRemark("");
        bx.setPaymentId(null);
        reimbursementMapper.updateById(bx);
        logService.recordOperateLog(MENU, "撤销报销已付", Map.of("reimburseNo", bx.getReimburseNo(), "status", "已报销"),
                Map.of("reimburseNo", bx.getReimburseNo(), "status", "待付款", "reason", reason.trim()));
        return detail(id);
    }

    private void requirePayer() {
        if (!perm.has(FinanceConstants.PERM_REIMBURSE_PAY)) {
            throw new BizException("没有「报销付款」权限");
        }
    }

    // ---------------------------------------------------------------- 待退款提醒

    /** 本人垫付过、有欠款、最后一次变动超过 N 天的应付单 */
    public List<RefundReminderVO> reminders() {
        Me me = me();
        List<Long> myPayments = paymentMapper.selectList(new LambdaQueryWrapper<PaymentDO>()
                        .select(PaymentDO::getId)
                        .eq(PaymentDO::getTenantId, PiStore.tenantId())
                        .eq(PaymentDO::getKind, FinanceConstants.KIND_PAYMENT)
                        .eq(PaymentDO::getMethod, FinanceConstants.METHOD_ADVANCE)
                        .eq(PaymentDO::getAdvanceUser, me.username())
                        .isNull(PaymentDO::getVoidAt))
                .stream().map(PaymentDO::getId).toList();
        if (myPayments.isEmpty()) {
            return List.of();
        }
        Set<Long> payableIds = allocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationDO>()
                        .select(PaymentAllocationDO::getPayableId)
                        .in(PaymentAllocationDO::getPaymentId, myPayments)
                        .isNull(PaymentAllocationDO::getDeletedAt))
                .stream().map(PaymentAllocationDO::getPayableId).collect(Collectors.toSet());
        if (payableIds.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        List<RefundReminderVO> out = new ArrayList<>();
        for (PayableDO p : payableMapper.selectList(new LambdaQueryWrapper<PayableDO>()
                .in(PayableDO::getId, payableIds)
                .gt(PayableDO::getCreditAmount, BigDecimal.ZERO)
                .isNull(PayableDO::getDeletedAt))) {
            long days = ChronoUnit.DAYS.between(p.getUpdateTime().toLocalDate(), today);
            if (days > FinanceConstants.REFUND_REMIND_DAYS) {
                RefundReminderVO r = new RefundReminderVO();
                r.setPayableId(p.getId());
                r.setPayableNo(p.getPayableNo());
                r.setPayeeName(p.getPayeeName());
                r.setSourceNo(p.getSourceNo());
                r.setCurrencyCode(p.getCurrencyCode());
                r.setCreditAmount(p.getCreditAmount());
                r.setDays((int) days);
                out.add(r);
            }
        }
        return out;
    }

    /** 本人能登记「已收到退款」的应付单：本人垫付过且有欠款 */
    public boolean advancedBy(Long payableId, String username) {
        List<Long> mine = paymentMapper.selectList(new LambdaQueryWrapper<PaymentDO>()
                        .select(PaymentDO::getId)
                        .eq(PaymentDO::getTenantId, PiStore.tenantId())
                        .eq(PaymentDO::getKind, FinanceConstants.KIND_PAYMENT)
                        .eq(PaymentDO::getMethod, FinanceConstants.METHOD_ADVANCE)
                        .eq(PaymentDO::getAdvanceUser, username)
                        .isNull(PaymentDO::getVoidAt))
                .stream().map(PaymentDO::getId).toList();
        return !mine.isEmpty() && allocationMapper.selectCount(new LambdaQueryWrapper<PaymentAllocationDO>()
                .in(PaymentAllocationDO::getPaymentId, mine)
                .eq(PaymentAllocationDO::getPayableId, payableId)
                .isNull(PaymentAllocationDO::getDeletedAt)) > 0;
    }

    // ---------------------------------------------------------------- 列表与详情

    public PageResult<ReimbursementVO> page(ReimbursementQuery q) {
        int size = q.getPageSize() == null ? 20 : Math.min(Math.max(q.getPageSize(), 1), MAX_PAGE_SIZE);
        int page = q.getPage() == null ? 1 : Math.max(q.getPage(), 1);
        LambdaQueryWrapper<ReimbursementDO> w = scoped();
        if (q.getStatus() != null) {
            w.eq(ReimbursementDO::getStatus, q.getStatus());
        }
        if (q.getApplicantId() != null) {
            w.eq(ReimbursementDO::getApplicantId, q.getApplicantId());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String k = q.getKeyword().trim();
            w.and(x -> x.like(ReimbursementDO::getReimburseNo, k).or().like(ReimbursementDO::getApplicantName, k));
        }
        if (q.getDateFrom() != null) {
            w.ge(ReimbursementDO::getSubmittedAt, q.getDateFrom().atStartOfDay());
        }
        if (q.getDateTo() != null) {
            w.lt(ReimbursementDO::getSubmittedAt, q.getDateTo().plusDays(1).atStartOfDay());
        }
        w.orderByDesc(ReimbursementDO::getUpdateTime).orderByDesc(ReimbursementDO::getId);
        Page<ReimbursementDO> p = reimbursementMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(r -> toVo(r, false)).toList());
    }

    /** 待付款合计（数据范围内） */
    public BigDecimal pendingTotal() {
        return reimbursementMapper.selectList(scoped().eq(ReimbursementDO::getStatus, FinanceConstants.BX_PENDING)
                        .select(ReimbursementDO::getTotalAmount))
                .stream().map(ReimbursementDO::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public ReimbursementVO detail(Long id) {
        ReimbursementDO bx = reimbursementMapper.selectOne(scoped().eq(ReimbursementDO::getId, id));
        if (bx == null) {
            throw new BizException("报销单不存在");
        }
        return toVo(bx, true);
    }

    /** 没有报销付款权限的只看本人的 */
    private LambdaQueryWrapper<ReimbursementDO> scoped() {
        LambdaQueryWrapper<ReimbursementDO> w = new LambdaQueryWrapper<ReimbursementDO>()
                .eq(ReimbursementDO::getTenantId, PiStore.tenantId())
                .isNull(ReimbursementDO::getDeletedAt);
        if (!perm.has(FinanceConstants.PERM_REIMBURSE_PAY)) {
            Long me = currentUser.resolve();
            w.eq(ReimbursementDO::getApplicantId, me == null ? -1L : me);
        }
        return w;
    }

    private ReimbursementDO lock(Long id) {
        ReimbursementDO bx = reimbursementMapper.selectOne(new LambdaQueryWrapper<ReimbursementDO>()
                .eq(ReimbursementDO::getId, id)
                .eq(ReimbursementDO::getTenantId, PiStore.tenantId())
                .isNull(ReimbursementDO::getDeletedAt)
                .last("FOR UPDATE"));
        if (bx == null) {
            throw new BizException("报销单不存在");
        }
        return bx;
    }

    private List<ReimbursementLineDO> linesOf(Long id) {
        return lineMapper.selectList(new LambdaQueryWrapper<ReimbursementLineDO>()
                .eq(ReimbursementLineDO::getReimbursementId, id)
                .isNull(ReimbursementLineDO::getDeletedAt)
                .orderByAsc(ReimbursementLineDO::getId));
    }

    private ReimbursementVO toVo(ReimbursementDO bx, boolean full) {
        ReimbursementVO vo = new ReimbursementVO();
        vo.setId(bx.getId());
        vo.setReimburseNo(bx.getReimburseNo());
        vo.setApplicantId(bx.getApplicantId());
        vo.setApplicantName(bx.getApplicantName());
        vo.setSubmittedAt(bx.getSubmittedAt());
        vo.setLineCount(bx.getLineCount());
        vo.setTotalAmount(bx.getTotalAmount());
        vo.setStatus(bx.getStatus());
        vo.setWithdrawReason(bx.getWithdrawReason());
        vo.setPayAccountId(bx.getPayAccountId());
        vo.setPayMethod(bx.getPayMethod());
        vo.setPayPayerAccount(bx.getPayPayerAccount());
        vo.setPayDate(bx.getPayDate());
        vo.setPaidBy(bx.getPaidBy());
        vo.setPaidAt(bx.getPaidAt());
        vo.setPayRemark(bx.getPayRemark());
        Long me = currentUser.resolve();
        vo.setCanWithdraw(bx.getStatus() == FinanceConstants.BX_PENDING && Objects.equals(bx.getApplicantId(), me));
        vo.setCreateTime(bx.getCreateTime());
        vo.setCreateBy(bx.getCreateBy());
        vo.setUpdateTime(bx.getUpdateTime());
        vo.setUpdateBy(bx.getUpdateBy());
        if (full) {
            vo.setPayAccountName(payableQuery.accountNames(List.of(bx.getPayAccountId() == null ? -1L : bx.getPayAccountId()))
                    .get(bx.getPayAccountId()));
            if (bx.getPaymentId() != null) {
                PaymentDO p = paymentMapper.selectById(bx.getPaymentId());
                vo.setPaymentNo(p == null ? null : p.getPaymentNo());
            }
            vo.setPayProofs(PayableQueryService.refs(attachments.listOf(AttachmentService.REIMBURSE, bx.getId())));
            List<ReimbursementLineDO> lines = linesOf(bx.getId());
            Map<String, List<AttachmentVO>> proofCache = new LinkedHashMap<>();
            for (ReimbursementLineDO l : lines) {
                AdvanceItemVO i = item(l.getSourceType(), l.getSourceId(), l.getSourceNo(), l.getOccurDate(), l.getDescription(),
                        l.getAmount());
                String owner = switch (l.getSourceType()) {
                    case FinanceConstants.ITEM_COURIER -> AttachmentService.COURIER_PROOF;
                    case FinanceConstants.ITEM_RETURN_FREIGHT -> AttachmentService.RETURN_PROOF;
                    case FinanceConstants.ITEM_EXPENSE -> AttachmentService.EXPENSE;
                    default -> AttachmentService.PAYMENT;
                };
                List<AttachmentVO> files = proofCache.computeIfAbsent(owner + ":" + l.getSourceId(),
                        k -> attachments.listOf(owner, l.getSourceId()));
                i.setProofOwnerType(owner);
                i.setProofOwnerId(l.getSourceId());
                i.setProofs(PayableQueryService.refs(files));
                vo.getLines().add(i);
            }
        }
        return vo;
    }

    private Map<Long, String> supplierNames(List<Long> ids) {
        List<Long> list = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (list.isEmpty()) {
            return Map.of();
        }
        return supplierMapper.selectBatchIds(list).stream().collect(Collectors.toMap(SupplierDO::getId,
                s -> StringUtils.hasText(s.getShortName()) ? s.getShortName() : s.getName()));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

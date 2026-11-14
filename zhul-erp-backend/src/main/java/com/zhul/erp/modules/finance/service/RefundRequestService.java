package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.aftersales.constants.AfterSalesConstants;
import com.zhul.erp.modules.aftersales.dto.ConfirmReturnRequest;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.aftersales.entity.ReturnReceiptDO;
import com.zhul.erp.modules.aftersales.entity.ReturnReceiptItemDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnItemDO;
import com.zhul.erp.modules.aftersales.repository.AfterSalesMapper;
import com.zhul.erp.modules.aftersales.repository.ReturnReceiptItemMapper;
import com.zhul.erp.modules.aftersales.repository.ReturnReceiptMapper;
import com.zhul.erp.modules.aftersales.repository.SalesReturnMapper;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.aftersales.service.AfterSalesService;
import com.zhul.erp.modules.aftersales.support.AfterSalesLogs;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.RefundRequestQuery;
import com.zhul.erp.modules.finance.dto.RefundRequestVO;
import com.zhul.erp.modules.finance.dto.RefundVO;
import com.zhul.erp.modules.finance.dto.RefundableVO;
import com.zhul.erp.modules.finance.dto.RequestRefundableVO;
import com.zhul.erp.modules.finance.dto.SaveRefundRequest;
import com.zhul.erp.modules.finance.dto.SaveRefundRequestApply;
import com.zhul.erp.modules.finance.entity.CustomerRefundDO;
import com.zhul.erp.modules.finance.entity.RefundRequestDO;
import com.zhul.erp.modules.finance.entity.RefundRequestItemDO;
import com.zhul.erp.modules.finance.repository.CustomerRefundMapper;
import com.zhul.erp.modules.finance.repository.RefundRequestItemMapper;
import com.zhul.erp.modules.finance.repository.RefundRequestMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 退款申请（spec finance/refund-request）：业务员提出 →（系统设置打开审批时先审批）→ 财务执行生成售后退款单或驳回。
 * 可申请 = 已到账货款 − 有效售后退款 − 其他待验收 / 待审批 / 待财务处理的申请（原币，HALF_UP 两位）。
 * 由销售退货生成的申请（spec after-sales-return-docs）：没勾选先退款时为待验收，退货入库单验收后才进入审批或财务处理。
 * 退款申请由表头与明细组成，表头金额 = 明细合计。
 */
@Service
@RequiredArgsConstructor
public class RefundRequestService {

    private static final String MENU = "退款管理";

    private final RefundRequestMapper mapper;
    private final RefundRequestItemMapper itemMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final SalesReturnMapper salesReturnMapper;
    private final ReturnReceiptMapper receiptMapper;
    private final ReturnReceiptItemMapper receiptItemMapper;
    private final AttachmentService attachments;
    private final DocLogs docLogs;
    private final CustomerRefundMapper refundMapper;
    private final SalesOrderMapper orderMapper;
    private final AfterSalesMapper afterSalesMapper;
    private final AfterSalesService afterSalesService;
    private final AfterSalesLogs afterSalesLogs;
    private final CustomerRefundService refundService;
    private final SysConfigMapper sysConfigMapper;
    private final DocumentNumberService documentNumberService;
    private final DataScopeResolver dataScopeResolver;
    private final PermissionChecker perm;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;

    public boolean approvalRequired() {
        List<SysConfigDO> rows = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, FinanceConstants.CONFIG_REFUND_APPROVAL)
                .in(SysConfigDO::getTenantId, List.of(0, PiStore.tenantId()))
                .isNull(SysConfigDO::getDeletedAt));
        return rows.stream().max(Comparator.comparing(SysConfigDO::getTenantId))
                .map(c -> "true".equalsIgnoreCase(c.getConfigValue())).orElse(false);
    }

    public RequestRefundableVO refundable(Long soId) {
        SalesOrderDO o = visibleOrder(soId);
        return refundableOf(o, null);
    }

    // ---------------------------------------------------------------- 提出与撤回

    @Transactional(rollbackFor = Exception.class)
    public RefundRequestVO apply(SaveRefundRequestApply req) {
        SalesOrderDO o = visibleOrder(req.getSoId());
        AfterSalesDO as = null;
        if (req.getAfterSalesId() != null) {
            afterSalesService.detail(req.getAfterSalesId());
            as = afterSalesService.requireOfOrder(req.getAfterSalesId(), o.getId());
            if (as.getStatus() == AfterSalesConstants.AS_CLOSED) {
                throw new BizException("售后单已结案");
            }
        }
        orderMapper.selectOne(new LambdaQueryWrapper<SalesOrderDO>().eq(SalesOrderDO::getId, o.getId()).last("FOR UPDATE"));
        if (!refundService.reasonNames().containsKey(req.getReason())) {
            throw new BizException("退款原因不存在");
        }
        int method = req.getMethod();
        if (!List.of(FinanceConstants.REFUND_PLATFORM, FinanceConstants.REFUND_BANK, FinanceConstants.REFUND_TO_CREDIT).contains(method)) {
            throw new BizException("退款去向不正确");
        }
        RequestRefundableVO r = refundableOf(o, null);
        if (Boolean.TRUE.equals(r.getPlatformOnly()) && method != FinanceConstants.REFUND_PLATFORM) {
            throw new BizException("平台收款的订单只能原路退回");
        }
        BigDecimal amount = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(r.getAvailable()) > 0) {
            throw new BizException("最多还能申请 " + o.getCurrencyCode() + " " + String.format("%,.2f", r.getAvailable()));
        }
        List<SaveRefundRequestApply.Line> lines = req.getLines() == null ? List.of() : req.getLines();
        if (!lines.isEmpty() && lines.stream().map(l -> l.getAmount().setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(amount) != 0) {
            throw new BizException("分项合计要等于申请金额 " + o.getCurrencyCode() + " " + amount);
        }
        Long me = me();
        RefundRequestDO q = new RefundRequestDO();
        q.setTenantId(o.getTenantId());
        q.setRequestNo(documentNumberService.next(DocumentType.RA));
        q.setAfterSalesId(as == null ? null : as.getId());
        q.setSoId(o.getId());
        q.setCurrencyCode(o.getCurrencyCode());
        q.setAmount(amount);
        q.setReason(req.getReason());
        q.setMethod(method);
        q.setPayeeInfo(trim(req.getPayeeInfo()));
        q.setRemark(trim(req.getRemark()));
        q.setStatus(approvalRequired() ? FinanceConstants.RQ_PENDING_APPROVAL : FinanceConstants.RQ_PENDING_FINANCE);
        q.setApplicantId(me);
        q.setRejectReason("");
        mapper.insert(q);
        if (lines.isEmpty()) {
            insertItem(q, FinanceConstants.RQ_LINE_OTHER, null, null, "退款", "", null, null, amount, "");
        } else {
            lines.forEach(l -> insertItem(q, FinanceConstants.RQ_LINE_OTHER, null, null, l.getItemName().trim(), "", null, null,
                    l.getAmount().setScale(2, RoundingMode.HALF_UP), trim(l.getNote())));
        }
        docLogs.add(DocumentType.RA, q.getId(), "提出", o.getCurrencyCode() + " " + amount + "，"
                + FinanceConstants.REFUND_METHOD_NAMES.get(method) + "（" + FinanceConstants.RQ_STATUS_NAMES.get(q.getStatus()) + "）");
        afterSalesLogs.add(q.getAfterSalesId(), AfterSalesConstants.LOG_REFUND, "申请退款 " + q.getRequestNo() + "：" + o.getCurrencyCode() + " "
                + amount + "，" + FinanceConstants.REFUND_METHOD_NAMES.get(method) + "（" + FinanceConstants.RQ_STATUS_NAMES.get(q.getStatus()) + "）", me);
        logService.recordOperateLog(MENU, "提出退款申请", null, Map.of("requestNo", q.getRequestNo(), "soNo", o.getSoNo(),
                "amount", o.getCurrencyCode() + " " + amount));
        return detail(q.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundRequestVO withdraw(Long id) {
        RefundRequestDO q = lock(id);
        visibleOrder(q.getSoId());
        requireStatus(q, FinanceConstants.RQ_PENDING_RECEIPT, FinanceConstants.RQ_PENDING_APPROVAL, FinanceConstants.RQ_PENDING_FINANCE);
        q.setStatus(FinanceConstants.RQ_WITHDRAWN);
        mapper.updateById(q);
        docLogs.add(DocumentType.RA, id, "撤回", "");
        afterSalesLogs.add(q.getAfterSalesId(), AfterSalesConstants.LOG_REFUND, "撤回退款申请 " + q.getRequestNo(), me());
        logService.recordOperateLog(MENU, "撤回退款申请", null, Map.of("requestNo", q.getRequestNo()));
        return detail(id);
    }

    // ---------------------------------------------------------------- 审批

    @Transactional(rollbackFor = Exception.class)
    public RefundRequestVO approve(Long id) {
        RefundRequestDO q = lock(id);
        requireStatus(q, FinanceConstants.RQ_PENDING_APPROVAL);
        q.setStatus(FinanceConstants.RQ_PENDING_FINANCE);
        q.setApproverId(me());
        q.setApprovedAt(LocalDateTime.now());
        mapper.updateById(q);
        docLogs.add(DocumentType.RA, id, "审批通过", "交财务处理");
        afterSalesLogs.add(q.getAfterSalesId(), AfterSalesConstants.LOG_REFUND, "退款申请 " + q.getRequestNo() + " 审批通过，交财务处理", me());
        logService.recordOperateLog(MENU, "审批通过退款申请", null, Map.of("requestNo", q.getRequestNo()));
        return detail(id);
    }

    /** 驳回：审批人驳回待审批的申请，财务驳回待财务处理的申请 */
    @Transactional(rollbackFor = Exception.class)
    public RefundRequestVO reject(Long id, String reason, boolean byApprover) {
        String why = trim(reason);
        if (why.isEmpty()) {
            throw new BizException("请填写驳回原因");
        }
        RefundRequestDO q = lock(id);
        requireStatus(q, byApprover ? FinanceConstants.RQ_PENDING_APPROVAL : FinanceConstants.RQ_PENDING_FINANCE);
        q.setStatus(FinanceConstants.RQ_REJECTED);
        q.setRejectReason(why.length() > 200 ? why.substring(0, 200) : why);
        if (byApprover) {
            q.setApproverId(me());
            q.setApprovedAt(LocalDateTime.now());
        } else {
            q.setHandlerId(me());
            q.setHandledAt(LocalDateTime.now());
        }
        mapper.updateById(q);
        docLogs.add(DocumentType.RA, id, "驳回", (byApprover ? "审批人" : "财务") + "驳回：" + q.getRejectReason());
        afterSalesLogs.add(q.getAfterSalesId(), AfterSalesConstants.LOG_REFUND, "退款申请 " + q.getRequestNo() + " 被"
                + (byApprover ? "审批人" : "财务") + "驳回：" + q.getRejectReason(), me());
        logService.recordOperateLog(MENU, "驳回退款申请", null, Map.of("requestNo", q.getRequestNo(), "reason", q.getRejectReason()));
        return detail(id);
    }

    // ---------------------------------------------------------------- 执行

    /** 财务执行：按退款单规则登记，金额不超过申请金额；同一事务内生成退款单并把申请置为已执行 */
    @Transactional(rollbackFor = Exception.class)
    public RefundRequestVO execute(Long id, SaveRefundRequest req) {
        RefundRequestDO q = lock(id);
        requireStatus(q, FinanceConstants.RQ_PENDING_FINANCE);
        if (req.getAmount() == null || req.getAmount().setScale(2, RoundingMode.HALF_UP).compareTo(q.getAmount()) > 0) {
            throw new BizException("执行金额不能超过申请金额 " + q.getCurrencyCode() + " " + q.getAmount());
        }
        if (req.getRefundDate() == null) {
            throw new BizException("请填写退款日期");
        }
        if (req.getMethod() == null) {
            req.setMethod(q.getMethod());
        }
        req.setSoId(q.getSoId());
        req.setAfterSalesId(q.getAfterSalesId());
        if (!StringUtils.hasText(req.getReason())) {
            req.setReason(q.getReason());
        }
        RefundVO f = refundService.create(req, q.getId());
        q.setStatus(FinanceConstants.RQ_EXECUTED);
        q.setRefundId(f.getId());
        q.setHandlerId(me());
        q.setHandledAt(LocalDateTime.now());
        mapper.updateById(q);
        docLogs.add(DocumentType.RA, id, "执行", "生成退款单 " + f.getRefundNo() + "，" + q.getCurrencyCode() + " " + f.getAmount());
        afterSalesLogs.add(q.getAfterSalesId(), AfterSalesConstants.LOG_REFUND, "退款申请 " + q.getRequestNo() + " 已执行，生成退款单 "
                + f.getRefundNo(), me());
        logService.recordOperateLog(MENU, "执行退款申请", null, Map.of("requestNo", q.getRequestNo(), "refundNo", f.getRefundNo()));
        return detail(id);
    }

    // ---------------------------------------------------------------- 由销售退货生成（销售退货单事务内，已锁售后单与订单）

    /** 确认退货退款的销售退货单时生成：每个退货型号一行，默认金额 = 退货数量 × 成交单价（HALF_UP 两位），可在确认弹窗逐行改 */
    public RefundRequestDO createForReturn(SalesReturnDO r, AfterSalesDO a, SalesOrderDO o, List<SalesReturnItemDO> items,
                                           ConfirmReturnRequest.Refund refund) {
        String reason = StringUtils.hasText(refund.getReason()) ? refund.getReason().trim() : FinanceConstants.RQ_DEFAULT_REASON;
        if (!refundService.reasonNames().containsKey(reason)) {
            throw new BizException("退款原因不存在");
        }
        Integer method = refund.getMethod();
        if (method == null || !List.of(FinanceConstants.REFUND_PLATFORM, FinanceConstants.REFUND_BANK, FinanceConstants.REFUND_TO_CREDIT)
                .contains(method)) {
            throw new BizException("请选择退款去向");
        }
        RequestRefundableVO avail = refundableOf(o, null);
        if (Boolean.TRUE.equals(avail.getPlatformOnly()) && method != FinanceConstants.REFUND_PLATFORM) {
            throw new BizException("平台收款的订单只能原路退回");
        }
        Map<Long, BigDecimal> edited = new HashMap<>();
        if (refund.getLines() != null) {
            for (ConfirmReturnRequest.Line l : refund.getLines()) {
                if (l.getAmount().signum() < 0) {
                    throw new BizException("退款金额不能为负数");
                }
                edited.put(l.getReturnItemId(), l.getAmount().setScale(2, RoundingMode.HALF_UP));
            }
        }
        if (!items.stream().map(SalesReturnItemDO::getId).collect(Collectors.toSet()).containsAll(edited.keySet())) {
            throw new BizException("退款行不正确");
        }
        Map<Long, SalesOrderItemDO> soItems = orderItemMapper.selectBatchIds(items.stream().map(SalesReturnItemDO::getSoItemId).toList())
                .stream().collect(Collectors.toMap(SalesOrderItemDO::getId, i -> i));
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        for (SalesReturnItemDO x : items) {
            SalesOrderItemDO si = soItems.get(x.getSoItemId());
            BigDecimal unit = si == null || si.getUnitPrice() == null ? BigDecimal.ZERO : si.getUnitPrice();
            amounts.put(x.getId(), edited.getOrDefault(x.getId(),
                    unit.multiply(BigDecimal.valueOf(x.getQuantity())).setScale(2, RoundingMode.HALF_UP)));
        }
        BigDecimal total = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw new BizException("退款金额须大于 0");
        }
        if (total.compareTo(avail.getAvailable()) > 0) {
            throw new BizException("最多还能申请 " + o.getCurrencyCode() + " " + String.format("%,.2f", avail.getAvailable()));
        }
        RefundRequestDO q = new RefundRequestDO();
        q.setTenantId(o.getTenantId());
        q.setRequestNo(documentNumberService.next(DocumentType.RA));
        q.setAfterSalesId(a.getId());
        q.setSoId(o.getId());
        q.setSalesReturnId(r.getId());
        q.setRefundFirst(r.getRefundFirst());
        q.setCurrencyCode(o.getCurrencyCode());
        q.setAmount(total);
        q.setReason(reason);
        q.setMethod(method);
        q.setPayeeInfo(trim(refund.getPayeeInfo()));
        q.setRemark(trim(refund.getRemark()));
        q.setStatus(Integer.valueOf(1).equals(r.getRefundFirst()) ? nextStatus() : FinanceConstants.RQ_PENDING_RECEIPT);
        q.setApplicantId(me());
        q.setRejectReason("");
        mapper.insert(q);
        for (SalesReturnItemDO x : items) {
            SalesOrderItemDO si = soItems.get(x.getSoItemId());
            insertItem(q, FinanceConstants.RQ_LINE_RETURN, x.getSoItemId(), x.getId(), x.getModel(), x.getBrand(), x.getQuantity(),
                    si == null ? null : si.getUnitPrice(), amounts.get(x.getId()), "");
        }
        docLogs.add(DocumentType.RA, q.getId(), "生成", "销售退货单 " + r.getReturnNo() + " 确认退货：" + o.getCurrencyCode() + " " + total
                + "（" + FinanceConstants.RQ_STATUS_NAMES.get(q.getStatus()) + "）");
        logService.recordOperateLog(MENU, "由销售退货生成退款申请", null, Map.of("requestNo", q.getRequestNo(), "returnNo", r.getReturnNo(),
                "amount", o.getCurrencyCode() + " " + total));
        return q;
    }

    /** 退货入库单验收：待验收的申请按审批开关进入待审批或待财务处理 */
    public List<String> onReturnReceived(SalesReturnDO r) {
        List<String> text = new ArrayList<>();
        for (RefundRequestDO q : lockOfReturn(r.getId(), FinanceConstants.RQ_PENDING_RECEIPT)) {
            q.setStatus(nextStatus());
            mapper.updateById(q);
            String to = FinanceConstants.RQ_STATUS_NAMES.get(q.getStatus());
            docLogs.add(DocumentType.RA, q.getId(), "退货已验收", "进入" + to);
            text.add("退款申请 " + q.getRequestNo() + " 进入" + to);
        }
        return text;
    }

    /** 退货入库单撤回入库：没勾先退款、还没执行的申请回到待验收 */
    public List<String> onReturnUnreceived(SalesReturnDO r) {
        List<String> text = new ArrayList<>();
        for (RefundRequestDO q : lockOfReturn(r.getId(), FinanceConstants.RQ_PENDING_APPROVAL, FinanceConstants.RQ_PENDING_FINANCE)) {
            if (Integer.valueOf(1).equals(q.getRefundFirst())) {
                continue;
            }
            q.setStatus(FinanceConstants.RQ_PENDING_RECEIPT);
            q.setApproverId(null);
            q.setApprovedAt(null);
            mapper.updateById(q);
            docLogs.add(DocumentType.RA, q.getId(), "退货撤回入库", "回到待验收");
            text.add("退款申请 " + q.getRequestNo() + " 回到待验收");
        }
        return text;
    }

    /** 销售退货单作废：还没执行的申请撤回；已执行的不能作废退货单 */
    public List<String> withdrawForReturn(SalesReturnDO r) {
        List<RefundRequestDO> rows = lockOfReturn(r.getId(), FinanceConstants.RQ_PENDING_RECEIPT, FinanceConstants.RQ_PENDING_APPROVAL,
                FinanceConstants.RQ_PENDING_FINANCE, FinanceConstants.RQ_EXECUTED);
        rows.stream().filter(q -> q.getStatus() == FinanceConstants.RQ_EXECUTED).findFirst().ifPresent(q -> {
            throw new BizException("退款申请 " + q.getRequestNo() + " 已执行，不能作废销售退货单");
        });
        List<String> text = new ArrayList<>();
        for (RefundRequestDO q : rows) {
            q.setStatus(FinanceConstants.RQ_WITHDRAWN);
            mapper.updateById(q);
            docLogs.add(DocumentType.RA, q.getId(), "撤回", "销售退货单 " + r.getReturnNo() + " 作废");
            text.add("退款申请 " + q.getRequestNo() + " 已撤回");
        }
        return text;
    }

    /** 销售退货单详情：由它生成的申请 */
    public List<RefundRequestVO> ofSalesReturn(Long salesReturnId) {
        return toVos(mapper.selectList(new LambdaQueryWrapper<RefundRequestDO>()
                .eq(RefundRequestDO::getSalesReturnId, salesReturnId)
                .isNull(RefundRequestDO::getDeletedAt)
                .orderByDesc(RefundRequestDO::getId)), false);
    }

    private List<RefundRequestDO> lockOfReturn(Long salesReturnId, Integer... statuses) {
        List<RefundRequestDO> rows = mapper.selectList(new LambdaQueryWrapper<RefundRequestDO>()
                .eq(RefundRequestDO::getSalesReturnId, salesReturnId)
                .in(RefundRequestDO::getStatus, List.of(statuses))
                .isNull(RefundRequestDO::getDeletedAt)
                .orderByAsc(RefundRequestDO::getId));
        List<RefundRequestDO> out = new ArrayList<>(rows.size());
        for (RefundRequestDO q : rows) {
            mapper.lockById(q.getId());
            RefundRequestDO fresh = mapper.selectById(q.getId());
            if (List.of(statuses).contains(fresh.getStatus())) {
                out.add(fresh);
            }
        }
        return out;
    }

    private int nextStatus() {
        return approvalRequired() ? FinanceConstants.RQ_PENDING_APPROVAL : FinanceConstants.RQ_PENDING_FINANCE;
    }

    private void insertItem(RefundRequestDO q, int lineType, Long soItemId, Long returnItemId, String name, String brand, Integer qty,
                            BigDecimal unitPrice, BigDecimal amount, String note) {
        RefundRequestItemDO i = new RefundRequestItemDO();
        i.setTenantId(q.getTenantId());
        i.setRequestId(q.getId());
        i.setLineType(lineType);
        i.setSoItemId(soItemId);
        i.setSalesReturnItemId(returnItemId);
        i.setItemName(name);
        i.setBrand(brand == null ? "" : brand);
        i.setQuantity(qty);
        i.setUnitPrice(unitPrice);
        i.setCurrencyCode(q.getCurrencyCode());
        i.setAmount(amount);
        i.setNote(note);
        itemMapper.insert(i);
    }

    // ---------------------------------------------------------------- 备注与附件

    @Transactional(rollbackFor = Exception.class)
    public RefundRequestVO remark(Long id, String remark) {
        visible(id);
        RefundRequestDO q = lock(id);
        q.setRemark(trim(remark));
        mapper.updateById(q);
        docLogs.add(DocumentType.RA, id, "修改备注", q.getRemark().isEmpty() ? "清空备注" : q.getRemark());
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundRequestVO attach(Long id, Collection<Long> ids) {
        visible(id);
        attachments.attach(AttachmentService.REFUND_REQUEST, id, ids);
        docLogs.add(DocumentType.RA, id, "上传附件", ids.size() + " 个");
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundRequestVO removeAttachment(Long id, Long attachmentId) {
        visible(id);
        attachments.remove(AttachmentService.REFUND_REQUEST, id, attachmentId);
        docLogs.add(DocumentType.RA, id, "删除附件", "");
        return detail(id);
    }

    // ---------------------------------------------------------------- 查看

    public PageResult<RefundRequestVO> page(RefundRequestQuery query) {
        LambdaQueryWrapper<RefundRequestDO> w = new LambdaQueryWrapper<RefundRequestDO>()
                .eq(RefundRequestDO::getTenantId, PiStore.tenantId())
                .isNull(RefundRequestDO::getDeletedAt);
        if (query.getStatus() != null) {
            w.eq(RefundRequestDO::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            Set<Long> soIds = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>().select(SalesOrderDO::getId)
                            .eq(SalesOrderDO::getTenantId, PiStore.tenantId()).like(SalesOrderDO::getSoNo, kw).last("LIMIT 200"))
                    .stream().map(SalesOrderDO::getId).collect(Collectors.toSet());
            Set<Long> asIds = afterSalesMapper.selectList(new LambdaQueryWrapper<AfterSalesDO>().select(AfterSalesDO::getId)
                            .eq(AfterSalesDO::getTenantId, PiStore.tenantId()).like(AfterSalesDO::getAsNo, kw).last("LIMIT 200"))
                    .stream().map(AfterSalesDO::getId).collect(Collectors.toSet());
            Set<Long> thIds = salesReturnMapper.selectList(new LambdaQueryWrapper<SalesReturnDO>().select(SalesReturnDO::getId)
                            .eq(SalesReturnDO::getTenantId, PiStore.tenantId()).like(SalesReturnDO::getReturnNo, kw).last("LIMIT 200"))
                    .stream().map(SalesReturnDO::getId).collect(Collectors.toSet());
            w.and(x -> {
                x.like(RefundRequestDO::getRequestNo, kw);
                if (!thIds.isEmpty()) {
                    x.or().in(RefundRequestDO::getSalesReturnId, thIds);
                }
                if (!soIds.isEmpty()) {
                    x.or().in(RefundRequestDO::getSoId, soIds);
                }
                if (!asIds.isEmpty()) {
                    x.or().in(RefundRequestDO::getAfterSalesId, asIds);
                }
            });
        }
        int size = query.getPageSize() == null || query.getPageSize() <= 0 ? 20 : Math.min(query.getPageSize(), 100);
        int page = Math.max(1, query.getPage() == null ? 1 : query.getPage());
        long total = mapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        w.last("ORDER BY update_time DESC, id DESC LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toVos(mapper.selectList(w), false));
    }

    /** 页签数量：待审批、待财务处理 */
    public Map<String, Long> counts() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("pendingReceipt", count(FinanceConstants.RQ_PENDING_RECEIPT));
        m.put("pendingApproval", count(FinanceConstants.RQ_PENDING_APPROVAL));
        m.put("pendingFinance", count(FinanceConstants.RQ_PENDING_FINANCE));
        m.put("approvalRequired", approvalRequired() ? 1L : 0L);
        return m;
    }

    public RefundRequestVO detail(Long id) {
        return toVos(List.of(visible(id)), true).get(0);
    }

    // ---------------------------------------------------------------- 内部

    private RequestRefundableVO refundableOf(SalesOrderDO o, Long excludeId) {
        RefundableVO r = refundService.refundable(o.getId());
        BigDecimal pending = mapper.selectList(new LambdaQueryWrapper<RefundRequestDO>()
                        .eq(RefundRequestDO::getSoId, o.getId())
                        .in(RefundRequestDO::getStatus, FinanceConstants.RQ_PENDING_RECEIPT, FinanceConstants.RQ_PENDING_APPROVAL,
                                FinanceConstants.RQ_PENDING_FINANCE)
                        .isNull(RefundRequestDO::getDeletedAt))
                .stream().filter(q -> !q.getId().equals(excludeId)).map(RefundRequestDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        RequestRefundableVO v = new RequestRefundableVO();
        v.setSoId(o.getId());
        v.setSoNo(o.getSoNo());
        v.setCurrencyCode(o.getCurrencyCode());
        v.setReceived(r.getReceived());
        v.setRefunded(r.getRefunded());
        v.setPending(pending);
        v.setAvailable(r.getRefundable().subtract(pending).max(BigDecimal.ZERO));
        v.setPlatformOnly(r.getPlatformOnly());
        v.setApprovalRequired(approvalRequired());
        return v;
    }

    private long count(int status) {
        return mapper.selectCount(new LambdaQueryWrapper<RefundRequestDO>()
                .eq(RefundRequestDO::getTenantId, PiStore.tenantId())
                .eq(RefundRequestDO::getStatus, status)
                .isNull(RefundRequestDO::getDeletedAt));
    }

    private List<RefundRequestVO> toVos(List<RefundRequestDO> rows, boolean full) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, SalesOrderDO> orders = orderMapper.selectBatchIds(rows.stream().map(RefundRequestDO::getSoId).distinct().toList())
                .stream().collect(Collectors.toMap(SalesOrderDO::getId, o -> o));
        Map<Long, CustomerDO> customers = lookups.customers(orders.values().stream().map(SalesOrderDO::getCustomerId).distinct().toList());
        List<Long> asIds = rows.stream().map(RefundRequestDO::getAfterSalesId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> asNos = asIds.isEmpty() ? Map.of() : afterSalesMapper.selectBatchIds(asIds).stream()
                .collect(Collectors.toMap(AfterSalesDO::getId, AfterSalesDO::getAsNo));
        List<Long> refundIds = rows.stream().map(RefundRequestDO::getRefundId).filter(Objects::nonNull).toList();
        Map<Long, CustomerRefundDO> refunds = refundIds.isEmpty() ? Map.of() : refundMapper.selectBatchIds(refundIds).stream()
                .collect(Collectors.toMap(CustomerRefundDO::getId, f -> f));
        List<Long> returnIds = rows.stream().map(RefundRequestDO::getSalesReturnId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> returnNos = returnIds.isEmpty() ? Map.of() : salesReturnMapper.selectBatchIds(returnIds).stream()
                .collect(Collectors.toMap(SalesReturnDO::getId, SalesReturnDO::getReturnNo));
        Map<Long, String> users = lookups.userNames(rows.stream().flatMap(q -> Stream.of(q.getApplicantId(), q.getApproverId(), q.getHandlerId()))
                .filter(Objects::nonNull).distinct().toList());
        Map<String, String> reasons = refundService.reasonNames();
        List<RefundRequestVO> out = new ArrayList<>(rows.size());
        for (RefundRequestDO q : rows) {
            SalesOrderDO o = orders.get(q.getSoId());
            RefundRequestVO v = new RefundRequestVO();
            v.setId(q.getId());
            v.setRequestNo(q.getRequestNo());
            v.setSalesReturnId(q.getSalesReturnId());
            v.setRefundFirst(q.getRefundFirst());
            v.setSourceNo(q.getSalesReturnId() != null ? returnNos.get(q.getSalesReturnId())
                    : q.getAfterSalesId() == null ? null : asNos.get(q.getAfterSalesId()));
            v.setAfterSalesId(q.getAfterSalesId());
            v.setAsNo(q.getAfterSalesId() == null ? null : asNos.get(q.getAfterSalesId()));
            v.setSoId(q.getSoId());
            v.setSoNo(o == null ? null : o.getSoNo());
            v.setCustomerName(o == null ? null : InquiryLookups.customerName(customers.get(o.getCustomerId())));
            CustomerDO customer = o == null ? null : customers.get(o.getCustomerId());
            v.setCustomerContact(InquiryLookups.contactName(customer));
            v.setCustomerCountry(customer == null ? null : customer.getCountry());
            v.setCurrencyCode(q.getCurrencyCode());
            v.setAmount(q.getAmount());
            v.setReason(q.getReason());
            v.setReasonName(reasons.getOrDefault(q.getReason(), q.getReason()));
            v.setMethod(q.getMethod());
            v.setMethodName(FinanceConstants.REFUND_METHOD_NAMES.get(q.getMethod()));
            v.setPayeeInfo(q.getPayeeInfo());
            v.setRemark(q.getRemark());
            v.setStatus(q.getStatus());
            v.setStatusName(FinanceConstants.RQ_STATUS_NAMES.get(q.getStatus()));
            v.setApplicantName(users.get(q.getApplicantId()));
            v.setApproverName(q.getApproverId() == null ? null : users.get(q.getApproverId()));
            v.setApprovedAt(q.getApprovedAt());
            v.setHandlerName(q.getHandlerId() == null ? null : users.get(q.getHandlerId()));
            v.setHandledAt(q.getHandledAt());
            v.setRejectReason(q.getRejectReason());
            v.setRefundId(q.getRefundId());
            CustomerRefundDO f = q.getRefundId() == null ? null : refunds.get(q.getRefundId());
            v.setRefundNo(f == null ? null : f.getRefundNo());
            v.setRefundAmount(f == null ? null : f.getAmount());
            if (full) {
                v.setItems(itemsOf(q.getId()));
                v.setReceivedHint(q.getSalesReturnId() == null ? null : receivedHint(q.getSalesReturnId()));
                v.setAttachments(attachments.listOf(AttachmentService.REFUND_REQUEST, q.getId()));
                v.setLogs(docLogs.list(DocumentType.RA, q.getId()));
            }
            v.setCreateTime(q.getCreateTime());
            v.setCreateBy(q.getCreateBy());
            v.setUpdateTime(q.getUpdateTime());
            v.setUpdateBy(q.getUpdateBy());
            out.add(v);
        }
        return out;
    }

    private List<RefundRequestVO.Item> itemsOf(Long requestId) {
        List<RefundRequestItemDO> rows = itemMapper.selectList(new LambdaQueryWrapper<RefundRequestItemDO>()
                .eq(RefundRequestItemDO::getRequestId, requestId)
                .isNull(RefundRequestItemDO::getDeletedAt)
                .orderByAsc(RefundRequestItemDO::getId));
        List<Long> soItemIds = rows.stream().map(RefundRequestItemDO::getSoItemId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> categories = soItemIds.isEmpty() ? Map.of() : orderItemMapper.selectBatchIds(soItemIds).stream()
                .collect(Collectors.toMap(SalesOrderItemDO::getId, i -> Objects.requireNonNullElse(i.getCategory(), "")));
        return rows.stream().map(x -> {
            RefundRequestVO.Item i = new RefundRequestVO.Item();
            i.setId(x.getId());
            i.setLineType(x.getLineType());
            i.setSoItemId(x.getSoItemId());
            i.setSalesReturnItemId(x.getSalesReturnItemId());
            i.setItemName(x.getItemName());
            i.setBrand(x.getBrand());
            i.setCategory(x.getSoItemId() == null ? null : categories.get(x.getSoItemId()));
            i.setQuantity(x.getQuantity());
            i.setUnitPrice(x.getUnitPrice());
            i.setAmount(x.getAmount());
            i.setNote(x.getNote());
            return i;
        }).toList();
    }

    /** 退货入库单已验收、实收少于退货数量时的提示 */
    private String receivedHint(Long salesReturnId) {
        ReturnReceiptDO rr = receiptMapper.selectOne(new LambdaQueryWrapper<ReturnReceiptDO>()
                .eq(ReturnReceiptDO::getSalesReturnId, salesReturnId)
                .in(ReturnReceiptDO::getStatus, AfterSalesConstants.RR_RECEIVED, AfterSalesConstants.RR_DONE)
                .isNull(ReturnReceiptDO::getDeletedAt)
                .last("LIMIT 1"));
        if (rr == null) {
            return null;
        }
        List<ReturnReceiptItemDO> items = receiptItemMapper.selectList(new LambdaQueryWrapper<ReturnReceiptItemDO>()
                .eq(ReturnReceiptItemDO::getReceiptId, rr.getId())
                .isNull(ReturnReceiptItemDO::getDeletedAt));
        int qty = items.stream().mapToInt(ReturnReceiptItemDO::getQuantity).sum();
        int got = items.stream().mapToInt(x -> Objects.requireNonNullElse(x.getReceivedQty(), 0)).sum();
        return got < qty ? "实收 " + got + " 个，少于退货数量 " + qty + " 个" : null;
    }

    /** 售后单详情与订单详情：订单的申请 */
    public List<RefundRequestVO> ofOrder(Long soId) {
        visibleOrder(soId);
        return toVos(mapper.selectList(new LambdaQueryWrapper<RefundRequestDO>()
                .eq(RefundRequestDO::getSoId, soId)
                .isNull(RefundRequestDO::getDeletedAt)
                .orderByDesc(RefundRequestDO::getId)), false);
    }

    private SalesOrderDO visibleOrder(Long soId) {
        SalesOrderDO o = soId == null ? null : orderMapper.selectById(soId);
        if (o == null || o.getDeletedAt() != null || !Objects.equals(o.getTenantId(), PiStore.tenantId())
                || !dataScopeResolver.current().canSee(o.getOwnerId())) {
            throw new BizException("销售订单不存在");
        }
        if (o.getStatus() != SalesConstants.SO_ACTIVE) {
            throw new BizException("订单已取消，不能申请退款");
        }
        return o;
    }

    private RefundRequestDO require(Long id) {
        RefundRequestDO q = id == null ? null : mapper.selectById(id);
        if (q == null || q.getDeletedAt() != null || !Objects.equals(q.getTenantId(), PiStore.tenantId())) {
            throw new BizException("退款申请不存在");
        }
        return q;
    }

    /**
     * 按 ID 读取、改备注、维护附件（audit SEC-013）：退款单菜单（财务）与审批人处理本租户全部申请；
     * 只从订单、售后进来的申请侧按订单负责人的数据范围，与按订单列出申请一致
     */
    private RefundRequestDO visible(Long id) {
        RefundRequestDO q = require(id);
        if (perm.canAccessMenu(FinanceConstants.MENU_REFUND)
                || perm.has(FinanceConstants.PERM_REFUND_REQUEST_APPROVE)) {
            return q;
        }
        SalesOrderDO o = q.getSoId() == null ? null : orderMapper.selectById(q.getSoId());
        if (o == null || !dataScopeResolver.current().canSee(o.getOwnerId())) {
            throw new BizException("退款申请不存在");
        }
        return q;
    }

    private RefundRequestDO lock(Long id) {
        require(id);
        mapper.lockById(id);
        return mapper.selectById(id);
    }

    private static void requireStatus(RefundRequestDO q, Integer... allowed) {
        if (!List.of(allowed).contains(q.getStatus())) {
            throw new BizException("退款申请" + FinanceConstants.RQ_STATUS_NAMES.get(q.getStatus()) + "，不能这样操作");
        }
    }

    private Long me() {
        Long id = currentUser.resolve();
        return id == null ? 0L : id;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}

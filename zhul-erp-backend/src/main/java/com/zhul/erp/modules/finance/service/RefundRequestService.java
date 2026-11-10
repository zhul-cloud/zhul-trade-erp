package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.modules.aftersales.constants.AfterSalesConstants;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.aftersales.repository.AfterSalesMapper;
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
import com.zhul.erp.modules.finance.repository.CustomerRefundMapper;
import com.zhul.erp.modules.finance.repository.RefundRequestMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 退款申请（spec finance/refund-request）：业务员提出 →（系统设置打开审批时先审批）→ 财务执行生成售后退款单或驳回。
 * 可申请 = 已到账货款 − 有效售后退款 − 其他待审批 / 待财务处理的申请（原币，HALF_UP 两位）。
 */
@Service
@RequiredArgsConstructor
public class RefundRequestService {

    private static final String MENU = "退款管理";

    private final RefundRequestMapper mapper;
    private final CustomerRefundMapper refundMapper;
    private final SalesOrderMapper orderMapper;
    private final AfterSalesMapper afterSalesMapper;
    private final AfterSalesService afterSalesService;
    private final AfterSalesLogs afterSalesLogs;
    private final CustomerRefundService refundService;
    private final SysConfigMapper sysConfigMapper;
    private final DocumentNumberService documentNumberService;
    private final DataScopeResolver dataScopeResolver;
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
        requireStatus(q, FinanceConstants.RQ_PENDING_APPROVAL, FinanceConstants.RQ_PENDING_FINANCE);
        q.setStatus(FinanceConstants.RQ_WITHDRAWN);
        mapper.updateById(q);
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
        afterSalesLogs.add(q.getAfterSalesId(), AfterSalesConstants.LOG_REFUND, "退款申请 " + q.getRequestNo() + " 已执行，生成退款单 "
                + f.getRefundNo(), me());
        logService.recordOperateLog(MENU, "执行退款申请", null, Map.of("requestNo", q.getRequestNo(), "refundNo", f.getRefundNo()));
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
            w.and(x -> {
                x.like(RefundRequestDO::getRequestNo, kw);
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
        return PageResult.of(total, toVos(mapper.selectList(w)));
    }

    /** 页签数量：待审批、待财务处理 */
    public Map<String, Long> counts() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("pendingApproval", count(FinanceConstants.RQ_PENDING_APPROVAL));
        m.put("pendingFinance", count(FinanceConstants.RQ_PENDING_FINANCE));
        m.put("approvalRequired", approvalRequired() ? 1L : 0L);
        return m;
    }

    public RefundRequestVO detail(Long id) {
        return toVos(List.of(require(id))).get(0);
    }

    // ---------------------------------------------------------------- 内部

    private RequestRefundableVO refundableOf(SalesOrderDO o, Long excludeId) {
        RefundableVO r = refundService.refundable(o.getId());
        BigDecimal pending = mapper.selectList(new LambdaQueryWrapper<RefundRequestDO>()
                        .eq(RefundRequestDO::getSoId, o.getId())
                        .in(RefundRequestDO::getStatus, FinanceConstants.RQ_PENDING_APPROVAL, FinanceConstants.RQ_PENDING_FINANCE)
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

    private List<RefundRequestVO> toVos(List<RefundRequestDO> rows) {
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
        Map<Long, String> refundNos = refundIds.isEmpty() ? Map.of() : refundMapper.selectBatchIds(refundIds).stream()
                .collect(Collectors.toMap(CustomerRefundDO::getId, CustomerRefundDO::getRefundNo));
        Map<Long, String> users = lookups.userNames(rows.stream().flatMap(q -> Stream.of(q.getApplicantId(), q.getApproverId(), q.getHandlerId()))
                .filter(Objects::nonNull).distinct().toList());
        Map<String, String> reasons = refundService.reasonNames();
        List<RefundRequestVO> out = new ArrayList<>(rows.size());
        for (RefundRequestDO q : rows) {
            SalesOrderDO o = orders.get(q.getSoId());
            RefundRequestVO v = new RefundRequestVO();
            v.setId(q.getId());
            v.setRequestNo(q.getRequestNo());
            v.setAfterSalesId(q.getAfterSalesId());
            v.setAsNo(q.getAfterSalesId() == null ? null : asNos.get(q.getAfterSalesId()));
            v.setSoId(q.getSoId());
            v.setSoNo(o == null ? null : o.getSoNo());
            v.setCustomerName(o == null ? null : InquiryLookups.customerName(customers.get(o.getCustomerId())));
            v.setCustomerContact(o == null ? null : InquiryLookups.contactName(customers.get(o.getCustomerId())));
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
            v.setRefundNo(q.getRefundId() == null ? null : refundNos.get(q.getRefundId()));
            v.setCreateTime(q.getCreateTime());
            v.setCreateBy(q.getCreateBy());
            v.setUpdateTime(q.getUpdateTime());
            v.setUpdateBy(q.getUpdateBy());
            out.add(v);
        }
        return out;
    }

    /** 售后单详情与订单详情：订单的申请 */
    public List<RefundRequestVO> ofOrder(Long soId) {
        visibleOrder(soId);
        return toVos(mapper.selectList(new LambdaQueryWrapper<RefundRequestDO>()
                .eq(RefundRequestDO::getSoId, soId)
                .isNull(RefundRequestDO::getDeletedAt)
                .orderByDesc(RefundRequestDO::getId)));
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

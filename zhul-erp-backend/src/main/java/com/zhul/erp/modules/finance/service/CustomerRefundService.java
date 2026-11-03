package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.utils.MigratedGuard;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.RefundQuery;
import com.zhul.erp.modules.finance.dto.RefundTotalsVO;
import com.zhul.erp.modules.finance.dto.RefundVO;
import com.zhul.erp.modules.finance.dto.RefundableVO;
import com.zhul.erp.modules.finance.dto.SaveRefundRequest;
import com.zhul.erp.modules.finance.entity.CustomerCreditEntryDO;
import com.zhul.erp.modules.finance.entity.CustomerRefundDO;
import com.zhul.erp.modules.finance.entity.RefundRequestDO;
import com.zhul.erp.modules.finance.repository.CustomerRefundMapper;
import com.zhul.erp.modules.finance.service.CustomerCreditLedger.Account;
import com.zhul.erp.modules.finance.service.CustomerCreditLedger.Source;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.sales.support.ReceiptPosition;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DictItemService;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 客户退款单（spec finance/customer-refund），分两类：
 * <ul>
 *   <li>售后退款：关联销售订单，冲减订单收入；去向为原路退回平台、银行汇款或转客户余额（转余额时同一事务内生成余额转入）。
 *       可退金额 = min(已到账, 合计 − 代收款) − 有效售后退款，即不含多收与代收；有平台收款的订单只能原路退回。
 *       转客户余额的人民币 = 退款金额 × 现金收款加权到账汇率，HALF_UP 两位并锁定；</li>
 *   <li>余额提现：把客户余额转给客户，不挂订单、不影响利润，由余额管理与多收处理生成（见 CustomerCreditService）。</li>
 * </ul>
 * 汇率 = 人民币 ÷ 退款金额，6 位。事务边界：登记 / 作废与附件、余额流水、汇兑损益在同一事务内；登记时对订单加行锁，避免并发超退。
 */
@Service
@RequiredArgsConstructor
public class CustomerRefundService {

    private static final String MENU = "退款管理";

    private final CustomerRefundMapper refundMapper;
    private final SalesOrderMapper orderMapper;
    private final DocumentNumberService documentNumberService;
    private final AttachmentService attachments;
    private final DictItemService dictItemService;
    private final InquiryLookups lookups;
    private final LogService logService;
    private final ReceiptPosition receiptPosition;
    private final CustomerCreditLedger ledger;
    private final OtherIncomeService otherIncomeService;
    private final com.zhul.erp.modules.aftersales.service.AfterSalesService afterSalesService;
    private final com.zhul.erp.modules.aftersales.support.AfterSalesLogs afterSalesLogs;
    private final com.zhul.erp.modules.aftersales.repository.AfterSalesMapper afterSalesMapper;
    private final com.zhul.erp.modules.finance.repository.RefundRequestMapper requestMapper;

    /** 订单的可退金额、平均到账汇率与可选去向 */
    public RefundableVO refundable(Long soId) {
        SalesOrderDO o = requireOrder(soId, false);
        return refundableOf(o, receiptPosition.ofOrder(o));
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundVO create(SaveRefundRequest req) {
        return create(req, null);
    }

    /** 登记售后退款；refundRequestId 不为空时为执行退款申请（由 RefundRequestService 在同一事务内调用） */
    @Transactional(rollbackFor = Exception.class)
    public RefundVO create(SaveRefundRequest req, Long refundRequestId) {
        SalesOrderDO o = requireOrder(req.getSoId(), true);
        com.zhul.erp.modules.aftersales.entity.AfterSalesDO as = req.getAfterSalesId() == null ? null
                : afterSalesService.requireOfOrder(req.getAfterSalesId(), o.getId());
        if (req.getRefundDate().isAfter(LocalDate.now())) {
            throw new BizException("退款日期不能晚于今天");
        }
        if (reasonNames().get(req.getReason()) == null) {
            throw new BizException("退款原因不存在");
        }
        int method = req.getMethod();
        if (method != FinanceConstants.REFUND_PLATFORM && method != FinanceConstants.REFUND_BANK
                && method != FinanceConstants.REFUND_TO_CREDIT) {
            throw new BizException("退款去向不正确");
        }
        ReceiptPosition.Position pos = receiptPosition.ofOrder(o);
        if (pos.hasOnline() && method != FinanceConstants.REFUND_PLATFORM) {
            throw new BizException("平台收款的订单只能原路退回");
        }
        boolean toCredit = method == FinanceConstants.REFUND_TO_CREDIT;
        if (!toCredit && (req.getAttachmentIds() == null || req.getAttachmentIds().isEmpty())) {
            throw new BizException("请上传退款凭证");
        }
        RefundableVO r = refundableOf(o, pos);
        BigDecimal amount = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(r.getRefundable()) > 0) {
            throw new BizException("最多还能退 " + o.getCurrencyCode() + " " + String.format("%,.2f", r.getRefundable()));
        }
        BigDecimal cny;
        if (toCredit) {
            cny = amount.multiply(r.getCreditRate()).setScale(2, RoundingMode.HALF_UP);
        } else {
            cny = req.getAmountCny() != null ? req.getAmountCny().setScale(2, RoundingMode.HALF_UP)
                    : amount.multiply(r.getAverageRate()).setScale(2, RoundingMode.HALF_UP);
        }
        CustomerRefundDO f = newRefund(FinanceConstants.REFUND_AFTER_SALES, o.getCustomerId(), o.getBusinessUnitId(),
                req.getRefundDate(), o.getCurrencyCode(), amount, cny, method, "", "", req.getRemark());
        f.setSoId(o.getId());
        f.setAfterSalesId(as == null ? null : as.getId());
        f.setRefundRequestId(refundRequestId);
        f.setReason(req.getReason());
        refundMapper.insert(f);
        afterSalesLogs.add(f.getAfterSalesId(), com.zhul.erp.modules.aftersales.constants.AfterSalesConstants.LOG_REFUND,
                "退款单 " + f.getRefundNo() + "：" + o.getCurrencyCode() + " " + amount + "，" + FinanceConstants.REFUND_METHOD_NAMES.get(method),
                null);
        if (toCredit) {
            CustomerCreditEntryDO in = ledger.credit(new Account(o.getCustomerId(), o.getCurrencyCode(), o.getBusinessUnitId()),
                    FinanceConstants.CREDIT_REFUND_IN, amount, cny, new Source(FinanceConstants.SRC_REFUND, f.getId(), f.getRefundNo()),
                    reasonNames().get(req.getReason()) + "，订单 " + o.getSoNo());
            f.setCreditEntryId(in.getId());
            refundMapper.updateById(f);
        }
        attachments.attach(AttachmentService.REFUND, f.getId(), req.getAttachmentIds());
        logService.recordOperateLog(MENU, "登记退款", null, Map.of("refundNo", f.getRefundNo(), "soNo", o.getSoNo(),
                "amount", o.getCurrencyCode() + " " + amount, "amountCny", cny.toPlainString(),
                "method", FinanceConstants.REFUND_METHOD_NAMES.get(method)));
        return detail(f.getId());
    }

    /**
     * 余额提现退款单（由 CustomerCreditService 在同一事务内调用）：先生成退款单，再由调用方生成提现流水并回填 creditEntryId。
     * method：2-银行汇款、4-其他（名称见 methodName，如支付宝）。
     */
    public CustomerRefundDO newWithdraw(Account a, LocalDate date, BigDecimal amount, BigDecimal actualCny, int method,
                                        String methodName, String payeeAccount, String remark) {
        CustomerRefundDO f = newRefund(FinanceConstants.REFUND_CREDIT_WITHDRAW, a.customerId(), a.businessUnitId(), date,
                a.currencyCode(), amount, actualCny, method, methodName, payeeAccount, remark);
        f.setReason("");
        refundMapper.insert(f);
        return f;
    }

    public void bindCreditEntry(CustomerRefundDO f, Long entryId) {
        f.setCreditEntryId(entryId);
        refundMapper.updateById(f);
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundVO voidRefund(Long id, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw new BizException("请填写作废原因");
        }
        CustomerRefundDO f = require(id);
        MigratedGuard.requireEditable(f.getMigrated());
        if (f.getStatus() == FinanceConstants.RECORD_VOID) {
            throw new BizException("退款单已作废");
        }
        String w = why.length() > 200 ? why.substring(0, 200) : why;
        if (f.getCreditEntryId() != null) {
            CustomerCreditEntryDO e = ledger.require(f.getCreditEntryId());
            if (f.getRefundType() == FinanceConstants.REFUND_CREDIT_WITHDRAW) {
                // 余额提现作废：按消耗明细原样退回余额，并作废它生成的汇兑损益
                ledger.release(e, FinanceConstants.CREDIT_REVERSE, "作废余额提现 " + f.getRefundNo() + "：" + w);
                otherIncomeService.voidBySource(f.getId(), "来源退款单 " + f.getRefundNo() + " 已作废");
            } else {
                ledger.reverse(e, "作废退款单 " + f.getRefundNo() + "：" + w);
            }
        }
        f.setStatus(FinanceConstants.RECORD_VOID);
        f.setVoidReason(w);
        refundMapper.updateById(f);
        // 由退款申请生成的退款单作废后，申请回到「待财务处理」（spec finance/refund-request）
        if (f.getRefundRequestId() != null) {
            RefundRequestDO q = requestMapper.selectById(f.getRefundRequestId());
            if (q != null && q.getStatus() == FinanceConstants.RQ_EXECUTED) {
                q.setStatus(FinanceConstants.RQ_PENDING_FINANCE);
                q.setRefundId(null);
                requestMapper.updateById(q);
            }
        }
        afterSalesLogs.add(f.getAfterSalesId(), com.zhul.erp.modules.aftersales.constants.AfterSalesConstants.LOG_REFUND,
                "作废退款单 " + f.getRefundNo() + "：" + w, null);
        logService.recordOperateLog(MENU, "作废退款单", Map.of("refundNo", f.getRefundNo(), "status", "有效"),
                Map.of("refundNo", f.getRefundNo(), "status", "已作废", "reason", f.getVoidReason()));
        return detail(id);
    }

    public PageResult<RefundVO> page(RefundQuery q) {
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        Page<CustomerRefundDO> page = refundMapper.selectPage(new Page<>(Math.max(1, q.getPage() == null ? 1 : q.getPage()), size),
                filter(q).orderByDesc(CustomerRefundDO::getUpdateTime).orderByDesc(CustomerRefundDO::getId));
        return PageResult.of(page.getTotal(), toVos(page.getRecords()));
    }

    /** 筛选结果中售后退款的人民币合计 */
    public BigDecimal total(RefundQuery q) {
        return totals(q).getAfterSales();
    }

    /** 筛选结果按类型分别汇总：售后退款（其中转客户余额）、余额提现 */
    public RefundTotalsVO totals(RefundQuery q) {
        RefundTotalsVO t = new RefundTotalsVO();
        t.setAfterSales(BigDecimal.ZERO);
        t.setToCredit(BigDecimal.ZERO);
        t.setWithdraw(BigDecimal.ZERO);
        int after = 0;
        int withdraw = 0;
        for (CustomerRefundDO f : refundMapper.selectList(filter(q))) {
            if (f.getRefundType() == FinanceConstants.REFUND_CREDIT_WITHDRAW) {
                t.setWithdraw(t.getWithdraw().add(f.getAmountCny()));
                withdraw++;
            } else {
                t.setAfterSales(t.getAfterSales().add(f.getAmountCny()));
                after++;
                if (f.getMethod() == FinanceConstants.REFUND_TO_CREDIT) {
                    t.setToCredit(t.getToCredit().add(f.getAmountCny()));
                }
            }
        }
        t.setAfterSalesCount(after);
        t.setWithdrawCount(withdraw);
        return t;
    }

    public RefundVO detail(Long id) {
        return toVos(List.of(require(id))).get(0);
    }

    /** 订单的有效售后退款单（订单成本明细用） */
    public List<RefundVO> ofOrder(Long soId) {
        return toVos(refundMapper.selectList(new LambdaQueryWrapper<CustomerRefundDO>()
                .eq(CustomerRefundDO::getSoId, soId)
                .eq(CustomerRefundDO::getRefundType, FinanceConstants.REFUND_AFTER_SALES)
                .eq(CustomerRefundDO::getStatus, FinanceConstants.RECORD_VALID)
                .isNull(CustomerRefundDO::getDeletedAt)
                .orderByAsc(CustomerRefundDO::getRefundDate)));
    }

    public Map<String, String> reasonNames() {
        Map<String, String> m = new LinkedHashMap<>();
        dictItemService.listByDictType(FinanceConstants.DICT_REFUND_REASON).stream().filter(d -> Objects.equals(d.getStatus(), 1))
                .forEach(d -> m.put(d.getItemCode(), d.getItemName()));
        return m;
    }

    // ---------------------------------------------------------------- 工具

    private CustomerRefundDO newRefund(int type, Long customerId, Integer bu, LocalDate date, String currency, BigDecimal amount,
                                       BigDecimal cny, int method, String methodName, String payeeAccount, String remark) {
        CustomerRefundDO f = new CustomerRefundDO();
        f.setTenantId(PiStore.tenantId());
        f.setRefundNo(documentNumberService.next(DocumentType.TK));
        f.setRefundType(type);
        f.setCustomerId(customerId);
        f.setBusinessUnitId(bu);
        f.setRefundDate(date);
        f.setCurrencyCode(currency);
        f.setAmount(amount);
        f.setAmountCny(cny);
        f.setExchangeRate(cny.divide(amount, 6, RoundingMode.HALF_UP));
        f.setMethod(method);
        f.setMethodName(methodName == null ? "" : methodName.trim());
        f.setPayeeAccount(payeeAccount == null ? "" : payeeAccount.trim());
        f.setRemark(remark == null ? "" : remark.trim());
        f.setStatus(FinanceConstants.RECORD_VALID);
        f.setVoidReason("");
        return f;
    }

    private RefundableVO refundableOf(SalesOrderDO o, ReceiptPosition.Position pos) {
        BigDecimal refunded = refundMapper.selectList(new LambdaQueryWrapper<CustomerRefundDO>()
                        .eq(CustomerRefundDO::getSoId, o.getId())
                        .eq(CustomerRefundDO::getRefundType, FinanceConstants.REFUND_AFTER_SALES)
                        .eq(CustomerRefundDO::getStatus, FinanceConstants.RECORD_VALID)
                        .isNull(CustomerRefundDO::getDeletedAt))
                .stream().map(CustomerRefundDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        // 可退的是货款：不含多收与代收
        BigDecimal goodsReceived = pos.received().min(pos.goods()).max(BigDecimal.ZERO);
        BigDecimal gross = pos.receipts().stream().map(x -> nz(x.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        // 按到账汇率加权（不用已舍入的人民币反算，避免 7.150005 这类尾差）
        BigDecimal grossCny = pos.receipts().stream().map(x -> nz(x.getAmount()).multiply(nz(x.getExchangeRate())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = gross.signum() > 0 ? grossCny.divide(gross, 6, RoundingMode.HALF_UP) : nz(o.getExchangeRate());
        RefundableVO vo = new RefundableVO();
        vo.setSoId(o.getId());
        vo.setSoNo(o.getSoNo());
        vo.setCurrencyCode(o.getCurrencyCode());
        vo.setReceived(goodsReceived);
        vo.setRefunded(refunded);
        vo.setRefundable(goodsReceived.subtract(refunded).max(BigDecimal.ZERO));
        vo.setAverageRate(average);
        vo.setCreditRate(pos.cashRate());
        vo.setPlatformOnly(pos.hasOnline());
        return vo;
    }

    private SalesOrderDO requireOrder(Long soId, boolean lock) {
        SalesOrderDO o = orderMapper.selectOne(new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getId, soId)
                .eq(SalesOrderDO::getTenantId, PiStore.tenantId())
                .isNull(SalesOrderDO::getDeletedAt)
                .last(lock ? "FOR UPDATE" : "LIMIT 1"));
        if (o == null) {
            throw new BizException("销售订单不存在");
        }
        if (o.getStatus() != SalesConstants.SO_ACTIVE) {
            throw new BizException("订单已取消，不能登记退款");
        }
        return o;
    }

    private CustomerRefundDO require(Long id) {
        CustomerRefundDO f = refundMapper.selectById(id);
        if (f == null || f.getDeletedAt() != null || !Objects.equals(f.getTenantId(), PiStore.tenantId())) {
            throw new BizException("退款单不存在");
        }
        return f;
    }

    private LambdaQueryWrapper<CustomerRefundDO> filter(RefundQuery q) {
        LambdaQueryWrapper<CustomerRefundDO> w = new LambdaQueryWrapper<CustomerRefundDO>()
                .eq(CustomerRefundDO::getTenantId, PiStore.tenantId())
                .isNull(CustomerRefundDO::getDeletedAt);
        if (!Boolean.TRUE.equals(q.getShowVoid())) {
            w.eq(CustomerRefundDO::getStatus, FinanceConstants.RECORD_VALID);
        }
        if (q.getRefundType() != null) {
            w.eq(CustomerRefundDO::getRefundType, q.getRefundType());
        }
        if (q.getMethod() != null) {
            w.eq(CustomerRefundDO::getMethod, q.getMethod());
        }
        if (StringUtils.hasText(q.getReason())) {
            w.eq(CustomerRefundDO::getReason, q.getReason());
        }
        if (q.getSoId() != null) {
            w.eq(CustomerRefundDO::getSoId, q.getSoId());
        }
        if (q.getDateFrom() != null) {
            w.ge(CustomerRefundDO::getRefundDate, q.getDateFrom());
        }
        if (q.getDateTo() != null) {
            w.le(CustomerRefundDO::getRefundDate, q.getDateTo());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String k = q.getKeyword().trim();
            w.and(x -> x.like(CustomerRefundDO::getRefundNo, k)
                    .or().apply("so_id IN (SELECT s.id FROM sales_order s WHERE s.so_no LIKE CONCAT('%', {0}, '%'))", k)
                    .or().apply("customer_id IN (SELECT c.id FROM customer c WHERE c.name LIKE CONCAT('%', {0}, '%'))", k));
        }
        return w;
    }

    private List<RefundVO> toVos(List<CustomerRefundDO> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> soIds = rows.stream().map(CustomerRefundDO::getSoId).filter(Objects::nonNull).distinct().toList();
        Map<Long, SalesOrderDO> orders = soIds.isEmpty() ? Map.of() : orderMapper.selectBatchIds(soIds).stream()
                .collect(Collectors.toMap(SalesOrderDO::getId, x -> x));
        List<Long> customerIds = rows.stream()
                .map(f -> f.getCustomerId() != null ? f.getCustomerId()
                        : orders.containsKey(f.getSoId()) ? orders.get(f.getSoId()).getCustomerId() : null)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, CustomerDO> customers = lookups.customers(customerIds);
        Map<Long, List<AttachmentVO>> files = attachments.list(AttachmentService.REFUND, rows.stream().map(CustomerRefundDO::getId).toList());
        Map<String, String> reasons = reasonNames();
        List<Long> asIds = rows.stream().map(CustomerRefundDO::getAfterSalesId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> asNos = asIds.isEmpty() ? Map.of() : afterSalesMapper.selectBatchIds(asIds).stream()
                .collect(Collectors.toMap(com.zhul.erp.modules.aftersales.entity.AfterSalesDO::getId,
                        com.zhul.erp.modules.aftersales.entity.AfterSalesDO::getAsNo));
        List<Long> rqIds = rows.stream().map(CustomerRefundDO::getRefundRequestId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> rqNos = rqIds.isEmpty() ? Map.of() : requestMapper.selectBatchIds(rqIds).stream()
                .collect(Collectors.toMap(RefundRequestDO::getId, RefundRequestDO::getRequestNo));
        List<RefundVO> out = new ArrayList<>(rows.size());
        for (CustomerRefundDO f : rows) {
            SalesOrderDO o = f.getSoId() == null ? null : orders.get(f.getSoId());
            Long customerId = f.getCustomerId() != null ? f.getCustomerId() : o == null ? null : o.getCustomerId();
            RefundVO vo = new RefundVO();
            vo.setMigrated(MigratedGuard.isMigrated(f.getMigrated()));
            vo.setId(f.getId());
            vo.setRefundNo(f.getRefundNo());
            vo.setRefundType(f.getRefundType());
            vo.setSoId(f.getSoId());
            vo.setSoNo(o == null ? null : o.getSoNo());
            vo.setAfterSalesId(f.getAfterSalesId());
            vo.setAsNo(f.getAfterSalesId() == null ? null : asNos.get(f.getAfterSalesId()));
            vo.setRefundRequestId(f.getRefundRequestId());
            vo.setRequestNo(f.getRefundRequestId() == null ? null : rqNos.get(f.getRefundRequestId()));
            vo.setCustomerId(customerId);
            vo.setCustomerName(customerId == null ? null : InquiryLookups.customerName(customers.get(customerId)));
            vo.setBusinessUnitId(f.getBusinessUnitId());
            vo.setRefundDate(f.getRefundDate());
            vo.setReason(f.getReason());
            vo.setReasonName(f.getRefundType() == FinanceConstants.REFUND_CREDIT_WITHDRAW ? "余额转给客户"
                    : reasons.getOrDefault(f.getReason(), f.getReason()));
            vo.setCurrencyCode(f.getCurrencyCode());
            vo.setAmount(f.getAmount());
            vo.setExchangeRate(f.getExchangeRate());
            vo.setAmountCny(f.getAmountCny());
            vo.setMethod(f.getMethod());
            String methodName = FinanceConstants.REFUND_METHOD_NAMES.get(f.getMethod());
            if (f.getMethod() == FinanceConstants.REFUND_OTHER && StringUtils.hasText(f.getMethodName())) {
                methodName = f.getMethodName();
            } else if (f.getMethod() == FinanceConstants.REFUND_PLATFORM && StringUtils.hasText(f.getMethodName())) {
                // 多收原路退回时记了平台名称
                methodName = methodName + "（" + f.getMethodName() + "）";
            }
            vo.setMethodName(methodName);
            vo.setPayeeAccount(f.getPayeeAccount());
            vo.setRemark(f.getRemark());
            vo.setStatus(f.getStatus());
            vo.setVoidReason(f.getVoidReason());
            vo.setAttachments(PayableQueryService.refs(files.getOrDefault(f.getId(), List.of())));
            vo.setCreateTime(f.getCreateTime());
            vo.setCreateBy(f.getCreateBy());
            vo.setUpdateTime(f.getUpdateTime());
            vo.setUpdateBy(f.getUpdateBy());
            out.add(vo);
        }
        return out;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

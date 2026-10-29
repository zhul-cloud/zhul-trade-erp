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
import com.zhul.erp.modules.finance.dto.RefundVO;
import com.zhul.erp.modules.finance.dto.RefundableVO;
import com.zhul.erp.modules.finance.dto.SaveRefundRequest;
import com.zhul.erp.modules.finance.entity.CustomerRefundDO;
import com.zhul.erp.modules.finance.repository.CustomerRefundMapper;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
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
 * 客户退款单（spec finance/customer-refund）：关联销售订单，冲减订单收入；不生成应付、不走付款。
 * 可退金额 = 订单已到账（PI 或订单的 received_amount）− 有效退款合计；默认人民币 = 退款金额 × 平均到账汇率
 * （Σ 到账毛额 × 到账汇率 ÷ Σ 到账毛额），HALF_UP 两位；汇率 = 人民币 ÷ 退款金额，6 位。
 * 事务边界：登记 / 作废与附件挂接在同一事务内；登记时对订单加行锁，避免并发超退。
 */
@Service
@RequiredArgsConstructor
public class CustomerRefundService {

    private static final String MENU = "退款单";

    private final CustomerRefundMapper refundMapper;
    private final SalesOrderMapper orderMapper;
    private final ProformaInvoiceMapper piMapper;
    private final PaymentReceiptMapper receiptMapper;
    private final DocumentNumberService documentNumberService;
    private final AttachmentService attachments;
    private final DictItemService dictItemService;
    private final InquiryLookups lookups;
    private final LogService logService;

    /** 订单的可退金额与平均到账汇率 */
    public RefundableVO refundable(Long soId) {
        SalesOrderDO o = requireOrder(soId, false);
        return refundableOf(o);
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundVO create(SaveRefundRequest req) {
        SalesOrderDO o = requireOrder(req.getSoId(), true);
        if (req.getRefundDate().isAfter(LocalDate.now())) {
            throw new BizException("退款日期不能晚于今天");
        }
        if (reasonNames().get(req.getReason()) == null) {
            throw new BizException("退款原因不存在");
        }
        if (req.getMethod() != FinanceConstants.REFUND_PLATFORM && req.getMethod() != FinanceConstants.REFUND_BANK) {
            throw new BizException("退款方式不正确");
        }
        RefundableVO r = refundableOf(o);
        BigDecimal amount = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(r.getRefundable()) > 0) {
            throw new BizException("最多还能退 " + o.getCurrencyCode() + " " + String.format("%,.2f", r.getRefundable()));
        }
        BigDecimal cny = req.getAmountCny() != null ? req.getAmountCny().setScale(2, RoundingMode.HALF_UP)
                : amount.multiply(r.getAverageRate()).setScale(2, RoundingMode.HALF_UP);
        CustomerRefundDO f = new CustomerRefundDO();
        f.setTenantId(PiStore.tenantId());
        f.setRefundNo(documentNumberService.next(DocumentType.TK));
        f.setSoId(o.getId());
        f.setRefundDate(req.getRefundDate());
        f.setReason(req.getReason());
        f.setCurrencyCode(o.getCurrencyCode());
        f.setAmount(amount);
        f.setAmountCny(cny);
        f.setExchangeRate(cny.divide(amount, 6, RoundingMode.HALF_UP));
        f.setMethod(req.getMethod());
        f.setRemark(req.getRemark() == null ? "" : req.getRemark().trim());
        f.setStatus(FinanceConstants.RECORD_VALID);
        f.setVoidReason("");
        refundMapper.insert(f);
        attachments.attach(AttachmentService.REFUND, f.getId(), req.getAttachmentIds());
        logService.recordOperateLog(MENU, "登记退款", null, Map.of("refundNo", f.getRefundNo(), "soNo", o.getSoNo(),
                "amount", o.getCurrencyCode() + " " + amount, "amountCny", cny.toPlainString()));
        return detail(f.getId());
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
        f.setStatus(FinanceConstants.RECORD_VOID);
        f.setVoidReason(why.length() > 200 ? why.substring(0, 200) : why);
        refundMapper.updateById(f);
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

    /** 筛选结果的人民币合计 */
    public BigDecimal total(RefundQuery q) {
        return refundMapper.selectList(filter(q)).stream().map(CustomerRefundDO::getAmountCny).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public RefundVO detail(Long id) {
        return toVos(List.of(require(id))).get(0);
    }

    /** 订单的有效退款单（订单成本明细用） */
    public List<RefundVO> ofOrder(Long soId) {
        return toVos(refundMapper.selectList(new LambdaQueryWrapper<CustomerRefundDO>()
                .eq(CustomerRefundDO::getSoId, soId)
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

    private RefundableVO refundableOf(SalesOrderDO o) {
        ProformaInvoiceDO pi = o.getPiId() == null ? null : piMapper.selectById(o.getPiId());
        BigDecimal received = pi != null ? nz(pi.getReceivedAmount()) : nz(o.getReceivedAmount());
        BigDecimal refunded = refundMapper.selectList(new LambdaQueryWrapper<CustomerRefundDO>()
                        .eq(CustomerRefundDO::getSoId, o.getId())
                        .eq(CustomerRefundDO::getStatus, FinanceConstants.RECORD_VALID)
                        .isNull(CustomerRefundDO::getDeletedAt))
                .stream().map(CustomerRefundDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        LambdaQueryWrapper<PaymentReceiptDO> rw = new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getKind, SalesConstants.KIND_RECEIPT)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt);
        if (pi != null) {
            rw.eq(PaymentReceiptDO::getPiId, pi.getId());
        } else {
            rw.eq(PaymentReceiptDO::getSoId, o.getId());
        }
        List<PaymentReceiptDO> receipts = receiptMapper.selectList(rw);
        BigDecimal gross = receipts.stream().map(r -> nz(r.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        // 按到账汇率加权（不用已舍入的人民币反算，避免 7.150005 这类尾差）
        BigDecimal grossCny = receipts.stream().map(r -> nz(r.getAmount()).multiply(nz(r.getExchangeRate())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        RefundableVO vo = new RefundableVO();
        vo.setSoId(o.getId());
        vo.setSoNo(o.getSoNo());
        vo.setCurrencyCode(o.getCurrencyCode());
        vo.setReceived(received);
        vo.setRefunded(refunded);
        vo.setRefundable(received.subtract(refunded).max(BigDecimal.ZERO));
        vo.setAverageRate(gross.signum() > 0 ? grossCny.divide(gross, 6, RoundingMode.HALF_UP) : nz(o.getExchangeRate()));
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
            w.and(x -> x.like(CustomerRefundDO::getRefundNo, k).or().apply("so_id IN (SELECT s.id FROM sales_order s "
                    + "LEFT JOIN customer c ON c.id = s.customer_id WHERE s.so_no LIKE CONCAT('%', {0}, '%') "
                    + "OR c.name LIKE CONCAT('%', {0}, '%'))", k));
        }
        return w;
    }

    private List<RefundVO> toVos(List<CustomerRefundDO> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, SalesOrderDO> orders = orderMapper.selectBatchIds(rows.stream().map(CustomerRefundDO::getSoId).distinct().toList())
                .stream().collect(Collectors.toMap(SalesOrderDO::getId, x -> x));
        Map<Long, CustomerDO> customers = lookups.customers(orders.values().stream().map(SalesOrderDO::getCustomerId).distinct().toList());
        Map<Long, List<AttachmentVO>> files = attachments.list(AttachmentService.REFUND, rows.stream().map(CustomerRefundDO::getId).toList());
        Map<String, String> reasons = reasonNames();
        List<RefundVO> out = new ArrayList<>(rows.size());
        for (CustomerRefundDO f : rows) {
            SalesOrderDO o = orders.get(f.getSoId());
            RefundVO vo = new RefundVO();
            vo.setMigrated(MigratedGuard.isMigrated(f.getMigrated()));
            vo.setId(f.getId());
            vo.setRefundNo(f.getRefundNo());
            vo.setSoId(f.getSoId());
            vo.setSoNo(o == null ? null : o.getSoNo());
            vo.setCustomerName(o == null ? null : InquiryLookups.customerName(customers.get(o.getCustomerId())));
            vo.setRefundDate(f.getRefundDate());
            vo.setReason(f.getReason());
            vo.setReasonName(reasons.getOrDefault(f.getReason(), f.getReason()));
            vo.setCurrencyCode(f.getCurrencyCode());
            vo.setAmount(f.getAmount());
            vo.setExchangeRate(f.getExchangeRate());
            vo.setAmountCny(f.getAmountCny());
            vo.setMethod(f.getMethod());
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

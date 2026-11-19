package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.CancelSettlementVO;
import com.zhul.erp.modules.finance.entity.PayableDO;
import com.zhul.erp.modules.finance.entity.PaymentAllocationDO;
import com.zhul.erp.modules.finance.entity.PaymentDO;
import com.zhul.erp.modules.finance.repository.PayableMapper;
import com.zhul.erp.modules.finance.repository.PaymentAllocationMapper;
import com.zhul.erp.modules.finance.repository.PaymentMapper;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 采购订单已付款后取消的收尾（openspec po-cancel-refund，spec finance/accounts-payable「采购订单取消后的收尾」）。
 * 由采购取消在同一事务内、应付同步之后调用；逐笔看核销到本应付单的实付（欠款抵扣转入的不处理）：
 * 员工垫付且还没进报销单、只付了本单、欠款没被用过的作废；其余垫付以垫付人名义登记已收到退款；
 * 公司对公、私对私不动，应付单标记待退款。金额为原币两位小数，沿用付款单的舍入（HALF_UP）。
 */
@Service
@RequiredArgsConstructor
public class PurchaseCancelSettlement {

    private final PayableMapper payableMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentAllocationMapper allocationMapper;
    private final PaymentService payments;
    private final com.zhul.erp.modules.system.doclog.DocLogs docLogs;

    /** 这张采购订单的应付上各笔实付及「会退款」时的处理；没有实付时为空 */
    public List<CancelSettlementVO> preview(Long poId) {
        PayableDO p = payableOf(poId);
        return p == null ? List.of() : plan(p);
    }

    /**
     * 取消后收尾，返回给采购订单日志的摘要；refund 为 false（不退款）时不处理，已付留作欠款。
     * 任何一笔失败抛出业务异常，由取消的事务整体回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> settle(Long poId, String poNo, boolean refund) {
        PayableDO p = payableOf(poId);
        if (p == null || !refund) {
            return List.of();
        }
        List<CancelSettlementVO> plan = plan(p);
        List<String> notes = new ArrayList<>();
        String why = "采购订单 " + poNo + " 取消，店铺原路退款";
        for (CancelSettlementVO s : plan) {
            if (s.getAction() == CancelSettlementVO.VOID) {
                payments.voidBySystem(s.getPaymentId(), why);
                notes.add("作废 " + s.getAdvanceUser() + " 垫付 " + s.getPaymentNo() + " " + money(s));
            }
        }
        for (CancelSettlementVO s : plan) {
            if (s.getAction() == CancelSettlementVO.RECEIVED) {
                var vo = payments.receivedOnCancel(p.getId(), s.getAdvanceUser(), s.getAmount(), s.getAmountCny(),
                        "采购订单 " + poNo + " 取消，自动登记", "pocancel-" + p.getId() + "-" + s.getPaymentId());
                if (vo != null) {
                    notes.add("以 " + s.getAdvanceUser() + " 名义登记已收到退款 " + vo.getPaymentNo() + " " + vo.getCurrencyCode()
                            + " " + vo.getAmount().toPlainString());
                }
            }
        }
        if (plan.stream().anyMatch(s -> s.getAction() == CancelSettlementVO.REFUND_EXPECTED)) {
            PayableDO fresh = payableMapper.selectById(p.getId());
            if (fresh.getCreditAmount() != null && fresh.getCreditAmount().signum() > 0) {
                fresh.setRefundExpectedAt(LocalDateTime.now());
                payableMapper.updateById(fresh);
                String m = fresh.getCurrencyCode() + " " + fresh.getCreditAmount().toPlainString();
                docLogs.add(DocumentType.AP, fresh.getId(), "待退款", "采购订单 " + poNo + " 取消，等供应商退回 " + m);
                notes.add("应付单 " + fresh.getPayableNo() + " 标记待退款 " + m);
            }
        }
        return notes;
    }

    private PayableDO payableOf(Long poId) {
        return payableMapper.selectOne(new LambdaQueryWrapper<PayableDO>()
                .eq(PayableDO::getTenantId, PiStore.tenantId())
                .eq(PayableDO::getSourceType, FinanceConstants.SOURCE_PURCHASE_ORDER)
                .eq(PayableDO::getSourceId, poId)
                .isNull(PayableDO::getDeletedAt));
    }

    private List<CancelSettlementVO> plan(PayableDO p) {
        List<PaymentAllocationDO> paid = allocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationDO>()
                .eq(PaymentAllocationDO::getPayableId, p.getId())
                .eq(PaymentAllocationDO::getFromCredit, 0)
                .isNull(PaymentAllocationDO::getDeletedAt)
                .orderByAsc(PaymentAllocationDO::getId));
        if (paid.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal[]> byPayment = new LinkedHashMap<>();
        for (PaymentAllocationDO a : paid) {
            BigDecimal[] sum = byPayment.computeIfAbsent(a.getPaymentId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            sum[0] = sum[0].add(a.getAmount());
            sum[1] = sum[1].add(a.getAmountCny() == null ? BigDecimal.ZERO : a.getAmountCny());
        }
        Map<Long, PaymentDO> pays = new LinkedHashMap<>();
        paymentMapper.selectBatchIds(byPayment.keySet()).forEach(x -> pays.put(x.getId(), x));
        boolean creditUsed = creditUsedElsewhere(p.getId());
        List<CancelSettlementVO> out = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal[]> e : byPayment.entrySet()) {
            PaymentDO pay = pays.get(e.getKey());
            if (pay == null || pay.getVoidAt() != null || pay.getDeletedAt() != null
                    || !Objects.equals(pay.getKind(), FinanceConstants.KIND_PAYMENT)) {
                continue;
            }
            CancelSettlementVO vo = new CancelSettlementVO();
            vo.setPaymentId(pay.getId());
            vo.setPaymentNo(pay.getPaymentNo());
            vo.setMethod(pay.getMethod());
            vo.setAdvanceUser(pay.getAdvanceUser());
            vo.setReimburseStatus(pay.getReimburseStatus());
            vo.setCurrencyCode(pay.getCurrencyCode());
            vo.setAmount(e.getValue()[0]);
            vo.setAmountCny(e.getValue()[1]);
            if (!Objects.equals(pay.getMethod(), FinanceConstants.METHOD_ADVANCE)) {
                vo.setAction(CancelSettlementVO.REFUND_EXPECTED);
            } else if (Objects.equals(pay.getReimburseStatus(), FinanceConstants.REIMBURSE_PENDING) && !creditUsed
                    && onlyThisPayable(pay.getId(), p.getId())) {
                vo.setAction(CancelSettlementVO.VOID);
            } else {
                vo.setAction(CancelSettlementVO.RECEIVED);
            }
            out.add(vo);
        }
        return out;
    }

    /** 这笔付款的所有有效核销都是实付到本应付单（没有付其他单，也没有抵扣转入） */
    private boolean onlyThisPayable(Long paymentId, Long payableId) {
        return allocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationDO>()
                        .eq(PaymentAllocationDO::getPaymentId, paymentId)
                        .isNull(PaymentAllocationDO::getDeletedAt))
                .stream().allMatch(a -> Objects.equals(a.getPayableId(), payableId)
                        && (a.getFromCredit() == null || a.getFromCredit() == 0) && a.getCreditPayableId() == null);
    }

    /** 本应付单的欠款已被其他有效单据使用（抵扣到别的应付或已登记退款）：此时作废垫付会让来源少钱，改为登记已收到退款 */
    private boolean creditUsedElsewhere(Long payableId) {
        List<Long> users = allocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationDO>()
                        .select(PaymentAllocationDO::getPaymentId)
                        .isNull(PaymentAllocationDO::getDeletedAt)
                        .and(w -> w.nested(x -> x.eq(PaymentAllocationDO::getFromCredit, 1)
                                        .eq(PaymentAllocationDO::getCreditPayableId, payableId))
                                .or().nested(x -> x.eq(PaymentAllocationDO::getFromCredit, 2)
                                        .eq(PaymentAllocationDO::getPayableId, payableId))))
                .stream().map(PaymentAllocationDO::getPaymentId).distinct().toList();
        return !users.isEmpty() && paymentMapper.selectBatchIds(users).stream()
                .anyMatch(x -> x.getVoidAt() == null && x.getDeletedAt() == null);
    }

    private static String money(CancelSettlementVO s) {
        return s.getCurrencyCode() + " " + s.getAmount().toPlainString();
    }
}

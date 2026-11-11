package com.zhul.erp.modules.sales.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.aftersales.constants.AfterSalesConstants;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnDO;
import com.zhul.erp.modules.aftersales.repository.AfterSalesMapper;
import com.zhul.erp.modules.aftersales.repository.SalesReturnMapper;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.entity.CustomerRefundDO;
import com.zhul.erp.modules.finance.entity.ExpenseAllocationDO;
import com.zhul.erp.modules.finance.entity.ExpenseDO;
import com.zhul.erp.modules.finance.entity.RefundRequestDO;
import com.zhul.erp.modules.finance.repository.CustomerRefundMapper;
import com.zhul.erp.modules.finance.repository.ExpenseAllocationMapper;
import com.zhul.erp.modules.finance.repository.ExpenseMapper;
import com.zhul.erp.modules.finance.repository.RefundRequestMapper;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.entity.OutboundOrderDO;
import com.zhul.erp.modules.logistics.repository.OutboundOrderMapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseRequirementMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.DeleteCheckVO;
import com.zhul.erp.modules.sales.dto.DeleteCheckVO.Doc;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.warehouse.entity.StockHoldDO;
import com.zhul.erp.modules.warehouse.repository.StockHoldMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 删除销售订单前检查关联单据（spec sales/sales-order「删除销售订单」）：
 * 影响钱、货、成本的单据挡住删除；PI、采购需求、草稿采购单行列出来，删除时按取消订单的规则一起处理。
 * 预检接口与删除共用，删除时在订单锁内再查一次。
 */
@Component
@RequiredArgsConstructor
public class OrderDeleteCheck {

    /** 暂存货物状态：暂存中 */
    private static final int HOLD_ACTIVE = 1;

    private final PaymentReceiptMapper receiptMapper;
    private final PurchaseOrderItemMapper poItemMapper;
    private final PurchaseOrderMapper poMapper;
    private final PurchaseRequirementMapper requirementMapper;
    private final OutboundOrderMapper outboundMapper;
    private final AfterSalesMapper afterSalesMapper;
    private final SalesReturnMapper returnMapper;
    private final RefundRequestMapper refundRequestMapper;
    private final CustomerRefundMapper customerRefundMapper;
    private final ExpenseAllocationMapper allocationMapper;
    private final ExpenseMapper expenseMapper;
    private final SalesOrderMapper orderMapper;
    private final StockHoldMapper holdMapper;

    /**
     * @param o  订单
     * @param pi PI 转成的订单的 PI，其他为 null
     */
    public DeleteCheckVO check(SalesOrderDO o, ProformaInvoiceDO pi) {
        List<Doc> blockers = new ArrayList<>(8);
        List<Doc> related = new ArrayList<>(4);
        boolean active = Objects.equals(o.getStatus(), SalesConstants.SO_ACTIVE);

        receipts(o, pi, blockers);
        purchase(o, active, blockers, related);
        if (Objects.equals(o.getSource(), SalesConstants.SO_REPLACEMENT) && o.getAfterSalesId() != null) {
            AfterSalesDO as = afterSalesMapper.selectById(o.getAfterSalesId());
            blockers.add(new Doc("售后补发", as == null ? "售后单" : as.getAsNo(), "/sales/after-sales/" + o.getAfterSalesId(),
                    "由售后单生成，请在售后单里处理"));
        }
        outboundMapper.selectList(new LambdaQueryWrapper<OutboundOrderDO>()
                        .eq(OutboundOrderDO::getSoId, o.getId())
                        .ne(OutboundOrderDO::getStatus, LogisticsConstants.OB_WITHDRAWN)
                        .isNull(OutboundOrderDO::getDeletedAt))
                .forEach(ob -> {
                    boolean inWarehouse = Objects.equals(ob.getStatus(), LogisticsConstants.OB_PENDING)
                            || Objects.equals(ob.getStatus(), LogisticsConstants.OB_PACKED);
                    blockers.add(new Doc("出库单", ob.getObNo() + " · " + LogisticsConstants.OB_STATUS_NAMES.getOrDefault(ob.getStatus(), ""),
                            null, inWarehouse ? "先撤回出库" : "货已交货代，只能取消订单"));
                });
        afterSalesMapper.selectList(new LambdaQueryWrapper<AfterSalesDO>()
                        .eq(AfterSalesDO::getSoId, o.getId())
                        .ne(AfterSalesDO::getStatus, AfterSalesConstants.AS_CANCELLED)
                        .isNull(AfterSalesDO::getDeletedAt))
                .forEach(a -> blockers.add(new Doc("售后单", a.getAsNo(), "/sales/after-sales/" + a.getId(), "先撤销售后单")));
        returnMapper.selectList(new LambdaQueryWrapper<SalesReturnDO>()
                        .eq(SalesReturnDO::getSoId, o.getId())
                        .ne(SalesReturnDO::getStatus, AfterSalesConstants.RT_VOID)
                        .isNull(SalesReturnDO::getDeletedAt))
                .forEach(r -> blockers.add(new Doc("退货单", r.getReturnNo(), null, "先作废退货单")));
        refundRequestMapper.selectList(new LambdaQueryWrapper<RefundRequestDO>()
                        .eq(RefundRequestDO::getSoId, o.getId())
                        .notIn(RefundRequestDO::getStatus, FinanceConstants.RQ_REJECTED, FinanceConstants.RQ_WITHDRAWN)
                        .isNull(RefundRequestDO::getDeletedAt))
                .forEach(r -> blockers.add(new Doc("退款申请", r.getRequestNo() + " · " + money(r.getCurrencyCode(), r.getAmount()), null,
                        "先撤回退款申请")));
        customerRefundMapper.selectList(new LambdaQueryWrapper<CustomerRefundDO>()
                        .eq(CustomerRefundDO::getSoId, o.getId())
                        .eq(CustomerRefundDO::getStatus, FinanceConstants.RECORD_VALID)
                        .isNull(CustomerRefundDO::getDeletedAt))
                .forEach(r -> blockers.add(new Doc("退款", r.getRefundNo() + " · " + money(r.getCurrencyCode(), r.getAmount()), null,
                        "先作废退款")));
        expenses(o, blockers);
        orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                        .eq(SalesOrderDO::getOriginalSoId, o.getId())
                        .eq(SalesOrderDO::getSource, SalesConstants.SO_FEE)
                        .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                        .isNull(SalesOrderDO::getDeletedAt))
                .forEach(f -> blockers.add(new Doc("费用订单", f.getSoNo(), "/sales/orders/" + f.getId(), "先删除或取消费用订单")));
        holdMapper.selectList(new LambdaQueryWrapper<StockHoldDO>()
                        .eq(StockHoldDO::getSoId, o.getId())
                        .eq(StockHoldDO::getStatus, HOLD_ACTIVE)
                        .isNull(StockHoldDO::getDeletedAt))
                .forEach(h -> blockers.add(new Doc("暂存货物", h.getModel() + " × " + h.getQuantity(), null, "先处理暂存货物")));

        if (pi != null) {
            related.add(0, new Doc("形式发票", pi.getPiNo(), "/sales/pi/" + pi.getId(),
                    active ? "回到「已发送」，可以再改" : "取消时已处理，不变"));
        }
        String level = !blockers.isEmpty() ? DeleteCheckVO.BLOCKED : related.isEmpty() ? DeleteCheckVO.DIRECT : DeleteCheckVO.CONFIRM;
        return new DeleteCheckVO(level, blockers, related);
    }

    /** 有效的水单、到账、余额抵扣：手动订单挂在订单上，PI 转成的订单挂在 PI 上 */
    private void receipts(SalesOrderDO o, ProformaInvoiceDO pi, List<Doc> blockers) {
        LambdaQueryWrapper<PaymentReceiptDO> w = new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt);
        if (pi != null) {
            w.and(x -> x.eq(PaymentReceiptDO::getSoId, o.getId()).or().eq(PaymentReceiptDO::getPiId, pi.getId()));
        } else {
            w.eq(PaymentReceiptDO::getSoId, o.getId());
        }
        String path = pi == null ? null : "/sales/pi/" + pi.getId();
        for (PaymentReceiptDO r : receiptMapper.selectList(w)) {
            boolean credit = SalesConstants.METHOD_CREDIT.equals(r.getPaymentMethod());
            boolean slip = Objects.equals(r.getKind(), SalesConstants.KIND_SLIP);
            String kind = credit ? "余额抵扣" : slip ? "水单" : "到账";
            String date = r.getReceiptDate() == null ? "" : " · " + r.getReceiptDate();
            blockers.add(new Doc(kind, money(r.getCurrencyCode(), r.getAmount()) + date, path, "先作废" + kind));
        }
    }

    /** 采购订单行：已下单的挡住删除；草稿采购单上的行与没下单的采购需求删除时一起处理（已取消的订单在取消时已处理） */
    private void purchase(SalesOrderDO o, boolean active, List<Doc> blockers, List<Doc> related) {
        List<PurchaseOrderItemDO> items = poItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .eq(PurchaseOrderItemDO::getSoId, o.getId())
                .isNull(PurchaseOrderItemDO::getDeletedAt));
        Map<Long, Long> linesByPo = items.stream().collect(Collectors.groupingBy(PurchaseOrderItemDO::getPoId, LinkedHashMap::new,
                Collectors.counting()));
        Map<Long, PurchaseOrderDO> pos = linesByPo.isEmpty() ? Map.of() : poMapper.selectBatchIds(linesByPo.keySet()).stream()
                .filter(p -> p.getDeletedAt() == null)
                .collect(Collectors.toMap(PurchaseOrderDO::getId, p -> p));
        List<Doc> drafts = new ArrayList<>(2);
        linesByPo.forEach((poId, lines) -> {
            PurchaseOrderDO p = pos.get(poId);
            if (p == null) {
                return;
            }
            if (Objects.equals(p.getStatus(), PurchaseConstants.PO_ORDERED)) {
                blockers.add(new Doc("采购订单", p.getPoNo() + " · 已下单", "/purchase/orders/" + poId, "先取消采购订单"));
            } else if (active && Objects.equals(p.getStatus(), PurchaseConstants.PO_DRAFT)) {
                drafts.add(new Doc("草稿采购单", p.getPoNo() + " · " + lines + " 行", "/purchase/orders/" + poId,
                        lines > 1 ? "删除这些行" : "删除这一行"));
            }
        });
        if (active) {
            List<PurchaseRequirementDO> reqs = requirementMapper.selectList(new LambdaQueryWrapper<PurchaseRequirementDO>()
                    .eq(PurchaseRequirementDO::getSoId, o.getId())
                    .eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                    .isNull(PurchaseRequirementDO::getDeletedAt));
            if (!reqs.isEmpty()) {
                String models = reqs.stream().map(PurchaseRequirementDO::getModel).distinct().limit(3).collect(Collectors.joining("、"));
                String more = reqs.size() > 3 ? " 等" : "";
                related.add(new Doc("采购需求", reqs.size() + " 条（" + models + more + "）", null, "关闭"));
            }
        }
        related.addAll(drafts);
    }

    /** 分摊到订单上的费用（有效的费用单） */
    private void expenses(SalesOrderDO o, List<Doc> blockers) {
        List<Long> expenseIds = allocationMapper.selectList(new LambdaQueryWrapper<ExpenseAllocationDO>()
                        .select(ExpenseAllocationDO::getExpenseId)
                        .eq(ExpenseAllocationDO::getSoId, o.getId())
                        .isNull(ExpenseAllocationDO::getDeletedAt))
                .stream().map(ExpenseAllocationDO::getExpenseId).filter(Objects::nonNull).distinct().toList();
        if (expenseIds.isEmpty()) {
            return;
        }
        expenseMapper.selectBatchIds(expenseIds).stream()
                .filter(e -> e.getDeletedAt() == null && Objects.equals(e.getStatus(), FinanceConstants.FE_VALID))
                .forEach(e -> blockers.add(new Doc("费用分摊", e.getExpenseNo(), null, "先调整费用分摊")));
    }

    private static String money(String currency, BigDecimal amount) {
        BigDecimal a = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        return (currency == null ? "" : currency + " ") + String.format("%,.2f", a);
    }
}

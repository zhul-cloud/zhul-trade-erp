package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.OrderCostVO;
import com.zhul.erp.modules.finance.entity.ExpenseAllocationDO;
import com.zhul.erp.modules.finance.entity.ExpenseDO;
import com.zhul.erp.modules.finance.entity.PayableDO;
import com.zhul.erp.modules.finance.repository.ExpenseAllocationMapper;
import com.zhul.erp.modules.finance.repository.ExpenseMapper;
import com.zhul.erp.modules.finance.repository.PayableMapper;
import com.zhul.erp.modules.finance.support.OrderCostCalc;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.entity.CourierWaybillDO;
import com.zhul.erp.modules.logistics.entity.FreightAllocationDO;
import com.zhul.erp.modules.logistics.entity.LogisticsShipmentDO;
import com.zhul.erp.modules.logistics.repository.CourierWaybillMapper;
import com.zhul.erp.modules.logistics.repository.FreightAllocationMapper;
import com.zhul.erp.modules.logistics.repository.LogisticsShipmentMapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.ReceivingDiscrepancyDO;
import com.zhul.erp.modules.warehouse.repository.ReceivingDiscrepancyMapper;
import com.zhul.erp.modules.warehouse.support.ReceivingQty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单实际成本（spec finance/order-cost）：读时计算，不存快照。按一批订单批量读取采购订单行、合格入库、到货差异、
 * 应付已付、运费分摊、费用单分摊与收款记录，交给 {@link OrderCostCalc} 计算；只读其他模块的仓储，不调用它们的服务。
 */
@Service
@RequiredArgsConstructor
public class OrderCostService {

    private final SalesOrderItemMapper soItemMapper;
    private final ProformaInvoiceMapper piMapper;
    private final PaymentReceiptMapper receiptMapper;
    private final PurchaseOrderItemMapper poItemMapper;
    private final PurchaseOrderMapper poMapper;
    private final ReceivingQty receivingQty;
    private final ReceivingDiscrepancyMapper discrepancyMapper;
    private final PayableMapper payableMapper;
    private final FreightAllocationMapper freightMapper;
    private final LogisticsShipmentMapper shipmentMapper;
    private final CourierWaybillMapper waybillMapper;
    private final ExpenseAllocationMapper expenseAllocationMapper;
    private final ExpenseMapper expenseMapper;

    public OrderCostVO of(SalesOrderDO order) {
        return of(List.of(order)).get(order.getId());
    }

    /** 一批订单的成本；已取消的订单不计算（不在结果里） */
    public Map<Long, OrderCostVO> of(Collection<SalesOrderDO> orders) {
        List<SalesOrderDO> active = orders.stream().filter(o -> o.getStatus() == SalesConstants.SO_ACTIVE).toList();
        if (active.isEmpty()) {
            return Map.of();
        }
        Set<Long> soIds = active.stream().map(SalesOrderDO::getId).collect(Collectors.toSet());
        List<SalesOrderItemDO> items = soItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                .in(SalesOrderItemDO::getSoId, soIds)
                .isNull(SalesOrderItemDO::getDeletedAt)
                .orderByAsc(SalesOrderItemDO::getLineNo));
        List<Long> itemIds = items.stream().map(SalesOrderItemDO::getId).toList();
        Map<Long, List<SalesOrderItemDO>> itemsBySo = items.stream().collect(Collectors.groupingBy(SalesOrderItemDO::getSoId));

        // 采购：已下单的采购订单行
        List<PurchaseOrderItemDO> poLines = itemIds.isEmpty() ? List.of() : poItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .in(PurchaseOrderItemDO::getSoItemId, itemIds)
                .isNull(PurchaseOrderItemDO::getDeletedAt)
                .orderByAsc(PurchaseOrderItemDO::getId));
        Map<Long, PurchaseOrderDO> pos = poLines.isEmpty() ? Map.of()
                : poMapper.selectBatchIds(poLines.stream().map(PurchaseOrderItemDO::getPoId).distinct().toList()).stream()
                .filter(p -> p.getStatus() == PurchaseConstants.PO_ORDERED && p.getDeletedAt() == null)
                .collect(Collectors.toMap(PurchaseOrderDO::getId, Function.identity()));
        poLines = poLines.stream().filter(l -> pos.containsKey(l.getPoId())).toList();
        List<Long> poItemIds = poLines.stream().map(PurchaseOrderItemDO::getId).toList();
        Map<Long, Integer> qualified = receivingQty.qualifiedByPoItem(poItemIds);
        Map<Long, BigDecimal> discounts = new HashMap<>();
        Map<Long, BigDecimal> returnFreight = new HashMap<>();
        if (!poItemIds.isEmpty()) {
            for (ReceivingDiscrepancyDO d : discrepancyMapper.selectList(new LambdaQueryWrapper<ReceivingDiscrepancyDO>()
                    .in(ReceivingDiscrepancyDO::getPoItemId, poItemIds)
                    .eq(ReceivingDiscrepancyDO::getStatus, WarehouseConstants.DIFF_HANDLED)
                    .isNull(ReceivingDiscrepancyDO::getDeletedAt))) {
                if (Objects.equals(d.getResolution(), WarehouseConstants.RES_DISCOUNT) && d.getDiscountAmount() != null) {
                    discounts.merge(d.getPoItemId(), d.getDiscountAmount(), BigDecimal::add);
                }
                if (d.getReturnFreight() != null && d.getReturnFreight().signum() > 0) {
                    returnFreight.merge(d.getPoItemId(), d.getReturnFreight(), BigDecimal::add);
                }
            }
        }
        Map<Long, PayableDO> payables = pos.isEmpty() ? Map.of() : payableMapper.selectList(new LambdaQueryWrapper<PayableDO>()
                        .eq(PayableDO::getSourceType, FinanceConstants.SOURCE_PURCHASE_ORDER)
                        .in(PayableDO::getSourceId, pos.keySet())
                        .isNull(PayableDO::getDeletedAt))
                .stream().collect(Collectors.toMap(PayableDO::getSourceId, Function.identity(), (a, b) -> a));

        // 运费与费用单
        List<FreightAllocationDO> freights = itemIds.isEmpty() ? List.of() : freightMapper.selectList(new LambdaQueryWrapper<FreightAllocationDO>()
                .in(FreightAllocationDO::getSoItemId, itemIds)
                .isNull(FreightAllocationDO::getDeletedAt));
        Set<Long> shipmentIds = freights.stream().filter(f -> f.getSourceType() == LogisticsConstants.FREIGHT_INTERNATIONAL)
                .map(FreightAllocationDO::getSourceId).collect(Collectors.toSet());
        Map<Long, LogisticsShipmentDO> shipments = shipmentIds.isEmpty() ? Map.of()
                : shipmentMapper.selectBatchIds(shipmentIds).stream().collect(Collectors.toMap(LogisticsShipmentDO::getId, Function.identity()));
        Set<Long> waybillIds = freights.stream().filter(f -> f.getSourceType() == LogisticsConstants.FREIGHT_DOMESTIC)
                .map(FreightAllocationDO::getSourceId).collect(Collectors.toSet());
        Map<Long, CourierWaybillDO> waybills = waybillIds.isEmpty() ? Map.of()
                : waybillMapper.selectBatchIds(waybillIds).stream().collect(Collectors.toMap(CourierWaybillDO::getId, Function.identity()));
        List<ExpenseAllocationDO> expenseAllocs = itemIds.isEmpty() ? List.of()
                : expenseAllocationMapper.selectList(new LambdaQueryWrapper<ExpenseAllocationDO>()
                .in(ExpenseAllocationDO::getSoItemId, itemIds)
                .isNull(ExpenseAllocationDO::getDeletedAt));
        Map<Long, ExpenseDO> expenses = expenseAllocs.isEmpty() ? Map.of()
                : expenseMapper.selectBatchIds(expenseAllocs.stream().map(ExpenseAllocationDO::getExpenseId).distinct().toList()).stream()
                .filter(e -> e.getDeletedAt() == null && Objects.equals(e.getStatus(), FinanceConstants.FE_VALID))
                .collect(Collectors.toMap(ExpenseDO::getId, Function.identity()));
        expenseAllocs = expenseAllocs.stream().filter(a -> expenses.containsKey(a.getExpenseId())).toList();

        // 收款：PI 转成的订单按 PI 的到账，手动订单按订单自己的
        Set<Long> piIds = active.stream().map(SalesOrderDO::getPiId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProformaInvoiceDO> pis = piIds.isEmpty() ? Map.of()
                : piMapper.selectBatchIds(piIds).stream().collect(Collectors.toMap(ProformaInvoiceDO::getId, Function.identity()));
        LambdaQueryWrapper<PaymentReceiptDO> rw = new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getKind, SalesConstants.KIND_RECEIPT)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt)
                .and(x -> {
                    x.in(PaymentReceiptDO::getSoId, soIds);
                    if (!piIds.isEmpty()) {
                        x.or().in(PaymentReceiptDO::getPiId, piIds);
                    }
                });
        List<PaymentReceiptDO> receipts = receiptMapper.selectList(rw);

        // 按订单组装输入
        Map<Long, List<PurchaseOrderItemDO>> poLinesByItem = poLines.stream().collect(Collectors.groupingBy(PurchaseOrderItemDO::getSoItemId));
        Map<Long, List<FreightAllocationDO>> freightByItem = freights.stream().collect(Collectors.groupingBy(FreightAllocationDO::getSoItemId));
        Map<Long, List<ExpenseAllocationDO>> expenseByItem = expenseAllocs.stream().collect(Collectors.groupingBy(ExpenseAllocationDO::getSoItemId));
        Map<Long, OrderCostVO> out = new LinkedHashMap<>();
        for (SalesOrderDO o : active) {
            List<SalesOrderItemDO> mine = itemsBySo.getOrDefault(o.getId(), List.of());
            ProformaInvoiceDO pi = o.getPiId() == null ? null : pis.get(o.getPiId());
            int receiptStatus = pi != null ? pi.getReceiptStatus() : Objects.requireNonNullElse(o.getReceiptStatus(), 0);
            List<PaymentReceiptDO> myReceipts = receipts.stream()
                    .filter(r -> pi != null ? Objects.equals(r.getPiId(), pi.getId()) : Objects.equals(r.getSoId(), o.getId())).toList();

            List<OrderCostCalc.Item> calcItems = new ArrayList<>();
            List<OrderCostCalc.PoLine> calcLines = new ArrayList<>();
            Map<Long, OrderCostCalc.Po> calcPos = new HashMap<>();
            for (SalesOrderItemDO i : mine) {
                List<FreightAllocationDO> fs = freightByItem.getOrDefault(i.getId(), List.of());
                BigDecimal dom = sum(fs.stream().filter(f -> f.getSourceType() == LogisticsConstants.FREIGHT_DOMESTIC).map(FreightAllocationDO::getAmount));
                List<FreightAllocationDO> intl = fs.stream().filter(f -> f.getSourceType() == LogisticsConstants.FREIGHT_INTERNATIONAL).toList();
                boolean reconciled = !intl.isEmpty() && intl.stream().allMatch(f -> {
                    LogisticsShipmentDO s = shipments.get(f.getSourceId());
                    return s != null && Objects.equals(s.getReconciled(), 1);
                });
                BigDecimal expense = sum(expenseByItem.getOrDefault(i.getId(), List.of()).stream().map(ExpenseAllocationDO::getAmountCny));
                calcItems.add(new OrderCostCalc.Item(i.getId(), Objects.requireNonNullElse(i.getQuantity(), 0), i.getAmount(), i.getCostPrice(),
                        dom, sum(intl.stream().map(FreightAllocationDO::getAmount)), expense, !intl.isEmpty(), reconciled));
                for (PurchaseOrderItemDO l : poLinesByItem.getOrDefault(i.getId(), List.of())) {
                    calcLines.add(new OrderCostCalc.PoLine(l.getId(), l.getPoId(), i.getId(), l.getQuantity(),
                            qualified.getOrDefault(l.getId(), 0), l.getUnitPrice(), discounts.get(l.getId()), returnFreight.get(l.getId())));
                    calcPos.computeIfAbsent(l.getPoId(), id -> toCalcPo(pos.get(id), payables.get(id)));
                }
            }
            List<OrderCostCalc.Receipt> calcReceipts = myReceipts.stream().map(r -> new OrderCostCalc.Receipt(r.getAmountCny(),
                    r.getNetAmountCny(), r.getFeeDiff(), r.getExchangeRate(), Objects.requireNonNullElse(r.getSettleStatus(), 0))).toList();
            OrderCostCalc.Result res = OrderCostCalc.compute(new OrderCostCalc.Input(receiptStatus == SalesConstants.RECEIPT_PAID,
                    o.getTotalAmountCny(), calcItems, calcLines, calcPos, calcReceipts));
            out.put(o.getId(), toVo(o, mine, res, poLinesByItem, pos, qualified, freightByItem, shipments, waybills, expenseByItem, expenses));
        }
        return out;
    }

    private static OrderCostCalc.Po toCalcPo(PurchaseOrderDO p, PayableDO ap) {
        BigDecimal payable = ap == null ? p.getTotalAmount() : ap.getAmount();
        return new OrderCostCalc.Po(p.getId(), p.getCurrencyCode(), p.getExchangeRate(), p.getItemAmount(), p.getFeeAmount(),
                p.getInvoiceCost(), payable, ap == null ? BigDecimal.ZERO : ap.getPaidAmount(), ap == null ? BigDecimal.ZERO : ap.getPaidCny());
    }

    private OrderCostVO toVo(SalesOrderDO o, List<SalesOrderItemDO> items, OrderCostCalc.Result res,
                             Map<Long, List<PurchaseOrderItemDO>> poLinesByItem, Map<Long, PurchaseOrderDO> pos, Map<Long, Integer> qualified,
                             Map<Long, List<FreightAllocationDO>> freightByItem, Map<Long, LogisticsShipmentDO> shipments,
                             Map<Long, CourierWaybillDO> waybills, Map<Long, List<ExpenseAllocationDO>> expenseByItem, Map<Long, ExpenseDO> expenses) {
        OrderCostVO vo = new OrderCostVO();
        vo.setSoId(o.getId());
        vo.setSalesAmountCny(o.getTotalAmountCny());
        vo.setRevenue(res.revenue());
        vo.setCost(res.cost());
        vo.setReceiptFee(res.receiptFee());
        vo.setProfit(res.profit());
        vo.setProfitRate(res.profitRate());
        vo.setSettled(res.settled());
        vo.setPending(res.pending());
        vo.setMissingCostCount(res.missingCostCount());
        Map<Long, SalesOrderItemDO> byId = items.stream().collect(Collectors.toMap(SalesOrderItemDO::getId, Function.identity()));
        vo.setLines(res.lines().stream().map(l -> {
            SalesOrderItemDO i = byId.get(l.soItemId());
            OrderCostVO.Line x = new OrderCostVO.Line();
            x.setSoItemId(l.soItemId());
            x.setModel(i.getModel());
            x.setBrand(i.getBrand());
            x.setQuantity(i.getQuantity());
            x.setPurchase(l.purchase());
            x.setPurchaseSettled(l.purchaseSettled());
            x.setInvoice(l.invoice());
            x.setPoFee(l.poFee());
            x.setClaim(l.claim());
            x.setReturnFreight(l.returnFreight());
            x.setDomestic(l.domestic());
            x.setInternational(l.international());
            List<FreightAllocationDO> fs = freightByItem.getOrDefault(l.soItemId(), List.of());
            List<FreightAllocationDO> intl = fs.stream().filter(f -> f.getSourceType() == LogisticsConstants.FREIGHT_INTERNATIONAL).toList();
            x.setInternationalSettled(!intl.isEmpty() && intl.stream()
                    .allMatch(f -> shipments.get(f.getSourceId()) != null && Objects.equals(shipments.get(f.getSourceId()).getReconciled(), 1)));
            x.setExpense(l.expense());
            x.setReceiptFee(l.receiptFee());
            x.setCost(l.cost());
            x.setRevenue(l.revenue());
            x.setProfit(l.profit());
            x.setProfitRate(l.profitRate());
            x.setUnreceivedQty(l.unreceivedQty());
            x.setUnorderedQty(l.unorderedQty());
            x.setMissingCostPrice(l.missingCostPrice());

            Map<String, List<OrderCostVO.Source>> sources = new LinkedHashMap<>();
            sources.put("purchase", poLinesByItem.getOrDefault(l.soItemId(), List.of()).stream().map(pl -> {
                PurchaseOrderDO p = pos.get(pl.getPoId());
                return source("PO", p.getId(), p.getPoNo(), pl.getAmount(), "合格入库 " + qualified.getOrDefault(pl.getId(), 0) + " / "
                        + pl.getQuantity() + " · 单价 " + p.getCurrencyCode() + " " + pl.getUnitPrice());
            }).toList());
            sources.put("domestic", fs.stream().filter(f -> f.getSourceType() == LogisticsConstants.FREIGHT_DOMESTIC)
                    .collect(Collectors.groupingBy(FreightAllocationDO::getSourceId, LinkedHashMap::new, Collectors.toList()))
                    .entrySet().stream().map(e -> {
                        CourierWaybillDO w = waybills.get(e.getKey());
                        return source("COURIER", e.getKey(), w == null ? null : w.getCarrier() + " " + w.getTrackingNo(),
                                sum(e.getValue().stream().map(FreightAllocationDO::getAmount)), null);
                    }).toList());
            sources.put("international", intl.stream()
                    .collect(Collectors.groupingBy(FreightAllocationDO::getSourceId, LinkedHashMap::new, Collectors.toList()))
                    .entrySet().stream().map(e -> {
                        LogisticsShipmentDO s = shipments.get(e.getKey());
                        return source("SHIPMENT", e.getKey(), s == null ? null : s.getShNo(),
                                sum(e.getValue().stream().map(FreightAllocationDO::getAmount)),
                                s != null && Objects.equals(s.getReconciled(), 1) ? "运费账单已确认" : "运费账单未确认");
                    }).toList());
            sources.put("expense", expenseByItem.getOrDefault(l.soItemId(), List.of()).stream().map(a -> {
                ExpenseDO e = expenses.get(a.getExpenseId());
                return source("EXPENSE", e.getId(), e.getExpenseNo(), a.getAmountCny(), e.getName());
            }).toList());
            x.setSources(sources);
            return x;
        }).toList());
        return vo;
    }

    private static OrderCostVO.Source source(String type, Long id, String no, BigDecimal amount, String note) {
        OrderCostVO.Source s = new OrderCostVO.Source();
        s.setType(type);
        s.setId(id);
        s.setNo(no);
        s.setAmount(amount == null ? null : OrderCostCalc.money(amount));
        s.setNote(note);
        return s;
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> s) {
        return s.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

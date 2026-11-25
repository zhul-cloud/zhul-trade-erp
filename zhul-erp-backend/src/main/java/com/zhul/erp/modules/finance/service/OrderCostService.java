package com.zhul.erp.modules.finance.service;

import com.zhul.erp.common.utils.MigratedGuard;
import com.zhul.erp.modules.finance.entity.SalesOrderCostSnapshotDO;
import com.zhul.erp.modules.finance.repository.SalesOrderCostSnapshotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.OrderCostVO;
import com.zhul.erp.modules.finance.entity.CustomerRefundDO;
import com.zhul.erp.modules.finance.entity.ExpenseAllocationDO;
import com.zhul.erp.modules.finance.entity.ExpenseDO;
import com.zhul.erp.modules.finance.entity.PayableDO;
import com.zhul.erp.modules.finance.repository.CustomerRefundMapper;
import com.zhul.erp.modules.finance.repository.ExpenseAllocationMapper;
import com.zhul.erp.modules.finance.repository.ExpenseMapper;
import com.zhul.erp.modules.finance.repository.PayableMapper;
import com.zhul.erp.modules.finance.support.OrderCostCalc;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.entity.CourierWaybillDO;
import com.zhul.erp.modules.logistics.entity.FreightAllocationDO;
import com.zhul.erp.modules.logistics.entity.LogisticsShipmentDO;
import com.zhul.erp.modules.logistics.entity.OutboundOrderDO;
import com.zhul.erp.modules.logistics.entity.OutboundOrderItemDO;
import com.zhul.erp.modules.logistics.repository.CourierWaybillMapper;
import com.zhul.erp.modules.logistics.repository.FreightAllocationMapper;
import com.zhul.erp.modules.logistics.repository.LogisticsShipmentMapper;
import com.zhul.erp.modules.logistics.repository.OutboundOrderItemMapper;
import com.zhul.erp.modules.logistics.repository.OutboundOrderMapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderFeeDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.support.ReceiptPosition;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.system.service.DictItemService;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 订单实际成本（spec finance/order-cost）：读时计算，不存快照。按一批订单批量读取采购订单行、合格入库、到货差异、
 * 应付已付、运费分摊、费用单分摊与收款记录，交给 {@link OrderCostCalc} 计算；只读其他模块的仓储，不调用它们的服务。
 */
@Service
@RequiredArgsConstructor
public class OrderCostService {

    /** 订单费用行里算作运费的名称（不分大小写），用于出运前估算国际运费（spec finance/order-cost） */
    private static final Pattern FREIGHT_FEE = Pattern.compile("shipping|freight|运费|\\bdhl\\b|\\bfedex\\b|\\bups\\b",
            Pattern.CASE_INSENSITIVE);

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
    private final com.zhul.erp.modules.aftersales.repository.SalesReturnDispositionMapper dispositionMapper;
    private final com.zhul.erp.modules.aftersales.repository.SalesReturnMapper salesReturnMapper;
    private final com.zhul.erp.modules.aftersales.repository.ReturnReceiptMapper returnReceiptMapper;
    private final com.zhul.erp.modules.purchase.purchasereturn.repository.PurchaseReturnMapper purchaseReturnMapper;
    private final ExpenseMapper expenseMapper;
    private final CustomerRefundMapper refundMapper;
    private final DictItemService dictItemService;
    private final SalesOrderCostSnapshotMapper snapshotMapper;
    private final com.zhul.erp.modules.sales.repository.SalesOrderFeeMapper orderFeeMapper;
    private final com.zhul.erp.modules.sales.support.ReceiptPosition receiptPosition;
    private final CreditReceiptSync receiptSync;
    private final com.zhul.erp.modules.sales.repository.SalesOrderMapper soMapper;
    private final com.zhul.erp.modules.aftersales.repository.AfterSalesMapper afterSalesMapper;
    private final com.zhul.erp.modules.inventory.service.InventoryCogs inventoryCogs;
    private final com.zhul.erp.modules.finance.repository.FxSettlementAllocationMapper fxAllocationMapper;
    private final OutboundOrderItemMapper outboundItemMapper;
    private final OutboundOrderMapper outboundMapper;

    public OrderCostVO of(SalesOrderDO order) {
        return of(List.of(order)).get(order.getId());
    }

    /** 一批订单的成本；已取消的订单不计算（不在结果里）。历史迁移的订单取成本核算快照，其余按实际单据推算 */
    public Map<Long, OrderCostVO> of(Collection<SalesOrderDO> orders) {
        List<SalesOrderDO> active = orders.stream().filter(o -> o.getStatus() == SalesConstants.SO_ACTIVE).toList();
        List<SalesOrderDO> migrated = active.stream().filter(o -> MigratedGuard.isMigrated(o.getMigrated())).toList();
        List<SalesOrderDO> normal = active.stream().filter(o -> !MigratedGuard.isMigrated(o.getMigrated())).toList();
        // 迁移时旧系统还没做成本核算的订单没有快照：收入、成本按普通订单口径算，不能按 0 计（audit BUS-094）
        Set<Long> snapped = snapshotted(migrated);
        List<SalesOrderDO> unsnapped = migrated.stream().filter(o -> !snapped.contains(o.getId())).toList();
        Map<Long, OrderCostVO> out = new LinkedHashMap<>();
        if (!normal.isEmpty()) {
            out.putAll(computed(normal));
        }
        if (!unsnapped.isEmpty()) {
            computed(unsnapped).forEach((id, vo) -> {
                // 切换日之前的单据一律完结，仍算已结算，不提示还没入库、还没对账
                vo.setSettled(true);
                vo.setPending(List.of("没有历史核算快照，按订单金额与系统里的单据计算"));
                out.put(id, vo);
            });
        }
        List<SalesOrderDO> withSnapshot = migrated.stream().filter(o -> snapped.contains(o.getId())).toList();
        if (!withSnapshot.isEmpty()) {
            out.putAll(fromSnapshots(withSnapshot));
        }
        attachReplacements(active, out);
        attachFeeOrigins(active, out);
        return out;
    }

    /** 费用订单标出原订单编号；收入与毛利照常按自己算 */
    private void attachFeeOrigins(List<SalesOrderDO> active, Map<Long, OrderCostVO> out) {
        List<SalesOrderDO> fees = active.stream().filter(o -> Objects.equals(o.getSource(), SalesConstants.SO_FEE)
                && o.getOriginalSoId() != null && out.containsKey(o.getId())).toList();
        if (fees.isEmpty()) {
            return;
        }
        Map<Long, String> nos = soMapper.selectBatchIds(fees.stream().map(SalesOrderDO::getOriginalSoId).distinct().toList())
                .stream().collect(Collectors.toMap(SalesOrderDO::getId, SalesOrderDO::getSoNo));
        for (SalesOrderDO o : fees) {
            OrderCostVO vo = out.get(o.getId());
            vo.setFeeOfId(o.getOriginalSoId());
            vo.setFeeOfNo(nos.get(o.getOriginalSoId()));
        }
    }

    private static boolean replacement(SalesOrderDO o) {
        return Objects.equals(o.getSource(), SalesConstants.SO_REPLACEMENT);
    }

    /**
     * 售后补发（spec finance/order-cost「售后补发成本计入原订单」）：补发订单自己不算收入与毛利；
     * 原订单的成本加上未取消补发订单的实际成本，补发订单未结算时原订单也未结算。
     */
    private void attachReplacements(List<SalesOrderDO> active, Map<Long, OrderCostVO> out) {
        List<SalesOrderDO> own = active.stream().filter(OrderCostService::replacement).toList();
        if (!own.isEmpty()) {
            Map<Long, String> originalNos = soMapper.selectBatchIds(own.stream().map(SalesOrderDO::getOriginalSoId).distinct().toList())
                    .stream().collect(Collectors.toMap(SalesOrderDO::getId, SalesOrderDO::getSoNo));
            for (SalesOrderDO o : own) {
                OrderCostVO vo = out.get(o.getId());
                if (vo != null) {
                    vo.setReplacementOfId(o.getOriginalSoId());
                    vo.setReplacementOfNo(originalNos.get(o.getOriginalSoId()));
                    vo.setProfit(null);
                    vo.setProfitRate(null);
                }
            }
        }
        List<Long> originalIds = active.stream().filter(o -> !replacement(o) && out.containsKey(o.getId())).map(SalesOrderDO::getId).toList();
        if (originalIds.isEmpty()) {
            return;
        }
        List<SalesOrderDO> children = soMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                .in(SalesOrderDO::getOriginalSoId, originalIds)
                .eq(SalesOrderDO::getSource, SalesConstants.SO_REPLACEMENT)
                .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                .isNull(SalesOrderDO::getDeletedAt)
                .orderByAsc(SalesOrderDO::getId));
        if (children.isEmpty()) {
            return;
        }
        Map<Long, OrderCostVO> childCost = new HashMap<>();
        List<SalesOrderDO> missing = new ArrayList<>();
        for (SalesOrderDO c : children) {
            if (out.containsKey(c.getId())) {
                childCost.put(c.getId(), out.get(c.getId()));
            } else {
                missing.add(c);
            }
        }
        if (!missing.isEmpty()) {
            childCost.putAll(computed(missing));
        }
        Map<Long, String> asNos = afterSalesMapper.selectBatchIds(children.stream().map(SalesOrderDO::getAfterSalesId).distinct().toList())
                .stream().collect(Collectors.toMap(com.zhul.erp.modules.aftersales.entity.AfterSalesDO::getId,
                        com.zhul.erp.modules.aftersales.entity.AfterSalesDO::getAsNo));
        Map<Long, List<SalesOrderDO>> byOriginal = children.stream().collect(Collectors.groupingBy(SalesOrderDO::getOriginalSoId,
                LinkedHashMap::new, Collectors.toList()));
        byOriginal.forEach((originalId, list) -> {
            OrderCostVO vo = out.get(originalId);
            BigDecimal total = BigDecimal.ZERO;
            List<OrderCostVO.Replacement> rows = new ArrayList<>(list.size());
            List<String> pending = new ArrayList<>(vo.getPending() == null ? List.of() : vo.getPending());
            boolean settled = Boolean.TRUE.equals(vo.getSettled());
            for (SalesOrderDO c : list) {
                OrderCostVO cc = childCost.get(c.getId());
                OrderCostVO.Replacement r = new OrderCostVO.Replacement();
                r.setSoId(c.getId());
                r.setSoNo(c.getSoNo());
                r.setAfterSalesId(c.getAfterSalesId());
                r.setAfterSalesNo(asNos.get(c.getAfterSalesId()));
                BigDecimal purchase = BigDecimal.ZERO;
                BigDecimal freight = BigDecimal.ZERO;
                BigDecimal expense = BigDecimal.ZERO;
                for (OrderCostVO.Line l : cc.getLines()) {
                    purchase = purchase.add(nz(l.getPurchase())).add(nz(l.getInvoice())).add(nz(l.getPoFee()))
                            .subtract(nz(l.getClaim())).add(nz(l.getReturnFreight())).subtract(nz(l.getSalesReturnCredit()));
                    freight = freight.add(nz(l.getDomestic())).add(nz(l.getInternational()));
                    expense = expense.add(nz(l.getExpense()));
                }
                r.setPurchase(OrderCostCalc.money(purchase));
                r.setFreight(OrderCostCalc.money(freight));
                r.setExpense(OrderCostCalc.money(expense));
                r.setCost(cc.getCost());
                r.setSettled(cc.getSettled());
                rows.add(r);
                total = total.add(cc.getCost());
                if (!Boolean.TRUE.equals(cc.getSettled())) {
                    settled = false;
                    pending.add("补发订单 " + c.getSoNo() + " 还没结算");
                }
            }
            BigDecimal net = nz(vo.getRevenue()).subtract(nz(vo.getRefund()));
            vo.setReplacementCost(OrderCostCalc.money(total));
            vo.setReplacements(rows);
            vo.setCost(nz(vo.getCost()).add(total));
            vo.setProfit(net.subtract(vo.getCost()));
            vo.setProfitRate(OrderCostCalc.rate(vo.getProfit(), net));
            vo.setSettled(settled);
            vo.setPending(pending);
        });
    }

    /** 按实际单据推算（spec finance/order-cost「订单型号行实际成本」「收入、毛利与结算状态」） */
    private Map<Long, OrderCostVO> computed(List<SalesOrderDO> active) {
        Set<Long> soIds = active.stream().map(SalesOrderDO::getId).collect(Collectors.toSet());
        List<SalesOrderItemDO> items = soItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                .in(SalesOrderItemDO::getSoId, soIds)
                .isNull(SalesOrderItemDO::getDeletedAt)
                .orderByAsc(SalesOrderItemDO::getLineNo));
        List<Long> itemIds = items.stream().map(SalesOrderItemDO::getId).toList();
        Map<Long, List<SalesOrderItemDO>> itemsBySo = items.stream().collect(Collectors.groupingBy(SalesOrderItemDO::getSoId));
        // 已出运的数量读出运结转的销售成本（openspec inventory-costing 决策 3）
        Map<Long, com.zhul.erp.modules.inventory.service.InventoryCogs.Cogs> cogs =
                inventoryCogs.ofSoItems(items.stream().map(SalesOrderItemDO::getId).toList());

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
        // 售后退货去向（spec finance/order-cost「售后退货冲减订单成本」）
        Map<Long, List<com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO>> returnsByItem = itemIds.isEmpty() ? Map.of()
                : dispositionMapper.selectList(new LambdaQueryWrapper<com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO>()
                        .in(com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO::getSoItemId, itemIds)
                        .isNull(com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO::getSoItemId));
        Map<Long, String> returnNos = returnsByItem.isEmpty() ? Map.of() : salesReturnMapper.selectBatchIds(returnsByItem.values().stream()
                        .flatMap(List::stream).map(com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO::getReturnId).distinct().toList())
                .stream().collect(Collectors.toMap(com.zhul.erp.modules.aftersales.entity.SalesReturnDO::getId,
                        com.zhul.erp.modules.aftersales.entity.SalesReturnDO::getReturnNo));
        // 来源单号：退回供应商显示采购退货单，其他去向显示退货入库单（spec after-sales-return-docs）
        List<Long> rrIds = returnsByItem.values().stream().flatMap(List::stream)
                .map(com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO::getReceiptId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> receiptNos = rrIds.isEmpty() ? Map.of() : returnReceiptMapper.selectBatchIds(rrIds).stream()
                .collect(Collectors.toMap(com.zhul.erp.modules.aftersales.entity.ReturnReceiptDO::getId,
                        com.zhul.erp.modules.aftersales.entity.ReturnReceiptDO::getReceiptNo));
        List<Long> prIds = returnsByItem.values().stream().flatMap(List::stream)
                .map(com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO::getPurchaseReturnId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> purchaseReturnNos = prIds.isEmpty() ? Map.of() : purchaseReturnMapper.selectBatchIds(prIds).stream()
                .collect(Collectors.toMap(com.zhul.erp.modules.purchase.purchasereturn.entity.PurchaseReturnDO::getId,
                        com.zhul.erp.modules.purchase.purchasereturn.entity.PurchaseReturnDO::getReturnNo));
        Map<String, String> sourceNos = new java.util.HashMap<>();
        returnNos.forEach((k, v) -> sourceNos.put("RETURN:" + k, v));
        receiptNos.forEach((k, v) -> sourceNos.put("RETURN_RECEIPT:" + k, v));
        purchaseReturnNos.forEach((k, v) -> sourceNos.put("PURCHASE_RETURN:" + k, v));

        // 收款：PI 转成的订单按 PI 的到账，手动订单按订单自己的
        // 手动、补发、迁移订单的 pi_id 存 0，不是 PI（audit BUS-041）
        Set<Long> piIds = active.stream().map(SalesOrderDO::getPiId).filter(x -> x != null && x > 0).collect(Collectors.toSet());
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
        // 结汇手续费（人民币）：有效结汇单分到每笔收款的手续费，计入收款手续费
        Map<Long, BigDecimal> fxFeeByReceipt = receipts.isEmpty() ? Map.of() : fxAllocationMapper.selectList(
                        new LambdaQueryWrapper<com.zhul.erp.modules.finance.entity.FxSettlementAllocationDO>()
                                .in(com.zhul.erp.modules.finance.entity.FxSettlementAllocationDO::getReceiptId,
                                        receipts.stream().map(PaymentReceiptDO::getId).toList())
                                .gt(com.zhul.erp.modules.finance.entity.FxSettlementAllocationDO::getFeeAmountCny, BigDecimal.ZERO)
                                .isNull(com.zhul.erp.modules.finance.entity.FxSettlementAllocationDO::getDeletedAt))
                .stream().collect(Collectors.toMap(com.zhul.erp.modules.finance.entity.FxSettlementAllocationDO::getReceiptId,
                        com.zhul.erp.modules.finance.entity.FxSettlementAllocationDO::getFeeAmountCny, BigDecimal::add));
        // 代收款（人民币）：订单费用行里类型为代收款的
        Map<Long, BigDecimal> collectionCnyBySo = orderFeeMapper.selectList(new LambdaQueryWrapper<SalesOrderFeeDO>()
                        .in(SalesOrderFeeDO::getSoId, soIds)
                        .eq(SalesOrderFeeDO::getFeeType, SalesConstants.FEE_COLLECTION)
                        .isNull(SalesOrderFeeDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(SalesOrderFeeDO::getSoId,
                        Collectors.reducing(BigDecimal.ZERO, SalesOrderFeeDO::getAmountCny, BigDecimal::add)));
        // 预计国际运费：订单费用行里的运费，还没出运的数量按它估算（已出运按出运单判断，老出运没有库存流水）
        Map<Long, List<SalesOrderFeeDO>> freightFeesBySo = orderFeeMapper.selectList(new LambdaQueryWrapper<SalesOrderFeeDO>()
                        .in(SalesOrderFeeDO::getSoId, soIds)
                        .eq(SalesOrderFeeDO::getFeeType, SalesConstants.FEE_NORMAL)
                        .isNull(SalesOrderFeeDO::getDeletedAt))
                .stream().filter(f -> f.getFeeName() != null && FREIGHT_FEE.matcher(f.getFeeName()).find()
                        && nz(f.getAmountCny()).signum() > 0)
                .collect(Collectors.groupingBy(SalesOrderFeeDO::getSoId));
        // 已出运数量（出运单口径）：估算运费，也用于成本明细的「已出运」列（老出运没有库存结转，audit BUS-099）
        Map<Long, Integer> shippedOut = shippedQty(itemIds);
        // 退款：与到账一致，PI 转成的订单按 PI 归集，取消重转前旧订单上的退款也算在当前订单上（audit BUS-003）
        Map<Long, Long> piOfSo = new HashMap<>();
        Set<Long> refundSoIds = new HashSet<>(soIds);
        if (!piIds.isEmpty()) {
            soMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                            .select(SalesOrderDO::getId, SalesOrderDO::getPiId)
                            .in(SalesOrderDO::getPiId, piIds))
                    .forEach(x -> {
                        piOfSo.put(x.getId(), x.getPiId());
                        refundSoIds.add(x.getId());
                    });
        }
        Map<String, List<CustomerRefundDO>> refundsBySo = refundMapper.selectList(
                        new LambdaQueryWrapper<CustomerRefundDO>()
                                .in(CustomerRefundDO::getSoId, refundSoIds)
                                .eq(CustomerRefundDO::getRefundType, FinanceConstants.REFUND_AFTER_SALES)
                                .eq(CustomerRefundDO::getStatus, FinanceConstants.RECORD_VALID)
                                .isNull(CustomerRefundDO::getDeletedAt)
                                .orderByAsc(CustomerRefundDO::getRefundDate))
                .stream().collect(Collectors.groupingBy(f -> refundKey(f.getSoId(), piOfSo.get(f.getSoId()))));
        Map<String, String> reasonNames = new HashMap<>();
        if (!refundsBySo.isEmpty()) {
            dictItemService.listByDictType(FinanceConstants.DICT_REFUND_REASON).forEach(d -> reasonNames.put(d.getItemCode(), d.getItemName()));
        }

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
            Map<Long, OrderCostCalc.Shipped> shipped = new HashMap<>();
            List<SalesOrderFeeDO> freightFees = freightFeesBySo.getOrDefault(o.getId(), List.of());
            Map<Long, OrderCostCalc.FreightLine> freightLines = new LinkedHashMap<>();
            mine.forEach(i -> freightLines.put(i.getId(), new OrderCostCalc.FreightLine(i.getAmount(),
                    Objects.requireNonNullElse(i.getQuantity(), 0), shippedOut.getOrDefault(i.getId(), 0))));
            Map<Long, BigDecimal> estimates = OrderCostCalc.estimatedFreight(
                    sum(freightFees.stream().map(SalesOrderFeeDO::getAmountCny)), freightLines);
            for (SalesOrderItemDO i : mine) {
                com.zhul.erp.modules.inventory.service.InventoryCogs.Cogs c = cogs.get(i.getId());
                if (c != null) {
                    shipped.put(i.getId(), new OrderCostCalc.Shipped(c.qty(), c.systemQty(), c.goods(), c.invoice(), c.fee(),
                            c.claim().negate()));
                }
                List<FreightAllocationDO> fs = freightByItem.getOrDefault(i.getId(), List.of());
                BigDecimal dom = sum(fs.stream().filter(f -> f.getSourceType() == LogisticsConstants.FREIGHT_DOMESTIC).map(FreightAllocationDO::getAmount));
                List<FreightAllocationDO> intl = fs.stream().filter(f -> f.getSourceType() == LogisticsConstants.FREIGHT_INTERNATIONAL).toList();
                boolean reconciled = !intl.isEmpty() && intl.stream().allMatch(f -> {
                    LogisticsShipmentDO s = shipments.get(f.getSourceId());
                    return s != null && Objects.equals(s.getReconciled(), 1);
                });
                BigDecimal expense = sum(expenseByItem.getOrDefault(i.getId(), List.of()).stream().map(ExpenseAllocationDO::getAmountCny));
                List<com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO> rets = returnsByItem.getOrDefault(i.getId(), List.of());
                calcItems.add(new OrderCostCalc.Item(i.getId(), Objects.requireNonNullElse(i.getQuantity(), 0), i.getAmount(), i.getCostPrice(),
                        dom, sum(intl.stream().map(FreightAllocationDO::getAmount)), expense, !intl.isEmpty(), reconciled,
                        sum(rets.stream().map(com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO::getCostCny)),
                        sum(rets.stream().map(com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO::getFreightCny)),
                        estimates.getOrDefault(i.getId(), BigDecimal.ZERO)));
                for (PurchaseOrderItemDO l : poLinesByItem.getOrDefault(i.getId(), List.of())) {
                    calcLines.add(new OrderCostCalc.PoLine(l.getId(), l.getPoId(), i.getId(), l.getQuantity(),
                            qualified.getOrDefault(l.getId(), 0), l.getUnitPrice(), discounts.get(l.getId()), returnFreight.get(l.getId())));
                    calcPos.computeIfAbsent(l.getPoId(), id -> toCalcPo(pos.get(id), payables.get(id)));
                }
            }
            List<OrderCostCalc.Receipt> calcReceipts = myReceipts.stream().map(r -> new OrderCostCalc.Receipt(r.getAmountCny(),
                    r.getNetAmountCny(), r.getFeeDiff(), r.getExchangeRate(), Objects.requireNonNullElse(r.getSettleStatus(), 0),
                    fxFeeByReceipt.getOrDefault(r.getId(), BigDecimal.ZERO))).toList();
            List<CustomerRefundDO> refunds = refundsBySo.getOrDefault(refundKey(o.getId(), o.getPiId()), List.of());
            BigDecimal refund = sum(refunds.stream().map(CustomerRefundDO::getAmountCny));
            // 代收与多收不是收入（spec finance/order-cost）：只对有代收款或多收的已到账订单查余额流水
            BigDecimal collectionCny = collectionCnyBySo.getOrDefault(o.getId(), BigDecimal.ZERO);
            // 售后补发订单不收款：按已收齐处理（没有到账，收入为 0，结算不等货款）
            boolean paid = receiptStatus == SalesConstants.RECEIPT_PAID || replacement(o);
            // PI 转成的订单按订单合计比较（部分取消后订单合计少于 PI 合计）
            boolean overpaid = pi != null ? nz(pi.getReceivedAmount()).add(nz(pi.getFeeDiffAmount())).compareTo(nz(o.getTotalAmount())) > 0
                    : nz(o.getReceivedAmount()).add(nz(o.getFeeDiffAmount())).compareTo(nz(o.getTotalAmount())) > 0;
            BigDecimal deduction = BigDecimal.ZERO;
            BigDecimal feeDeduction = BigDecimal.ZERO;
            BigDecimal collectionDeducted = BigDecimal.ZERO;
            BigDecimal overpaidDeducted = BigDecimal.ZERO;
            if (paid && (collectionCny.signum() > 0 || overpaid)) {
                ReceiptPosition.Position position = receiptPosition.ofOrder(o);
                CreditReceiptSync.Deductions d = receiptSync.deductions(position);
                collectionDeducted = d.collectionCny();
                overpaidDeducted = d.overpaidCny();
                feeDeduction = d.collectionFee().multiply(position.cashRate());
                // 客户付的毛额里：代收净额转入了余额，代收承担的手续费被平台扣走，两者都不是收入
                deduction = collectionDeducted.add(overpaidDeducted).add(feeDeduction);
            }
            OrderCostCalc.Result res = OrderCostCalc.compute(new OrderCostCalc.Input(paid,
                    nz(o.getTotalAmountCny()).subtract(collectionCny), calcItems, calcLines, calcPos, calcReceipts, refund, deduction,
                    feeDeduction, shipped));
            OrderCostVO vo = toVo(o, mine, res, poLinesByItem, pos, qualified, freightByItem, shipments, waybills, expenseByItem, expenses,
                    returnsByItem, sourceNos, cogs);
            estimateSources(vo, o, freightFees, freightLines);
            // 「已出运」显示实际出运数量；成本仍按库存结转的数量取结转成本（老出运没有结转成本，不能当作已结转）
            vo.getLines().forEach(l -> l.setShippedQty(Math.max(Objects.requireNonNullElse(l.getShippedQty(), 0),
                    shippedOut.getOrDefault(l.getSoItemId(), 0))));
            vo.setRefund(res.refund());
            vo.setRefunds(refunds.stream().map(f -> brief(f, reasonNames)).toList());
            vo.setCollectionCny(OrderCostCalc.money(collectionDeducted));
            vo.setOverpaidCny(OrderCostCalc.money(overpaidDeducted));
            vo.setHistorySnapshot(false);
            out.put(o.getId(), vo);
        }
        return out;
    }

    /** 预计国际运费的来源说明：订单运费、按行金额分到这一行、还没出运的数量（原型 32-11-25反馈 Y1） */
    private static void estimateSources(OrderCostVO vo, SalesOrderDO o, List<SalesOrderFeeDO> fees,
                                        Map<Long, OrderCostCalc.FreightLine> lines) {
        String feeText = fees.stream().map(f -> f.getFeeName() + " " + o.getCurrencyCode() + " "
                + OrderCostCalc.money(nz(f.getAmount())).toPlainString()).collect(Collectors.joining("、"));
        BigDecimal feeCny = OrderCostCalc.money(sum(fees.stream().map(SalesOrderFeeDO::getAmountCny)));
        for (OrderCostVO.Line l : vo.getLines()) {
            if (l.getEstimatedInternational() == null || l.getEstimatedInternational().signum() <= 0) {
                continue;
            }
            OrderCostCalc.FreightLine fl = lines.get(l.getSoItemId());
            int unshipped = fl == null ? 0 : Math.max(0, fl.quantity() - fl.shippedQty());
            List<OrderCostVO.Source> list = new ArrayList<>();
            list.add(source("ESTIMATE", null, "预计", l.getEstimatedInternational(), "订单运费 " + feeText + " ≈ ¥"
                    + feeCny.toPlainString() + "，按行金额分到这一行，" + unshipped + " 个还没出运"));
            list.addAll(l.getSources().getOrDefault("international", List.of()));
            l.getSources().put("international", list);
        }
    }

    /** 型号行已出运的数量：在已出运的出运单上的销售出库单数量 */
    private Map<Long, Integer> shippedQty(List<Long> soItemIds) {
        if (soItemIds.isEmpty()) {
            return Map.of();
        }
        List<OutboundOrderItemDO> obItems = outboundItemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItemDO>()
                .in(OutboundOrderItemDO::getSoItemId, soItemIds)
                .isNull(OutboundOrderItemDO::getDeletedAt));
        if (obItems.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> logisticsOf = outboundMapper.selectBatchIds(obItems.stream().map(OutboundOrderItemDO::getOutboundId)
                        .distinct().toList()).stream()
                .filter(ob -> ob.getDeletedAt() == null && ob.getLogisticsId() != null)
                .collect(Collectors.toMap(OutboundOrderDO::getId, OutboundOrderDO::getLogisticsId));
        Set<Long> shippedSh = logisticsOf.isEmpty() ? Set.of() : shipmentMapper.selectBatchIds(new HashSet<>(logisticsOf.values()))
                .stream().filter(sh -> Objects.equals(sh.getStatus(), LogisticsConstants.SH_SHIPPED))
                .map(LogisticsShipmentDO::getId).collect(Collectors.toSet());
        Map<Long, Integer> out = new HashMap<>();
        for (OutboundOrderItemDO i : obItems) {
            Long sh = logisticsOf.get(i.getOutboundId());
            if (sh != null && shippedSh.contains(sh)) {
                out.merge(i.getSoItemId(), Objects.requireNonNullElse(i.getQuantity(), 0), Integer::sum);
            }
        }
        return out;
    }

    /** 退款归集键：PI 转成的订单按 PI，手动订单（pi_id 为 0）按订单 */
    private static String refundKey(Long soId, Long piId) {
        return piId != null && piId > 0 ? "PI" + piId : "SO" + soId;
    }

    /** 有历史核算快照的订单 ID */
    private Set<Long> snapshotted(List<SalesOrderDO> orders) {
        if (orders.isEmpty()) {
            return Set.of();
        }
        return snapshotMapper.selectList(new LambdaQueryWrapper<SalesOrderCostSnapshotDO>()
                        .select(SalesOrderCostSnapshotDO::getSoId)
                        .in(SalesOrderCostSnapshotDO::getSoId, orders.stream().map(SalesOrderDO::getId).toList())
                        .isNull(SalesOrderCostSnapshotDO::getDeletedAt))
                .stream().map(SalesOrderCostSnapshotDO::getSoId).collect(Collectors.toSet());
    }

    /**
     * 历史迁移订单取成本核算快照（spec finance/order-cost「迁移订单按历史核算快照」）：收入、成本、结算状态取快照；
     * 退款仍按有效退款单（含切换后新建的）；型号行按行金额比例分摊快照收入、成本与退款，尾差放到金额最大的行。
     */
    private Map<Long, OrderCostVO> fromSnapshots(List<SalesOrderDO> orders) {
        Set<Long> soIds = orders.stream().map(SalesOrderDO::getId).collect(Collectors.toSet());
        Map<Long, SalesOrderCostSnapshotDO> snaps = snapshotMapper.selectList(new LambdaQueryWrapper<SalesOrderCostSnapshotDO>()
                        .in(SalesOrderCostSnapshotDO::getSoId, soIds)
                        .isNull(SalesOrderCostSnapshotDO::getDeletedAt))
                .stream().collect(Collectors.toMap(SalesOrderCostSnapshotDO::getSoId, Function.identity(), (a, b) -> a));
        Map<Long, List<SalesOrderItemDO>> itemsBySo = soItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                        .in(SalesOrderItemDO::getSoId, soIds)
                        .isNull(SalesOrderItemDO::getDeletedAt)
                        .orderByAsc(SalesOrderItemDO::getLineNo))
                .stream().collect(Collectors.groupingBy(SalesOrderItemDO::getSoId));
        Map<Long, List<CustomerRefundDO>> refundsBySo = refundMapper.selectList(new LambdaQueryWrapper<CustomerRefundDO>()
                        .in(CustomerRefundDO::getSoId, soIds)
                        .eq(CustomerRefundDO::getStatus, FinanceConstants.RECORD_VALID)
                        .isNull(CustomerRefundDO::getDeletedAt)
                        .orderByAsc(CustomerRefundDO::getRefundDate))
                .stream().collect(Collectors.groupingBy(CustomerRefundDO::getSoId));
        Map<String, String> reasonNames = new HashMap<>();
        if (!refundsBySo.isEmpty()) {
            dictItemService.listByDictType(FinanceConstants.DICT_REFUND_REASON).forEach(d -> reasonNames.put(d.getItemCode(), d.getItemName()));
        }
        Map<Long, OrderCostVO> out = new LinkedHashMap<>();
        for (SalesOrderDO o : orders) {
            SalesOrderCostSnapshotDO snap = snaps.get(o.getId());
            BigDecimal revenue = snap == null ? BigDecimal.ZERO.setScale(2) : OrderCostCalc.money(snap.getRevenue());
            BigDecimal cost = snap == null ? BigDecimal.ZERO.setScale(2) : OrderCostCalc.money(snap.getCost());
            List<CustomerRefundDO> refunds = refundsBySo.getOrDefault(o.getId(), List.of());
            BigDecimal refund = OrderCostCalc.money(sum(refunds.stream().map(CustomerRefundDO::getAmountCny)));
            BigDecimal profit = revenue.subtract(refund).subtract(cost);

            OrderCostVO vo = new OrderCostVO();
            vo.setSoId(o.getId());
            vo.setSalesAmountCny(o.getTotalAmountCny());
            vo.setRevenue(revenue);
            vo.setCost(cost);
            vo.setReceiptFee(snap == null ? BigDecimal.ZERO.setScale(2) : OrderCostCalc.money(snap.getServiceFee()));
            vo.setRefund(refund);
            vo.setRefunds(refunds.stream().map(f -> brief(f, reasonNames)).toList());
            vo.setProfit(profit);
            vo.setProfitRate(OrderCostCalc.rate(profit, revenue.subtract(refund)));
            // 切换日之前的单据一律完结，迁移订单都算已结算（含没有快照的）
            vo.setSettled(true);
            vo.setPending(snap == null ? List.of("没有历史核算快照") : List.of());
            vo.setMissingCostCount(0);
            vo.setHistorySnapshot(true);
            if (snap != null) {
                OrderCostVO.Snapshot s = new OrderCostVO.Snapshot();
                s.setPurchaseAmount(snap.getPurchaseAmount());
                s.setFreight(snap.getFreight());
                s.setSurcharge(snap.getSurcharge());
                s.setServiceFee(snap.getServiceFee());
                s.setAccountingMonth(snap.getAccountingMonth());
                s.setSourceCodes(snap.getSourceCodes());
                vo.setSnapshot(s);
            }

            List<SalesOrderItemDO> items = itemsBySo.getOrDefault(o.getId(), List.of());
            Map<Long, BigDecimal> weights = new LinkedHashMap<>();
            items.forEach(i -> weights.put(i.getId(), i.getAmount() == null ? BigDecimal.ZERO : i.getAmount()));
            Map<Long, BigDecimal> lineRevenue = OrderCostCalc.split(revenue, weights);
            Map<Long, BigDecimal> lineCost = OrderCostCalc.split(cost, weights);
            Map<Long, BigDecimal> lineRefund = OrderCostCalc.split(refund, weights);
            vo.setLines(items.stream().map(i -> {
                BigDecimal r = lineRevenue.get(i.getId());
                BigDecimal c = lineCost.get(i.getId());
                BigDecimal f = lineRefund.get(i.getId());
                BigDecimal p = r.subtract(f).subtract(c);
                OrderCostVO.Line x = new OrderCostVO.Line();
                x.setSoItemId(i.getId());
                x.setModel(i.getModel());
                x.setBrand(i.getBrand());
                x.setQuantity(i.getQuantity());
                x.setCost(c);
                x.setRevenue(r);
                x.setRefund(f);
                x.setProfit(p);
                x.setProfitRate(OrderCostCalc.rate(p, r.subtract(f)));
                x.setPurchaseSettled(vo.getSettled());
                x.setUnreceivedQty(0);
                x.setUnorderedQty(0);
                x.setMissingCostPrice(false);
                x.setSources(Map.of());
                return x;
            }).toList());
            out.put(o.getId(), vo);
        }
        return out;
    }

    private static OrderCostVO.RefundBrief brief(CustomerRefundDO f, Map<String, String> reasonNames) {
        OrderCostVO.RefundBrief b = new OrderCostVO.RefundBrief();
        b.setId(f.getId());
        b.setRefundNo(f.getRefundNo());
        b.setRefundDate(f.getRefundDate());
        b.setReasonName(reasonNames.getOrDefault(f.getReason(), f.getReason()));
        b.setCurrencyCode(f.getCurrencyCode());
        b.setAmount(f.getAmount());
        b.setAmountCny(f.getAmountCny());
        return b;
    }

    /** 采购订单与应付 → 计算用的采购订单（库存批次成本共用，openspec inventory-costing） */
    public static OrderCostCalc.Po toCalcPo(PurchaseOrderDO p, PayableDO ap) {
        BigDecimal payable = ap == null ? p.getTotalAmount() : ap.getAmount();
        return new OrderCostCalc.Po(p.getId(), p.getCurrencyCode(), p.getExchangeRate(), p.getItemAmount(), p.getFeeAmount(),
                p.getInvoiceCost(), payable, ap == null ? BigDecimal.ZERO : ap.getPaidAmount(), ap == null ? BigDecimal.ZERO : ap.getPaidCny());
    }

    private OrderCostVO toVo(SalesOrderDO o, List<SalesOrderItemDO> items, OrderCostCalc.Result res,
                             Map<Long, List<PurchaseOrderItemDO>> poLinesByItem, Map<Long, PurchaseOrderDO> pos, Map<Long, Integer> qualified,
                             Map<Long, List<FreightAllocationDO>> freightByItem, Map<Long, LogisticsShipmentDO> shipments,
                             Map<Long, CourierWaybillDO> waybills, Map<Long, List<ExpenseAllocationDO>> expenseByItem, Map<Long, ExpenseDO> expenses,
                             Map<Long, List<com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO>> returnsByItem,
                             Map<String, String> sourceNos, Map<Long, com.zhul.erp.modules.inventory.service.InventoryCogs.Cogs> cogs) {
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
            x.setSalesReturnCredit(l.salesReturnCredit());
            x.setDomestic(l.domestic());
            x.setInternational(l.international());
            x.setEstimatedInternational(l.estimatedFreight());
            List<FreightAllocationDO> fs = freightByItem.getOrDefault(l.soItemId(), List.of());
            List<FreightAllocationDO> intl = fs.stream().filter(f -> f.getSourceType() == LogisticsConstants.FREIGHT_INTERNATIONAL).toList();
            x.setInternationalSettled(!intl.isEmpty() && intl.stream()
                    .allMatch(f -> shipments.get(f.getSourceId()) != null && Objects.equals(shipments.get(f.getSourceId()).getReconciled(), 1)));
            x.setExpense(l.expense());
            x.setReceiptFee(l.receiptFee());
            x.setCost(l.cost());
            x.setRevenue(l.revenue());
            x.setRefund(l.refund());
            x.setProfit(l.profit());
            x.setProfitRate(l.profitRate());
            x.setUnreceivedQty(l.unreceivedQty());
            x.setUnorderedQty(l.unorderedQty());
            x.setMissingCostPrice(l.missingCostPrice());
            x.setShippedQty(l.shippedQty());
            x.setSystemQty(l.systemQty());

            Map<String, List<OrderCostVO.Source>> sources = new LinkedHashMap<>();
            // 采购货款来源：出运结转的出运单、之后的成本调整单，再是采购订单（还没出运的数量照旧推算，原型 O1）
            List<OrderCostVO.Source> purchaseSources = new ArrayList<>();
            com.zhul.erp.modules.inventory.service.InventoryCogs.Cogs c = cogs.get(l.soItemId());
            if (c != null) {
                c.sources().forEach(s -> purchaseSources.add(source("SH".equals(s.type()) ? "SHIPMENT" : "COST_ADJUST", s.id(), s.no(),
                        s.amount(), "SH".equals(s.type()) ? "出运结转 " + s.qty() + " 个" : "成本调整")));
            }
            poLinesByItem.getOrDefault(l.soItemId(), List.of()).forEach(pl -> {
                PurchaseOrderDO p = pos.get(pl.getPoId());
                purchaseSources.add(source("PO", p.getId(), p.getPoNo(), pl.getAmount(), "合格入库 " + qualified.getOrDefault(pl.getId(), 0)
                        + " / " + pl.getQuantity() + " · 单价 " + p.getCurrencyCode() + " " + pl.getUnitPrice()));
            });
            sources.put("purchase", purchaseSources);
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
            sources.put("salesReturn", returnsByItem.getOrDefault(l.soItemId(), List.of()).stream()
                    .filter(d -> d.getCostCny().signum() != 0 || d.getFreightCny().signum() != 0)
                    .map(d -> returnSource(d, sourceNos,
                            com.zhul.erp.modules.aftersales.constants.AfterSalesConstants.DISP_NAMES.get(d.getDisposition()) + " × " + d.getQuantity()
                                    + (d.getFreightCny().signum() > 0 ? "，退回运费 ¥" + d.getFreightCny() : ""))).toList());
            x.setSources(sources);
            return x;
        }).toList());
        return vo;
    }

    /** 退货去向的来源单：退回供应商为采购退货单，其他去向为退货入库单，迁移前没有入库单的为销售退货单 */
    private static OrderCostVO.Source returnSource(com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO d,
                                                   Map<String, String> sourceNos, String note) {
        String type = d.getPurchaseReturnId() != null ? "PURCHASE_RETURN" : d.getReceiptId() != null ? "RETURN_RECEIPT" : "RETURN";
        Long id = d.getPurchaseReturnId() != null ? d.getPurchaseReturnId() : d.getReceiptId() != null ? d.getReceiptId() : d.getReturnId();
        return source(type, id, sourceNos.get(type + ":" + id), d.getCostCny().negate(), note);
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

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> s) {
        return s.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

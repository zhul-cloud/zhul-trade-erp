package com.zhul.erp.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.entity.PayableDO;
import com.zhul.erp.modules.finance.repository.PayableMapper;
import com.zhul.erp.modules.finance.service.OrderCostService;
import com.zhul.erp.modules.finance.support.OrderCostCalc;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvBalanceDO;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.entity.InvCostAdjustDO;
import com.zhul.erp.modules.inventory.entity.InvCostAdjustItemDO;
import com.zhul.erp.modules.inventory.entity.InvMoveDO;
import com.zhul.erp.modules.inventory.repository.InvBalanceMapper;
import com.zhul.erp.modules.inventory.repository.InvBatchMapper;
import com.zhul.erp.modules.inventory.repository.InvCostAdjustItemMapper;
import com.zhul.erp.modules.inventory.repository.InvCostAdjustMapper;
import com.zhul.erp.modules.inventory.repository.InvMoveMapper;
import com.zhul.erp.modules.inventory.service.InventoryPosting.CostMove;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Doc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Loc;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptItemDO;
import com.zhul.erp.modules.warehouse.entity.ReceivingDiscrepancyDO;
import com.zhul.erp.modules.warehouse.repository.PurchaseReceiptItemMapper;
import com.zhul.erp.modules.warehouse.repository.ReceivingDiscrepancyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
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
 * 批次成本与成本调整（spec finance/inventory-cost「批次成本」「成本调整」，openspec inventory-costing 决策 2、4、5）。
 * <ul>
 *   <li>批次单位成本 = 货款 + 开票成本 + 采购费用 + 质量索赔（负数），算法与订单成本共用 {@link OrderCostCalc#unitCost}；
 *       各项 HALF_UP 6 位。</li>
 *   <li>重算后单位成本有变化的批次，按在库、已出运、其他出库拆分，数量 × 单位成本差（各项 HALF_UP 2 位）记一张成本调整单；
 *       在库部分同时记「成本调整」流水（数量 0）。同样的单据状态再算一次不会再生成调整（只比较数值）。</li>
 * </ul>
 * 只处理采购入库批次（含期初生成的采购批次）；退货入库、系统外入库批次入库后不随采购订单调整。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryCosting {

    private static final MathContext MC = MathContext.DECIMAL128;
    /** 不参与拆分的流水：批次的入库本身（含冲销）、商品合并、成本调整 */
    private static final Set<String> NOT_OUTFLOW = Set.of(InvConstants.BIZ_PURCHASE_IN, InvConstants.BIZ_PURCHASE_IN_REVERSE,
            InvConstants.BIZ_OPENING, InvConstants.BIZ_MERGE, InvConstants.BIZ_COST_ADJUST, InvConstants.BIZ_SYSTEM_IN,
            InvConstants.BIZ_RETURN_IN, InvConstants.BIZ_RETURN_IN_UNDO);
    private static final Set<String> SHIPPED = Set.of(InvConstants.BIZ_SALES_OUT, InvConstants.BIZ_SALES_OUT_UNDO);

    private final InvBatchMapper batchMapper;
    private final InvBalanceMapper balanceMapper;
    private final InvMoveMapper moveMapper;
    private final InvCostAdjustMapper adjustMapper;
    private final InvCostAdjustItemMapper adjustItemMapper;
    private final PurchaseOrderMapper poMapper;
    private final PurchaseOrderItemMapper poItemMapper;
    private final PayableMapper payableMapper;
    private final ReceivingDiscrepancyMapper discrepancyMapper;
    private final PurchaseReceiptItemMapper receiptItemMapper;
    private final ProductMapper productMapper;
    private final InventoryPosting posting;
    private final DocumentNumberService numbers;
    private final DocLogs docLogs;

    /** 单位成本四项（人民币，6 位） */
    public record Cost(BigDecimal goods, BigDecimal invoice, BigDecimal fee, BigDecimal claim) {
        public static final Cost ZERO = new Cost(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO).scaled();

        public static Cost of(InvBatchDO b) {
            return new Cost(nz(b.getCostGoods()), nz(b.getCostInvoice()), nz(b.getCostFee()), nz(b.getCostClaim())).scaled();
        }

        Cost scaled() {
            return new Cost(s6(goods), s6(invoice), s6(fee), s6(claim));
        }

        public BigDecimal total() {
            return goods.add(invoice).add(fee).add(claim);
        }

        public Cost minus(Cost o) {
            return new Cost(goods.subtract(o.goods), invoice.subtract(o.invoice), fee.subtract(o.fee), claim.subtract(o.claim));
        }

        public boolean sameAs(Cost o) {
            return goods.compareTo(o.goods) == 0 && invoice.compareTo(o.invoice) == 0 && fee.compareTo(o.fee) == 0
                    && claim.compareTo(o.claim) == 0;
        }
    }

    /** 触发成本调整的原因与来源单据（InvConstants.CA_*） */
    public record Trigger(int reason, String docType, Long docId, String docNo) {
        public static Trigger of(int reason) {
            return new Trigger(reason, "", null, "");
        }
    }

    /** 拆分出的一部分：在库按仓库 × 状态，已出运按出运单 × 型号行，其他出库按出库单 */
    record Part(int part, Long warehouseId, Long productId, int status, String docType, Long docId, String docNo, Long soItemId,
                int qty) {
    }

    // ---------------------------------------------------------------- 计算

    /** 新建的采购批次按当前单据定成本（还没有流水，不生成调整）；没有采购订单行的保持原值 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void initialize(InvBatchDO b) {
        Cost c = compute(List.of(b)).get(b.getId());
        if (c != null) {
            set(b, c);
            batchMapper.updateById(b);
        }
    }

    static void set(InvBatchDO b, Cost c) {
        b.setCostGoods(c.goods());
        b.setCostInvoice(c.invoice());
        b.setCostFee(c.fee());
        b.setCostClaim(c.claim());
        b.setUnitPriceCny(c.total());
    }

    /** 按当前单据算批次成本：只算有采购订单行的采购批次，其他批次不在结果里 */
    public Map<Long, Cost> compute(List<InvBatchDO> batches) {
        List<InvBatchDO> list = batches.stream()
                .filter(b -> b.getPoItemId() != null && Objects.equals(b.getSourceType(), InvConstants.BATCH_PURCHASE)).toList();
        Map<Long, Cost> out = new HashMap<>();
        if (list.isEmpty()) {
            return out;
        }
        Map<Long, PurchaseOrderItemDO> poItems = poItemMapper.selectBatchIds(list.stream().map(InvBatchDO::getPoItemId).distinct().toList())
                .stream().collect(Collectors.toMap(PurchaseOrderItemDO::getId, Function.identity()));
        Set<Long> poIds = poItems.values().stream().map(PurchaseOrderItemDO::getPoId).collect(Collectors.toSet());
        Map<Long, PurchaseOrderDO> pos = poIds.isEmpty() ? Map.of() : poMapper.selectBatchIds(poIds).stream()
                .collect(Collectors.toMap(PurchaseOrderDO::getId, Function.identity()));
        Map<Long, PayableDO> payables = poIds.isEmpty() ? Map.of() : payableMapper.selectList(new LambdaQueryWrapper<PayableDO>()
                        .eq(PayableDO::getSourceType, FinanceConstants.SOURCE_PURCHASE_ORDER)
                        .in(PayableDO::getSourceId, poIds)
                        .isNull(PayableDO::getDeletedAt))
                .stream().collect(Collectors.toMap(PayableDO::getSourceId, Function.identity(), (a, b) -> a));
        Map<Long, OrderCostCalc.Po> calcPos = new HashMap<>();
        Map<Long, BigDecimal> rates = new HashMap<>();
        pos.forEach((id, p) -> {
            OrderCostCalc.Po cp = OrderCostService.toCalcPo(p, payables.get(id));
            calcPos.put(id, cp);
            rates.put(id, OrderCostCalc.actualRate(cp));
        });
        Map<Long, BigDecimal> discounts = discounts(list);
        Map<Long, Integer> inQty = discounts.isEmpty() ? Map.of() : inQty(list);
        for (InvBatchDO b : list) {
            PurchaseOrderItemDO pi = poItems.get(b.getPoItemId());
            OrderCostCalc.Po po = pi == null ? null : calcPos.get(pi.getPoId());
            if (po == null) {
                continue;
            }
            BigDecimal rate = rates.get(po.id());
            OrderCostCalc.UnitCost u = OrderCostCalc.unitCost(po, pi.getUnitPrice(), rate);
            BigDecimal claim = BigDecimal.ZERO;
            BigDecimal discount = b.getSourceItemId() == null ? null : discounts.get(b.getSourceItemId());
            int qty = inQty.getOrDefault(b.getId(), 0);
            if (discount != null && discount.signum() != 0 && qty > 0) {
                claim = discount.multiply(rate, MC).divide(BigDecimal.valueOf(qty), MC).negate();
            }
            out.put(b.getId(), new Cost(u.goods(), u.invoice(), u.fee(), claim).scaled());
        }
        return out;
    }

    /** 入库单行 → 已处理的折价接收的折价合计（原币） */
    private Map<Long, BigDecimal> discounts(List<InvBatchDO> batches) {
        List<Long> receiptItemIds = batches.stream().map(InvBatchDO::getSourceItemId).filter(Objects::nonNull).distinct().toList();
        Map<Long, BigDecimal> out = new HashMap<>();
        if (receiptItemIds.isEmpty()) {
            return out;
        }
        for (ReceivingDiscrepancyDO d : discrepancyMapper.selectList(new LambdaQueryWrapper<ReceivingDiscrepancyDO>()
                .in(ReceivingDiscrepancyDO::getReceiptItemId, receiptItemIds)
                .eq(ReceivingDiscrepancyDO::getStatus, WarehouseConstants.DIFF_HANDLED)
                .eq(ReceivingDiscrepancyDO::getResolution, WarehouseConstants.RES_DISCOUNT)
                .isNull(ReceivingDiscrepancyDO::getDeletedAt))) {
            if (d.getDiscountAmount() != null) {
                out.merge(d.getReceiptItemId(), d.getDiscountAmount(), BigDecimal::add);
            }
        }
        return out;
    }

    /** 批次的入库数量：入库流水（含期初、扣冲销）；还没有流水时取入库单行的实收（合格 + 不良） */
    private Map<Long, Integer> inQty(List<InvBatchDO> batches) {
        Map<Long, Integer> out = new HashMap<>();
        moveMapper.selectList(new LambdaQueryWrapper<InvMoveDO>()
                        .select(InvMoveDO::getBatchId, InvMoveDO::getQuantity)
                        .eq(InvMoveDO::getTenantId, PiStore.tenantId())
                        .in(InvMoveDO::getBatchId, batches.stream().map(InvBatchDO::getId).toList())
                        .in(InvMoveDO::getBizType, List.of(InvConstants.BIZ_PURCHASE_IN, InvConstants.BIZ_PURCHASE_IN_REVERSE,
                                InvConstants.BIZ_OPENING))
                        .in(InvMoveDO::getOpType, List.of(InvConstants.OP_IN, InvConstants.OP_OUT)))
                .forEach(m -> out.merge(m.getBatchId(), m.getQuantity(), Integer::sum));
        List<Long> missing = batches.stream().filter(b -> !out.containsKey(b.getId()) && b.getSourceItemId() != null)
                .map(InvBatchDO::getSourceItemId).toList();
        if (!missing.isEmpty()) {
            Map<Long, PurchaseReceiptItemDO> items = receiptItemMapper.selectBatchIds(missing).stream()
                    .collect(Collectors.toMap(PurchaseReceiptItemDO::getId, Function.identity()));
            for (InvBatchDO b : batches) {
                PurchaseReceiptItemDO i = b.getSourceItemId() == null ? null : items.get(b.getSourceItemId());
                if (!out.containsKey(b.getId()) && i != null) {
                    out.put(b.getId(), InventoryHooks.nz(i.getQualifiedQty()) + InventoryHooks.nz(i.getDefectiveQty()));
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 重算与调整

    /** 某些采购订单下的全部采购批次 */
    public List<InvBatchDO> batchesOfOrders(Collection<Long> poIds) {
        if (poIds.isEmpty()) {
            return List.of();
        }
        List<Long> poItemIds = poItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                        .select(PurchaseOrderItemDO::getId)
                        .in(PurchaseOrderItemDO::getPoId, poIds))
                .stream().map(PurchaseOrderItemDO::getId).toList();
        return poItemIds.isEmpty() ? List.of() : batchMapper.selectList(new LambdaQueryWrapper<InvBatchDO>()
                .eq(InvBatchDO::getTenantId, PiStore.tenantId())
                .eq(InvBatchDO::getSourceType, InvConstants.BATCH_PURCHASE)
                .in(InvBatchDO::getPoItemId, poItemIds)
                .isNull(InvBatchDO::getDeletedAt)
                .orderByAsc(InvBatchDO::getId));
    }

    /** 当前租户入库日期在 since 之后（含）的采购批次；since 为空时全部 */
    public List<InvBatchDO> purchaseBatches(LocalDate since) {
        return batchMapper.selectList(new LambdaQueryWrapper<InvBatchDO>()
                .eq(InvBatchDO::getTenantId, PiStore.tenantId())
                .eq(InvBatchDO::getSourceType, InvConstants.BATCH_PURCHASE)
                .isNotNull(InvBatchDO::getPoItemId)
                .ge(since != null, InvBatchDO::getInDate, since)
                .isNull(InvBatchDO::getDeletedAt)
                .orderByAsc(InvBatchDO::getId));
    }

    /** 重算采购订单下的批次；有变化时生成一张成本调整单并返回，没有变化返回 null */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public InvCostAdjustDO recost(Collection<Long> poIds, Trigger trigger) {
        return recostBatches(batchesOfOrders(poIds), trigger);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public InvCostAdjustDO recostBatches(List<InvBatchDO> batches, Trigger trigger) {
        Map<Long, Cost> fresh = compute(batches);
        List<InvCostAdjustItemDO> items = new ArrayList<>();
        List<CostMove> stockMoves = new ArrayList<>();
        List<InvBatchDO> changed = new ArrayList<>();
        for (InvBatchDO b : batches) {
            Cost now = fresh.get(b.getId());
            Cost old = Cost.of(b);
            if (now == null || now.sameAs(old)) {
                continue;
            }
            Cost diff = now.minus(old);
            List<InvCostAdjustItemDO> mine = new ArrayList<>();
            for (Part p : parts(b.getId())) {
                InvCostAdjustItemDO i = item(b, p, old, now, diff);
                mine.add(i);
            }
            set(b, now);
            batchMapper.updateById(b);
            if (mine.isEmpty()) {
                // 批次已没有任何数量（如入库已冲销）：只更新单位成本，不影响金额
                continue;
            }
            changed.add(b);
            items.addAll(mine);
        }
        if (items.isEmpty()) {
            return null;
        }
        LocalDate today = LocalDate.now();
        InvCostAdjustDO a = new InvCostAdjustDO();
        a.setTenantId(PiStore.tenantId());
        a.setAdjustNo(numbers.next(DocumentType.CA));
        a.setAdjustDate(today);
        a.setPeriodMonth(InventoryPeriods.month(today));
        a.setReason(trigger.reason());
        a.setSourceDocType(Objects.requireNonNullElse(trigger.docType(), ""));
        a.setSourceDocId(trigger.docId());
        a.setSourceDocNo(Objects.requireNonNullElse(trigger.docNo(), ""));
        a.setBatchCount(changed.size());
        a.setFirstModel(model(changed.get(0).getProductId()));
        a.setCurrencyCode("CNY");
        a.setStockAmount(sum(items, InvConstants.PART_STOCK));
        a.setCogsAmount(sum(items, InvConstants.PART_SHIPPED));
        a.setOtherAmount(sum(items, InvConstants.PART_OTHER));
        a.setRemark("");
        adjustMapper.insert(a);
        Doc doc = Doc.of(InvConstants.BIZ_COST_ADJUST, "CA", a.getId(), a.getAdjustNo())
                .label(InvConstants.CA_REASON_NAMES.getOrDefault(trigger.reason(), ""));
        Map<Long, InvBatchDO> byId = changed.stream().collect(Collectors.toMap(InvBatchDO::getId, Function.identity()));
        for (InvCostAdjustItemDO i : items) {
            i.setAdjustId(a.getId());
            adjustItemMapper.insert(i);
            if (i.getPart() == InvConstants.PART_STOCK) {
                stockMoves.add(new CostMove(new Loc(i.getWarehouseId(), i.getProductId(), i.getBatchId(), stockStatusOf(i)),
                        byId.get(i.getBatchId()).getUnitPriceCny(), i.getAmountGoods(), i.getAmountInvoice(), i.getAmountFee(),
                        i.getAmountClaim(), doc.item(i.getId())));
            }
        }
        posting.postCostAdjust(stockMoves);
        String reason = InvConstants.CA_REASON_NAMES.getOrDefault(trigger.reason(), "");
        docLogs.addBySystem(DocumentType.CA, a.getId(), "生成", reason
                + (a.getSourceDocNo().isEmpty() ? "" : " " + a.getSourceDocNo()) + "，" + changed.size() + " 个批次，在库 "
                + a.getStockAmount() + "，销售成本 " + a.getCogsAmount() + "，其他出库 " + a.getOtherAmount());
        log.info("生成成本调整单，adjustNo={}, reason={}, batches={}", a.getAdjustNo(), reason, changed.size());
        return a;
    }

    /** 在库部分的状态记在 docType 上（在库行没有出库单据），见 {@link #item} */
    private static int stockStatusOf(InvCostAdjustItemDO i) {
        return "DEFECTIVE".equals(i.getDocType()) ? InvConstants.DEFECTIVE : InvConstants.GOOD;
    }

    private static InvCostAdjustItemDO item(InvBatchDO b, Part p, Cost old, Cost now, Cost diff) {
        InvCostAdjustItemDO i = new InvCostAdjustItemDO();
        i.setTenantId(b.getTenantId());
        i.setBatchId(b.getId());
        i.setProductId(p.productId());
        i.setPart(p.part());
        i.setWarehouseId(p.warehouseId());
        i.setDocType(p.part() == InvConstants.PART_STOCK ? (p.status() == InvConstants.DEFECTIVE ? "DEFECTIVE" : "")
                : Objects.requireNonNullElse(p.docType(), ""));
        i.setDocId(p.docId());
        i.setDocNo(Objects.requireNonNullElse(p.docNo(), ""));
        i.setSoItemId(p.soItemId());
        i.setQuantity(p.qty());
        i.setOldUnitCost(old.total());
        i.setNewUnitCost(now.total());
        i.setDiffGoods(diff.goods());
        i.setDiffInvoice(diff.invoice());
        i.setDiffFee(diff.fee());
        i.setDiffClaim(diff.claim());
        i.setCurrencyCode("CNY");
        BigDecimal q = BigDecimal.valueOf(p.qty());
        i.setAmountGoods(money(diff.goods().multiply(q)));
        i.setAmountInvoice(money(diff.invoice().multiply(q)));
        i.setAmountFee(money(diff.fee().multiply(q)));
        i.setAmountClaim(money(diff.claim().multiply(q)));
        i.setAmountCny(i.getAmountGoods().add(i.getAmountInvoice()).add(i.getAmountFee()).add(i.getAmountClaim()));
        return i;
    }

    /**
     * 批次现在的去向：在库 = 各仓库 × 状态的结存；已出运 = 销售出库流水按出运单 × 型号行的净数量；
     * 其他出库 = 其余离开公司的流水（其他出库、采购退货出库及其作废）按单据的净数量。数量为 0 的不列。
     */
    List<Part> parts(Long batchId) {
        List<Part> out = new ArrayList<>();
        for (InvBalanceDO b : balanceMapper.selectList(new LambdaQueryWrapper<InvBalanceDO>()
                .eq(InvBalanceDO::getTenantId, PiStore.tenantId())
                .eq(InvBalanceDO::getBatchId, batchId)
                .gt(InvBalanceDO::getOnHand, 0)
                .orderByAsc(InvBalanceDO::getWarehouseId, InvBalanceDO::getStockStatus))) {
            out.add(new Part(InvConstants.PART_STOCK, b.getWarehouseId(), b.getProductId(), b.getStockStatus(), "", null, "", null,
                    b.getOnHand()));
        }
        Map<String, Part> flows = new LinkedHashMap<>();
        for (InvMoveDO m : moveMapper.selectList(new LambdaQueryWrapper<InvMoveDO>()
                .eq(InvMoveDO::getTenantId, PiStore.tenantId())
                .eq(InvMoveDO::getBatchId, batchId)
                .in(InvMoveDO::getOpType, List.of(InvConstants.OP_IN, InvConstants.OP_OUT))
                .notIn(InvMoveDO::getBizType, NOT_OUTFLOW)
                .orderByAsc(InvMoveDO::getId))) {
            boolean shipped = SHIPPED.contains(m.getBizType());
            Long soItem = shipped && Objects.equals(m.getTargetType(), InvConstants.TARGET_SO_ITEM) ? m.getTargetId() : null;
            String key = (shipped ? "S" : "O") + "|" + m.getDocType() + "|" + m.getDocId() + "|" + soItem + "|" + m.getProductId();
            Part prev = flows.get(key);
            int qty = (prev == null ? 0 : prev.qty()) - m.getQuantity();
            flows.put(key, new Part(shipped ? InvConstants.PART_SHIPPED : InvConstants.PART_OTHER, null, m.getProductId(),
                    m.getStockStatus(), m.getDocType(), m.getDocId(), m.getDocNo(), soItem, qty));
        }
        flows.values().stream().filter(p -> p.qty() > 0).forEach(out::add);
        return out;
    }

    private String model(Long productId) {
        ProductDO p = productId == null ? null : productMapper.selectById(productId);
        return p == null ? "" : Objects.requireNonNullElse(p.getMpnDisplay(), Objects.requireNonNullElse(p.getMpnRaw(), ""));
    }

    private static BigDecimal sum(List<InvCostAdjustItemDO> items, int part) {
        return items.stream().filter(i -> i.getPart() == part).map(InvCostAdjustItemDO::getAmountCny)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal s6(BigDecimal v) {
        return v.setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

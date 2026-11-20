package com.zhul.erp.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvBalanceDO;
import com.zhul.erp.modules.inventory.repository.InvBalanceMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.entity.InvMoveDO;
import com.zhul.erp.modules.inventory.entity.InvReservationDO;
import com.zhul.erp.modules.inventory.entity.InvWarehouseDO;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Doc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Line;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Loc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Target;
import com.zhul.erp.modules.inventory.service.InventoryWarehouses.Price;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.system.service.DictItemService;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptItemDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 现有单据的自动过账（openspec inventory-foundation 决策 3）：业务服务在自己的事务里调用，不另开事务。
 * 明细没有关联商品（product_id 为空，历史数据）的行不过账，记 WARN 日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryHooks {

    static final String DICT_CONDITION = "inquiry_item_condition";

    private final InventoryPosting posting;
    private final InventoryWarehouses warehouses;
    private final InventoryReservations reservations;
    private final SalesOrderItemMapper soItemMapper;
    private final DictItemService dictItemService;
    private final InvBalanceMapper balanceMapper;

    // ---------------------------------------------------------------- 采购入库

    /**
     * 采购入库验收、直接收货、直发货代确认：每行一个批次（入库单号-行序号）；合格入合格、不良入不良；
     * 合格数按来源型号行还需要的数量预占，超出的不预占。直发货代确认时入该货代仓。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void receiptAccepted(PurchaseReceiptDO r, List<PurchaseReceiptItemDO> items, PurchaseOrderDO po,
                                Map<Long, PurchaseOrderItemDO> poItems, Long directForwarderId) {
        InvWarehouseDO wh = directForwarderId == null ? warehouses.own() : warehouses.forwarder(directForwarderId);
        Map<Long, Integer> need = new HashMap<>(reservations.need(items.stream().map(PurchaseReceiptItemDO::getSoItemId).toList()));
        Map<Long, String> grades = grades(items.stream().map(PurchaseReceiptItemDO::getSoItemId).toList());
        Doc doc = Doc.of(InvConstants.BIZ_PURCHASE_IN, "GR", r.getId(), r.getGrNo());
        List<Line> lines = new ArrayList<>();
        int seq = 0;
        for (PurchaseReceiptItemDO i : items) {
            seq++;
            int good = nz(i.getQualifiedQty());
            int bad = nz(i.getDefectiveQty());
            if (good + bad == 0) {
                continue;
            }
            if (i.getProductId() == null) {
                log.warn("采购入库行没有关联商品，不过账：grNo={}, itemId={}, model={}", r.getGrNo(), i.getId(), i.getModel());
                continue;
            }
            PurchaseOrderItemDO pi = poItems.get(i.getPoItemId());
            InvBatchDO b = warehouses.newBatch(r.getGrNo() + "-" + seq, i.getProductId(), grades.get(i.getSoItemId()),
                    InvConstants.BATCH_PURCHASE, r.getId(), i.getId(), i.getPoItemId(), i.getSoItemId(), r.getReceivedDate(),
                    price(po, pi));
            Doc d = doc.item(i.getId());
            if (good > 0) {
                Loc loc = new Loc(wh.getId(), i.getProductId(), b.getId(), InvConstants.GOOD);
                int reserve = i.getSoItemId() == null ? 0 : Math.min(good, need.getOrDefault(i.getSoItemId(), 0));
                if (reserve > 0) {
                    lines.add(Line.of(loc, InvConstants.OP_IN, reserve, Target.soItem(i.getSoItemId()), d));
                    need.merge(i.getSoItemId(), -reserve, Integer::sum);
                }
                if (good > reserve) {
                    lines.add(Line.of(loc, InvConstants.OP_IN, good - reserve, null, d));
                }
            }
            if (bad > 0) {
                lines.add(Line.of(new Loc(wh.getId(), i.getProductId(), b.getId(), InvConstants.DEFECTIVE), InvConstants.OP_IN, bad, null, d));
            }
        }
        posting.post(lines);
    }

    /** 冲销采购入库：按原流水反向（原规则已限制被出库单用到的不能冲销） */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void receiptReversed(PurchaseReceiptDO r) {
        List<InvMoveDO> moves = posting.activeMoves("GR", r.getId(), List.of(InvConstants.BIZ_PURCHASE_IN));
        posting.reverse(moves, InvConstants.BIZ_PURCHASE_IN_REVERSE);
    }

    // ---------------------------------------------------------------- 到货差异

    /**
     * 到货差异 · 折价接收：入库单行批次的不良转为合格（转出、转入各一笔），转入的合格按来源型号行需要的数量预占。
     * 来源单据记为到货差异（RD），单号显示采购入库单号。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void discountAccepted(Long discrepancyId, String grNo, Long receiptItemId, Long productId, Long soItemId, int qty) {
        InvBatchDO b = warehouses.batchOf(InvConstants.BATCH_PURCHASE, receiptItemId);
        if (b == null || productId == null || qty <= 0) {
            log.warn("折价接收没有找到入库批次，不过账：discrepancyId={}, receiptItemId={}", discrepancyId, receiptItemId);
            return;
        }
        Long wh = warehouseHolding(b.getId(), InvConstants.DEFECTIVE, qty);
        Doc d = Doc.of(InvConstants.BIZ_DISCOUNT_ACCEPT, "RD", discrepancyId, grNo).item(receiptItemId);
        Loc bad = new Loc(wh, b.getProductId(), b.getId(), InvConstants.DEFECTIVE);
        Loc good = new Loc(wh, b.getProductId(), b.getId(), InvConstants.GOOD);
        int reserve = soItemId == null ? 0 : Math.min(qty, reservations.need(List.of(soItemId)).getOrDefault(soItemId, 0));
        List<Line> lines = new ArrayList<>();
        lines.add(Line.of(bad, InvConstants.OP_CONVERT_OUT, qty, null, d));
        if (reserve > 0) {
            lines.add(Line.of(good, InvConstants.OP_CONVERT_IN, reserve, Target.soItem(soItemId), d));
        }
        if (qty > reserve) {
            lines.add(Line.of(good, InvConstants.OP_CONVERT_IN, qty - reserve, null, d));
        }
        posting.post(lines);
    }

    /** 重新打开折价接收的差异：反向转换 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void discountReopened(Long discrepancyId) {
        posting.reverse(posting.activeMoves("RD", discrepancyId, List.of(InvConstants.BIZ_DISCOUNT_ACCEPT)),
                InvConstants.BIZ_DISCOUNT_ACCEPT_UNDO);
    }

    // ---------------------------------------------------------------- 采购退货

    /**
     * 采购退货单行的退货预占：从某批次没有预占的结存里按 statusOrder 依次取，最多 qty。
     * strict 时取不够就报库存不足（库存退回）；否则取到多少算多少并记 WARN（历史数据没有入库流水时不拦业务）。
     * 返回实际预占的数量。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public int reserveForReturn(Long returnItemId, Long batchId, List<Integer> statusOrder, Long warehouseId, int qty, Doc doc,
                                boolean strict) {
        if (batchId == null || qty <= 0) {
            if (qty > 0) {
                log.warn("采购退货行没有来源批次，不预占：returnItemId={}, docNo={}", returnItemId, doc.docNo());
            }
            return 0;
        }
        List<Line> lines = new ArrayList<>();
        int left = qty;
        for (int status : statusOrder) {
            for (InvBalanceDO b : freeBalances(batchId, status, warehouseId)) {
                if (left == 0) {
                    break;
                }
                int n = Math.min(left, b.getOnHand() - b.getReserved());
                lines.add(Line.of(new Loc(b.getWarehouseId(), b.getProductId(), batchId, status), InvConstants.OP_RESERVE, n,
                        Target.returnItem(returnItemId), doc));
                left -= n;
            }
        }
        if (left > 0) {
            if (strict) {
                InvBatchDO b = warehouses.batchOf(batchId);
                throw new com.zhul.erp.common.exception.BizException("库存不足：批次 " + (b == null ? batchId : b.getBatchNo())
                        + " 没有预占的只有 " + (qty - left) + "，要退 " + qty);
            }
            log.warn("采购退货行可预占的库存不足，只预占 {}/{}：returnItemId={}, docNo={}", qty - left, qty, returnItemId, doc.docNo());
        }
        posting.post(lines);
        return qty - left;
    }

    /** 采购入库单行的批次 ID（没有为 null） */
    public Long purchaseBatchOf(Long receiptItemId) {
        InvBatchDO b = receiptItemId == null ? null : warehouses.batchOf(InvConstants.BATCH_PURCHASE, receiptItemId);
        return b == null ? null : b.getId();
    }

    /** 退货入库单行的批次 ID（没有为 null） */
    public Long returnBatchOf(Long returnReceiptItemId) {
        InvBatchDO b = returnReceiptItemId == null ? null : warehouses.batchOf(InvConstants.BATCH_RETURN, returnReceiptItemId);
        return b == null ? null : b.getId();
    }

    /** 释放采购退货单行的退货预占，最多 qty（作废、减少数量、差异重新打开） */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void releaseReturn(Long returnItemId, int qty, Doc doc) {
        List<Line> lines = new ArrayList<>();
        int left = qty;
        List<InvReservationDO> rs = new ArrayList<>(posting.reservations(Target.returnItem(returnItemId)));
        rs.sort(Comparator.comparing(InvReservationDO::getBatchId, Comparator.reverseOrder()));
        for (InvReservationDO r : rs) {
            if (left <= 0) {
                break;
            }
            int n = Math.min(left, r.getQuantity());
            lines.add(Line.of(new Loc(r.getWarehouseId(), r.getProductId(), r.getBatchId(), r.getStockStatus()), InvConstants.OP_RELEASE, n,
                    Target.returnItem(returnItemId), doc));
            left -= n;
        }
        posting.post(lines);
    }

    /** 采购退货寄出：按各行的退货预占出库（采购退货出库）；寄出后不能作废，没有反向 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void returnShipped(Long purchaseReturnId, String returnNo, List<Long> returnItemIds) {
        Doc doc = Doc.of(InvConstants.BIZ_PURCHASE_RETURN_OUT, "PR", purchaseReturnId, returnNo);
        List<Line> lines = new ArrayList<>();
        for (Long itemId : returnItemIds) {
            for (InvReservationDO r : posting.reservations(Target.returnItem(itemId))) {
                lines.add(Line.of(new Loc(r.getWarehouseId(), r.getProductId(), r.getBatchId(), r.getStockStatus()), InvConstants.OP_OUT,
                        r.getQuantity(), Target.returnItem(itemId), doc.item(itemId)));
            }
        }
        posting.post(lines);
    }

    // ---------------------------------------------------------------- 工具

    /** 某批次某状态有没预占结存的余额行（指定仓库时只看该仓库；否则自有仓优先） */
    List<InvBalanceDO> freeBalances(Long batchId, int status, Long warehouseId) {
        Long own = warehouses.own().getId();
        return balanceMapper.selectList(new LambdaQueryWrapper<InvBalanceDO>()
                        .eq(InvBalanceDO::getTenantId, PiStore.tenantId())
                        .eq(InvBalanceDO::getBatchId, batchId)
                        .eq(InvBalanceDO::getStockStatus, status)
                        .eq(warehouseId != null, InvBalanceDO::getWarehouseId, warehouseId)
                        .apply("on_hand > reserved"))
                .stream()
                .sorted(Comparator.<InvBalanceDO>comparingInt(r -> Objects.equals(r.getWarehouseId(), own) ? 0 : 1)
                        .thenComparing(InvBalanceDO::getWarehouseId))
                .toList();
    }

    /** 某批次某状态没有预占的结存够 qty 的仓库（优先自有仓）；都不够时取结存最多的，过账时再报库存不足 */
    Long warehouseHolding(Long batchId, int status, int qty) {
        List<InvBalanceDO> rows = balanceMapper.selectList(new LambdaQueryWrapper<InvBalanceDO>()
                .eq(InvBalanceDO::getTenantId, PiStore.tenantId())
                .eq(InvBalanceDO::getBatchId, batchId)
                .eq(InvBalanceDO::getStockStatus, status)
                .gt(InvBalanceDO::getOnHand, 0));
        Long own = warehouses.own().getId();
        return rows.stream()
                .sorted(Comparator.<InvBalanceDO>comparingInt(r -> r.getOnHand() - r.getReserved() >= qty ? 0 : 1)
                        .thenComparingInt(r -> Objects.equals(r.getWarehouseId(), own) ? 0 : 1)
                        .thenComparing(InvBalanceDO::getOnHand, Comparator.reverseOrder()))
                .map(InvBalanceDO::getWarehouseId).findFirst().orElse(own);
    }


    /** 采购入库批次单价：采购订单币种与汇率，人民币取采购订单行的人民币不含税单价 */
    static Price price(PurchaseOrderDO po, PurchaseOrderItemDO pi) {
        if (pi == null) {
            return Price.ZERO;
        }
        String currency = po == null || po.getCurrencyCode() == null ? "CNY" : po.getCurrencyCode();
        return new Price(currency, pi.getUnitPrice(), po == null ? null : po.getExchangeRate(), pi.getNetPriceCny());
    }

    /** 型号行的成色（货况名称） */
    Map<Long, String> grades(List<Long> soItemIds) {
        List<Long> ids = soItemIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        Map<Integer, String> names = dictItemService.intLabels(DICT_CONDITION);
        for (SalesOrderItemDO i : soItemMapper.selectBatchIds(ids)) {
            out.put(i.getId(), i.getItemCondition() == null ? "" : names.getOrDefault(i.getItemCondition(), ""));
        }
        return out;
    }

    static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}

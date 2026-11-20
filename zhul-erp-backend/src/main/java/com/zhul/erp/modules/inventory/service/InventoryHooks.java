package com.zhul.erp.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
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
    private final com.zhul.erp.modules.inventory.repository.InvMoveMapper moveMapper;
    private final OtherOutboundService otherOutbounds;

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

    // ---------------------------------------------------------------- 交货代

    /** 交货代的一行：销售出库单行 */
    public record HandoverLine(Long obItemId, Long soItemId, Long productId, String model, int qty) {
    }

    /**
     * 交国内快递：自有仓 → 该货代仓调拨。每行依次取：预占给这个型号行的合格批次（先进先出）；
     * 同一商品没有预占的合格批次（先进先出，调入货代仓时预占给这个型号行）；还不够时，无货源模式补一笔
     * 「系统外入库」（批次 SYS-出库单号-序号，单价 0），库存模式报「库存不足」。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void handedOver(Long outboundId, String obNo, Long forwarderId, List<HandoverLine> items) {
        Long own = warehouses.own().getId();
        Long fwd = warehouses.forwarder(forwarderId).getId();
        boolean dropship = warehouses.dropship();
        Doc doc = Doc.of(InvConstants.BIZ_HANDOVER, "OB", outboundId, obNo);
        List<Line> lines = new ArrayList<>();
        List<String> shortages = new ArrayList<>();
        Map<Long, Integer> usedFree = new HashMap<>();
        int sysSeq = 0;
        for (HandoverLine it : items) {
            if (it.qty() <= 0) {
                continue;
            }
            if (it.productId() == null) {
                log.warn("销售出库单行没有关联商品，不过账：obNo={}, itemId={}, model={}", obNo, it.obItemId(), it.model());
                continue;
            }
            Doc d = doc.item(it.obItemId());
            Target t = Target.soItem(it.soItemId());
            int left = it.qty();
            // 1) 预占给这个型号行的
            if (t != null) {
                for (InvReservationDO r : posting.reservations(t)) {
                    if (left == 0) {
                        break;
                    }
                    if (!Objects.equals(r.getWarehouseId(), own) || r.getStockStatus() != InvConstants.GOOD) {
                        continue;
                    }
                    int n = Math.min(left, r.getQuantity());
                    transfer(lines, own, fwd, r.getProductId(), r.getBatchId(), n, t, t, d);
                    left -= n;
                }
            }
            // 2) 同一商品没有预占的
            if (left > 0) {
                for (InvBalanceDO b : freeOfProduct(own, it.productId())) {
                    if (left == 0) {
                        break;
                    }
                    int free = b.getOnHand() - b.getReserved() - usedFree.getOrDefault(b.getId(), 0);
                    if (free <= 0) {
                        continue;
                    }
                    int n = Math.min(left, free);
                    usedFree.merge(b.getId(), n, Integer::sum);
                    transfer(lines, own, fwd, b.getProductId(), b.getBatchId(), n, null, t, d);
                    left -= n;
                }
            }
            // 3) 不够
            if (left > 0) {
                if (!dropship) {
                    shortages.add(it.model() + " × 缺 " + left);
                    continue;
                }
                sysSeq++;
                InvBatchDO sys = warehouses.newBatch("SYS-" + obNo + "-" + sysSeq, it.productId(), "", InvConstants.BATCH_SYSTEM,
                        outboundId, it.obItemId(), null, it.soItemId(), java.time.LocalDate.now(), Price.ZERO);
                Loc in = new Loc(own, it.productId(), sys.getId(), InvConstants.GOOD);
                lines.add(Line.of(in, InvConstants.OP_IN, left, null, d.biz(InvConstants.BIZ_SYSTEM_IN)));
                transfer(lines, own, fwd, it.productId(), sys.getId(), left, null, t, d);
            }
        }
        if (!shortages.isEmpty()) {
            throw new com.zhul.erp.common.exception.BizException("库存不足：" + String.join("、", shortages)
                    + "。库存模式下不能交货代，请先完成采购入库");
        }
        posting.post(lines);
    }

    private static void transfer(List<Line> lines, Long from, Long to, Long productId, Long batchId, int n, Target fromTarget,
                                 Target toTarget, Doc d) {
        lines.add(Line.of(new Loc(from, productId, batchId, InvConstants.GOOD), InvConstants.OP_TRANSFER_OUT, n, fromTarget, d));
        lines.add(Line.of(new Loc(to, productId, batchId, InvConstants.GOOD), InvConstants.OP_TRANSFER_IN, n, toTarget, d));
    }

    /** 撤销交货代：按原调拨流水反向（系统外入库保留在自有仓，成为可用库存），再释放超出订单需要的预占 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void handoverUndone(Long outboundId, String obNo, List<Long> soItemIds) {
        posting.reverse(posting.activeMoves("OB", outboundId, List.of(InvConstants.BIZ_HANDOVER)), InvConstants.BIZ_HANDOVER_UNDO);
        reservations.trim(soItemIds, Doc.of(InvConstants.BIZ_RELEASE, "OB", outboundId, obNo), warehouses.own().getId());
    }

    /**
     * 登记出运：从货代仓销售出库。每行先出预占给该型号行的，再出同一商品没有预占的；还不够时无货源模式补系统外入库
     * （上线前已交货代、期初没推出来的货），库存模式报库存不足。出运后不能撤销，没有反向。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void shipped(Long shipmentId, String shNo, Long forwarderId, List<HandoverLine> items) {
        Long fwd = warehouses.forwarder(forwarderId).getId();
        boolean dropship = warehouses.dropship();
        Doc doc = Doc.of(InvConstants.BIZ_SALES_OUT, "SH", shipmentId, shNo);
        List<Line> lines = new ArrayList<>();
        List<String> shortages = new ArrayList<>();
        Map<Long, Integer> usedFree = new HashMap<>();
        Map<Long, Integer> usedReserved = new HashMap<>();
        int sysSeq = 0;
        for (HandoverLine it : items) {
            if (it.qty() <= 0) {
                continue;
            }
            if (it.productId() == null) {
                log.warn("出运的销售出库单行没有关联商品，不过账：shNo={}, itemId={}, model={}", shNo, it.obItemId(), it.model());
                continue;
            }
            Doc d = doc.item(it.obItemId());
            Target t = Target.soItem(it.soItemId());
            int left = it.qty();
            if (t != null) {
                for (InvReservationDO r : posting.reservations(t)) {
                    if (left == 0) {
                        break;
                    }
                    int avail = r.getQuantity() - usedReserved.getOrDefault(r.getId(), 0);
                    if (!Objects.equals(r.getWarehouseId(), fwd) || r.getStockStatus() != InvConstants.GOOD || avail <= 0) {
                        continue;
                    }
                    int n = Math.min(left, avail);
                    usedReserved.merge(r.getId(), n, Integer::sum);
                    lines.add(Line.of(new Loc(fwd, r.getProductId(), r.getBatchId(), InvConstants.GOOD), InvConstants.OP_OUT, n, t, d));
                    left -= n;
                }
            }
            if (left > 0) {
                for (InvBalanceDO b : freeOfProduct(fwd, it.productId())) {
                    if (left == 0) {
                        break;
                    }
                    int free = b.getOnHand() - b.getReserved() - usedFree.getOrDefault(b.getId(), 0);
                    if (free <= 0) {
                        continue;
                    }
                    int n = Math.min(left, free);
                    usedFree.merge(b.getId(), n, Integer::sum);
                    // 出没有预占的货也记到这个型号行上（已销售出库数按型号行统计），但不减预占
                    lines.add(Line.of(new Loc(fwd, b.getProductId(), b.getBatchId(), InvConstants.GOOD), InvConstants.OP_RESERVE, n, t, d));
                    lines.add(Line.of(new Loc(fwd, b.getProductId(), b.getBatchId(), InvConstants.GOOD), InvConstants.OP_OUT, n, t, d));
                    left -= n;
                }
            }
            if (left > 0) {
                if (!dropship) {
                    shortages.add(it.model() + " × 缺 " + left);
                    continue;
                }
                sysSeq++;
                InvBatchDO sys = warehouses.newBatch("SYS-" + shNo + "-" + sysSeq, it.productId(), "", InvConstants.BATCH_SYSTEM,
                        shipmentId, it.obItemId(), null, it.soItemId(), java.time.LocalDate.now(), Price.ZERO);
                Loc loc = new Loc(fwd, it.productId(), sys.getId(), InvConstants.GOOD);
                lines.add(Line.of(loc, InvConstants.OP_IN, left, t, d.biz(InvConstants.BIZ_SYSTEM_IN)));
                lines.add(Line.of(loc, InvConstants.OP_OUT, left, t, d));
            }
        }
        if (!shortages.isEmpty()) {
            throw new com.zhul.erp.common.exception.BizException("库存不足：" + String.join("、", shortages)
                    + "。库存模式下不能出运，请先核对货代仓的库存");
        }
        posting.post(lines);
    }

    /** 某仓库某商品没有预占的合格余额，按批次先进先出 */
    List<InvBalanceDO> freeOfProduct(Long warehouseId, Long productId) {
        return balanceMapper.selectList(new LambdaQueryWrapper<InvBalanceDO>()
                .eq(InvBalanceDO::getTenantId, PiStore.tenantId())
                .eq(InvBalanceDO::getWarehouseId, warehouseId)
                .eq(InvBalanceDO::getProductId, productId)
                .eq(InvBalanceDO::getStockStatus, InvConstants.GOOD)
                .apply("on_hand > reserved")
                .orderByAsc(InvBalanceDO::getBatchId));
    }

    // ---------------------------------------------------------------- 订单取消

    /**
     * 订单取消、删除、部分取消后：型号行超出新需要数量的预占释放（先公司仓库，同仓从最新批次往前），货成为可用库存。
     * action 为「订单取消」「订单部分取消」等，写进流水的业务类型。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public int orderReduced(Long soId, String soNo, java.util.Collection<Long> soItemIds, String action) {
        return reservations.trim(soItemIds, Doc.of(InvConstants.BIZ_RELEASE, "SO", soId, soNo).label(action + " · " + soNo),
                warehouses.own().getId());
    }

    // ---------------------------------------------------------------- 退货入库

    /** 退货入库单行：实收、合格；fallbackCny 为原订单没有销售出库流水时用的单价（原采购成本） */
    public record ReturnLine(Long itemId, Long soItemId, Long productId, String model, int received, int qualified, BigDecimal fallbackCny) {
    }

    /** 退货入库验收：自有仓「退货入库」，每行一个批次（退货入库单号-行序号），合格、不良分开，不预占 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void returnReceived(Long receiptId, String rrNo, java.time.LocalDate date, List<ReturnLine> items) {
        Long own = warehouses.own().getId();
        Doc doc = Doc.of(InvConstants.BIZ_RETURN_IN, "RR", receiptId, rrNo);
        Map<Long, String> grades = grades(items.stream().map(ReturnLine::soItemId).toList());
        Map<Long, BigDecimal> soldPrice = soldUnitPrice(items.stream().map(ReturnLine::soItemId).toList());
        List<Line> lines = new ArrayList<>();
        int seq = 0;
        for (ReturnLine it : items) {
            seq++;
            if (it.received() <= 0) {
                continue;
            }
            if (it.productId() == null) {
                log.warn("退货入库单行没有关联商品，不过账：rrNo={}, itemId={}, model={}", rrNo, it.itemId(), it.model());
                continue;
            }
            BigDecimal unit = soldPrice.get(it.soItemId());
            if (unit == null) {
                unit = it.fallbackCny() == null ? BigDecimal.ZERO : it.fallbackCny();
            }
            // 撤回后再次验收沿用原批次（批次号不变），更新单价与入库日期
            InvBatchDO b = warehouses.batchOf(InvConstants.BATCH_RETURN, it.itemId());
            if (b == null) {
                b = warehouses.newBatch(rrNo + "-" + seq, it.productId(), grades.getOrDefault(it.soItemId(), ""),
                        InvConstants.BATCH_RETURN, receiptId, it.itemId(), null, it.soItemId(), date, Price.cny(unit));
            } else {
                warehouses.reprice(b, date, Price.cny(unit));
            }
            Doc d = doc.item(it.itemId());
            if (it.qualified() > 0) {
                lines.add(Line.of(new Loc(own, it.productId(), b.getId(), InvConstants.GOOD), InvConstants.OP_IN, it.qualified(), null, d));
            }
            int bad = it.received() - it.qualified();
            if (bad > 0) {
                lines.add(Line.of(new Loc(own, it.productId(), b.getId(), InvConstants.DEFECTIVE), InvConstants.OP_IN, bad, null, d));
            }
        }
        posting.post(lines);
    }

    /** 撤回退货入库：按原流水反向（批次保留，再次验收时沿用） */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void returnUnreceived(Long receiptId) {
        posting.reverse(posting.activeMoves("RR", receiptId, List.of(InvConstants.BIZ_RETURN_IN)), InvConstants.BIZ_RETURN_IN_UNDO);
    }

    /** 退货去向报废：从该行退货批次没有预占的结存出库（不良优先），生成一张报废的其他出库单 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public String returnScrapped(Long receiptId, String rrNo, Long receiptItemId, Long dispositionId, int qty, String reason) {
        Long batchId = returnBatchOf(receiptItemId);
        if (batchId == null || qty <= 0) {
            log.warn("退货入库单行没有批次，报废不出库：rrNo={}, itemId={}", rrNo, receiptItemId);
            return null;
        }
        Long own = warehouses.own().getId();
        List<OtherOutboundService.Pick> picks = new ArrayList<>();
        int left = qty;
        for (int status : List.of(InvConstants.DEFECTIVE, InvConstants.GOOD)) {
            for (InvBalanceDO b : freeBalances(batchId, status, own)) {
                if (left == 0) {
                    break;
                }
                int n = Math.min(left, b.getOnHand() - b.getReserved());
                picks.add(new OtherOutboundService.Pick(b.getProductId(), batchId, status, n));
                left -= n;
            }
        }
        if (left > 0) {
            throw new com.zhul.erp.common.exception.BizException("库存不足：退货批次没有预占的只有 " + (qty - left) + "，要报废 " + qty);
        }
        return otherOutbounds.create(InvConstants.OO_SCRAP, own, java.time.LocalDate.now(), reason, picks,
                new OtherOutboundService.Source("RR", receiptId, rrNo, dispositionId)).getOutboundNo();
    }

    /** 型号行销售出库的加权人民币单价（HALF_UP 6 位）；没有销售出库流水的不在结果里 */
    Map<Long, BigDecimal> soldUnitPrice(List<Long> soItemIds) {
        List<Long> ids = soItemIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, BigDecimal[]> sum = new HashMap<>();
        if (!ids.isEmpty()) {
            moveMapper.selectList(new LambdaQueryWrapper<InvMoveDO>()
                            .select(InvMoveDO::getTargetId, InvMoveDO::getQuantity, InvMoveDO::getAmountCny)
                            .eq(InvMoveDO::getTenantId, PiStore.tenantId())
                            .eq(InvMoveDO::getTargetType, InvConstants.TARGET_SO_ITEM)
                            .in(InvMoveDO::getTargetId, ids)
                            .in(InvMoveDO::getBizType, List.of(InvConstants.BIZ_SALES_OUT, InvConstants.BIZ_SALES_OUT_UNDO))
                            .in(InvMoveDO::getOpType, List.of(InvConstants.OP_OUT, InvConstants.OP_IN)))
                    .forEach(m -> sum.merge(m.getTargetId(), new BigDecimal[]{BigDecimal.valueOf(m.getQuantity()), m.getAmountCny()},
                            (x, y) -> new BigDecimal[]{x[0].add(y[0]), x[1].add(y[1])}));
        }
        Map<Long, BigDecimal> out = new HashMap<>();
        sum.forEach((k, v) -> {
            if (v[0].signum() != 0) {
                out.put(k, v[1].divide(v[0], 6, java.math.RoundingMode.HALF_UP));
            }
        });
        return out;
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

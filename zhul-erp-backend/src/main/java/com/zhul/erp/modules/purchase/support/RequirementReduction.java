package com.zhul.erp.modules.purchase.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.logistics.support.LogisticsQty;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseRequirementMapper;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.warehouse.support.PartialCancelHolds;
import com.zhul.erp.modules.warehouse.support.ReceivingQty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 订单型号部分取消后扣减采购需求（spec sales/sales-order「部分取消后的采购需求」），依次：
 * 还没排进采购单的数量 → 草稿采购订单撤出 → 已在仓（合格入库、还没出库）转闲置库存 → 已下单没到货的数量
 * （采购订单行累计「来源订单减少」，写采购日志，由采购员决定取消或保留）。
 * 没有采购需求的型号（系统外采购）不处理。preview 只计算不写库，供确认框展示；apply 在调用方事务内执行。
 */
@Component
@RequiredArgsConstructor
public class RequirementReduction {

    private final PurchaseRequirementMapper requirementMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final RequirementQty qty;
    private final PurchaseDrafts drafts;
    private final PurchaseLogs logs;
    private final ReceivingQty receivingQty;
    private final LogisticsQty logisticsQty;
    private final PartialCancelHolds holds;

    /** 采购订单上被减少的数量 */
    public record PoQty(Long poId, String poNo, int quantity) {
    }

    /**
     * 扣减结果：tracked 为是否有采购需求；unplaced 未排单、drafts 草稿撤出（按采购订单）、held 转闲置库存、
     * ordered 已下单的减少（按采购订单）；left 为没有对应采购的部分（只改订单）
     */
    public record Result(boolean tracked, int unplaced, List<PoQty> drafts, int held, List<PoQty> ordered, int left) {

        /** 采购与库存处理结果摘要，写进部分取消记录 */
        public String summary() {
            if (!tracked) {
                return "系统外采购，只改订单";
            }
            List<String> parts = new ArrayList<>();
            if (unplaced > 0) {
                parts.add("关闭未排单的需求 " + unplaced + " 个");
            }
            drafts.forEach(p -> parts.add("草稿采购单 " + p.poNo() + " 撤出 " + p.quantity() + " 个"));
            if (held > 0) {
                parts.add("在仓 " + held + " 个转闲置库存");
            }
            ordered.forEach(p -> parts.add("采购订单 " + p.poNo() + " 来源订单减少 " + p.quantity() + " 个"));
            if (left > 0) {
                parts.add("另有 " + left + " 个没有对应采购");
            }
            return parts.isEmpty() ? "无需处理采购" : String.join("；", parts);
        }
    }

    public Result preview(SalesOrderItemDO item, int n) {
        return run(null, item, n, null, false);
    }

    public Result apply(SalesOrderDO o, SalesOrderItemDO item, int n, Long operatorId) {
        return run(o, item, n, operatorId, true);
    }

    private Result run(SalesOrderDO o, SalesOrderItemDO item, int n, Long operatorId, boolean write) {
        List<PurchaseRequirementDO> reqs = requirementMapper.selectList(new LambdaQueryWrapper<PurchaseRequirementDO>()
                .eq(PurchaseRequirementDO::getSoItemId, item.getId())
                .eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                .isNull(PurchaseRequirementDO::getDeletedAt)
                .orderByAsc(PurchaseRequirementDO::getId));
        if (reqs.isEmpty()) {
            return new Result(false, 0, List.of(), 0, List.of(), n);
        }
        if (write) {
            requirementMapper.lockByIds(reqs.stream().map(PurchaseRequirementDO::getId).toList());
        }
        Map<Long, RequirementQty.Qty> q = qty.byRequirement(reqs.stream().map(PurchaseRequirementDO::getId).toList());
        int left = n;
        int unplaced = 0;
        Map<Long, Integer> reduced = new LinkedHashMap<>();
        // 1. 还没排进采购单的数量
        for (PurchaseRequirementDO r : reqs) {
            int take = Math.min(left, RequirementQty.available(r, q.getOrDefault(r.getId(), RequirementQty.Qty.ZERO)));
            if (take > 0) {
                unplaced += take;
                left -= take;
                reduced.merge(r.getId(), take, Integer::sum);
            }
        }
        // 2. 草稿采购订单撤出
        Map<Long, Integer> draftByPo = new LinkedHashMap<>();
        Set<Long> touched = new LinkedHashSet<>();
        for (PurchaseRequirementDO r : reqs) {
            if (left <= 0) {
                break;
            }
            int fromDraft = 0;
            for (PurchaseOrderItemDO i : lines(r.getId(), PurchaseConstants.PO_DRAFT, write)) {
                int take = Math.min(left, i.getQuantity());
                if (take <= 0) {
                    break;
                }
                draftByPo.merge(i.getPoId(), take, Integer::sum);
                left -= take;
                fromDraft += take;
            }
            if (fromDraft > 0) {
                reduced.merge(r.getId(), fromDraft, Integer::sum);
                if (write) {
                    drafts.takeFromDrafts(r.getId(), fromDraft, touched);
                }
            }
        }
        // 3. 已在仓：合格入库（已扣除转闲置的）− 已出库（含出库中）
        int held = 0;
        List<PurchaseOrderItemDO> orderedLines = new ArrayList<>();
        reqs.forEach(r -> orderedLines.addAll(lines(r.getId(), PurchaseConstants.PO_ORDERED, write)));
        Map<Long, Integer> qualifiedByLine = receivingQty.qualifiedByPoItem(orderedLines.stream().map(PurchaseOrderItemDO::getId).toList());
        if (left > 0) {
            int inWarehouse = Math.max(0, receivingQty.qualifiedBySoItem(List.of(item.getId())).getOrDefault(item.getId(), 0)
                    - logisticsQty.bySoItem(List.of(item.getId())).getOrDefault(item.getId(), LogisticsQty.Qty.ZERO).occupied());
            held = Math.min(left, inWarehouse);
            left -= held;
            if (held > 0) {
                // 按合格入库最多的采购订单行取成本，扣到它的需求上
                PurchaseOrderItemDO src = orderedLines.stream()
                        .max(Comparator.comparingInt(l -> qualifiedByLine.getOrDefault(l.getId(), 0))).orElse(null);
                Long reqId = src == null ? reqs.get(0).getId() : src.getRequirementId();
                reduced.merge(reqId, held, Integer::sum);
                if (write) {
                    holds.atCancel(o, item, held, src);
                }
            }
        }
        // 4. 已下单还没到货的数量：采购订单行累计「来源订单减少」，后下单的先扣
        Map<Long, Integer> orderedByPo = new LinkedHashMap<>();
        if (left > 0) {
            List<PurchaseOrderItemDO> open = new ArrayList<>(orderedLines);
            open.sort(Comparator.comparing(PurchaseOrderItemDO::getId).reversed());
            for (PurchaseOrderItemDO l : open) {
                if (left <= 0) {
                    break;
                }
                int pending = l.getQuantity() - qualifiedByLine.getOrDefault(l.getId(), 0)
                        - Objects.requireNonNullElse(l.getSoReducedQuantity(), 0);
                int take = Math.min(left, Math.max(0, pending));
                if (take <= 0) {
                    continue;
                }
                orderedByPo.merge(l.getPoId(), take, Integer::sum);
                left -= take;
                reduced.merge(l.getRequirementId(), take, Integer::sum);
                if (write) {
                    l.setSoReducedQuantity(Objects.requireNonNullElse(l.getSoReducedQuantity(), 0) + take);
                    itemMapper.updateById(l);
                }
            }
        }
        Map<Long, String> poNos = poNos(draftByPo.keySet(), orderedByPo.keySet());
        if (write) {
            drafts.settle(touched, operatorId);
            for (PurchaseRequirementDO r : reqs) {
                int cut = reduced.getOrDefault(r.getId(), 0);
                if (cut <= 0) {
                    continue;
                }
                r.setQuantity(Math.max(0, r.getQuantity() - cut));
                if (r.getQuantity() == 0) {
                    r.setStatus(PurchaseConstants.REQ_CLOSED);
                }
                requirementMapper.updateById(r);
            }
            orderedByPo.forEach((poId, x) -> logs.add(poId, "来源订单部分取消",
                    "订单 " + o.getSoNo() + " 取消 " + item.getModel() + " × " + n + "，本单减少 " + x
                            + " 个：请和供应商确认取消采购，或保留——到货后超出订单需要的合格数量自动转入闲置库存", operatorId));
        }
        return new Result(true, unplaced, toList(draftByPo, poNos), held, toList(orderedByPo, poNos), left);
    }

    /** 需求在某状态采购订单上的行；lock 时锁住采购订单 */
    private List<PurchaseOrderItemDO> lines(Long requirementId, int status, boolean lock) {
        List<PurchaseOrderItemDO> rows = itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .eq(PurchaseOrderItemDO::getRequirementId, requirementId)
                .isNull(PurchaseOrderItemDO::getDeletedAt)
                .orderByDesc(PurchaseOrderItemDO::getId));
        List<PurchaseOrderItemDO> out = new ArrayList<>(rows.size());
        for (PurchaseOrderItemDO i : rows) {
            PurchaseOrderDO po = orderMapper.selectById(i.getPoId());
            if (po != null && po.getDeletedAt() == null && po.getStatus() == status) {
                if (lock) {
                    orderMapper.lockById(po.getId());
                }
                out.add(i);
            }
        }
        return out;
    }

    private Map<Long, String> poNos(Set<Long> a, Set<Long> b) {
        Set<Long> ids = new LinkedHashSet<>(a);
        ids.addAll(b);
        Map<Long, String> out = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            orderMapper.selectBatchIds(ids).forEach(p -> out.put(p.getId(), p.getPoNo()));
        }
        return out;
    }

    private static List<PoQty> toList(Map<Long, Integer> byPo, Map<Long, String> poNos) {
        return byPo.entrySet().stream().map(e -> new PoQty(e.getKey(), poNos.get(e.getKey()), e.getValue())).toList();
    }
}

package com.zhul.erp.modules.purchase.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseRequirementMapper;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import com.zhul.erp.modules.quotation.repository.QuotationItemMapper;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 采购需求的生命周期，由销售订单在自己的事务内调用：生成订单时生成需求（重转时接回已下单数量、自动排入草稿），
 * 取消订单时关闭或标记需求，改采购员时把草稿行移到新采购员名下。
 */
@Component
@RequiredArgsConstructor
public class RequirementLifecycle {

    private final PurchaseRequirementMapper requirementMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final QuotationItemMapper quotationItemMapper;
    private final SourcingQuoteMapper sourcingQuoteMapper;
    private final SupplierMapper supplierMapper;
    private final RequirementQty qty;
    private final PurchaseDrafts drafts;
    private final PurchaseLogs logs;
    private final OrderPurchaseProgress progress;

    // ---------------------------------------------------------------- 订单生成

    /** 每个型号行生成一条需求；同一 PI 重转时接回「订单已取消」需求的已下单数量；有采购员和启用的建议供应商的排入草稿 */
    public List<PurchaseRequirementDO> onOrderCreated(SalesOrderDO o, List<SalesOrderItemDO> items) {
        Map<Long, SourcingQuoteDO> quotes = costQuotes(items.stream().map(SalesOrderItemDO::getQuotationItemId).toList());
        List<PurchaseRequirementDO> reqs = new ArrayList<>(items.size());
        for (SalesOrderItemDO i : items) {
            PurchaseRequirementDO r = new PurchaseRequirementDO();
            r.setTenantId(o.getTenantId());
            r.setSoId(o.getId());
            r.setSoItemId(i.getId());
            r.setQuotationItemId(Objects.requireNonNullElse(i.getQuotationItemId(), 0L));
            r.setModel(i.getModel());
            r.setBrand(i.getBrand());
            r.setQuantity(i.getQuantity());
            r.setTargetPrice(i.getCostPrice());
            r.setPurchaserId(i.getPurchaserId());
            applySuggestion(r, quotes.get(r.getQuotationItemId()));
            r.setStatus(PurchaseConstants.REQ_ACTIVE);
            requirementMapper.insert(r);
            reqs.add(r);
        }
        reattach(o, reqs);
        placeAvailable(reqs, "由订单 " + o.getSoNo() + " 自动生成", null);
        progress.sync(items.stream().map(SalesOrderItemDO::getId).toList());
        return reqs;
    }

    /** 报价行 → 被选为采购成本价的回价 */
    private Map<Long, SourcingQuoteDO> costQuotes(Collection<Long> quotationItemIds) {
        List<Long> ids = quotationItemIds.stream().filter(x -> x != null && x > 0).distinct().toList();
        Map<Long, SourcingQuoteDO> result = new HashMap<>(ids.size() * 2 + 1);
        if (ids.isEmpty()) {
            return result;
        }
        Map<Long, Long> quoteOf = new HashMap<>(ids.size() * 2);
        quotationItemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                        .select(QuotationItemDO::getId, QuotationItemDO::getCostQuoteId)
                        .in(QuotationItemDO::getId, ids)
                        .isNotNull(QuotationItemDO::getCostQuoteId))
                .forEach(q -> quoteOf.put(q.getId(), q.getCostQuoteId()));
        if (quoteOf.isEmpty()) {
            return result;
        }
        Map<Long, SourcingQuoteDO> byId = new HashMap<>(quoteOf.size() * 2);
        sourcingQuoteMapper.selectBatchIds(new LinkedHashSet<>(quoteOf.values())).forEach(q -> byId.put(q.getId(), q));
        quoteOf.forEach((item, quote) -> {
            SourcingQuoteDO q = byId.get(quote);
            if (q != null) {
                result.put(item, q);
            }
        });
        return result;
    }

    private static void applySuggestion(PurchaseRequirementDO r, SourcingQuoteDO q) {
        r.setSuggestedChannel(0);
        r.setSuggestedShopName("");
        if (q == null) {
            return;
        }
        r.setSuggestedChannel(Objects.requireNonNullElse(q.getChannel(), 0));
        if (q.getSupplierId() != null && q.getSupplierId() > 0) {
            r.setSuggestedSupplierId(q.getSupplierId());
        } else if (q.getShopName() != null) {
            r.setSuggestedShopName(q.getShopName().trim());
        }
    }

    /** 同一 PI 重新转订单：按报价行接回「订单已取消」需求在已下单采购单上的数量 */
    private void reattach(SalesOrderDO o, List<PurchaseRequirementDO> reqs) {
        for (PurchaseRequirementDO r : reqs) {
            if (r.getQuotationItemId() == null || r.getQuotationItemId() <= 0) {
                continue;
            }
            List<PurchaseRequirementDO> olds = requirementMapper.selectList(new LambdaQueryWrapper<PurchaseRequirementDO>()
                    .eq(PurchaseRequirementDO::getTenantId, PiStore.tenantId())
                    .eq(PurchaseRequirementDO::getQuotationItemId, r.getQuotationItemId())
                    .eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ORDER_CANCELLED)
                    .isNull(PurchaseRequirementDO::getDeletedAt)
                    .orderByAsc(PurchaseRequirementDO::getId));
            if (olds.isEmpty()) {
                continue;
            }
            requirementMapper.lockByIds(olds.stream().map(PurchaseRequirementDO::getId).toList());
            int need = r.getQuantity();
            for (PurchaseRequirementDO old : olds) {
                if (need <= 0) {
                    break;
                }
                int moved = 0;
                for (PurchaseOrderItemDO i : orderedItems(old.getId())) {
                    if (need <= 0) {
                        break;
                    }
                    int take = Math.min(need, i.getQuantity());
                    if (take == i.getQuantity()) {
                        i.setRequirementId(r.getId());
                        i.setSoId(r.getSoId());
                        i.setSoItemId(r.getSoItemId());
                        itemMapper.updateById(i);
                    } else {
                        i.setQuantity(i.getQuantity() - take);
                        itemMapper.updateById(i);
                        PurchaseOrderItemDO x = copyOf(i);
                        x.setRequirementId(r.getId());
                        x.setSoId(r.getSoId());
                        x.setSoItemId(r.getSoItemId());
                        x.setQuantity(take);
                        itemMapper.insert(x);
                    }
                    PurchaseOrderDO po = orderMapper.selectById(i.getPoId());
                    drafts.recalc(po);
                    logs.add(po.getId(), "接回订单", i.getModel() + " × " + take + " 接回重新转出的订单 " + o.getSoNo(), null);
                    need -= take;
                    moved += take;
                }
                old.setQuantity(old.getQuantity() - moved);
                if (old.getQuantity() <= 0) {
                    old.setQuantity(0);
                    old.setStatus(PurchaseConstants.REQ_CLOSED);
                }
                requirementMapper.updateById(old);
            }
        }
    }

    /** 需求在已下单采购单上的行（先下单的先接回） */
    private List<PurchaseOrderItemDO> orderedItems(Long requirementId) {
        List<PurchaseOrderItemDO> rows = itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .eq(PurchaseOrderItemDO::getRequirementId, requirementId)
                .isNull(PurchaseOrderItemDO::getDeletedAt)
                .orderByAsc(PurchaseOrderItemDO::getId));
        List<PurchaseOrderItemDO> out = new ArrayList<>(rows.size());
        for (PurchaseOrderItemDO i : rows) {
            PurchaseOrderDO po = orderMapper.selectById(i.getPoId());
            if (po != null && po.getDeletedAt() == null && po.getStatus() == PurchaseConstants.PO_ORDERED) {
                orderMapper.lockById(po.getId());
                out.add(i);
            }
        }
        return out;
    }

    private static PurchaseOrderItemDO copyOf(PurchaseOrderItemDO i) {
        PurchaseOrderItemDO x = new PurchaseOrderItemDO();
        x.setTenantId(i.getTenantId());
        x.setPoId(i.getPoId());
        x.setModel(i.getModel());
        x.setBrand(i.getBrand());
        x.setUnitPrice(i.getUnitPrice());
        x.setTargetPrice(i.getTargetPrice());
        x.setAmount(BigDecimal.ZERO);
        x.setSortOrder(i.getSortOrder());
        return x;
    }

    /** 有采购员、建议供应商已关联且启用的需求，把可下单数量排入草稿 */
    public void placeAvailable(List<PurchaseRequirementDO> reqs, String note, Long operatorId) {
        Map<Long, SupplierDO> suppliers = activeSuppliers(reqs.stream().map(PurchaseRequirementDO::getSuggestedSupplierId).toList());
        Map<Long, RequirementQty.Qty> q = qty.byRequirement(reqs.stream().map(PurchaseRequirementDO::getId).toList());
        List<PurchaseDrafts.Placement> list = new ArrayList<>();
        for (PurchaseRequirementDO r : reqs) {
            if (r.getStatus() != PurchaseConstants.REQ_ACTIVE || r.getPurchaserId() == null
                    || r.getSuggestedSupplierId() == null || !suppliers.containsKey(r.getSuggestedSupplierId())) {
                continue;
            }
            int available = RequirementQty.available(r, q.getOrDefault(r.getId(), RequirementQty.Qty.ZERO));
            if (available > 0) {
                list.add(new PurchaseDrafts.Placement(r, r.getPurchaserId(), r.getSuggestedSupplierId(), available));
            }
        }
        drafts.place(list, null, note, operatorId);
    }

    private Map<Long, SupplierDO> activeSuppliers(Collection<Long> ids) {
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, SupplierDO> map = new HashMap<>(keys.size() * 2 + 1);
        if (!keys.isEmpty()) {
            supplierMapper.selectBatchIds(keys).stream()
                    .filter(s -> s.getDeletedAt() == null && Objects.equals(s.getStatus(), 1)
                            && Objects.equals(s.getTenantId(), PiStore.tenantId()))
                    .forEach(s -> map.put(s.getId(), s));
        }
        return map;
    }

    // ---------------------------------------------------------------- 订单取消

    /** 没下单的需求关闭、草稿行删除；有已下单数量的标「订单已取消」，数量收成已下单数量，交给采购员处理 */
    public void onOrderCancelled(SalesOrderDO o, Long operatorId) {
        List<PurchaseRequirementDO> reqs = requirementMapper.selectList(new LambdaQueryWrapper<PurchaseRequirementDO>()
                .eq(PurchaseRequirementDO::getSoId, o.getId())
                .eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                .isNull(PurchaseRequirementDO::getDeletedAt));
        if (reqs.isEmpty()) {
            return;
        }
        requirementMapper.lockByIds(reqs.stream().map(PurchaseRequirementDO::getId).toList());
        Map<Long, RequirementQty.Qty> q = qty.byRequirement(reqs.stream().map(PurchaseRequirementDO::getId).toList());
        Set<Long> touched = new LinkedHashSet<>();
        Set<Long> flagged = new LinkedHashSet<>();
        for (PurchaseRequirementDO r : reqs) {
            RequirementQty.Qty x = q.getOrDefault(r.getId(), RequirementQty.Qty.ZERO);
            if (x.draft() > 0) {
                drafts.takeFromDrafts(r.getId(), x.draft(), touched);
            }
            if (x.ordered() > 0) {
                r.setStatus(PurchaseConstants.REQ_ORDER_CANCELLED);
                r.setQuantity(x.ordered());
                orderedItems(r.getId()).forEach(i -> flagged.add(i.getPoId()));
            } else {
                r.setStatus(PurchaseConstants.REQ_CLOSED);
            }
            requirementMapper.updateById(r);
        }
        drafts.settle(touched, operatorId);
        for (Long poId : flagged) {
            logs.add(poId, "来源订单已取消", "订单 " + o.getSoNo() + " 已取消，请决定取消采购单或保留货物", operatorId);
        }
    }

    // ---------------------------------------------------------------- 改采购员

    /**
     * 改需求的采购员：草稿行移到新采购员对同一供应商的草稿（保留已填单价），
     * 没排入草稿的按建议供应商排入；新采购员为空时草稿行回到需求池。已下单的数量不动。
     */
    public void reassign(List<PurchaseRequirementDO> reqs, Long purchaserId, Long operatorId) {
        Set<Long> touched = new LinkedHashSet<>();
        List<PurchaseDrafts.Placement> moves = new ArrayList<>();
        for (PurchaseRequirementDO r : reqs) {
            for (PurchaseOrderItemDO i : draftItems(r.getId())) {
                i.setDeletedAt(LocalDateTime.now());
                itemMapper.updateById(i);
                touched.add(i.getPoId());
                if (purchaserId != null) {
                    PurchaseOrderDO po = orderMapper.selectById(i.getPoId());
                    moves.add(new PurchaseDrafts.Placement(r, purchaserId, po.getSupplierId(), i.getQuantity(), i.getUnitPrice()));
                }
            }
            r.setPurchaserId(purchaserId);
            requirementMapper.updateById(r);
        }
        drafts.settle(touched, operatorId);
        drafts.place(moves, null, "改采购员", operatorId);
        if (purchaserId != null) {
            placeAvailable(reqs, "改采购员", operatorId);
        }
    }

    /** 订单详情改型号的采购员：改这些型号还没全部下单的有效需求 */
    public void reassignSoItems(Collection<Long> soItemIds, Long purchaserId, Long operatorId) {
        List<PurchaseRequirementDO> reqs = soItemIds.isEmpty() ? List.of()
                : requirementMapper.selectList(new LambdaQueryWrapper<PurchaseRequirementDO>()
                        .in(PurchaseRequirementDO::getSoItemId, soItemIds)
                        .eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                        .isNull(PurchaseRequirementDO::getDeletedAt));
        if (reqs.isEmpty()) {
            return;
        }
        requirementMapper.lockByIds(reqs.stream().map(PurchaseRequirementDO::getId).toList());
        Map<Long, RequirementQty.Qty> q = qty.byRequirement(reqs.stream().map(PurchaseRequirementDO::getId).toList());
        List<PurchaseRequirementDO> open = reqs.stream()
                .filter(r -> q.getOrDefault(r.getId(), RequirementQty.Qty.ZERO).ordered() < r.getQuantity()).toList();
        if (!open.isEmpty()) {
            reassign(open, purchaserId, operatorId);
        }
    }

    /** 需求在草稿采购单上的行（加锁草稿） */
    public List<PurchaseOrderItemDO> draftItems(Long requirementId) {
        List<PurchaseOrderItemDO> rows = itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .eq(PurchaseOrderItemDO::getRequirementId, requirementId)
                .isNull(PurchaseOrderItemDO::getDeletedAt)
                .orderByAsc(PurchaseOrderItemDO::getId));
        List<PurchaseOrderItemDO> out = new ArrayList<>(rows.size());
        for (PurchaseOrderItemDO i : rows) {
            PurchaseOrderDO po = orderMapper.selectById(i.getPoId());
            if (po != null && po.getDeletedAt() == null && po.getStatus() == PurchaseConstants.PO_DRAFT) {
                orderMapper.lockById(po.getId());
                out.add(i);
            }
        }
        return out;
    }
}

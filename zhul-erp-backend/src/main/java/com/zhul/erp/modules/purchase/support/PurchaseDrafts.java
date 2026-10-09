package com.zhul.erp.modules.purchase.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderFeeDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderFeeMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 草稿采购单的放置与重算。自动生成、需求池生成、拆分、改采购员、改到其他供应商都走 {@link #place}：
 * 按「采购员 + 供应商」追加到该采购员对该供应商最近的 CNY 草稿，没有时新建；调用方负责已锁住相关需求。
 */
@Component
@RequiredArgsConstructor
public class PurchaseDrafts {

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final PurchaseOrderFeeMapper feeMapper;
    private final SupplierMapper supplierMapper;
    private final PurchaseLogs logs;
    private final RequirementTouch touch;

    /** 线上店铺采购单的默认付款条件：100% 下单后 */
    public static final String SHOP_TERMS = "[{\"percent\":100.00,\"trigger\":1,\"days\":0}]";

    /** 一条需求的多少数量放到哪位采购员对哪个采购对象的草稿；单价可带（改到其他供应商时保留已填的价） */
    public record Placement(PurchaseRequirementDO requirement, Long purchaserId, Counterparty counterparty, int quantity, BigDecimal unitPrice) {
        public Placement(PurchaseRequirementDO requirement, Long purchaserId, Counterparty counterparty, int quantity) {
            this(requirement, purchaserId, counterparty, quantity, null);
        }
    }

    /** 放入草稿，返回涉及的草稿采购单（按放置顺序）；action 写在每张草稿的日志上 */
    public List<PurchaseOrderDO> place(List<Placement> placements, String action, String note, Long operatorId) {
        Map<String, List<Placement>> groups = new LinkedHashMap<>();
        for (Placement p : placements) {
            if (p.quantity() <= 0) {
                continue;
            }
            groups.computeIfAbsent(p.purchaserId() + ":" + p.counterparty().key(), k -> new ArrayList<>()).add(p);
        }
        List<PurchaseOrderDO> touched = new ArrayList<>(groups.size());
        for (List<Placement> group : groups.values()) {
            Placement first = group.get(0);
            PurchaseOrderDO po = lockLatestDraft(first.purchaserId(), first.counterparty());
            boolean created = po == null;
            if (created) {
                po = newDraft(first.purchaserId(), first.counterparty());
            }
            int sort = nextSort(po.getId());
            List<String> models = new ArrayList<>(group.size());
            for (Placement p : group) {
                PurchaseRequirementDO r = p.requirement();
                PurchaseOrderItemDO x = new PurchaseOrderItemDO();
                x.setTenantId(po.getTenantId());
                x.setPoId(po.getId());
                x.setRequirementId(r.getId());
                x.setSoId(r.getSoId());
                x.setSoItemId(r.getSoItemId());
                x.setModel(r.getModel());
                x.setBrand(r.getBrand());
                x.setQuantity(p.quantity());
                x.setUnitPrice(p.unitPrice() != null ? p.unitPrice() : defaultPrice(po, r.getTargetPrice()));
                x.setTargetPrice(r.getTargetPrice());
                x.setAmount(BigDecimal.ZERO);
                x.setSortOrder(sort++);
                itemMapper.insert(x);
                models.add(r.getModel() + " × " + p.quantity());
            }
            recalc(po);
            touch.touch(group.stream().map(x -> x.requirement().getId()).toList());
            logs.add(po.getId(), created ? "新建草稿" : "追加型号",
                    (note == null ? "" : note + "：") + String.join("、", models) + (action == null ? "" : "（" + action + "）"), operatorId);
            touched.add(po);
        }
        return touched;
    }

    /**
     * 默认单价：目标价（CNY 不含税）折算为草稿口径 = 目标价 ×（1 + 税率）÷ 汇率，HALF_UP 保留两位；没有目标价时为空。
     * 这样不改价时不含税单价等于目标价，砍价为 0。
     */
    public static BigDecimal defaultPrice(PurchaseOrderDO po, BigDecimal target) {
        if (target == null) {
            return null;
        }
        BigDecimal gross = Objects.equals(po.getTaxIncluded(), 1) && po.getTaxRate() != null
                ? target.multiply(BigDecimal.ONE.add(po.getTaxRate().movePointLeft(2))) : target;
        return gross.divide(po.getExchangeRate(), 2, java.math.RoundingMode.HALF_UP);
    }

    /** 该采购员对该采购对象最近的 CNY 草稿（加锁） */
    public PurchaseOrderDO lockLatestDraft(Long purchaserId, Counterparty cp) {
        return orderMapper.selectOne(new LambdaQueryWrapper<PurchaseOrderDO>()
                .eq(PurchaseOrderDO::getTenantId, PiStore.tenantId())
                .eq(PurchaseOrderDO::getPurchaserId, purchaserId)
                .eq(PurchaseOrderDO::getChannel, cp.channel())
                .eq(PurchaseOrderDO::getSupplierId, cp.isShop() ? 0L : cp.supplierId())
                .eq(PurchaseOrderDO::getShopName, cp.isShop() ? cp.shopName() : "")
                .eq(PurchaseOrderDO::getCurrencyCode, PurchaseConstants.CNY)
                .eq(PurchaseOrderDO::getStatus, PurchaseConstants.PO_DRAFT)
                .isNull(PurchaseOrderDO::getDeletedAt)
                .orderByDesc(PurchaseOrderDO::getId)
                .last("LIMIT 1 FOR UPDATE"));
    }

    private PurchaseOrderDO newDraft(Long purchaserId, Counterparty cp) {
        SupplierDO s = cp.isShop() ? null : requireSupplier(cp.supplierId());
        PurchaseOrderDO po = new PurchaseOrderDO();
        po.setTenantId(PiStore.tenantId());
        po.setSupplierId(cp.isShop() ? 0L : cp.supplierId());
        po.setChannel(cp.channel());
        po.setShopName(cp.isShop() ? cp.shopName() : "");
        po.setPurchaserId(purchaserId);
        po.setStatus(PurchaseConstants.PO_DRAFT);
        po.setCurrencyCode(PurchaseConstants.CNY);
        po.setExchangeRate(BigDecimal.ONE);
        po.setTaxIncluded(0);
        po.setTaxRate(BigDecimal.ZERO);
        po.setItemAmount(BigDecimal.ZERO);
        po.setFeeAmount(BigDecimal.ZERO);
        po.setTotalAmount(BigDecimal.ZERO);
        po.setTotalAmountCny(BigDecimal.ZERO);
        po.setTargetAmount(BigDecimal.ZERO);
        po.setBargainAmount(BigDecimal.ZERO);
        // 线上店铺默认全额预付（平台下单即付款）
        po.setPaymentTerms(s == null ? SHOP_TERMS : Objects.requireNonNullElse(s.getPaymentTerms(), ""));
        po.setContractNo("");
        po.setCancelReason("");
        orderMapper.insert(po);
        return po;
    }

    /** 本租户、启用、未删除的供应商 */
    public SupplierDO requireSupplier(Long supplierId) {
        SupplierDO s = supplierId == null ? null : supplierMapper.selectById(supplierId);
        if (s == null || s.getDeletedAt() != null || !Objects.equals(s.getTenantId(), PiStore.tenantId())) {
            throw new BizException("供应商不存在");
        }
        if (!Objects.equals(s.getStatus(), 1)) {
            throw new BizException("供应商「" + s.getName() + "」已停用");
        }
        return s;
    }

    private int nextSort(Long poId) {
        return items(poId).stream().mapToInt(i -> Objects.requireNonNullElse(i.getSortOrder(), 0)).max().orElse(0) + 1;
    }

    public List<PurchaseOrderItemDO> items(Long poId) {
        return itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .eq(PurchaseOrderItemDO::getPoId, poId)
                .isNull(PurchaseOrderItemDO::getDeletedAt)
                .orderByAsc(PurchaseOrderItemDO::getSortOrder)
                .orderByAsc(PurchaseOrderItemDO::getId));
    }

    public List<PurchaseOrderFeeDO> fees(Long poId) {
        return feeMapper.selectList(new LambdaQueryWrapper<PurchaseOrderFeeDO>()
                .eq(PurchaseOrderFeeDO::getPoId, poId)
                .isNull(PurchaseOrderFeeDO::getDeletedAt)
                .orderByAsc(PurchaseOrderFeeDO::getSortOrder)
                .orderByAsc(PurchaseOrderFeeDO::getId));
    }

    /** 按单头的汇率与税重算各行不含税单价、小计、砍价，以及单头合计、目标金额与砍价合计 */
    public void recalc(PurchaseOrderDO po) {
        boolean tax = Objects.equals(po.getTaxIncluded(), 1);
        BigDecimal items = BigDecimal.ZERO;
        BigDecimal target = BigDecimal.ZERO;
        BigDecimal bargain = BigDecimal.ZERO;
        for (PurchaseOrderItemDO i : items(po.getId())) {
            BigDecimal net = PurchaseCalc.netPrice(i.getUnitPrice(), po.getExchangeRate(), tax, po.getTaxRate());
            BigDecimal amount = i.getUnitPrice() == null ? BigDecimal.ZERO
                    : PurchaseCalc.money(i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())));
            BigDecimal b = PurchaseCalc.bargain(i.getTargetPrice(), i.getUnitPrice(), i.getQuantity(), po.getExchangeRate(), tax, po.getTaxRate());
            i.setNetPriceCny(net);
            i.setAmount(amount);
            i.setBargainAmount(b);
            itemMapper.updateById(i);
            items = items.add(amount);
            if (b != null) {
                bargain = bargain.add(b);
                target = target.add(PurchaseCalc.targetAmount(i.getTargetPrice(), i.getQuantity()));
            }
        }
        BigDecimal fees = fees(po.getId()).stream().map(PurchaseOrderFeeDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        po.setItemAmount(items);
        po.setFeeAmount(fees);
        po.setTotalAmount(items.add(fees));
        po.setTotalAmountCny(PurchaseCalc.money(po.getTotalAmount().multiply(po.getExchangeRate())));
        po.setTargetAmount(target);
        po.setBargainAmount(bargain);
        orderMapper.updateById(po);
    }

    /** 草稿没有行时删除（软删除），返回是否删除 */
    public boolean deleteIfEmpty(PurchaseOrderDO po, Long operatorId) {
        if (po.getStatus() != PurchaseConstants.PO_DRAFT || !items(po.getId()).isEmpty()) {
            return false;
        }
        po.setDeletedAt(LocalDateTime.now());
        orderMapper.updateById(po);
        logs.add(po.getId(), "删除草稿", "草稿已没有型号，自动删除", operatorId);
        return true;
    }

    /**
     * 从草稿行里扣掉数量（拆分、改采购员时用）：按行倒序扣，扣到 0 的行删除；
     * 返回实际扣掉的数量与涉及的草稿。
     */
    public int takeFromDrafts(Long requirementId, int quantity, Set<Long> touched) {
        int left = quantity;
        List<PurchaseOrderItemDO> rows = itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .eq(PurchaseOrderItemDO::getRequirementId, requirementId)
                .isNull(PurchaseOrderItemDO::getDeletedAt)
                .orderByDesc(PurchaseOrderItemDO::getId));
        for (PurchaseOrderItemDO i : rows) {
            if (left <= 0) {
                break;
            }
            PurchaseOrderDO po = orderMapper.selectById(i.getPoId());
            if (po == null || po.getDeletedAt() != null || po.getStatus() != PurchaseConstants.PO_DRAFT) {
                continue;
            }
            orderMapper.lockById(po.getId());
            int take = Math.min(left, i.getQuantity());
            if (take == i.getQuantity()) {
                i.setDeletedAt(LocalDateTime.now());
            } else {
                i.setQuantity(i.getQuantity() - take);
            }
            itemMapper.updateById(i);
            left -= take;
            touched.add(po.getId());
        }
        touch.touch(List.of(requirementId));
        return quantity - left;
    }

    /** 重算并清理一批草稿 */
    public void settle(Set<Long> poIds, Long operatorId) {
        for (Long id : new LinkedHashSet<>(poIds)) {
            PurchaseOrderDO po = orderMapper.selectById(id);
            if (po != null && po.getDeletedAt() == null && !deleteIfEmpty(po, operatorId)) {
                recalc(po);
            }
        }
    }
}

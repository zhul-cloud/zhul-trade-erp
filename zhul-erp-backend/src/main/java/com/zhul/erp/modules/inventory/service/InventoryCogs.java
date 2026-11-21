package com.zhul.erp.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.entity.InvCostAdjustDO;
import com.zhul.erp.modules.inventory.entity.InvCostAdjustItemDO;
import com.zhul.erp.modules.inventory.entity.InvMoveDO;
import com.zhul.erp.modules.inventory.repository.InvBatchMapper;
import com.zhul.erp.modules.inventory.repository.InvCostAdjustItemMapper;
import com.zhul.erp.modules.inventory.repository.InvCostAdjustMapper;
import com.zhul.erp.modules.inventory.repository.InvMoveMapper;
import com.zhul.erp.modules.sales.support.PiStore;
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
 * 型号行的销售成本（spec finance/inventory-cost「销售成本结转」）：该行全部销售出库流水（扣撤销）的四项金额，
 * 加之后针对这些出运的成本调整；来自系统外入库批次（单位成本 0）的数量单列，由订单成本回退采购单价。
 */
@Service
@RequiredArgsConstructor
public class InventoryCogs {

    private static final List<String> SALES = List.of(InvConstants.BIZ_SALES_OUT, InvConstants.BIZ_SALES_OUT_UNDO);

    private final InvMoveMapper moveMapper;
    private final InvBatchMapper batchMapper;
    private final InvCostAdjustItemMapper adjustItemMapper;
    private final InvCostAdjustMapper adjustMapper;

    /** 一个来源：出运单（SH）或成本调整单（CA）；金额为正数的成本 */
    public record Source(String type, Long id, String no, int qty, BigDecimal amount) {
    }

    /** 型号行的销售成本；四项为人民币两位，claim 为质量索赔（负数） */
    public record Cogs(int qty, int systemQty, BigDecimal goods, BigDecimal invoice, BigDecimal fee, BigDecimal claim,
                       List<Source> sources) {
        public BigDecimal total() {
            return goods.add(invoice).add(fee).add(claim);
        }
    }

    private static final class Acc {
        int qty;
        int systemQty;
        BigDecimal goods = BigDecimal.ZERO.setScale(2);
        BigDecimal invoice = BigDecimal.ZERO.setScale(2);
        BigDecimal fee = BigDecimal.ZERO.setScale(2);
        BigDecimal claim = BigDecimal.ZERO.setScale(2);
        final Map<String, Source> sources = new LinkedHashMap<>();

        void source(String type, Long id, String no, int q, BigDecimal amount) {
            sources.merge(type + ":" + id, new Source(type, id, no, q, amount),
                    (a, b) -> new Source(type, id, no, a.qty() + b.qty(), a.amount().add(b.amount())));
        }
    }

    /** 各型号行的销售成本（当前租户）；没有出运的行不在结果里 */
    public Map<Long, Cogs> ofSoItems(Collection<Long> soItemIds) {
        List<Long> ids = soItemIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<InvMoveDO> moves = moveMapper.selectList(new LambdaQueryWrapper<InvMoveDO>()
                .eq(InvMoveDO::getTenantId, PiStore.tenantId())
                .eq(InvMoveDO::getTargetType, InvConstants.TARGET_SO_ITEM)
                .in(InvMoveDO::getTargetId, ids)
                .in(InvMoveDO::getBizType, SALES)
                .in(InvMoveDO::getOpType, List.of(InvConstants.OP_IN, InvConstants.OP_OUT))
                .orderByAsc(InvMoveDO::getId));
        Set<Long> systemBatches = moves.isEmpty() ? Set.of() : batchMapper.selectBatchIds(moves.stream().map(InvMoveDO::getBatchId)
                        .distinct().toList()).stream()
                .filter(b -> Objects.equals(b.getSourceType(), InvConstants.BATCH_SYSTEM))
                .map(InvBatchDO::getId).collect(Collectors.toSet());
        Map<Long, Acc> acc = new HashMap<>();
        for (InvMoveDO m : moves) {
            Acc a = acc.computeIfAbsent(m.getTargetId(), k -> new Acc());
            int q = -m.getQuantity();
            a.qty += q;
            if (systemBatches.contains(m.getBatchId())) {
                a.systemQty += q;
            }
            a.goods = a.goods.subtract(nz(m.getAmountGoods()));
            a.invoice = a.invoice.subtract(nz(m.getAmountInvoice()));
            a.fee = a.fee.subtract(nz(m.getAmountFee()));
            a.claim = a.claim.subtract(nz(m.getAmountClaim()));
            a.source("SH", m.getDocId(), m.getDocNo(), q, nz(m.getAmountCny()).negate());
        }
        List<InvCostAdjustItemDO> items = adjustItemMapper.selectList(new LambdaQueryWrapper<InvCostAdjustItemDO>()
                .eq(InvCostAdjustItemDO::getTenantId, PiStore.tenantId())
                .eq(InvCostAdjustItemDO::getPart, InvConstants.PART_SHIPPED)
                .in(InvCostAdjustItemDO::getSoItemId, ids)
                .isNull(InvCostAdjustItemDO::getDeletedAt)
                .orderByAsc(InvCostAdjustItemDO::getId));
        Map<Long, InvCostAdjustDO> adjusts = items.isEmpty() ? Map.of() : adjustMapper.selectBatchIds(items.stream()
                        .map(InvCostAdjustItemDO::getAdjustId).distinct().toList()).stream()
                .filter(a -> a.getDeletedAt() == null)
                .collect(Collectors.toMap(InvCostAdjustDO::getId, Function.identity()));
        for (InvCostAdjustItemDO i : items) {
            InvCostAdjustDO h = adjusts.get(i.getAdjustId());
            if (h == null) {
                continue;
            }
            Acc a = acc.computeIfAbsent(i.getSoItemId(), k -> new Acc());
            a.goods = a.goods.add(nz(i.getAmountGoods()));
            a.invoice = a.invoice.add(nz(i.getAmountInvoice()));
            a.fee = a.fee.add(nz(i.getAmountFee()));
            a.claim = a.claim.add(nz(i.getAmountClaim()));
            a.source("CA", h.getId(), h.getAdjustNo(), 0, nz(i.getAmountCny()));
        }
        Map<Long, Cogs> out = new HashMap<>();
        acc.forEach((k, a) -> {
            if (a.qty > 0 || !a.sources.isEmpty()) {
                out.put(k, new Cogs(Math.max(0, a.qty), Math.max(0, a.systemQty), a.goods, a.invoice, a.fee, a.claim,
                        new ArrayList<>(a.sources.values())));
            }
        });
        return out;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

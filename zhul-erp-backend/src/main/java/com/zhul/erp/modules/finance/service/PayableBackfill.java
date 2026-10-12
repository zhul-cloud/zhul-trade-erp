package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.entity.ForwarderStatementDO;
import com.zhul.erp.modules.logistics.repository.ForwarderStatementMapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Consumer;

/**
 * 存量补齐（openspec add-accounts-payable）：已下单、还没有应付单的采购订单，已确认、还没有应付单的运费账单，启动时补生成应付。
 * 有应付单的不再处理，所以重复启动不会重复生成；每张单据一个事务，失败只记日志，下次启动再补。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayableBackfill implements ApplicationRunner {

    private static final String NO_PAYABLE = "SELECT 1 FROM fin_payable f WHERE f.source_type = %1$d AND f.source_id = %2$s.id"
            + " AND f.tenant_id = %2$s.tenant_id";

    private final PurchaseOrderMapper orderMapper;
    private final ForwarderStatementMapper statementMapper;
    private final PayableSyncService sync;
    private final TransactionTemplate tx;

    @Override
    public void run(ApplicationArguments args) {
        List<PurchaseOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrderDO>()
                .select(PurchaseOrderDO::getId, PurchaseOrderDO::getTenantId)
                .eq(PurchaseOrderDO::getStatus, PurchaseConstants.PO_ORDERED)
                .isNull(PurchaseOrderDO::getDeletedAt)
                .notExists(String.format(NO_PAYABLE, 1, "purchase_order")));
        int po = each(orders.stream().map(o -> new Item(o.getId(), o.getTenantId())).toList(), sync::syncPurchaseOrder);
        List<ForwarderStatementDO> statements = statementMapper.selectList(new LambdaQueryWrapper<ForwarderStatementDO>()
                .select(ForwarderStatementDO::getId, ForwarderStatementDO::getTenantId)
                .eq(ForwarderStatementDO::getStatus, LogisticsConstants.STATEMENT_CONFIRMED)
                .isNull(ForwarderStatementDO::getDeletedAt)
                .notExists(String.format(NO_PAYABLE, 2, "forwarder_statement")));
        int st = each(statements.stream().map(s -> new Item(s.getId(), s.getTenantId())).toList(), sync::createForStatement);
        if (po + st > 0) {
            log.info("应付存量补齐完成：采购订单 {} 张，运费账单 {} 张", po, st);
        }
    }

    private record Item(Long id, Integer tenantId) {
    }

    private int each(List<Item> items, Consumer<Long> action) {
        int done = 0;
        for (Item i : items) {
            try {
                TenantContext.setTenantId(i.tenantId());
                tx.executeWithoutResult(s -> action.accept(i.id()));
                done++;
            } catch (RuntimeException e) {
                log.warn("应付存量补齐失败，下次启动重试，sourceId={}", i.id(), e);
            } finally {
                TenantContext.clear();
            }
        }
        return done;
    }
}

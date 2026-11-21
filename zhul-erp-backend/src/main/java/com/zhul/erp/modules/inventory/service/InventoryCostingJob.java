package com.zhul.erp.modules.inventory.service;

import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvCostAdjustDO;
import com.zhul.erp.modules.inventory.service.InventoryCosting.Trigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 批次成本的后台任务（openspec inventory-costing design 决策 5）：
 * <ul>
 *   <li>每日核对：每天 02:30 重算最近 180 天有采购入库的采购订单下的批次，原因「每日核对」，兜住没有经过钩子的单据变化；</li>
 *   <li>成本口径升级：上线后启动完成时，每个租户重算全部采购批次一次，原因「成本口径升级」，
 *       完成后在 sys_config 记 inventory.costing.upgraded=1；失败的租户记日志，下次启动重试。</li>
 * </ul>
 * 没有登录上下文：租户列表在平台范围查询（JdbcTemplate 手写，跨租户），每个租户用 {@link TenantContext#runAs}、一个事务执行；
 * 单个租户失败不影响其他租户。启动完成事件在期初（ApplicationRunner）之后触发，期初生成的批次也会被升级。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryCostingJob {

    static final int DAILY_DAYS = 180;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final InventoryCosting costing;
    private final InventoryWarehouses warehouses;

    @Scheduled(cron = "${zhul.inventory.costing-cron:0 30 2 * * *}", zone = "Asia/Shanghai")
    public void daily() {
        for (Integer t : tenants()) {
            try {
                InvCostAdjustDO[] out = new InvCostAdjustDO[1];
                TenantContext.runAs(t, () -> out[0] = tx.execute(s -> costing.recostRecent(LocalDate.now().minusDays(DAILY_DAYS),
                        Trigger.of(InvConstants.CA_DAILY))));
                InvCostAdjustDO a = out[0];
                if (a != null) {
                    log.info("每日核对生成成本调整单，tenantId={}, adjustNo={}, 批次数={}", t, a.getAdjustNo(), a.getBatchCount());
                }
            } catch (RuntimeException e) {
                log.error("每日核对批次成本失败，tenantId={}", t, e);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void upgrade() {
        Set<Integer> done = Set.copyOf(TenantScope.platform(() -> jdbc.queryForList("SELECT tenant_id FROM sys_config"
                + " WHERE config_key = ? AND config_value = '1' AND deleted_at IS NULL", Integer.class, InvConstants.CONFIG_COSTING_UPGRADED)));
        for (Integer t : tenants()) {
            if (done.contains(t)) {
                continue;
            }
            try {
                InvCostAdjustDO a = upgradeTenant(t);
                log.info("成本口径升级完成，tenantId={}, 调整单={}, 调整批次数={}", t, a == null ? "无" : a.getAdjustNo(),
                        a == null ? 0 : a.getBatchCount());
            } catch (RuntimeException e) {
                log.warn("成本口径升级失败，下次启动重试，tenantId={}", t, e);
            }
        }
    }

    /** 某租户的成本口径升级（已完成的不再执行，返回空） */
    public InvCostAdjustDO upgradeTenant(int tenantId) {
        InvCostAdjustDO[] out = new InvCostAdjustDO[1];
        TenantContext.runAs(tenantId, () -> out[0] = tx.execute(s -> {
            if ("1".equals(warehouses.config(InvConstants.CONFIG_COSTING_UPGRADED))) {
                return null;
            }
            InvCostAdjustDO a = costing.recostBatches(costing.purchaseBatches(null), Trigger.of(InvConstants.CA_UPGRADE));
            warehouses.saveConfig(InvConstants.CONFIG_COSTING_UPGRADED, "批次成本已按新口径重算", "1");
            return a;
        }));
        return out[0];
    }

    /** 有采购批次的租户（平台范围） */
    private List<Integer> tenants() {
        return TenantScope.platform(() -> jdbc.queryForList("SELECT DISTINCT tenant_id FROM inv_batch WHERE source_type = ?"
                + " AND deleted_at IS NULL", Integer.class, InvConstants.BATCH_PURCHASE));
    }
}

package com.zhul.erp.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvBalanceDO;
import com.zhul.erp.modules.inventory.entity.InvReservationDO;
import com.zhul.erp.modules.inventory.repository.InvBalanceMapper;
import com.zhul.erp.modules.inventory.repository.InvReservationMapper;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Doc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Line;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Loc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Target;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 商品合并转移库存（spec warehouse/inventory「自动过账」商品合并）：被并入商品在各租户的每个余额行「转出」，
 * 主商品同仓库、同批次、同状态「转入」，预占一起转。商品是平台共享数据，合并跨租户：先用 JdbcTemplate 显式查出有余额的租户，
 * 再逐个 {@link TenantContext#runAs} 过账，过账里的查询都带本租户条件。在合并服务的事务内执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryMerge {

    private final JdbcTemplate jdbc;
    private final InvBalanceMapper balanceMapper;
    private final InvReservationMapper reservationMapper;
    private final InventoryPosting posting;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void merge(Long fromId, Long toId, String fromLabel) {
        List<Integer> tenants = jdbc.queryForList("SELECT DISTINCT tenant_id FROM inv_balance WHERE product_id = ? AND on_hand > 0"
                + " ORDER BY tenant_id", Integer.class, fromId);
        for (Integer tenant : tenants) {
            TenantContext.runAs(tenant, () -> mergeTenant(fromId, toId, fromLabel));
        }
    }

    private void mergeTenant(Long fromId, Long toId, String fromLabel) {
        int tenantId = PiStore.tenantId();
        Doc doc = Doc.of(InvConstants.BIZ_MERGE, "PRODUCT", fromId, "").label("商品合并 · " + fromLabel + " 并入");
        List<Line> lines = new ArrayList<>();
        List<InvBalanceDO> rows = balanceMapper.selectList(new LambdaQueryWrapper<InvBalanceDO>()
                .eq(InvBalanceDO::getTenantId, tenantId)
                .eq(InvBalanceDO::getProductId, fromId)
                .gt(InvBalanceDO::getOnHand, 0));
        List<InvReservationDO> reservations = reservationMapper.selectList(new LambdaQueryWrapper<InvReservationDO>()
                .eq(InvReservationDO::getTenantId, tenantId)
                .eq(InvReservationDO::getProductId, fromId)
                .gt(InvReservationDO::getQuantity, 0));
        for (InvBalanceDO b : rows) {
            Loc from = new Loc(b.getWarehouseId(), fromId, b.getBatchId(), b.getStockStatus());
            Loc to = new Loc(b.getWarehouseId(), toId, b.getBatchId(), b.getStockStatus());
            int reserved = 0;
            for (InvReservationDO r : reservations) {
                if (Objects.equals(r.getWarehouseId(), b.getWarehouseId()) && Objects.equals(r.getBatchId(), b.getBatchId())
                        && Objects.equals(r.getStockStatus(), b.getStockStatus())) {
                    Target t = new Target(r.getTargetType(), r.getTargetId());
                    lines.add(Line.of(from, InvConstants.OP_CONVERT_OUT, r.getQuantity(), t, doc));
                    lines.add(Line.of(to, InvConstants.OP_CONVERT_IN, r.getQuantity(), t, doc));
                    reserved += r.getQuantity();
                }
            }
            int free = b.getOnHand() - reserved;
            if (free > 0) {
                lines.add(Line.of(from, InvConstants.OP_CONVERT_OUT, free, null, doc));
                lines.add(Line.of(to, InvConstants.OP_CONVERT_IN, free, null, doc));
            }
        }
        posting.post(lines);
        // 预占按「对象 + 仓库 + 批次 + 状态」唯一，转出转入落在同一行上，商品改记主商品
        reservationMapper.update(null, new LambdaUpdateWrapper<InvReservationDO>()
                .eq(InvReservationDO::getTenantId, tenantId)
                .eq(InvReservationDO::getProductId, fromId)
                .set(InvReservationDO::getProductId, toId)
                .set(InvReservationDO::getUpdateTime, LocalDateTime.now()));
        log.info("商品合并转移库存，tenantId={}, fromId={}, toId={}, rows={}", tenantId, fromId, toId, rows.size());
    }
}

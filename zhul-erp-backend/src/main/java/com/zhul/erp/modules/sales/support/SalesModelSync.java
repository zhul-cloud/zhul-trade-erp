package com.zhul.erp.modules.sales.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryItemModelChangedEvent;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.entity.OutboundOrderDO;
import com.zhul.erp.modules.logistics.entity.OutboundOrderItemDO;
import com.zhul.erp.modules.logistics.repository.OutboundOrderItemMapper;
import com.zhul.erp.modules.logistics.repository.OutboundOrderMapper;
import com.zhul.erp.modules.purchase.support.RequirementLifecycle;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PiItemDO;
import com.zhul.erp.modules.sales.entity.PiVersionDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.PiItemMapper;
import com.zhul.erp.modules.sales.repository.PiVersionMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 真实型号变了以后的 PI 与销售订单（spec sales/proforma-invoice「真实型号变更后的 PI」、sales/sales-order「真实型号变更后的销售订单」）：
 * PI 草稿与编辑中新版本的行跟着改；还没出库、未取消的订单型号行跟着改，并同步采购需求与草稿采购订单。
 */
@Component
@RequiredArgsConstructor
public class SalesModelSync {

    private static final Set<Integer> PI_CLOSED_STATES = Set.of(SalesConstants.PI_CONVERTED, SalesConstants.PI_VOID,
            SalesConstants.PI_CLOSED);

    private final PiItemMapper piItemMapper;
    private final PiVersionMapper piVersionMapper;
    private final ProformaInvoiceMapper piMapper;
    private final SalesOrderItemMapper soItemMapper;
    private final SalesOrderMapper soMapper;
    private final OutboundOrderItemMapper outboundItemMapper;
    private final OutboundOrderMapper outboundMapper;
    private final RequirementLifecycle requirementLifecycle;
    private final CurrentUserResolver currentUser;

    /** 与保存真实型号同一事务同步处理 */
    @EventListener
    public void on(InquiryItemModelChangedEvent event) {
        Map<Long, InquiryItemModelChangedEvent.Change> changes = event.changes().stream()
                .collect(Collectors.toMap(InquiryItemModelChangedEvent.Change::inquiryItemId, Function.identity(), (a, b) -> b));
        syncPis(changes);
        syncOrders(changes);
    }

    private void syncPis(Map<Long, InquiryItemModelChangedEvent.Change> changes) {
        List<PiItemDO> lines = piItemMapper.selectList(new LambdaQueryWrapper<PiItemDO>()
                .select(PiItemDO::getId, PiItemDO::getPiId, PiItemDO::getVersionId, PiItemDO::getInquiryItemId)
                .in(PiItemDO::getInquiryItemId, changes.keySet())
                .isNull(PiItemDO::getDeletedAt));
        if (lines.isEmpty()) {
            return;
        }
        Map<Long, PiVersionDO> versions = piVersionMapper.selectBatchIds(lines.stream().map(PiItemDO::getVersionId).distinct().toList())
                .stream().collect(Collectors.toMap(PiVersionDO::getId, Function.identity()));
        Map<Long, ProformaInvoiceDO> pis = piMapper.selectBatchIds(lines.stream().map(PiItemDO::getPiId).distinct().toList())
                .stream().collect(Collectors.toMap(ProformaInvoiceDO::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        for (PiItemDO line : lines) {
            if (!editable(pis.get(line.getPiId()), versions.get(line.getVersionId()))) {
                continue;
            }
            InquiryItemModelChangedEvent.Change c = changes.get(line.getInquiryItemId());
            piItemMapper.update(null, new LambdaUpdateWrapper<PiItemDO>()
                    .eq(PiItemDO::getId, line.getId())
                    .set(PiItemDO::getModel, c.docModel())
                    .set(PiItemDO::getCustomerModel, c.customerModel())
                    .set(PiItemDO::getUpdateTime, now));
        }
    }

    /** PI 草稿或编辑中的新版本：版本是 PI 正在编辑的版本，且 PI 没有转订单、作废或关闭 */
    static boolean editable(ProformaInvoiceDO pi, PiVersionDO v) {
        return pi != null && v != null && pi.getDeletedAt() == null && v.getDeletedAt() == null
                && !PI_CLOSED_STATES.contains(pi.getStatus())
                && Objects.equals(v.getVersionNo(), pi.getEditingVersionNo());
    }

    private void syncOrders(Map<Long, InquiryItemModelChangedEvent.Change> changes) {
        List<SalesOrderItemDO> lines = soItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                .select(SalesOrderItemDO::getId, SalesOrderItemDO::getSoId, SalesOrderItemDO::getInquiryItemId,
                        SalesOrderItemDO::getModel, SalesOrderItemDO::getCustomerModel, SalesOrderItemDO::getProgressCode)
                .in(SalesOrderItemDO::getInquiryItemId, changes.keySet())
                .gt(SalesOrderItemDO::getQuantity, 0)
                .ne(SalesOrderItemDO::getProgressCode, SalesConstants.PROGRESS_CANCELLED)
                .isNull(SalesOrderItemDO::getDeletedAt));
        if (lines.isEmpty()) {
            return;
        }
        Set<Long> activeOrders = soMapper.selectBatchIds(lines.stream().map(SalesOrderItemDO::getSoId).distinct().toList()).stream()
                .filter(o -> o.getDeletedAt() == null && Objects.equals(o.getStatus(), SalesConstants.SO_ACTIVE))
                .map(SalesOrderDO::getId).collect(Collectors.toSet());
        Set<Long> shipped = shipped(lines.stream().map(SalesOrderItemDO::getId).toList());
        Map<Long, String> changed = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (SalesOrderItemDO line : lines) {
            if (!activeOrders.contains(line.getSoId()) || shipped.contains(line.getId())) {
                continue;
            }
            InquiryItemModelChangedEvent.Change c = changes.get(line.getInquiryItemId());
            if (Objects.equals(line.getModel(), c.docModel()) && Objects.equals(line.getCustomerModel(), c.customerModel())) {
                continue;
            }
            soItemMapper.update(null, new LambdaUpdateWrapper<SalesOrderItemDO>()
                    .eq(SalesOrderItemDO::getId, line.getId())
                    .set(SalesOrderItemDO::getModel, c.docModel())
                    .set(SalesOrderItemDO::getCustomerModel, c.customerModel())
                    .set(SalesOrderItemDO::getUpdateTime, now));
            if (!Objects.equals(line.getModel(), c.docModel())) {
                changed.put(line.getId(), c.docModel());
            }
        }
        requirementLifecycle.onSoItemModelChanged(changed, currentUser.resolve());
    }

    /** 已有出库记录（未删除、未撤回的出库单）的订单型号行 */
    private Set<Long> shipped(List<Long> soItemIds) {
        List<OutboundOrderItemDO> items = outboundItemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItemDO>()
                .select(OutboundOrderItemDO::getSoItemId, OutboundOrderItemDO::getOutboundId)
                .in(OutboundOrderItemDO::getSoItemId, soItemIds)
                .isNull(OutboundOrderItemDO::getDeletedAt));
        if (items.isEmpty()) {
            return Set.of();
        }
        Set<Long> live = outboundMapper.selectBatchIds(items.stream().map(OutboundOrderItemDO::getOutboundId).distinct().toList())
                .stream().filter(o -> o.getDeletedAt() == null && !Objects.equals(o.getStatus(), LogisticsConstants.OB_WITHDRAWN))
                .map(OutboundOrderDO::getId).collect(Collectors.toSet());
        Set<Long> result = new HashSet<>();
        for (OutboundOrderItemDO i : items) {
            if (live.contains(i.getOutboundId())) {
                result.add(i.getSoItemId());
            }
        }
        return result;
    }
}

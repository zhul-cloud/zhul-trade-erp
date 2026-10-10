package com.zhul.erp.modules.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.support.PurchaseLogs;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.dto.SaveShipmentRequest;
import com.zhul.erp.modules.warehouse.dto.ShipmentFormVO;
import com.zhul.erp.modules.warehouse.dto.ShipmentItemVO;
import com.zhul.erp.modules.warehouse.dto.ShipmentListVO;
import com.zhul.erp.modules.warehouse.dto.ShipmentPageQuery;
import com.zhul.erp.modules.warehouse.dto.ShipmentVO;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentDO;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentItemDO;
import com.zhul.erp.modules.warehouse.repository.PurchaseReceiptMapper;
import com.zhul.erp.modules.warehouse.repository.SupplierShipmentItemMapper;
import com.zhul.erp.modules.warehouse.repository.SupplierShipmentMapper;
import com.zhul.erp.modules.warehouse.support.ReceivingQty;
import com.zhul.erp.modules.warehouse.support.ReceivingSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商发货单：采购员登记、修改、作废（按数据权限），仓库在「待收货」里查看在途的（不按采购员过滤）。
 * 事务边界：登记 / 修改 / 作废各自一个事务，含发货单、行、附件挂载与采购单日志。
 */
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final SupplierShipmentMapper shipmentMapper;
    private final SupplierShipmentItemMapper shipmentItemMapper;
    private final PurchaseReceiptMapper receiptMapper;
    private final com.zhul.erp.modules.sales.repository.SalesOrderItemMapper soItemMapper;
    private final ReceivingSupport support;
    private final ReceivingQty receivingQty;
    private final AttachmentService attachments;
    private final DocumentNumberService documentNumberService;
    private final PurchaseLogs logs;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;
    private final TransitTimeService transitTimes;
    private final com.zhul.erp.modules.logistics.support.LogisticsSupport logisticsSupport;

    // ---------------------------------------------------------------- 列表

    /** scoped：采购员侧按数据权限；pendingOnly：仓库「待收货」，只看在途、按发货日期从早到晚 */
    public PageResult<ShipmentListVO> page(ShipmentPageQuery q, boolean scoped, boolean pendingOnly) {
        LambdaQueryWrapper<SupplierShipmentDO> w = new LambdaQueryWrapper<SupplierShipmentDO>()
                .eq(SupplierShipmentDO::getTenantId, PiStore.tenantId())
                .isNull(SupplierShipmentDO::getDeletedAt);
        String scopeSql = scoped ? support.scopedPoSql() : null;
        if (scopeSql != null) {
            w.inSql(SupplierShipmentDO::getPoId, scopeSql);
        }
        if (pendingOnly) {
            // 仓库待收货不含直发货代的（在出运单上确认）
            w.eq(SupplierShipmentDO::getStatus, WarehouseConstants.SHIP_IN_TRANSIT).isNull(SupplierShipmentDO::getDirectForwarderId);
        } else if (q.getStatus() != null) {
            w.eq(SupplierShipmentDO::getStatus, q.getStatus());
        }
        if (q.getShipFrom() != null) {
            w.ge(SupplierShipmentDO::getShipDate, q.getShipFrom());
        }
        if (q.getShipTo() != null) {
            w.le(SupplierShipmentDO::getShipDate, q.getShipTo());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            Set<Long> poIds = support.poIdsByKeyword(kw);
            w.and(x -> {
                x.like(SupplierShipmentDO::getSdNo, kw).or().like(SupplierShipmentDO::getTrackingNo, kw);
                if (!poIds.isEmpty()) {
                    x.or().in(SupplierShipmentDO::getPoId, poIds);
                }
            });
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = shipmentMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        // 待收货按发货日期从早到晚；其余按列表页统一规范，更新时间倒序
        String order = pendingOnly ? "ORDER BY ship_date ASC, id ASC" : "ORDER BY update_time DESC, id DESC";
        w.last(order + " LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toListVos(shipmentMapper.selectList(w)));
    }

    public long countInTransit() {
        return shipmentMapper.selectCount(new LambdaQueryWrapper<SupplierShipmentDO>()
                .eq(SupplierShipmentDO::getTenantId, PiStore.tenantId())
                .eq(SupplierShipmentDO::getStatus, WarehouseConstants.SHIP_IN_TRANSIT)
                .isNull(SupplierShipmentDO::getDirectForwarderId)
                .isNull(SupplierShipmentDO::getDeletedAt));
    }

    List<ShipmentListVO> toListVos(List<SupplierShipmentDO> rows) {
        List<Long> ids = rows.stream().map(SupplierShipmentDO::getId).toList();
        Map<Long, List<SupplierShipmentItemDO>> items = itemsOf(ids);
        Map<Long, PurchaseOrderDO> pos = support.pos(rows.stream().map(SupplierShipmentDO::getPoId).toList());
        Map<Long, String> names = support.counterparties(pos.values());
        Map<Long, String> users = lookups.userNames(pos.values().stream().map(PurchaseOrderDO::getPurchaserId).toList());
        Map<Long, Long> files = attachments.counts(AttachmentService.SHIPMENT, ids);
        Map<Long, String> forwarders = logisticsSupport.supplierNames(rows.stream().map(SupplierShipmentDO::getDirectForwarderId).toList());
        Map<Long, PurchaseReceiptDO> receipts = new HashMap<>();
        if (!ids.isEmpty()) {
            receiptMapper.selectList(new LambdaQueryWrapper<PurchaseReceiptDO>()
                            .in(PurchaseReceiptDO::getShipmentId, ids)
                            .eq(PurchaseReceiptDO::getStatus, WarehouseConstants.GR_VALID)
                            .isNull(PurchaseReceiptDO::getDeletedAt))
                    .forEach(r -> receipts.put(r.getShipmentId(), r));
        }
        List<ShipmentListVO> out = new ArrayList<>(rows.size());
        for (SupplierShipmentDO s : rows) {
            List<SupplierShipmentItemDO> lines = items.getOrDefault(s.getId(), List.of());
            PurchaseOrderDO p = pos.get(s.getPoId());
            ShipmentListVO vo = new ShipmentListVO();
            vo.setId(s.getId());
            vo.setSdNo(s.getSdNo());
            vo.setDirectForwarderId(s.getDirectForwarderId());
            vo.setDirectForwarderName(s.getDirectForwarderId() == null ? null : forwarders.get(s.getDirectForwarderId()));
            vo.setLogisticsId(s.getLogisticsId());
            vo.setPoId(s.getPoId());
            vo.setPoNo(p == null ? null : p.getPoNo());
            vo.setSupplierName(names.get(s.getPoId()));
            vo.setShop(p != null && p.getChannel() != null && p.getChannel() != PurchaseConstants.CHANNEL_SUPPLIER);
            vo.setCarrier(s.getCarrier());
            vo.setTrackingNo(s.getTrackingNo());
            vo.setShipDate(s.getShipDate());
            vo.setExpectedArrivalDate(s.getExpectedArrivalDate());
            vo.setArrivalOverdue(s.getStatus() == WarehouseConstants.SHIP_IN_TRANSIT && s.getExpectedArrivalDate() != null
                    && s.getExpectedArrivalDate().isBefore(LocalDate.now()));
            vo.setItemCount(lines.size());
            vo.setTotalQuantity(lines.stream().mapToInt(SupplierShipmentItemDO::getQuantity).sum());
            vo.setItems(lines.stream().map(ShipmentService::itemVo).toList());
            vo.setAttachmentCount(files.getOrDefault(s.getId(), 0L).intValue());
            vo.setSource(s.getSource());
            vo.setSourceName(WarehouseConstants.SHIP_SOURCE_NAMES.get(s.getSource()));
            vo.setStatus(s.getStatus());
            vo.setStatusName(WarehouseConstants.SHIP_STATUS_NAMES.get(s.getStatus()));
            vo.setVoidReason(s.getVoidReason());
            vo.setNote(s.getNote());
            vo.setPurchaserId(p == null ? null : p.getPurchaserId());
            vo.setPurchaserName(p == null ? null : users.get(p.getPurchaserId()));
            PurchaseReceiptDO r = receipts.get(s.getId());
            vo.setReceiptId(r == null ? null : r.getId());
            vo.setGrNo(r == null ? null : r.getGrNo());
            vo.setCreateTime(s.getCreateTime());
            vo.setCreateBy(s.getCreateBy());
            vo.setUpdateTime(s.getUpdateTime());
            vo.setUpdateBy(s.getUpdateBy());
            out.add(vo);
        }
        return out;
    }

    private static ShipmentItemVO itemVo(SupplierShipmentItemDO i) {
        ShipmentItemVO x = new ShipmentItemVO();
        x.setId(i.getId());
        x.setPoItemId(i.getPoItemId());
        x.setModel(i.getModel());
        x.setBrand(i.getBrand());
        x.setCategory(i.getCategory());
        x.setQuantity(i.getQuantity());
        return x;
    }

    public Map<Long, List<SupplierShipmentItemDO>> itemsOf(List<Long> shipmentIds) {
        if (shipmentIds.isEmpty()) {
            return Map.of();
        }
        return shipmentItemMapper.selectList(new LambdaQueryWrapper<SupplierShipmentItemDO>()
                        .in(SupplierShipmentItemDO::getShipmentId, shipmentIds)
                        .isNull(SupplierShipmentItemDO::getDeletedAt)
                        .orderByAsc(SupplierShipmentItemDO::getId))
                .stream().collect(Collectors.groupingBy(SupplierShipmentItemDO::getShipmentId, LinkedHashMap::new, Collectors.toList()));
    }

    // ---------------------------------------------------------------- 详情与表单

    public ShipmentVO detail(Long id, boolean scoped) {
        SupplierShipmentDO s = visible(id, scoped);
        ShipmentVO vo = new ShipmentVO();
        vo.setShipment(toListVos(List.of(s)).get(0));
        vo.setAttachments(attachments.listOf(AttachmentService.SHIPMENT, id));
        vo.setEditable(s.getStatus() == WarehouseConstants.SHIP_IN_TRANSIT);
        return vo;
    }

    /** 登记（shipmentId 为空）或修改发货单的表单 */
    public ShipmentFormVO form(Long poId, Long shipmentId) {
        SupplierShipmentDO s = shipmentId == null ? null : visible(shipmentId, true);
        PurchaseOrderDO p = support.po(s == null ? poId : s.getPoId(), true);
        List<PurchaseOrderItemDO> lines = support.poItems(p.getId());
        Map<Long, Integer> shipped = receivingQty.shippedByPoItem(lines.stream().map(PurchaseOrderItemDO::getId).toList());
        Map<Long, Integer> mine = s == null ? Map.of() : itemsOf(List.of(s.getId())).getOrDefault(s.getId(), List.of()).stream()
                .collect(Collectors.toMap(SupplierShipmentItemDO::getPoItemId, SupplierShipmentItemDO::getQuantity, Integer::sum));
        Map<Long, String> categories = support.categories(lines.stream().map(PurchaseOrderItemDO::getRequirementId).toList());
        ShipmentFormVO vo = new ShipmentFormVO();
        vo.setPoId(p.getId());
        vo.setPoNo(p.getPoNo());
        vo.setSupplierName(support.counterparties(List.of(p)).get(p.getId()));
        if (s != null) {
            vo.setShipmentId(s.getId());
            vo.setDirectForwarderId(s.getDirectForwarderId());
            vo.setCarrier(s.getCarrier());
            vo.setTrackingNo(s.getTrackingNo());
            vo.setShipDate(s.getShipDate());
            vo.setExpectedArrivalDate(s.getExpectedArrivalDate());
            vo.setNote(s.getNote());
            vo.setAttachments(attachments.listOf(AttachmentService.SHIPMENT, s.getId()));
        } else {
            vo.setShipDate(LocalDate.now());
            vo.setAttachments(List.of());
        }
        List<ShipmentFormVO.Line> out = new ArrayList<>();
        for (PurchaseOrderItemDO i : lines) {
            int done = shipped.getOrDefault(i.getId(), 0);
            int own = mine.getOrDefault(i.getId(), 0);
            int max = Math.max(0, i.getQuantity() - done) + own;
            if (max <= 0 && own == 0) {
                continue;
            }
            ShipmentFormVO.Line x = new ShipmentFormVO.Line();
            x.setPoItemId(i.getId());
            x.setModel(i.getModel());
            x.setBrand(i.getBrand());
            x.setCategory(categories.get(i.getRequirementId()));
            x.setOrderedQty(i.getQuantity());
            x.setShippedQty(done - own);
            x.setMaxQuantity(max);
            x.setQuantity(s == null ? max : own);
            out.add(x);
        }
        vo.setLines(out);
        return vo;
    }

    // ---------------------------------------------------------------- 登记、修改、作废

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ShipmentVO create(SaveShipmentRequest req) {
        PurchaseOrderDO p = support.lockPo(req.getPoId(), true);
        requireOrdered(p, "确认下单后才能登记发货");
        SupplierShipmentDO s = new SupplierShipmentDO();
        s.setTenantId(PiStore.tenantId());
        s.setSdNo(documentNumberService.next(DocumentType.SD));
        s.setPoId(p.getId());
        s.setSource(WarehouseConstants.SHIP_BY_BUYER);
        s.setStatus(WarehouseConstants.SHIP_IN_TRANSIT);
        s.setVoidReason("");
        fill(s, req);
        direct(s, req);
        arrival(s, p, req);
        shipmentMapper.insert(s);
        String text = writeItems(s, p, req.getItems(), Map.of());
        requireOneCustomer(s);
        attachments.attach(AttachmentService.SHIPMENT, s.getId(), req.getAttachmentIds());
        logs.add(p.getId(), "登记发货", s.getSdNo() + "：" + text + trackingText(s), currentUser.resolve());
        logService.recordOperateLog(WarehouseConstants.MENU_SHIPMENT, "登记发货", null,
                Map.of("sdNo", s.getSdNo(), "poNo", p.getPoNo(), "items", text));
        return detail(s.getId(), true);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ShipmentVO update(Long id, SaveShipmentRequest req) {
        SupplierShipmentDO s = visible(id, true);
        PurchaseOrderDO p = support.lockPo(s.getPoId(), true);
        s = lockShipment(id);
        requireInTransit(s, "修改");
        requireOrdered(p, "确认下单后才能登记发货");
        List<SupplierShipmentItemDO> old = itemsOf(List.of(id)).getOrDefault(id, List.of());
        Map<Long, Integer> own = old.stream()
                .collect(Collectors.toMap(SupplierShipmentItemDO::getPoItemId, SupplierShipmentItemDO::getQuantity, Integer::sum));
        String before = old.stream().map(i -> i.getModel() + " × " + i.getQuantity()).collect(Collectors.joining("、")) + trackingText(s);
        if (s.getLogisticsId() != null && !java.util.Objects.equals(s.getDirectForwarderId(), req.getDirectForwarderId())) {
            throw new BizException("直发货已放进出运单，不能改收货方");
        }
        fill(s, req);
        direct(s, req);
        arrival(s, p, req);
        shipmentMapper.updateById(s);
        old.forEach(i -> {
            i.setDeletedAt(LocalDateTime.now());
            shipmentItemMapper.updateById(i);
        });
        String after = writeItems(s, p, req.getItems(), own) + trackingText(s);
        requireOneCustomer(s);
        attachments.sync(AttachmentService.SHIPMENT, id, req.getAttachmentIds());
        logs.add(p.getId(), "修改发货", s.getSdNo() + "：" + before + " → " + after, currentUser.resolve());
        logService.recordOperateLog(WarehouseConstants.MENU_SHIPMENT, "修改发货", Map.of("sdNo", s.getSdNo(), "items", before),
                Map.of("sdNo", s.getSdNo(), "items", after));
        return detail(id, true);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ShipmentVO voidShipment(Long id, String reason) {
        SupplierShipmentDO s = visible(id, true);
        PurchaseOrderDO p = support.lockPo(s.getPoId(), true);
        s = lockShipment(id);
        if (s.getStatus() == WarehouseConstants.SHIP_RECEIVED) {
            throw new BizException("已入库的发货单不能作废，有问题请在到货差异里处理");
        }
        requireInTransit(s, "作废");
        if (s.getLogisticsId() != null) {
            throw new BizException("直发货已放进出运单，请先从出运单里移出");
        }
        String why = reason.trim();
        s.setStatus(WarehouseConstants.SHIP_VOID);
        s.setVoidReason(why);
        shipmentMapper.updateById(s);
        logs.add(p.getId(), "作废发货", s.getSdNo() + "，原因：" + why + "；数量回到未发", currentUser.resolve());
        logService.recordOperateLog(WarehouseConstants.MENU_SHIPMENT, "作废发货", Map.of("sdNo", s.getSdNo(), "status", "在途"),
                Map.of("sdNo", s.getSdNo(), "status", "已作废", "reason", why));
        return detail(id, true);
    }

    /** 预计到货日期：采购员填了用填的（不能早于发货日期），没填按快递时效估算 */
    private void arrival(SupplierShipmentDO s, PurchaseOrderDO p, SaveShipmentRequest req) {
        if (req.getExpectedArrivalDate() != null) {
            if (req.getExpectedArrivalDate().isBefore(s.getShipDate())) {
                throw new BizException("预计到货日期不能早于发货日期");
            }
            s.setExpectedArrivalDate(req.getExpectedArrivalDate());
        } else {
            s.setExpectedArrivalDate(transitTimes.estimate(p, s.getCarrier(), s.getShipDate()).getDate());
        }
    }

    /** 直发货代：货代须是启用的服务商 */
    private void direct(SupplierShipmentDO s, SaveShipmentRequest req) {
        if (req.getDirectForwarderId() != null) {
            logisticsSupport.requireForwarder(req.getDirectForwarderId());
        }
        s.setDirectForwarderId(req.getDirectForwarderId());
    }

    /** 直发货代的发货单只能是同一个客户的型号（出运单按客户组单） */
    private void requireOneCustomer(SupplierShipmentDO s) {
        if (s.getDirectForwarderId() == null) {
            return;
        }
        List<Long> soItemIds = itemsOf(List.of(s.getId())).getOrDefault(s.getId(), List.of()).stream()
                .map(SupplierShipmentItemDO::getSoItemId).distinct().toList();
        if (directCustomers(soItemIds).size() > 1) {
            throw new BizException("直发货代时只能选同一个客户的型号");
        }
    }

    /** 订单型号行所属的客户 */
    public java.util.Set<Long> directCustomers(List<Long> soItemIds) {
        return logisticsSupport.orders(soItemIds.isEmpty() ? List.of() : soItemMapper.selectBatchIds(soItemIds).stream()
                        .map(com.zhul.erp.modules.sales.entity.SalesOrderItemDO::getSoId).toList())
                .values().stream().map(com.zhul.erp.modules.sales.entity.SalesOrderDO::getCustomerId).collect(java.util.stream.Collectors.toSet());
    }

    /** 登记发货时估算预计到货日期 */
    public com.zhul.erp.modules.warehouse.dto.ArrivalEstimateVO estimate(Long poId, String carrier, LocalDate shipDate) {
        return transitTimes.estimate(support.po(poId, true), carrier, shipDate == null ? LocalDate.now() : shipDate);
    }

    private static void fill(SupplierShipmentDO s, SaveShipmentRequest req) {
        if (req.getShipDate().isAfter(LocalDate.now())) {
            throw new BizException("发货日期不能晚于今天");
        }
        s.setCarrier(ReceivingSupport.trim(req.getCarrier()));
        s.setTrackingNo(ReceivingSupport.trim(req.getTrackingNo()));
        s.setShipDate(req.getShipDate());
        s.setNote(ReceivingSupport.trim(req.getNote()));
    }

    /**
     * 写发货行：每行数量大于 0 且不超过未发数量（修改时加上本单原数量 own）；返回「A × 40、C × 10」。
     * 未发数量 = 订购数量 − 已发数量（口径见 ReceivingQty）。
     */
    String writeItems(SupplierShipmentDO s, PurchaseOrderDO p, List<SaveShipmentRequest.Line> lines, Map<Long, Integer> own) {
        Map<Long, PurchaseOrderItemDO> poItems = support.poItems(p.getId()).stream()
                .collect(Collectors.toMap(PurchaseOrderItemDO::getId, i -> i));
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (SaveShipmentRequest.Line l : lines) {
            if (!poItems.containsKey(l.getPoItemId())) {
                throw new BizException("型号不属于这张采购单");
            }
            if (wanted.containsKey(l.getPoItemId())) {
                throw new BizException("同一个型号不能重复填写");
            }
            if (l.getQuantity() == null || l.getQuantity() <= 0) {
                throw new BizException(poItems.get(l.getPoItemId()).getModel() + " 的发货数量须大于 0");
            }
            wanted.put(l.getPoItemId(), l.getQuantity());
        }
        Map<Long, Integer> shipped = receivingQty.shippedByPoItem(poItems.keySet());
        Map<Long, String> categories = support.categories(poItems.values().stream().map(PurchaseOrderItemDO::getRequirementId).toList());
        List<String> text = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            PurchaseOrderItemDO i = poItems.get(e.getKey());
            int max = Math.max(0, i.getQuantity() - shipped.getOrDefault(i.getId(), 0)) + own.getOrDefault(i.getId(), 0);
            if (e.getValue() > max) {
                throw new BizException(max == 0 ? i.getModel() + " 已经发完了" : i.getModel() + " 最多还能发 " + max + " 个");
            }
            SupplierShipmentItemDO x = new SupplierShipmentItemDO();
            x.setTenantId(s.getTenantId());
            x.setShipmentId(s.getId());
            x.setPoItemId(i.getId());
            x.setRequirementId(i.getRequirementId());
            x.setSoItemId(i.getSoItemId());
            x.setModel(i.getModel());
            x.setBrand(i.getBrand());
            x.setCategory(Objects.requireNonNullElse(categories.get(i.getRequirementId()), ""));
            x.setQuantity(e.getValue());
            shipmentItemMapper.insert(x);
            text.add(i.getModel() + " × " + e.getValue());
        }
        return String.join("、", text);
    }

    private static String trackingText(SupplierShipmentDO s) {
        String t = (s.getCarrier() + " " + s.getTrackingNo()).trim();
        return t.isEmpty() ? "" : "（" + t + "）";
    }

    static void requireOrdered(PurchaseOrderDO p, String draftMessage) {
        if (p.getStatus() == PurchaseConstants.PO_DRAFT) {
            throw new BizException(draftMessage);
        }
        if (p.getStatus() != PurchaseConstants.PO_ORDERED) {
            throw new BizException("采购单已取消");
        }
    }

    private static void requireInTransit(SupplierShipmentDO s, String action) {
        if (s.getStatus() == WarehouseConstants.SHIP_RECEIVED) {
            throw new BizException("已入库的发货单不能" + action);
        }
        if (s.getStatus() != WarehouseConstants.SHIP_IN_TRANSIT) {
            throw new BizException("发货单已作废");
        }
    }

    /** scoped 为 true 时按采购单的采购员校验数据权限 */
    SupplierShipmentDO visible(Long id, boolean scoped) {
        SupplierShipmentDO s = id == null ? null : shipmentMapper.selectById(id);
        if (s == null || s.getDeletedAt() != null || !Objects.equals(s.getTenantId(), PiStore.tenantId())) {
            throw new BizException("发货单不存在");
        }
        if (scoped) {
            PurchaseOrderDO p = support.po(s.getPoId(), false);
            if (!support.scope().canSee(p.getPurchaserId())) {
                throw new BizException("发货单不存在");
            }
        }
        return s;
    }

    SupplierShipmentDO lockShipment(Long id) {
        shipmentMapper.lockById(id);
        return shipmentMapper.selectById(id);
    }
}

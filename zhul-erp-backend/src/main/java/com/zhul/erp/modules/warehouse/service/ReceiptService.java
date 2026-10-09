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
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.purchase.support.OrderPurchaseProgress;
import com.zhul.erp.modules.purchase.support.PurchaseLogs;
import com.zhul.erp.modules.purchase.support.RequirementTouch;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.dto.AcceptRequest;
import com.zhul.erp.modules.warehouse.dto.DirectReceiveRequest;
import com.zhul.erp.modules.warehouse.dto.ReceiptListVO;
import com.zhul.erp.modules.warehouse.dto.ReceiptPageQuery;
import com.zhul.erp.modules.warehouse.dto.ReceiptVO;
import com.zhul.erp.modules.warehouse.dto.ReceivableOrderVO;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptItemDO;
import com.zhul.erp.modules.warehouse.entity.ReceivingDiscrepancyDO;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentDO;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentItemDO;
import com.zhul.erp.modules.warehouse.repository.PurchaseReceiptItemMapper;
import com.zhul.erp.modules.warehouse.repository.PurchaseReceiptMapper;
import com.zhul.erp.modules.warehouse.repository.ReceivingDiscrepancyMapper;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采购入库单：仓库验收在途的发货单、直接收货（补一张「仓库补登」的发货单）、冲销。仓库侧不按采购员过滤。
 * 事务边界：验收 = 入库单、行、发货单状态、到货差异、拍摄任务、订单进度、需求更新时间；
 * 直接收货再加补登的发货单；冲销 = 入库单状态、发货单回在途、差异与拍摄任务作废、订单进度。
 * 写操作用读已提交并先锁采购单：同一采购单的发货、入库、差异处理串行，拿到锁后读到的是最新的已发、合格数量。
 */
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final PurchaseReceiptMapper receiptMapper;
    private final PurchaseReceiptItemMapper receiptItemMapper;
    private final SupplierShipmentMapper shipmentMapper;
    private final SupplierShipmentItemMapper shipmentItemMapper;
    private final ReceivingDiscrepancyMapper discrepancyMapper;
    private final PurchaseOrderMapper orderMapper;
    private final ShipmentService shipments;
    private final ShootService shoots;
    private final DiscrepancyService discrepancies;
    private final ReceivingSupport support;
    private final ReceivingQty receivingQty;
    private final AttachmentService attachments;
    private final DocumentNumberService documentNumberService;
    private final OrderPurchaseProgress progress;
    private final RequirementTouch touch;
    private final PurchaseLogs logs;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;

    // ---------------------------------------------------------------- 列表与详情

    public PageResult<ReceiptListVO> page(ReceiptPageQuery q) {
        LambdaQueryWrapper<PurchaseReceiptDO> w = new LambdaQueryWrapper<PurchaseReceiptDO>()
                .eq(PurchaseReceiptDO::getTenantId, PiStore.tenantId())
                .isNull(PurchaseReceiptDO::getDeletedAt);
        if (q.getStatus() != null) {
            w.eq(PurchaseReceiptDO::getStatus, q.getStatus());
        }
        if (q.getReceivedFrom() != null) {
            w.ge(PurchaseReceiptDO::getReceivedDate, q.getReceivedFrom());
        }
        if (q.getReceivedTo() != null) {
            w.le(PurchaseReceiptDO::getReceivedDate, q.getReceivedTo());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            Set<Long> poIds = support.poIdsByKeyword(kw);
            List<Long> shipmentIds = shipmentMapper.selectList(new LambdaQueryWrapper<SupplierShipmentDO>()
                            .select(SupplierShipmentDO::getId)
                            .eq(SupplierShipmentDO::getTenantId, PiStore.tenantId())
                            .and(x -> x.like(SupplierShipmentDO::getSdNo, kw).or().like(SupplierShipmentDO::getTrackingNo, kw))
                            .last("LIMIT 500"))
                    .stream().map(SupplierShipmentDO::getId).toList();
            w.and(x -> {
                x.like(PurchaseReceiptDO::getGrNo, kw);
                if (!poIds.isEmpty()) {
                    x.or().in(PurchaseReceiptDO::getPoId, poIds);
                }
                if (!shipmentIds.isEmpty()) {
                    x.or().in(PurchaseReceiptDO::getShipmentId, shipmentIds);
                }
            });
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = receiptMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        w.last("ORDER BY update_time DESC, id DESC LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toListVos(receiptMapper.selectList(w)));
    }

    private List<ReceiptListVO> toListVos(List<PurchaseReceiptDO> rows) {
        List<Long> ids = rows.stream().map(PurchaseReceiptDO::getId).toList();
        Map<Long, List<PurchaseReceiptItemDO>> items = itemsOf(ids);
        Map<Long, Long> diffs = ids.isEmpty() ? Map.of()
                : discrepancyMapper.selectList(new LambdaQueryWrapper<ReceivingDiscrepancyDO>()
                        .select(ReceivingDiscrepancyDO::getReceiptId)
                        .in(ReceivingDiscrepancyDO::getReceiptId, ids)
                        .isNull(ReceivingDiscrepancyDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(ReceivingDiscrepancyDO::getReceiptId, Collectors.counting()));
        Map<Long, PurchaseOrderDO> pos = support.pos(rows.stream().map(PurchaseReceiptDO::getPoId).toList());
        Map<Long, String> names = support.counterparties(pos.values());
        Map<Long, SupplierShipmentDO> ships = ReceivingSupport.byId(rows.stream().map(PurchaseReceiptDO::getShipmentId).toList(),
                shipmentMapper::selectBatchIds, SupplierShipmentDO::getId);
        Map<Long, String> users = lookups.userNames(rows.stream().map(PurchaseReceiptDO::getReceivedBy).toList());
        List<ReceiptListVO> out = new ArrayList<>(rows.size());
        for (PurchaseReceiptDO r : rows) {
            List<PurchaseReceiptItemDO> lines = items.getOrDefault(r.getId(), List.of());
            PurchaseOrderDO p = pos.get(r.getPoId());
            SupplierShipmentDO s = ships.get(r.getShipmentId());
            ReceiptListVO vo = new ReceiptListVO();
            vo.setId(r.getId());
            vo.setGrNo(r.getGrNo());
            vo.setReceivedDate(r.getReceivedDate());
            vo.setShipmentId(r.getShipmentId());
            vo.setSdNo(s == null ? null : s.getSdNo());
            vo.setPoId(r.getPoId());
            vo.setPoNo(p == null ? null : p.getPoNo());
            vo.setSupplierName(names.get(r.getPoId()));
            vo.setItemCount(lines.size());
            vo.setReceivedQty(lines.stream().mapToInt(PurchaseReceiptItemDO::getReceivedQty).sum());
            vo.setQualifiedQty(lines.stream().mapToInt(PurchaseReceiptItemDO::getQualifiedQty).sum());
            vo.setDefectiveQty(lines.stream().mapToInt(PurchaseReceiptItemDO::getDefectiveQty).sum());
            vo.setDiscrepancyCount(diffs.getOrDefault(r.getId(), 0L).intValue());
            vo.setReceivedByName(users.get(r.getReceivedBy()));
            vo.setStatus(r.getStatus());
            vo.setStatusName(WarehouseConstants.GR_STATUS_NAMES.get(r.getStatus()));
            vo.setReverseReason(r.getReverseReason());
            vo.setNote(r.getNote());
            vo.setCreateTime(r.getCreateTime());
            vo.setCreateBy(r.getCreateBy());
            vo.setUpdateTime(r.getUpdateTime());
            vo.setUpdateBy(r.getUpdateBy());
            out.add(vo);
        }
        return out;
    }

    private Map<Long, List<PurchaseReceiptItemDO>> itemsOf(List<Long> receiptIds) {
        if (receiptIds.isEmpty()) {
            return Map.of();
        }
        return receiptItemMapper.selectList(new LambdaQueryWrapper<PurchaseReceiptItemDO>()
                        .in(PurchaseReceiptItemDO::getReceiptId, receiptIds)
                        .isNull(PurchaseReceiptItemDO::getDeletedAt)
                        .orderByAsc(PurchaseReceiptItemDO::getId))
                .stream().collect(Collectors.groupingBy(PurchaseReceiptItemDO::getReceiptId, LinkedHashMap::new, Collectors.toList()));
    }

    public ReceiptVO detail(Long id) {
        PurchaseReceiptDO r = visible(id);
        ReceiptVO vo = new ReceiptVO();
        vo.setReceipt(toListVos(List.of(r)).get(0));
        vo.setItems(itemsOf(List.of(id)).getOrDefault(id, List.of()).stream().map(i -> {
            ReceiptVO.Item x = new ReceiptVO.Item();
            x.setId(i.getId());
            x.setModel(i.getModel());
            x.setBrand(i.getBrand());
            x.setCategory(i.getCategory());
            x.setShippedQty(i.getShippedQty());
            x.setReceivedQty(i.getReceivedQty());
            x.setQualifiedQty(i.getQualifiedQty());
            x.setDefectiveQty(i.getDefectiveQty());
            x.setNote(i.getNote());
            return x;
        }).toList());
        vo.setDiscrepancies(discrepancies.ofReceipt(id));
        vo.setAttachments(attachments.listOf(AttachmentService.RECEIPT, id));
        vo.setShipmentAttachments(attachments.listOf(AttachmentService.SHIPMENT, r.getShipmentId()));
        vo.setReversible(r.getStatus() == WarehouseConstants.GR_VALID && reversible(id));
        return vo;
    }

    /** 直接收货可选的采购单：已下单，按采购单号、采购对象、型号搜索，最多 20 张 */
    public List<ReceivableOrderVO> receivableOrders(String keyword) {
        LambdaQueryWrapper<PurchaseOrderDO> w = new LambdaQueryWrapper<PurchaseOrderDO>()
                .eq(PurchaseOrderDO::getTenantId, PiStore.tenantId())
                .eq(PurchaseOrderDO::getStatus, PurchaseConstants.PO_ORDERED)
                .isNull(PurchaseOrderDO::getDeletedAt);
        if (StringUtils.hasText(keyword)) {
            Set<Long> ids = support.poIdsByKeyword(keyword.trim());
            if (ids.isEmpty()) {
                return List.of();
            }
            w.in(PurchaseOrderDO::getId, ids);
        }
        w.last("ORDER BY order_date DESC, id DESC LIMIT 20");
        List<PurchaseOrderDO> rows = orderMapper.selectList(w);
        Map<Long, String> names = support.counterparties(rows);
        Map<Long, String> users = lookups.userNames(rows.stream().map(PurchaseOrderDO::getPurchaserId).toList());
        List<ReceivableOrderVO> out = new ArrayList<>(rows.size());
        for (PurchaseOrderDO p : rows) {
            List<PurchaseOrderItemDO> lines = support.poItems(p.getId());
            Map<Long, Integer> shipped = receivingQty.shippedByPoItem(lines.stream().map(PurchaseOrderItemDO::getId).toList());
            Map<Long, String> categories = support.categories(lines.stream().map(PurchaseOrderItemDO::getRequirementId).toList());
            ReceivableOrderVO vo = new ReceivableOrderVO();
            vo.setPoId(p.getId());
            vo.setPoNo(p.getPoNo());
            vo.setSupplierName(names.get(p.getId()));
            vo.setPurchaserName(users.get(p.getPurchaserId()));
            vo.setOrderDate(p.getOrderDate());
            vo.setLines(lines.stream().map(i -> {
                ReceivableOrderVO.Line x = new ReceivableOrderVO.Line();
                x.setPoItemId(i.getId());
                x.setModel(i.getModel());
                x.setBrand(i.getBrand());
                x.setCategory(categories.get(i.getRequirementId()));
                x.setOrderedQty(i.getQuantity());
                x.setUnshippedQty(Math.max(0, i.getQuantity() - shipped.getOrDefault(i.getId(), 0)));
                return x;
            }).toList());
            out.add(vo);
        }
        return out;
    }

    // ---------------------------------------------------------------- 验收、直接收货、冲销

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ReceiptVO accept(AcceptRequest req) {
        SupplierShipmentDO s = shipments.visible(req.getShipmentId(), false);
        PurchaseOrderDO p = support.lockPo(s.getPoId(), false);
        s = shipments.lockShipment(s.getId());
        if (s.getStatus() == WarehouseConstants.SHIP_RECEIVED) {
            throw new BizException("这张发货单已经验收入库了");
        }
        if (s.getStatus() != WarehouseConstants.SHIP_IN_TRANSIT) {
            throw new BizException("发货单已作废");
        }
        List<Qty> lines = new ArrayList<>();
        for (AcceptRequest.Line l : req.getItems()) {
            lines.add(new Qty(l.getShipmentItemId(), l.getReceivedQty(), l.getQualifiedQty(), l.getDefectiveQty(), l.getNote()));
        }
        PurchaseReceiptDO r = receive(p, s, req.getReceivedDate(), req.getNote(), lines, req.getAttachmentIds());
        return detail(r.getId());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ReceiptVO direct(DirectReceiveRequest req) {
        PurchaseOrderDO p = support.lockPo(req.getPoId(), false);
        ShipmentService.requireOrdered(p, "确认下单后才能收货");
        requireDate(req.getReceivedDate());
        Map<Long, PurchaseOrderItemDO> poItems = support.poItems(p.getId()).stream()
                .collect(Collectors.toMap(PurchaseOrderItemDO::getId, i -> i));
        Map<Long, DirectReceiveRequest.Line> wanted = new LinkedHashMap<>();
        for (DirectReceiveRequest.Line l : req.getItems()) {
            if (!poItems.containsKey(l.getPoItemId())) {
                throw new BizException("型号不属于这张采购单");
            }
            if (wanted.put(l.getPoItemId(), l) != null) {
                throw new BizException("同一个型号不能重复填写");
            }
            validate(poItems.get(l.getPoItemId()).getModel(), l.getReceivedQty(), l.getQualifiedQty(), l.getDefectiveQty());
        }
        wanted.values().removeIf(l -> l.getReceivedQty() == 0);
        if (wanted.isEmpty()) {
            throw new BizException("请至少填写一个型号的实收数量");
        }
        // 补一张「仓库补登」的发货单：发货数量 = 实收数量，超出未发数量的部分计为多发
        Map<Long, Integer> shipped = receivingQty.shippedByPoItem(wanted.keySet());
        Map<Long, String> categories = support.categories(poItems.values().stream().map(PurchaseOrderItemDO::getRequirementId).toList());
        SupplierShipmentDO s = new SupplierShipmentDO();
        s.setTenantId(PiStore.tenantId());
        s.setSdNo(documentNumberService.next(DocumentType.SD));
        s.setPoId(p.getId());
        s.setSource(WarehouseConstants.SHIP_BY_WAREHOUSE);
        s.setCarrier("");
        s.setTrackingNo("");
        s.setShipDate(req.getReceivedDate());
        s.setStatus(WarehouseConstants.SHIP_IN_TRANSIT);
        s.setVoidReason("");
        s.setNote("仓库直接收货");
        shipmentMapper.insert(s);
        List<Qty> lines = new ArrayList<>();
        List<String> text = new ArrayList<>();
        for (DirectReceiveRequest.Line l : wanted.values()) {
            PurchaseOrderItemDO i = poItems.get(l.getPoItemId());
            int unshipped = Math.max(0, i.getQuantity() - shipped.getOrDefault(i.getId(), 0));
            SupplierShipmentItemDO x = new SupplierShipmentItemDO();
            x.setTenantId(s.getTenantId());
            x.setShipmentId(s.getId());
            x.setPoItemId(i.getId());
            x.setRequirementId(i.getRequirementId());
            x.setSoItemId(i.getSoItemId());
            x.setModel(i.getModel());
            x.setBrand(i.getBrand());
            x.setCategory(Objects.requireNonNullElse(categories.get(i.getRequirementId()), ""));
            x.setQuantity(Math.min(l.getReceivedQty(), unshipped));
            shipmentItemMapper.insert(x);
            text.add(i.getModel() + " × " + x.getQuantity());
            lines.add(new Qty(x.getId(), l.getReceivedQty(), l.getQualifiedQty(), l.getDefectiveQty(), l.getNote()));
        }
        logs.add(p.getId(), "仓库补登发货", s.getSdNo() + "：" + String.join("、", text) + "（仓库直接收货）", currentUser.resolve());
        PurchaseReceiptDO r = receive(p, s, req.getReceivedDate(), req.getNote(), lines, req.getAttachmentIds());
        return detail(r.getId());
    }

    private record Qty(Long shipmentItemId, Integer received, Integer qualified, Integer defective, String note) {
    }

    /** 验收一张在途发货单（发货单与采购单已锁） */
    private PurchaseReceiptDO receive(PurchaseOrderDO p, SupplierShipmentDO s, LocalDate date, String note, List<Qty> lines,
                                      List<Long> attachmentIds) {
        requireDate(date);
        List<SupplierShipmentItemDO> shipItems = shipments.itemsOf(List.of(s.getId())).getOrDefault(s.getId(), List.of());
        Map<Long, Qty> byItem = new HashMap<>();
        for (Qty q : lines) {
            if (byItem.put(q.shipmentItemId(), q) != null) {
                throw new BizException("同一个型号不能重复填写");
            }
        }
        if (byItem.size() != shipItems.size() || !shipItems.stream().allMatch(i -> byItem.containsKey(i.getId()))) {
            throw new BizException("请填写每个型号的验收数量");
        }
        for (SupplierShipmentItemDO i : shipItems) {
            Qty q = byItem.get(i.getId());
            validate(i.getModel(), q.received(), q.qualified(), q.defective());
        }
        Long operator = currentUser.resolve();
        PurchaseReceiptDO r = new PurchaseReceiptDO();
        r.setTenantId(PiStore.tenantId());
        r.setGrNo(documentNumberService.next(DocumentType.GR));
        r.setShipmentId(s.getId());
        r.setPoId(p.getId());
        r.setReceivedDate(date);
        r.setReceivedBy(operator == null ? 0L : operator);
        r.setStatus(WarehouseConstants.GR_VALID);
        r.setReverseReason("");
        r.setNote(ReceivingSupport.trim(note));
        receiptMapper.insert(r);
        Map<Long, PurchaseOrderItemDO> poItems = support.poItemsById(shipItems.stream().map(SupplierShipmentItemDO::getPoItemId).toList());
        List<PurchaseReceiptItemDO> items = new ArrayList<>();
        List<String> text = new ArrayList<>();
        for (SupplierShipmentItemDO i : shipItems) {
            Qty q = byItem.get(i.getId());
            PurchaseReceiptItemDO x = new PurchaseReceiptItemDO();
            x.setTenantId(r.getTenantId());
            x.setReceiptId(r.getId());
            x.setShipmentItemId(i.getId());
            x.setPoItemId(i.getPoItemId());
            x.setRequirementId(i.getRequirementId());
            x.setSoItemId(i.getSoItemId());
            x.setModel(i.getModel());
            x.setBrand(i.getBrand());
            x.setCategory(i.getCategory());
            x.setShippedQty(i.getQuantity());
            x.setReceivedQty(q.received());
            x.setQualifiedQty(q.qualified());
            x.setDefectiveQty(q.defective());
            x.setNote(ReceivingSupport.trim(q.note()));
            receiptItemMapper.insert(x);
            items.add(x);
            text.add(i.getModel() + " 实收 " + q.received() + "、合格 " + q.qualified() + (q.defective() > 0 ? "、不良 " + q.defective() : ""));
        }
        s.setStatus(WarehouseConstants.SHIP_RECEIVED);
        shipmentMapper.updateById(s);
        int diffCount = discrepancies.createForReceipt(r, items, p);
        Map<Long, Long> soIds = new HashMap<>();
        poItems.forEach((k, v) -> soIds.put(k, v.getSoId()));
        shoots.createForReceipt(r, items, soIds);
        attachments.attach(AttachmentService.RECEIPT, r.getId(), attachmentIds);
        touch.touch(items.stream().map(PurchaseReceiptItemDO::getRequirementId).toList());
        progress.sync(items.stream().map(PurchaseReceiptItemDO::getSoItemId).toList());
        logs.add(p.getId(), "验收入库", r.getGrNo() + "（发货单 " + s.getSdNo() + "）：" + String.join("；", text)
                + (diffCount > 0 ? "；产生 " + diffCount + " 条到货差异" : ""), operator);
        logService.recordOperateLog(WarehouseConstants.MENU_RECEIPT, "验收入库", null,
                Map.of("grNo", r.getGrNo(), "sdNo", s.getSdNo(), "poNo", p.getPoNo(), "items", text));
        return r;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ReceiptVO reverse(Long id, String reason) {
        PurchaseReceiptDO r = visible(id);
        PurchaseOrderDO p = support.lockPo(r.getPoId(), false);
        receiptMapper.lockById(id);
        r = visible(id);
        if (r.getStatus() != WarehouseConstants.GR_VALID) {
            throw new BizException("入库单已冲销");
        }
        if (!discrepancies.allPending(id)) {
            throw new BizException("到货差异已经处理，不能冲销；请先让采购员重新打开差异");
        }
        if (!shoots.notStarted(id)) {
            throw new BizException("拍摄任务已经开始，不能冲销");
        }
        String why = reason.trim();
        r.setStatus(WarehouseConstants.GR_REVERSED);
        r.setReverseReason(why);
        receiptMapper.updateById(r);
        SupplierShipmentDO s = shipments.lockShipment(r.getShipmentId());
        s.setStatus(WarehouseConstants.SHIP_IN_TRANSIT);
        shipmentMapper.updateById(s);
        discrepancies.voidForReceipt(id);
        shoots.voidForReceipt(id);
        List<PurchaseReceiptItemDO> items = itemsOf(List.of(id)).getOrDefault(id, List.of());
        touch.touch(items.stream().map(PurchaseReceiptItemDO::getRequirementId).toList());
        progress.sync(items.stream().map(PurchaseReceiptItemDO::getSoItemId).toList());
        logs.add(p.getId(), "冲销入库", r.getGrNo() + "，原因：" + why + "；发货单 " + s.getSdNo() + " 回到在途", currentUser.resolve());
        logService.recordOperateLog(WarehouseConstants.MENU_RECEIPT, "冲销入库", Map.of("grNo", r.getGrNo(), "status", "有效"),
                Map.of("grNo", r.getGrNo(), "status", "已冲销", "reason", why));
        return detail(id);
    }

    private boolean reversible(Long receiptId) {
        return discrepancies.allPending(receiptId) && shoots.notStarted(receiptId);
    }

    private static void requireDate(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            throw new BizException("收货日期不能晚于今天");
        }
    }

    private static void validate(String model, Integer received, Integer qualified, Integer defective) {
        if (received < 0 || qualified < 0 || defective < 0) {
            throw new BizException(model + " 的数量不能为负数");
        }
        if (received != qualified + defective) {
            throw new BizException(model + "：实收须等于合格加不良");
        }
    }

    private PurchaseReceiptDO visible(Long id) {
        PurchaseReceiptDO r = id == null ? null : receiptMapper.selectById(id);
        if (r == null || r.getDeletedAt() != null || !Objects.equals(r.getTenantId(), PiStore.tenantId())) {
            throw new BizException("入库单不存在");
        }
        return r;
    }
}

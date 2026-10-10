package com.zhul.erp.modules.logistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.dto.ConfirmDirectRequest;
import com.zhul.erp.modules.logistics.dto.DirectRef;
import com.zhul.erp.modules.logistics.dto.LogisticsPageQuery;
import com.zhul.erp.modules.logistics.dto.LogisticsVO;
import com.zhul.erp.modules.logistics.dto.OutboundVO;
import com.zhul.erp.modules.logistics.dto.PackRequest;
import com.zhul.erp.modules.logistics.dto.PendingGroupVO;
import com.zhul.erp.modules.logistics.dto.SaveLogisticsRequest;
import com.zhul.erp.modules.logistics.dto.ShipRequest;
import com.zhul.erp.modules.logistics.entity.LogisticsShipmentDO;
import com.zhul.erp.modules.logistics.entity.OutboundBoxDO;
import com.zhul.erp.modules.logistics.entity.OutboundOrderDO;
import com.zhul.erp.modules.logistics.entity.OutboundOrderItemDO;
import com.zhul.erp.modules.logistics.entity.ShipmentDocGroupDO;
import com.zhul.erp.modules.logistics.repository.LogisticsShipmentMapper;
import com.zhul.erp.modules.logistics.repository.OutboundBoxMapper;
import com.zhul.erp.modules.logistics.repository.OutboundOrderItemMapper;
import com.zhul.erp.modules.logistics.repository.OutboundOrderMapper;
import com.zhul.erp.modules.logistics.repository.ShipmentDocGroupMapper;
import com.zhul.erp.modules.logistics.support.LogisticsSupport;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.support.OrderPurchaseProgress;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentDO;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentItemDO;
import com.zhul.erp.modules.warehouse.repository.SupplierShipmentMapper;
import com.zhul.erp.modules.warehouse.service.ReceiptService;
import com.zhul.erp.modules.warehouse.service.ShipmentService;
import com.zhul.erp.modules.warehouse.support.ReceivingSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 出运单：同一客户、同一货代已交货代的出库单与直发货合成一票；直发货按货代实收确认；登记运单与国际运费。
 * 按业务员数据权限（出运单的业务员）。事务边界：直发确认 = 入库单与差异、直发出库单与箱、订单进度；
 * 登记出运 = 出运单、国际运费分摊、订单进度。写操作读已提交并先锁出运单。
 */
@Service
@RequiredArgsConstructor
public class LogisticsShipmentService {

    private final LogisticsShipmentMapper shipmentMapper;
    private final OutboundOrderMapper outboundMapper;
    private final OutboundOrderItemMapper outboundItemMapper;
    private final OutboundBoxMapper boxMapper;
    private final ShipmentDocGroupMapper docGroupMapper;
    private final SupplierShipmentMapper supplierShipmentMapper;
    private final SalesOrderMapper soMapper;
    private final SalesOrderItemMapper soItemMapper;
    private final OutboundService outbounds;
    private final FreightService freight;
    private final ShipmentService supplierShipments;
    private final ReceiptService receipts;
    private final ReceivingSupport receivingSupport;
    private final LogisticsSupport support;
    private final OrderPurchaseProgress progress;
    private final AttachmentService attachments;
    private final DocumentNumberService documentNumberService;
    private final CurrentUserResolver currentUser;
    private final LogService logService;

    // ---------------------------------------------------------------- 待出运的货

    /** 已交货代、还没放进出运单的出库单与直发货，按客户与货代分组（按业务员数据权限） */
    public List<PendingGroupVO> pending() {
        LambdaQueryWrapper<OutboundOrderDO> w = new LambdaQueryWrapper<OutboundOrderDO>()
                .eq(OutboundOrderDO::getTenantId, PiStore.tenantId())
                .eq(OutboundOrderDO::getStatus, LogisticsConstants.OB_HANDED)
                .isNull(OutboundOrderDO::getLogisticsId)
                .isNull(OutboundOrderDO::getDeletedAt)
                .orderByAsc(OutboundOrderDO::getId);
        support.scope().apply(w, OutboundOrderDO::getOwnerId);
        List<OutboundVO> obs = outbounds.toVos(outboundMapper.selectList(w));
        List<DirectRef> directs = directRefs(supplierShipmentMapper.selectList(new LambdaQueryWrapper<SupplierShipmentDO>()
                        .eq(SupplierShipmentDO::getTenantId, PiStore.tenantId())
                        .isNotNull(SupplierShipmentDO::getDirectForwarderId)
                        .eq(SupplierShipmentDO::getStatus, WarehouseConstants.SHIP_IN_TRANSIT)
                        .isNull(SupplierShipmentDO::getLogisticsId)
                        .isNull(SupplierShipmentDO::getDeletedAt)
                        .orderByAsc(SupplierShipmentDO::getId)), true);
        Map<String, PendingGroupVO> groups = new LinkedHashMap<>();
        for (OutboundVO ob : obs) {
            group(groups, ob.getCustomerId(), ob.getCustomerName(), ob.getForwarderId(), ob.getForwarderName()).getOutbounds().add(ob);
        }
        Map<Long, String> forwarders = support.supplierNames(directs.stream().map(d -> forwarderOfDirect(d.getId())).toList());
        for (DirectRef d : directs) {
            Long f = forwarderOfDirect(d.getId());
            group(groups, d.getCustomerId(), d.getCustomerName(), f, forwarders.get(f)).getDirects().add(d);
        }
        return new ArrayList<>(groups.values());
    }

    private Long forwarderOfDirect(Long shipmentId) {
        return supplierShipmentMapper.selectById(shipmentId).getDirectForwarderId();
    }

    private static PendingGroupVO group(Map<String, PendingGroupVO> groups, Long customerId, String customerName, Long forwarderId,
                                        String forwarderName) {
        return groups.computeIfAbsent(customerId + ":" + forwarderId, k -> {
            PendingGroupVO g = new PendingGroupVO();
            g.setCustomerId(customerId);
            g.setCustomerName(customerName);
            g.setForwarderId(forwarderId);
            g.setForwarderName(forwarderName);
            g.setOutbounds(new ArrayList<>());
            g.setDirects(new ArrayList<>());
            return g;
        });
    }

    // ---------------------------------------------------------------- 新建、修改、作废

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public LogisticsVO create(SaveLogisticsRequest req) {
        if (req.getCustomerId() == null || req.getForwarderId() == null) {
            throw new BizException("请选择客户与货代");
        }
        support.requireForwarder(req.getForwarderId());
        LogisticsShipmentDO sh = new LogisticsShipmentDO();
        sh.setTenantId(PiStore.tenantId());
        sh.setShNo(documentNumberService.next(DocumentType.SH));
        sh.setCustomerId(req.getCustomerId());
        sh.setForwarderId(req.getForwarderId());
        sh.setOwnerId(0L);
        sh.setStatus(LogisticsConstants.SH_PENDING);
        sh.setCarrier("");
        sh.setWaybillNo("");
        sh.setReconciled(0);
        sh.setVoidReason("");
        sh.setNote(req.getNote() == null ? "" : req.getNote().trim());
        shipmentMapper.insert(sh);
        attach(sh, req.getOutboundIds(), req.getDirectShipmentIds());
        logService.recordOperateLog(LogisticsConstants.MENU_SHIPMENT, "新建出运单", null, Map.of("shNo", sh.getShNo()));
        return detail(sh.getId());
    }

    /** 修改出运单里的货（待出运时）：以传入的出库单与直发货为准 */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public LogisticsVO updateItems(Long id, SaveLogisticsRequest req) {
        LogisticsShipmentDO sh = lockPending(id);
        Set<Long> keepOb = new LinkedHashSet<>(nn(req.getOutboundIds()));
        Set<Long> keepDirect = new LinkedHashSet<>(nn(req.getDirectShipmentIds()));
        for (OutboundOrderDO ob : outboundsOf(id)) {
            if (!keepOb.remove(ob.getId())) {
                if (ob.getSource() == LogisticsConstants.OB_DIRECT && keepDirect.contains(ob.getSupplierShipmentId())) {
                    continue;
                }
                ob.setLogisticsId(null);
                outboundMapper.updateById(ob);
            }
        }
        for (SupplierShipmentDO s : directsOf(id)) {
            if (!keepDirect.remove(s.getId())) {
                s.setLogisticsId(null);
                supplierShipmentMapper.updateById(s);
                outboundMapper.selectList(new LambdaQueryWrapper<OutboundOrderDO>()
                                .eq(OutboundOrderDO::getSupplierShipmentId, s.getId())
                                .eq(OutboundOrderDO::getLogisticsId, id)
                                .isNull(OutboundOrderDO::getDeletedAt))
                        .forEach(ob -> {
                            ob.setLogisticsId(null);
                            outboundMapper.updateById(ob);
                        });
            }
        }
        if (req.getNote() != null) {
            sh.setNote(req.getNote().trim());
            shipmentMapper.updateById(sh);
        }
        attach(sh, keepOb, keepDirect);
        if (outboundsOf(id).isEmpty() && directsOf(id).isEmpty()) {
            throw new BizException("出运单里至少要有一张出库单或直发货；不要了请作废");
        }
        return detail(id);
    }

    /** 把出库单与直发货放进出运单：同一客户、同一货代、已交货代（直发货在途）、还没放进别的出运单 */
    private void attach(LogisticsShipmentDO sh, Collection<Long> outboundIds, Collection<Long> directIds) {
        List<Long> obIds = nn(outboundIds).stream().distinct().sorted().toList();
        List<Long> dIds = nn(directIds).stream().distinct().sorted().toList();
        for (Long obId : obIds) {
            OutboundOrderDO ob = outbounds.visible(obId, true);
            ob = outbounds.lock(obId);
            if (ob.getStatus() != LogisticsConstants.OB_HANDED) {
                throw new BizException(ob.getObNo() + " 还没交货代");
            }
            if (ob.getLogisticsId() != null) {
                throw new BizException(ob.getObNo() + " 已在别的出运单上");
            }
            if (!Objects.equals(ob.getCustomerId(), sh.getCustomerId())) {
                throw new BizException("出运单只能放同一个客户的货");
            }
            if (!Objects.equals(ob.getForwarderId(), sh.getForwarderId())) {
                throw new BizException(ob.getObNo() + " 交的是别的货代");
            }
            ob.setLogisticsId(sh.getId());
            outboundMapper.updateById(ob);
            ownerFrom(sh, ob.getOwnerId());
        }
        for (Long dId : dIds) {
            SupplierShipmentDO s = supplierShipmentMapper.selectById(dId);
            if (s == null || s.getDeletedAt() != null || !Objects.equals(s.getTenantId(), PiStore.tenantId()) || s.getDirectForwarderId() == null) {
                throw new BizException("直发货不存在");
            }
            if (!Objects.equals(s.getDirectForwarderId(), sh.getForwarderId())) {
                throw new BizException(s.getSdNo() + " 发往的是别的货代");
            }
            if (s.getLogisticsId() != null && !Objects.equals(s.getLogisticsId(), sh.getId())) {
                throw new BizException(s.getSdNo() + " 已在别的出运单上");
            }
            if (s.getStatus() != WarehouseConstants.SHIP_IN_TRANSIT && s.getLogisticsId() == null) {
                throw new BizException(s.getSdNo() + (s.getStatus() == WarehouseConstants.SHIP_VOID ? " 已作废" : " 已经确认过了"));
            }
            List<Long> soItemIds = supplierShipments.itemsOf(List.of(dId)).getOrDefault(dId, List.of()).stream()
                    .map(SupplierShipmentItemDO::getSoItemId).toList();
            Set<Long> customers = supplierShipments.directCustomers(soItemIds);
            if (!customers.equals(Set.of(sh.getCustomerId()))) {
                throw new BizException("出运单只能放同一个客户的货");
            }
            s.setLogisticsId(sh.getId());
            supplierShipmentMapper.updateById(s);
            soItemIds.stream().findFirst().map(soItemMapper::selectById).map(i -> soMapper.selectById(i.getSoId()))
                    .ifPresent(o -> ownerFrom(sh, o.getOwnerId()));
        }
        if (obIds.isEmpty() && dIds.isEmpty() && outboundsOf(sh.getId()).isEmpty() && directsOf(sh.getId()).isEmpty()) {
            throw new BizException("请勾选要出运的货");
        }
    }

    private void ownerFrom(LogisticsShipmentDO sh, Long ownerId) {
        if ((sh.getOwnerId() == null || sh.getOwnerId() == 0) && ownerId != null) {
            sh.setOwnerId(ownerId);
            shipmentMapper.updateById(sh);
        }
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public LogisticsVO voidShipment(Long id, String reason) {
        LogisticsShipmentDO sh = lockPending(id);
        for (OutboundOrderDO ob : outboundsOf(id)) {
            ob.setLogisticsId(null);
            outboundMapper.updateById(ob);
        }
        for (SupplierShipmentDO s : directsOf(id)) {
            s.setLogisticsId(null);
            supplierShipmentMapper.updateById(s);
        }
        docGroupMapper.selectList(liveGroups(id)).forEach(g -> {
            g.setStatus(LogisticsConstants.DOC_VOID);
            docGroupMapper.updateById(g);
        });
        sh.setStatus(LogisticsConstants.SH_VOID);
        sh.setVoidReason(reason.trim());
        shipmentMapper.updateById(sh);
        logService.recordOperateLog(LogisticsConstants.MENU_SHIPMENT, "作废出运单", Map.of("shNo", sh.getShNo(), "status", "待出运"),
                Map.of("shNo", sh.getShNo(), "status", "已作废", "reason", sh.getVoidReason()));
        return detail(id);
    }

    // ---------------------------------------------------------------- 直发货确认

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public LogisticsVO confirmDirect(Long id, Long shipmentId, ConfirmDirectRequest req) {
        LogisticsShipmentDO sh = lockPending(id);
        SupplierShipmentDO s = supplierShipmentMapper.selectById(shipmentId);
        if (s == null || !Objects.equals(s.getLogisticsId(), id)) {
            throw new BizException("直发货不在这张出运单上");
        }
        Map<Long, Integer> received = new HashMap<>();
        req.getLines().forEach(l -> received.merge(l.getShipmentItemId(), l.getQuantity(), Integer::sum));
        if (received.values().stream().mapToInt(Integer::intValue).sum() <= 0) {
            throw new BizException("货代一件都没收到时请把它从出运单里移出，让采购员处理");
        }
        Map<Long, com.zhul.erp.modules.warehouse.entity.PurchaseReceiptItemDO> receiptItems =
                receipts.receiveDirect(shipmentId, LocalDate.now(), received);
        // 直发出库单：已交货代，没有国内快递，箱规按货代量的尺寸重量
        OutboundOrderDO ob = new OutboundOrderDO();
        ob.setTenantId(sh.getTenantId());
        ob.setObNo(documentNumberService.next(DocumentType.OB));
        Map<Long, SupplierShipmentItemDO> shipItems = supplierShipments.itemsOf(List.of(shipmentId)).getOrDefault(shipmentId, List.of())
                .stream().collect(Collectors.toMap(SupplierShipmentItemDO::getId, x -> x));
        SalesOrderItemDO firstItem = soItemMapper.selectById(shipItems.values().iterator().next().getSoItemId());
        SalesOrderDO so = soMapper.selectById(firstItem.getSoId());
        ob.setSoId(so.getId());
        ob.setCustomerId(so.getCustomerId());
        ob.setOwnerId(so.getOwnerId() == null ? 0L : so.getOwnerId());
        ob.setForwarderId(sh.getForwarderId());
        ob.setSource(LogisticsConstants.OB_DIRECT);
        ob.setStatus(LogisticsConstants.OB_HANDED);
        ob.setLogisticsId(id);
        ob.setSupplierShipmentId(shipmentId);
        ob.setNote("直发货代 " + s.getSdNo());
        ob.setWithdrawReason("");
        ob.setPackedBy(currentUser.resolve());
        ob.setPackedAt(LocalDateTime.now());
        outboundMapper.insert(ob);
        Map<Long, Long> itemByShipItem = new HashMap<>();
        List<OutboundOrderItemDO> items = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : received.entrySet()) {
            SupplierShipmentItemDO si = shipItems.get(e.getKey());
            if (si == null || e.getValue() <= 0) {
                continue;
            }
            SalesOrderItemDO soi = soItemMapper.selectById(si.getSoItemId());
            SalesOrderDO o = soMapper.selectById(soi.getSoId());
            OutboundOrderItemDO x = new OutboundOrderItemDO();
            x.setTenantId(ob.getTenantId());
            x.setOutboundId(ob.getId());
            x.setSoItemId(si.getSoItemId());
            x.setModel(si.getModel());
            x.setBrand(si.getBrand());
            x.setQuantity(e.getValue());
            x.setUnitPriceCny((soi.getUnitPrice() == null ? BigDecimal.ZERO : soi.getUnitPrice())
                    .multiply(o.getExchangeRate() == null ? BigDecimal.ONE : o.getExchangeRate()));
            outboundItemMapper.insert(x);
            items.add(x);
            itemByShipItem.put(si.getId(), x.getId());
        }
        List<PackRequest.BoxLine> boxes = req.getBoxes().stream().map(b -> {
            PackRequest.BoxLine x = new PackRequest.BoxLine();
            x.setLength(b.getLength());
            x.setWidth(b.getWidth());
            x.setHeight(b.getHeight());
            x.setGrossWeight(b.getGrossWeight());
            x.setNetWeight(b.getNetWeight());
            x.setItems(b.getItems().stream().map(bi -> {
                Long obItem = itemByShipItem.get(bi.getShipmentItemId());
                if (obItem == null) {
                    throw new BizException("箱子里有没收到的型号");
                }
                PackRequest.BoxItemLine l = new PackRequest.BoxItemLine();
                l.setOutboundItemId(obItem);
                l.setQuantity(bi.getQuantity());
                return l;
            }).toList());
            return x;
        }).toList();
        outbounds.writeBoxes(ob, items, boxes);
        progress.sync(items.stream().map(OutboundOrderItemDO::getSoItemId).toList());
        logService.recordOperateLog(LogisticsConstants.MENU_SHIPMENT, "确认直发货", null, Map.of("shNo", sh.getShNo(), "sdNo", s.getSdNo(),
                "obNo", ob.getObNo(), "received", receiptItems.size()));
        return detail(id);
    }

    // ---------------------------------------------------------------- 登记出运

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public LogisticsVO ship(Long id, ShipRequest req) {
        LogisticsShipmentDO sh = lockPending(id);
        long unconfirmed = directsOf(id).stream().filter(s -> s.getStatus() == WarehouseConstants.SHIP_IN_TRANSIT).count();
        if (unconfirmed > 0) {
            throw new BizException("还有 " + unconfirmed + " 批直发货没确认实收");
        }
        List<OutboundOrderDO> obs = outboundsOf(id);
        if (obs.isEmpty()) {
            throw new BizException("出运单里没有货");
        }
        applyWaybill(sh, req);
        sh.setStatus(LogisticsConstants.SH_SHIPPED);
        shipmentMapper.updateById(sh);
        freight.allocateInternational(id, sh.getFreight(), obs.stream().map(OutboundOrderDO::getId).toList(), support.divisorOf(sh.getForwarderId()));
        progress.sync(outbounds.soItemIds(obs.stream().map(OutboundOrderDO::getId).toList()));
        logService.recordOperateLog(LogisticsConstants.MENU_SHIPMENT, "登记出运", null, Map.of("shNo", sh.getShNo(),
                "waybill", sh.getCarrier() + " " + sh.getWaybillNo(), "freight", "CNY " + sh.getFreight()));
        return detail(id);
    }

    /** 已出运后修改运单号、面单与运费（对账后运费不能改） */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public LogisticsVO updateShipping(Long id, ShipRequest req) {
        visible(id);
        shipmentMapper.lockById(id);
        LogisticsShipmentDO sh = visible(id);
        if (sh.getStatus() != LogisticsConstants.SH_SHIPPED) {
            throw new BizException("出运单还没出运");
        }
        BigDecimal before = sh.getFreight();
        BigDecimal after = req.getFreight().setScale(2, RoundingMode.HALF_UP);
        if (Objects.equals(sh.getReconciled(), 1) && before != null && before.compareTo(after) != 0) {
            throw new BizException("已对账的出运单不能改运费");
        }
        applyWaybill(sh, req);
        shipmentMapper.updateById(sh);
        if (before == null || before.compareTo(after) != 0) {
            freight.allocateInternational(id, after, outboundsOf(id).stream().map(OutboundOrderDO::getId).toList(), support.divisorOf(sh.getForwarderId()));
        }
        logService.recordOperateLog(LogisticsConstants.MENU_SHIPMENT, "修改运单", Map.of("shNo", sh.getShNo(), "freight", String.valueOf(before)),
                Map.of("shNo", sh.getShNo(), "waybill", sh.getCarrier() + " " + sh.getWaybillNo(), "freight", String.valueOf(after)));
        return detail(id);
    }

    /** 对账确认时改运费并重新分摊（对账服务在自己的事务内调用） */
    public void reconcile(Long id, BigDecimal amount) {
        shipmentMapper.lockById(id);
        LogisticsShipmentDO sh = shipmentMapper.selectById(id);
        if (amount != null && (sh.getFreight() == null || sh.getFreight().compareTo(amount) != 0)) {
            sh.setFreight(amount);
            freight.allocateInternational(id, amount, outboundsOf(id).stream().map(OutboundOrderDO::getId).toList(), support.divisorOf(sh.getForwarderId()));
        }
        sh.setReconciled(1);
        shipmentMapper.updateById(sh);
    }

    private void applyWaybill(LogisticsShipmentDO sh, ShipRequest req) {
        if (req.getShippedDate().isAfter(LocalDate.now())) {
            throw new BizException("出运日期不能晚于今天");
        }
        sh.setCarrier(req.getCarrier().trim());
        sh.setWaybillNo(req.getWaybillNo().trim());
        sh.setShippedDate(req.getShippedDate());
        if (!Objects.equals(sh.getReconciled(), 1)) {
            sh.setFreight(req.getFreight().setScale(2, RoundingMode.HALF_UP));
        }
        attachments.sync(AttachmentService.LOGISTICS, sh.getId(), req.getAttachmentIds());
    }

    // ---------------------------------------------------------------- 查询

    public PageResult<LogisticsVO> page(LogisticsPageQuery q) {
        LambdaQueryWrapper<LogisticsShipmentDO> w = scoped();
        if (q.getStatus() != null) {
            w.eq(LogisticsShipmentDO::getStatus, q.getStatus());
        }
        if (q.getForwarderId() != null) {
            w.eq(LogisticsShipmentDO::getForwarderId, q.getForwarderId());
        }
        if (q.getShippedFrom() != null) {
            w.ge(LogisticsShipmentDO::getShippedDate, q.getShippedFrom());
        }
        if (q.getShippedTo() != null) {
            w.le(LogisticsShipmentDO::getShippedDate, q.getShippedTo());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            Set<Long> ids = keywordIds(kw);
            w.and(x -> {
                x.like(LogisticsShipmentDO::getShNo, kw).or().like(LogisticsShipmentDO::getWaybillNo, kw);
                if (!ids.isEmpty()) {
                    x.or().in(LogisticsShipmentDO::getId, ids);
                }
            });
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = shipmentMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        w.last("ORDER BY update_time DESC, id DESC LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toVos(shipmentMapper.selectList(w), false));
    }

    public LogisticsVO detail(Long id) {
        return toVos(List.of(visible(id)), true).get(0);
    }

    private List<LogisticsVO> toVos(List<LogisticsShipmentDO> rows, boolean full) {
        List<Long> ids = rows.stream().map(LogisticsShipmentDO::getId).toList();
        Map<Long, List<OutboundOrderDO>> obs = ids.isEmpty() ? Map.of()
                : outboundMapper.selectList(new LambdaQueryWrapper<OutboundOrderDO>()
                        .in(OutboundOrderDO::getLogisticsId, ids)
                        .isNull(OutboundOrderDO::getDeletedAt)
                        .orderByAsc(OutboundOrderDO::getId))
                .stream().collect(Collectors.groupingBy(OutboundOrderDO::getLogisticsId));
        List<Long> obIds = obs.values().stream().flatMap(List::stream).map(OutboundOrderDO::getId).toList();
        Map<Long, List<OutboundBoxDO>> boxes = obIds.isEmpty() ? Map.of()
                : boxMapper.selectList(new LambdaQueryWrapper<OutboundBoxDO>().in(OutboundBoxDO::getOutboundId, obIds).isNull(OutboundBoxDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(OutboundBoxDO::getOutboundId));
        Map<Long, SalesOrderDO> orders = support.orders(obs.values().stream().flatMap(List::stream).map(OutboundOrderDO::getSoId).toList());
        Map<Long, String> customers = support.customerNames(rows.stream().map(LogisticsShipmentDO::getCustomerId).toList());
        Map<Long, String> forwarders = support.supplierNames(rows.stream().map(LogisticsShipmentDO::getForwarderId).toList());
        Map<Long, String> users = support.userNames(rows.stream().map(LogisticsShipmentDO::getOwnerId).toList());
        List<LogisticsVO> out = new ArrayList<>(rows.size());
        for (LogisticsShipmentDO sh : rows) {
            BigDecimal divisor = support.divisorOf(sh.getForwarderId());
            List<OutboundOrderDO> mine = obs.getOrDefault(sh.getId(), List.of());
            LogisticsVO vo = new LogisticsVO();
            vo.setId(sh.getId());
            vo.setShNo(sh.getShNo());
            vo.setCustomerId(sh.getCustomerId());
            vo.setCustomerName(customers.get(sh.getCustomerId()));
            vo.setForwarderId(sh.getForwarderId());
            vo.setForwarderName(forwarders.get(sh.getForwarderId()));
            vo.setVolumeDivisor(divisor.intValue());
            vo.setOwnerId(sh.getOwnerId());
            vo.setOwnerName(users.get(sh.getOwnerId()));
            vo.setStatus(sh.getStatus());
            vo.setStatusName(LogisticsConstants.SH_STATUS_NAMES.get(sh.getStatus()));
            vo.setCarrier(sh.getCarrier());
            vo.setWaybillNo(sh.getWaybillNo());
            vo.setShippedDate(sh.getShippedDate());
            vo.setFreight(sh.getFreight());
            vo.setReconciled(Objects.equals(sh.getReconciled(), 1));
            vo.setVoidReason(sh.getVoidReason());
            vo.setNote(sh.getNote());
            vo.setOutboundCount(mine.size());
            Map<Long, String> soNos = new LinkedHashMap<>();
            mine.forEach(o -> soNos.putIfAbsent(o.getSoId(), orders.get(o.getSoId()) == null ? null : orders.get(o.getSoId()).getSoNo()));
            vo.setOrders(soNos.entrySet().stream().map(e -> {
                LogisticsVO.SoRef r = new LogisticsVO.SoRef();
                r.setId(e.getKey());
                r.setSoNo(e.getValue());
                return r;
            }).toList());
            List<OutboundBoxDO> bs = mine.stream().flatMap(o -> boxes.getOrDefault(o.getId(), List.of()).stream()).toList();
            vo.setBoxCount(bs.size());
            Map<Long, BigDecimal> chargeable = new LinkedHashMap<>();
            bs.forEach(b -> chargeable.put(b.getId(), FreightService.chargeable(b, divisor)));
            vo.setChargeableWeight(chargeable.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
            if (full) {
                vo.setOutbounds(outbounds.toVos(mine));
                vo.setBoxChargeable(chargeable);
                vo.setBoxFreight(freight.byBox(LogisticsConstants.FREIGHT_INTERNATIONAL, sh.getId()));
                vo.setDirects(directRefs(directsOf(sh.getId()), false));
                vo.setDocGroups(docGroups(sh.getId()));
                vo.setFaceSheets(attachments.listOf(AttachmentService.LOGISTICS, sh.getId()));
            }
            vo.setCreateTime(sh.getCreateTime());
            vo.setCreateBy(sh.getCreateBy());
            vo.setUpdateTime(sh.getUpdateTime());
            vo.setUpdateBy(sh.getUpdateBy());
            out.add(vo);
        }
        return out;
    }

    private List<LogisticsVO.DocGroup> docGroups(Long id) {
        List<ShipmentDocGroupDO> groups = docGroupMapper.selectList(liveGroups(id).orderByAsc(ShipmentDocGroupDO::getId));
        Map<Long, String> soNos = new HashMap<>();
        support.orders(groups.stream().flatMap(g -> parseIds(g.getSoIds()).stream()).toList()).forEach((k, v) -> soNos.put(k, v.getSoNo()));
        return groups.stream().map(g -> {
            LogisticsVO.DocGroup x = new LogisticsVO.DocGroup();
            x.setId(g.getId());
            x.setCiNo(g.getCiNo());
            x.setPlNo(g.getPlNo());
            x.setPaymentRef(g.getPaymentRef());
            x.setSoNos(parseIds(g.getSoIds()).stream().map(soNos::get).toList());
            return x;
        }).toList();
    }

    /** 直发货的展示信息；pendingOnly 时按业务员数据权限过滤 */
    private List<DirectRef> directRefs(List<SupplierShipmentDO> rows, boolean scoped) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(SupplierShipmentDO::getId).toList();
        Map<Long, List<SupplierShipmentItemDO>> items = supplierShipments.itemsOf(ids);
        Map<Long, SalesOrderItemDO> soItems = new HashMap<>();
        List<Long> soItemIds = items.values().stream().flatMap(List::stream).map(SupplierShipmentItemDO::getSoItemId).distinct().toList();
        if (!soItemIds.isEmpty()) {
            soItemMapper.selectBatchIds(soItemIds).forEach(i -> soItems.put(i.getId(), i));
        }
        Map<Long, SalesOrderDO> orders = support.orders(soItems.values().stream().map(SalesOrderItemDO::getSoId).toList());
        Map<Long, PurchaseOrderDO> pos = receivingSupport.pos(rows.stream().map(SupplierShipmentDO::getPoId).toList());
        Map<Long, String> suppliers = receivingSupport.counterparties(pos.values());
        Map<Long, Long> outboundOf = new HashMap<>();
        outboundMapper.selectList(new LambdaQueryWrapper<OutboundOrderDO>()
                        .in(OutboundOrderDO::getSupplierShipmentId, ids)
                        .isNull(OutboundOrderDO::getDeletedAt))
                .forEach(o -> outboundOf.put(o.getSupplierShipmentId(), o.getId()));
        Map<Long, String> customers = support.customerNames(orders.values().stream().map(SalesOrderDO::getCustomerId).toList());
        List<DirectRef> out = new ArrayList<>();
        for (SupplierShipmentDO s : rows) {
            List<SupplierShipmentItemDO> lines = items.getOrDefault(s.getId(), List.of());
            List<SalesOrderDO> os = lines.stream().map(l -> soItems.get(l.getSoItemId())).filter(Objects::nonNull)
                    .map(i -> orders.get(i.getSoId())).filter(Objects::nonNull).distinct().toList();
            if (scoped && os.stream().noneMatch(o -> support.scope().canSee(o.getOwnerId()))) {
                continue;
            }
            DirectRef d = new DirectRef();
            d.setId(s.getId());
            d.setSdNo(s.getSdNo());
            d.setPoId(s.getPoId());
            d.setPoNo(pos.get(s.getPoId()) == null ? null : pos.get(s.getPoId()).getPoNo());
            d.setSupplierName(suppliers.get(s.getPoId()));
            d.setCustomerId(os.isEmpty() ? null : os.get(0).getCustomerId());
            d.setCustomerName(os.isEmpty() ? null : customers.get(os.get(0).getCustomerId()));
            d.setSoNos(os.stream().map(SalesOrderDO::getSoNo).toList());
            d.setCarrier(s.getCarrier());
            d.setTrackingNo(s.getTrackingNo());
            d.setShipDate(s.getShipDate());
            d.setExpectedArrivalDate(s.getExpectedArrivalDate());
            d.setConfirmed(s.getStatus() == WarehouseConstants.SHIP_RECEIVED);
            d.setOutboundId(outboundOf.get(s.getId()));
            d.setItems(lines.stream().map(l -> {
                DirectRef.Line x = new DirectRef.Line();
                x.setShipmentItemId(l.getId());
                x.setModel(l.getModel());
                x.setBrand(l.getBrand());
                x.setQuantity(l.getQuantity());
                return x;
            }).toList());
            out.add(d);
        }
        return out;
    }

    // ---------------------------------------------------------------- 通用

    public List<OutboundOrderDO> outboundsOf(Long id) {
        return outboundMapper.selectList(new LambdaQueryWrapper<OutboundOrderDO>()
                .eq(OutboundOrderDO::getLogisticsId, id)
                .isNull(OutboundOrderDO::getDeletedAt)
                .orderByAsc(OutboundOrderDO::getId));
    }

    private List<SupplierShipmentDO> directsOf(Long id) {
        return supplierShipmentMapper.selectList(new LambdaQueryWrapper<SupplierShipmentDO>()
                .eq(SupplierShipmentDO::getLogisticsId, id)
                .isNull(SupplierShipmentDO::getDeletedAt)
                .orderByAsc(SupplierShipmentDO::getId));
    }

    private LambdaQueryWrapper<ShipmentDocGroupDO> liveGroups(Long id) {
        return new LambdaQueryWrapper<ShipmentDocGroupDO>()
                .eq(ShipmentDocGroupDO::getLogisticsId, id)
                .eq(ShipmentDocGroupDO::getStatus, LogisticsConstants.DOC_VALID)
                .isNull(ShipmentDocGroupDO::getDeletedAt);
    }

    private LambdaQueryWrapper<LogisticsShipmentDO> scoped() {
        LambdaQueryWrapper<LogisticsShipmentDO> w = new LambdaQueryWrapper<LogisticsShipmentDO>()
                .eq(LogisticsShipmentDO::getTenantId, PiStore.tenantId())
                .isNull(LogisticsShipmentDO::getDeletedAt);
        support.scope().apply(w, LogisticsShipmentDO::getOwnerId);
        return w;
    }

    private Set<Long> keywordIds(String kw) {
        Set<Long> ids = new LinkedHashSet<>();
        List<Long> soIds = soMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                        .select(SalesOrderDO::getId)
                        .eq(SalesOrderDO::getTenantId, PiStore.tenantId())
                        .like(SalesOrderDO::getSoNo, kw)
                        .last("LIMIT 500"))
                .stream().map(SalesOrderDO::getId).toList();
        if (!soIds.isEmpty()) {
            outboundMapper.selectList(new LambdaQueryWrapper<OutboundOrderDO>().select(OutboundOrderDO::getLogisticsId)
                            .in(OutboundOrderDO::getSoId, soIds).isNotNull(OutboundOrderDO::getLogisticsId))
                    .forEach(o -> ids.add(o.getLogisticsId()));
        }
        List<Long> all = shipmentMapper.selectList(new LambdaQueryWrapper<LogisticsShipmentDO>()
                        .select(LogisticsShipmentDO::getCustomerId)
                        .eq(LogisticsShipmentDO::getTenantId, PiStore.tenantId())
                        .groupBy(LogisticsShipmentDO::getCustomerId))
                .stream().map(LogisticsShipmentDO::getCustomerId).toList();
        List<Long> matched = support.customerNames(all).entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().toLowerCase().contains(kw.toLowerCase())).map(Map.Entry::getKey).toList();
        if (!matched.isEmpty()) {
            shipmentMapper.selectList(new LambdaQueryWrapper<LogisticsShipmentDO>().select(LogisticsShipmentDO::getId)
                    .in(LogisticsShipmentDO::getCustomerId, matched)).forEach(s -> ids.add(s.getId()));
        }
        return ids;
    }

    public LogisticsShipmentDO visible(Long id) {
        LogisticsShipmentDO sh = id == null ? null : shipmentMapper.selectById(id);
        if (sh == null || sh.getDeletedAt() != null || !Objects.equals(sh.getTenantId(), PiStore.tenantId())
                || !support.scope().canSee(sh.getOwnerId())) {
            throw new BizException("出运单不存在");
        }
        return sh;
    }

    private LogisticsShipmentDO lockPending(Long id) {
        visible(id);
        shipmentMapper.lockById(id);
        LogisticsShipmentDO sh = visible(id);
        if (sh.getStatus() != LogisticsConstants.SH_PENDING) {
            throw new BizException(sh.getStatus() == LogisticsConstants.SH_SHIPPED ? "出运单已出运" : "出运单已作废");
        }
        return sh;
    }

    public static List<Long> parseIds(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(",")).map(String::trim).filter(StringUtils::hasText).map(Long::valueOf).toList();
    }

    private static <T> List<T> nn(Collection<T> c) {
        return c == null ? List.of() : new ArrayList<>(c);
    }
}

package com.zhul.erp.modules.logistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.dto.CountsVO;
import com.zhul.erp.modules.logistics.dto.HandOverRequest;
import com.zhul.erp.modules.logistics.dto.NoticeFormVO;
import com.zhul.erp.modules.logistics.dto.OutboundPageQuery;
import com.zhul.erp.modules.logistics.dto.OutboundVO;
import com.zhul.erp.modules.logistics.dto.PackRequest;
import com.zhul.erp.modules.logistics.dto.SaveNoticeRequest;
import com.zhul.erp.modules.logistics.entity.CourierWaybillDO;
import com.zhul.erp.modules.logistics.entity.OutboundBoxDO;
import com.zhul.erp.modules.logistics.entity.OutboundBoxItemDO;
import com.zhul.erp.modules.logistics.entity.OutboundOrderDO;
import com.zhul.erp.modules.logistics.entity.OutboundOrderItemDO;
import com.zhul.erp.modules.logistics.repository.CourierWaybillMapper;
import com.zhul.erp.modules.logistics.repository.LogisticsShipmentMapper;
import com.zhul.erp.modules.logistics.repository.OutboundBoxItemMapper;
import com.zhul.erp.modules.logistics.repository.OutboundBoxMapper;
import com.zhul.erp.modules.logistics.repository.OutboundOrderItemMapper;
import com.zhul.erp.modules.logistics.repository.OutboundOrderMapper;
import com.zhul.erp.modules.logistics.support.LogisticsQty;
import com.zhul.erp.modules.logistics.support.LogisticsSupport;
import com.zhul.erp.modules.purchase.support.OrderPurchaseProgress;
import com.zhul.erp.modules.purchase.support.PurchaseLinks;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
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
 * 销售出库单：业务员发货通知（按订单所属业务员的数据权限），仓库打包、交国内快递（仓库看全部）。
 * 事务边界：发货通知 = 出库单与行；交国内快递 = 快递单、出库单状态、国内运费分摊、订单进度；
 * 撤销交货代 = 出库单状态、快递单（全部撤销时作废）、分摊重算、订单进度。
 * 写操作用读已提交并先锁订单或出库单：拿到锁后读到别的事务已提交的出库数量。
 */
@Service
@RequiredArgsConstructor
public class OutboundService {

    private final OutboundOrderMapper outboundMapper;
    private final OutboundOrderItemMapper itemMapper;
    private final OutboundBoxMapper boxMapper;
    private final OutboundBoxItemMapper boxItemMapper;
    private final CourierWaybillMapper waybillMapper;
    private final LogisticsShipmentMapper shipmentMapper;
    private final SalesOrderMapper soMapper;
    private final SalesOrderItemMapper soItemMapper;
    private final LogisticsSupport support;
    private final LogisticsQty logisticsQty;
    private final PurchaseLinks purchaseLinks;
    private final FreightService freight;
    private final OrderPurchaseProgress progress;
    private final DocumentNumberService documentNumberService;
    private final CurrentUserResolver currentUser;
    private final LogService logService;

    // ---------------------------------------------------------------- 发货通知

    /** 发货通知表单：订单各型号的可出库数量；修改时传 outboundId */
    public NoticeFormVO noticeForm(Long soId, Long outboundId) {
        OutboundOrderDO ob = outboundId == null ? null : visible(outboundId, true);
        SalesOrderDO o = support.order(ob == null ? soId : ob.getSoId(), true);
        List<SalesOrderItemDO> items = soItems(o.getId());
        Map<Long, Integer> mine = ob == null ? Map.of() : itemsOf(List.of(ob.getId())).getOrDefault(ob.getId(), List.of()).stream()
                .collect(Collectors.toMap(OutboundOrderItemDO::getSoItemId, OutboundOrderItemDO::getQuantity, Integer::sum));
        List<Long> ids = items.stream().map(SalesOrderItemDO::getId).toList();
        Map<Long, PurchaseLinks.ItemPurchase> links = purchaseLinks.forSoItems(ids);
        Map<Long, LogisticsQty.Qty> out = logisticsQty.bySoItem(ids);
        NoticeFormVO vo = new NoticeFormVO();
        vo.setSoId(o.getId());
        vo.setSoNo(o.getSoNo());
        vo.setCustomerName(support.customerNames(List.of(o.getCustomerId())).get(o.getCustomerId()));
        vo.setLines(items.stream().map(i -> {
            PurchaseLinks.ItemPurchase lp = links.get(i.getId());
            boolean tracked = lp != null && lp.tracked();
            int occupied = out.getOrDefault(i.getId(), LogisticsQty.Qty.ZERO).occupied();
            int own = mine.getOrDefault(i.getId(), 0);
            int received = tracked ? Math.min(lp.received(), i.getQuantity()) : i.getQuantity();
            NoticeFormVO.Line l = new NoticeFormVO.Line();
            l.setSoItemId(i.getId());
            l.setModel(i.getModel());
            l.setBrand(i.getBrand());
            l.setQuantity(i.getQuantity());
            l.setTracked(tracked);
            l.setReceived(tracked ? lp.received() : null);
            l.setOccupied(occupied - own);
            l.setAvailable(Math.max(0, received - occupied) + own);
            l.setCurrent(own);
            if (tracked) {
                PurchaseLinks.Goods g = lp.goods(i.getQuantity());
                l.setInTransit(g.inTransit());
                l.setPendingShip(g.pendingShip());
                l.setEarliestArrival(lp.transits().stream().map(PurchaseLinks.Transit::expectedArrival).filter(Objects::nonNull)
                        .min(LocalDate::compareTo).orElse(null));
            }
            return l;
        }).toList());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public OutboundVO createNotice(SaveNoticeRequest req) {
        SalesOrderDO o = lockOrder(req.getSoId());
        support.requireForwarder(req.getForwarderId());
        // 先校验数量再取号，校验不过不占编号
        List<OutboundOrderItemDO> lines = buildItems(o, req.getItems(), Map.of());
        OutboundOrderDO ob = new OutboundOrderDO();
        ob.setTenantId(PiStore.tenantId());
        ob.setObNo(documentNumberService.next(DocumentType.OB));
        ob.setSoId(o.getId());
        ob.setCustomerId(o.getCustomerId());
        ob.setOwnerId(o.getOwnerId() == null ? 0L : o.getOwnerId());
        ob.setForwarderId(req.getForwarderId());
        ob.setSource(LogisticsConstants.OB_NOTICE);
        ob.setStatus(LogisticsConstants.OB_PENDING);
        ob.setNote(trim(req.getNote()));
        ob.setWithdrawReason("");
        outboundMapper.insert(ob);
        String text = saveItems(ob, lines);
        logService.recordOperateLog("销售订单", "发货通知", null, Map.of("soNo", o.getSoNo(), "obNo", ob.getObNo(), "items", text));
        return detail(ob.getId(), true);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public OutboundVO updateNotice(Long id, SaveNoticeRequest req) {
        OutboundOrderDO ob = visible(id, true);
        SalesOrderDO o = lockOrder(ob.getSoId());
        ob = lock(id);
        if (ob.getStatus() != LogisticsConstants.OB_PENDING) {
            throw new BizException("仓库已经打包，不能修改发货通知");
        }
        support.requireForwarder(req.getForwarderId());
        List<OutboundOrderItemDO> old = itemsOf(List.of(id)).getOrDefault(id, List.of());
        Map<Long, Integer> own = old.stream().collect(Collectors.toMap(OutboundOrderItemDO::getSoItemId, OutboundOrderItemDO::getQuantity, Integer::sum));
        old.forEach(i -> {
            i.setDeletedAt(LocalDateTime.now());
            itemMapper.updateById(i);
        });
        ob.setForwarderId(req.getForwarderId());
        ob.setNote(trim(req.getNote()));
        outboundMapper.updateById(ob);
        String text = saveItems(ob, buildItems(o, req.getItems(), own));
        logService.recordOperateLog("销售订单", "修改发货通知", null, Map.of("soNo", o.getSoNo(), "obNo", ob.getObNo(), "items", text));
        return detail(id, true);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public OutboundVO withdraw(Long id, String reason) {
        OutboundOrderDO ob = visible(id, true);
        lockOrder(ob.getSoId());
        ob = lock(id);
        if (ob.getStatus() != LogisticsConstants.OB_PENDING) {
            throw new BizException(ob.getStatus() == LogisticsConstants.OB_WITHDRAWN ? "发货通知已撤回" : "仓库已经打包，不能撤回");
        }
        ob.setStatus(LogisticsConstants.OB_WITHDRAWN);
        ob.setWithdrawReason(reason.trim());
        outboundMapper.updateById(ob);
        logService.recordOperateLog("销售订单", "撤回发货通知", Map.of("obNo", ob.getObNo()), Map.of("obNo", ob.getObNo(), "reason", ob.getWithdrawReason()));
        return detail(id, true);
    }

    private String saveItems(OutboundOrderDO ob, List<OutboundOrderItemDO> lines) {
        List<String> text = new ArrayList<>();
        for (OutboundOrderItemDO x : lines) {
            x.setTenantId(ob.getTenantId());
            x.setOutboundId(ob.getId());
            itemMapper.insert(x);
            text.add(x.getModel() + " × " + x.getQuantity());
        }
        return String.join("、", text);
    }

    /** 出库单行（未落库）：有采购需求的不超过 合格入库 − 已出库；系统外采购的不超过 型号数量 − 已出库（own 为本单原数量） */
    private List<OutboundOrderItemDO> buildItems(SalesOrderDO o, List<SaveNoticeRequest.Line> lines, Map<Long, Integer> own) {
        Map<Long, SalesOrderItemDO> soItems = soItems(o.getId()).stream().collect(Collectors.toMap(SalesOrderItemDO::getId, i -> i));
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (SaveNoticeRequest.Line l : lines) {
            if (l.getQuantity() == null || l.getQuantity() <= 0) {
                continue;
            }
            if (!soItems.containsKey(l.getSoItemId())) {
                throw new BizException("型号不属于这张订单");
            }
            if (wanted.put(l.getSoItemId(), l.getQuantity()) != null) {
                throw new BizException("同一个型号不能重复填写");
            }
        }
        if (wanted.isEmpty()) {
            throw new BizException("请勾选要发货的型号");
        }
        Map<Long, PurchaseLinks.ItemPurchase> links = purchaseLinks.forSoItems(wanted.keySet());
        Map<Long, LogisticsQty.Qty> out = logisticsQty.bySoItem(wanted.keySet());
        BigDecimal rate = o.getExchangeRate() == null ? BigDecimal.ONE : o.getExchangeRate();
        List<OutboundOrderItemDO> lines2 = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            SalesOrderItemDO i = soItems.get(e.getKey());
            PurchaseLinks.ItemPurchase lp = links.get(i.getId());
            boolean tracked = lp != null && lp.tracked();
            int base = tracked ? Math.min(lp.received(), i.getQuantity()) : i.getQuantity();
            int max = Math.max(0, base - out.getOrDefault(i.getId(), LogisticsQty.Qty.ZERO).occupied()) + own.getOrDefault(i.getId(), 0);
            if (e.getValue() > max) {
                throw new BizException(max == 0 ? i.getModel() + " 还没有可以出库的数量" : i.getModel() + " 最多可以出库 " + max + " 个");
            }
            OutboundOrderItemDO x = new OutboundOrderItemDO();
            x.setSoItemId(i.getId());
            x.setModel(i.getModel());
            x.setBrand(i.getBrand() == null ? "" : i.getBrand());
            x.setQuantity(e.getValue());
            x.setUnitPriceCny((i.getUnitPrice() == null ? BigDecimal.ZERO : i.getUnitPrice()).multiply(rate));
            lines2.add(x);
        }
        return lines2;
    }

    // ---------------------------------------------------------------- 打包

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public OutboundVO pack(Long id, PackRequest req) {
        visible(id, false);
        OutboundOrderDO ob = lock(id);
        if (ob.getStatus() != LogisticsConstants.OB_PENDING && ob.getStatus() != LogisticsConstants.OB_PACKED) {
            throw new BizException(ob.getStatus() == LogisticsConstants.OB_HANDED ? "已交货代的出库单不能重新打包，请先撤销交货代" : "发货通知已撤回");
        }
        List<OutboundOrderItemDO> items = itemsOf(List.of(id)).getOrDefault(id, List.of());
        writeBoxes(ob, items, req.getBoxes());
        ob.setStatus(LogisticsConstants.OB_PACKED);
        ob.setPackedBy(currentUser.resolve());
        ob.setPackedAt(LocalDateTime.now());
        outboundMapper.updateById(ob);
        logService.recordOperateLog(LogisticsConstants.MENU_OUTBOUND, "打包", null, Map.of("obNo", ob.getObNo(), "boxes", req.getBoxes().size()));
        return detail(id, false);
    }

    /** 替换出库单的箱子：每个型号在各箱的数量之和须等于出库单上的数量 */
    void writeBoxes(OutboundOrderDO ob, List<OutboundOrderItemDO> items, List<PackRequest.BoxLine> boxes) {
        Map<Long, OutboundOrderItemDO> byId = items.stream().collect(Collectors.toMap(OutboundOrderItemDO::getId, i -> i));
        Map<Long, Integer> packed = new HashMap<>();
        for (PackRequest.BoxLine b : boxes) {
            for (PackRequest.BoxItemLine bi : b.getItems()) {
                if (!byId.containsKey(bi.getOutboundItemId())) {
                    throw new BizException("箱内型号不属于这张出库单");
                }
                packed.merge(bi.getOutboundItemId(), bi.getQuantity(), Integer::sum);
            }
        }
        for (OutboundOrderItemDO i : items) {
            int n = packed.getOrDefault(i.getId(), 0);
            if (n < i.getQuantity()) {
                throw new BizException(i.getModel() + " 还差 " + (i.getQuantity() - n) + " 个没装箱");
            }
            if (n > i.getQuantity()) {
                throw new BizException(i.getModel() + " 装箱 " + n + " 个，比出库数量多 " + (n - i.getQuantity()) + " 个");
            }
        }
        List<OutboundBoxDO> old = boxMapper.selectList(new LambdaQueryWrapper<OutboundBoxDO>()
                .eq(OutboundBoxDO::getOutboundId, ob.getId())
                .isNull(OutboundBoxDO::getDeletedAt));
        LocalDateTime now = LocalDateTime.now();
        if (!old.isEmpty()) {
            boxItemMapper.selectList(new LambdaQueryWrapper<OutboundBoxItemDO>()
                            .in(OutboundBoxItemDO::getBoxId, old.stream().map(OutboundBoxDO::getId).toList())
                            .isNull(OutboundBoxItemDO::getDeletedAt))
                    .forEach(x -> {
                        x.setDeletedAt(now);
                        boxItemMapper.updateById(x);
                    });
            old.forEach(x -> {
                x.setDeletedAt(now);
                boxMapper.updateById(x);
            });
        }
        int no = 1;
        for (PackRequest.BoxLine b : boxes) {
            OutboundBoxDO box = new OutboundBoxDO();
            box.setTenantId(ob.getTenantId());
            box.setOutboundId(ob.getId());
            box.setBoxNo(no++);
            box.setLength(b.getLength());
            box.setWidth(b.getWidth());
            box.setHeight(b.getHeight());
            box.setGrossWeight(b.getGrossWeight().setScale(2, RoundingMode.HALF_UP));
            box.setNetWeight(b.getNetWeight() == null ? null : b.getNetWeight().setScale(2, RoundingMode.HALF_UP));
            boxMapper.insert(box);
            for (PackRequest.BoxItemLine bi : b.getItems()) {
                OutboundBoxItemDO x = new OutboundBoxItemDO();
                x.setTenantId(ob.getTenantId());
                x.setBoxId(box.getId());
                x.setOutboundItemId(bi.getOutboundItemId());
                x.setQuantity(bi.getQuantity());
                boxItemMapper.insert(x);
            }
        }
    }

    // ---------------------------------------------------------------- 交国内快递

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public List<OutboundVO> handOver(HandOverRequest req) {
        List<Long> ids = req.getOutboundIds().stream().filter(Objects::nonNull).distinct().sorted().toList();
        List<OutboundOrderDO> obs = new ArrayList<>();
        for (Long id : ids) {
            visible(id, false);
            obs.add(lock(id));
        }
        Set<Long> forwarders = obs.stream().map(OutboundOrderDO::getForwarderId).collect(Collectors.toSet());
        if (forwarders.size() > 1) {
            throw new BizException("只能把同一家货代的出库单合成一票");
        }
        for (OutboundOrderDO ob : obs) {
            if (ob.getStatus() != LogisticsConstants.OB_PACKED) {
                throw new BizException(ob.getObNo() + (ob.getStatus() == LogisticsConstants.OB_PENDING ? " 还没打包" : " 不是待交快递的状态"));
            }
        }
        if (req.getSentDate().isAfter(LocalDate.now())) {
            throw new BizException("发出日期不能晚于今天");
        }
        Long me = currentUser.resolve();
        CourierWaybillDO w = new CourierWaybillDO();
        w.setTenantId(PiStore.tenantId());
        w.setCarrier(req.getCarrier().trim());
        w.setTrackingNo(trim(req.getTrackingNo()));
        w.setSentDate(req.getSentDate());
        w.setFreight(req.getFreight().setScale(2, RoundingMode.HALF_UP));
        w.setPayerId(req.getPayerId() != null ? req.getPayerId() : me == null ? 0L : me);
        w.setForwarderId(forwarders.iterator().next());
        w.setStatus(LogisticsConstants.WAYBILL_VALID);
        w.setUndoReason("");
        waybillMapper.insert(w);
        for (OutboundOrderDO ob : obs) {
            ob.setStatus(LogisticsConstants.OB_HANDED);
            ob.setCourierWaybillId(w.getId());
            outboundMapper.updateById(ob);
        }
        freight.allocateDomestic(w.getId(), w.getFreight(), ids);
        progress.sync(soItemIds(ids));
        logService.recordOperateLog(LogisticsConstants.MENU_OUTBOUND, "交国内快递", null, Map.of("outbounds",
                obs.stream().map(OutboundOrderDO::getObNo).toList(), "tracking", (w.getCarrier() + " " + w.getTrackingNo()).trim(),
                "freight", "CNY " + w.getFreight()));
        return ids.stream().map(id -> detail(id, false)).toList();
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public OutboundVO undoHandOver(Long id, String reason) {
        visible(id, false);
        OutboundOrderDO ob = lock(id);
        if (ob.getStatus() != LogisticsConstants.OB_HANDED || ob.getSource() != LogisticsConstants.OB_NOTICE) {
            throw new BizException("只有已交货代的出库单可以撤销");
        }
        if (ob.getLogisticsId() != null) {
            throw new BizException("出库单已放进出运单，请先从出运单里移出");
        }
        Long waybillId = ob.getCourierWaybillId();
        ob.setStatus(LogisticsConstants.OB_PACKED);
        ob.setCourierWaybillId(null);
        outboundMapper.updateById(ob);
        if (waybillId != null) {
            waybillMapper.lockById(waybillId);
            CourierWaybillDO w = waybillMapper.selectById(waybillId);
            List<Long> rest = outboundMapper.selectList(new LambdaQueryWrapper<OutboundOrderDO>()
                            .select(OutboundOrderDO::getId)
                            .eq(OutboundOrderDO::getCourierWaybillId, waybillId)
                            .isNull(OutboundOrderDO::getDeletedAt))
                    .stream().map(OutboundOrderDO::getId).toList();
            if (rest.isEmpty()) {
                w.setStatus(LogisticsConstants.WAYBILL_UNDONE);
                w.setUndoReason(reason.trim());
                waybillMapper.updateById(w);
                freight.clear(LogisticsConstants.FREIGHT_DOMESTIC, waybillId);
            } else {
                freight.allocateDomestic(waybillId, w.getFreight(), rest);
            }
        }
        progress.sync(soItemIds(List.of(id)));
        logService.recordOperateLog(LogisticsConstants.MENU_OUTBOUND, "撤销交货代", Map.of("obNo", ob.getObNo(), "status", "已交货代"),
                Map.of("obNo", ob.getObNo(), "status", "已打包", "reason", reason.trim()));
        return detail(id, false);
    }

    // ---------------------------------------------------------------- 查询

    public PageResult<OutboundVO> page(OutboundPageQuery q) {
        LambdaQueryWrapper<OutboundOrderDO> w = new LambdaQueryWrapper<OutboundOrderDO>()
                .eq(OutboundOrderDO::getTenantId, PiStore.tenantId())
                .eq(OutboundOrderDO::getSource, LogisticsConstants.OB_NOTICE)
                .isNull(OutboundOrderDO::getDeletedAt);
        if (q.getStatus() != null) {
            w.eq(OutboundOrderDO::getStatus, q.getStatus());
        } else {
            w.ne(OutboundOrderDO::getStatus, LogisticsConstants.OB_WITHDRAWN);
        }
        if (q.getForwarderId() != null) {
            w.eq(OutboundOrderDO::getForwarderId, q.getForwarderId());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            Set<Long> ids = keywordIds(kw);
            w.and(x -> {
                x.like(OutboundOrderDO::getObNo, kw);
                if (!ids.isEmpty()) {
                    x.or().in(OutboundOrderDO::getId, ids);
                }
            });
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = outboundMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        // 待打包按通知时间从早到晚；其余按列表页统一规范，更新时间倒序
        String order = Objects.equals(q.getStatus(), LogisticsConstants.OB_PENDING) ? "ORDER BY create_time ASC, id ASC"
                : "ORDER BY update_time DESC, id DESC";
        w.last(order + " LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toVos(outboundMapper.selectList(w)));
    }

    public CountsVO counts() {
        CountsVO vo = new CountsVO();
        vo.setPending(countByStatus(LogisticsConstants.OB_PENDING));
        vo.setPacked(countByStatus(LogisticsConstants.OB_PACKED));
        return vo;
    }

    /** 订单上的出库单（含撤回的），新的在前 */
    public List<OutboundVO> byOrder(Long soId) {
        support.order(soId, true);
        return toVos(outboundMapper.selectList(new LambdaQueryWrapper<OutboundOrderDO>()
                .eq(OutboundOrderDO::getSoId, soId)
                .isNull(OutboundOrderDO::getDeletedAt)
                .orderByDesc(OutboundOrderDO::getId)));
    }

    public OutboundVO detail(Long id, boolean scoped) {
        return toVos(List.of(visible(id, scoped))).get(0);
    }

    public List<OutboundVO> toVos(List<OutboundOrderDO> rows) {
        List<Long> ids = rows.stream().map(OutboundOrderDO::getId).toList();
        Map<Long, List<OutboundOrderItemDO>> items = itemsOf(ids);
        Map<Long, List<OutboundBoxDO>> boxes = ids.isEmpty() ? Map.of()
                : boxMapper.selectList(new LambdaQueryWrapper<OutboundBoxDO>()
                        .in(OutboundBoxDO::getOutboundId, ids)
                        .isNull(OutboundBoxDO::getDeletedAt)
                        .orderByAsc(OutboundBoxDO::getBoxNo))
                .stream().collect(Collectors.groupingBy(OutboundBoxDO::getOutboundId));
        List<Long> boxIds = boxes.values().stream().flatMap(List::stream).map(OutboundBoxDO::getId).toList();
        Map<Long, List<OutboundBoxItemDO>> contents = boxIds.isEmpty() ? Map.of()
                : boxItemMapper.selectList(new LambdaQueryWrapper<OutboundBoxItemDO>()
                        .in(OutboundBoxItemDO::getBoxId, boxIds)
                        .isNull(OutboundBoxItemDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(OutboundBoxItemDO::getBoxId));
        Map<Long, CourierWaybillDO> waybills = new HashMap<>();
        List<Long> wIds = rows.stream().map(OutboundOrderDO::getCourierWaybillId).filter(Objects::nonNull).distinct().toList();
        if (!wIds.isEmpty()) {
            waybillMapper.selectBatchIds(wIds).forEach(x -> waybills.put(x.getId(), x));
        }
        Map<Long, Long> waybillUse = wIds.isEmpty() ? Map.of()
                : outboundMapper.selectList(new LambdaQueryWrapper<OutboundOrderDO>()
                        .select(OutboundOrderDO::getCourierWaybillId)
                        .in(OutboundOrderDO::getCourierWaybillId, wIds)
                        .isNull(OutboundOrderDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(OutboundOrderDO::getCourierWaybillId, Collectors.counting()));
        Map<Long, String> shNos = new HashMap<>();
        List<Long> shIds = rows.stream().map(OutboundOrderDO::getLogisticsId).filter(Objects::nonNull).distinct().toList();
        if (!shIds.isEmpty()) {
            shipmentMapper.selectBatchIds(shIds).forEach(s -> shNos.put(s.getId(), s.getShNo()));
        }
        Map<Long, SalesOrderDO> orders = support.orders(rows.stream().map(OutboundOrderDO::getSoId).toList());
        Map<Long, String> customers = support.customerNames(rows.stream().map(OutboundOrderDO::getCustomerId).toList());
        Map<Long, String> forwarders = support.supplierNames(rows.stream().map(OutboundOrderDO::getForwarderId).toList());
        Map<Long, String> users = support.userNames(java.util.stream.Stream.concat(
                rows.stream().flatMap(r -> java.util.stream.Stream.of(r.getOwnerId(), r.getPackedBy())),
                waybills.values().stream().map(CourierWaybillDO::getPayerId)).toList());
        Map<Long, Map<Long, BigDecimal>> shares = new HashMap<>();
        for (Long wId : wIds) {
            shares.put(wId, freight.byOutbound(LogisticsConstants.FREIGHT_DOMESTIC, wId));
        }
        List<OutboundVO> out = new ArrayList<>(rows.size());
        for (OutboundOrderDO ob : rows) {
            List<OutboundOrderItemDO> lines = items.getOrDefault(ob.getId(), List.of());
            Map<Long, String> models = lines.stream().collect(Collectors.toMap(OutboundOrderItemDO::getId, OutboundOrderItemDO::getModel));
            OutboundVO vo = new OutboundVO();
            vo.setId(ob.getId());
            vo.setObNo(ob.getObNo());
            vo.setSoId(ob.getSoId());
            SalesOrderDO so = orders.get(ob.getSoId());
            vo.setSoNo(so == null ? null : so.getSoNo());
            vo.setCustomerId(ob.getCustomerId());
            vo.setCustomerName(customers.get(ob.getCustomerId()));
            vo.setOwnerName(users.get(ob.getOwnerId()));
            vo.setForwarderId(ob.getForwarderId());
            vo.setForwarderName(forwarders.get(ob.getForwarderId()));
            vo.setSource(ob.getSource());
            vo.setSourceName(LogisticsConstants.OB_SOURCE_NAMES.get(ob.getSource()));
            vo.setStatus(ob.getStatus());
            vo.setStatusName(LogisticsConstants.OB_STATUS_NAMES.get(ob.getStatus()));
            vo.setNote(ob.getNote());
            vo.setWithdrawReason(ob.getWithdrawReason());
            vo.setTotalQuantity(lines.stream().mapToInt(OutboundOrderItemDO::getQuantity).sum());
            vo.setItems(lines.stream().map(i -> {
                OutboundVO.Item x = new OutboundVO.Item();
                x.setId(i.getId());
                x.setSoItemId(i.getSoItemId());
                x.setModel(i.getModel());
                x.setBrand(i.getBrand());
                x.setQuantity(i.getQuantity());
                return x;
            }).toList());
            List<OutboundBoxDO> bs = boxes.getOrDefault(ob.getId(), List.of());
            BigDecimal weight = BigDecimal.ZERO;
            List<OutboundVO.Box> boxVos = new ArrayList<>(bs.size());
            for (OutboundBoxDO b : bs) {
                OutboundVO.Box x = new OutboundVO.Box();
                x.setId(b.getId());
                x.setBoxNo(b.getBoxNo());
                x.setLength(b.getLength());
                x.setWidth(b.getWidth());
                x.setHeight(b.getHeight());
                x.setGrossWeight(b.getGrossWeight());
                x.setNetWeight(b.getNetWeight());
                x.setChargeable(FreightService.chargeable(b, LogisticsConstants.DOMESTIC_DIVISOR));
                weight = weight.add(x.getChargeable());
                x.setItems(contents.getOrDefault(b.getId(), List.of()).stream().map(c -> {
                    OutboundVO.BoxItem bi = new OutboundVO.BoxItem();
                    bi.setOutboundItemId(c.getOutboundItemId());
                    bi.setModel(models.get(c.getOutboundItemId()));
                    bi.setQuantity(c.getQuantity());
                    return bi;
                }).toList());
                boxVos.add(x);
            }
            vo.setBoxes(boxVos);
            vo.setBoxCount(bs.size());
            vo.setChargeableWeight(weight);
            CourierWaybillDO wb = ob.getCourierWaybillId() == null ? null : waybills.get(ob.getCourierWaybillId());
            if (wb != null) {
                OutboundVO.Courier c = new OutboundVO.Courier();
                c.setId(wb.getId());
                c.setCarrier(wb.getCarrier());
                c.setTrackingNo(wb.getTrackingNo());
                c.setSentDate(wb.getSentDate());
                c.setFreight(wb.getFreight());
                c.setPayerName(users.get(wb.getPayerId()));
                c.setShare(shares.getOrDefault(wb.getId(), Map.of()).get(ob.getId()));
                c.setOutboundCount(waybillUse.getOrDefault(wb.getId(), 1L).intValue());
                vo.setCourier(c);
            }
            vo.setLogisticsId(ob.getLogisticsId());
            vo.setShNo(ob.getLogisticsId() == null ? null : shNos.get(ob.getLogisticsId()));
            vo.setPackedByName(users.get(ob.getPackedBy()));
            vo.setPackedAt(ob.getPackedAt());
            vo.setCreateTime(ob.getCreateTime());
            vo.setCreateBy(ob.getCreateBy());
            vo.setUpdateTime(ob.getUpdateTime());
            vo.setUpdateBy(ob.getUpdateBy());
            out.add(vo);
        }
        return out;
    }

    // ---------------------------------------------------------------- 通用

    public Map<Long, List<OutboundOrderItemDO>> itemsOf(Collection<Long> outboundIds) {
        if (outboundIds.isEmpty()) {
            return Map.of();
        }
        return itemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItemDO>()
                        .in(OutboundOrderItemDO::getOutboundId, outboundIds)
                        .isNull(OutboundOrderItemDO::getDeletedAt)
                        .orderByAsc(OutboundOrderItemDO::getId))
                .stream().collect(Collectors.groupingBy(OutboundOrderItemDO::getOutboundId, LinkedHashMap::new, Collectors.toList()));
    }

    public List<Long> soItemIds(Collection<Long> outboundIds) {
        return itemsOf(outboundIds).values().stream().flatMap(List::stream).map(OutboundOrderItemDO::getSoItemId).distinct().toList();
    }

    private Set<Long> keywordIds(String kw) {
        int tenant = PiStore.tenantId();
        Set<Long> ids = new LinkedHashSet<>();
        List<Long> soIds = soMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                        .select(SalesOrderDO::getId)
                        .eq(SalesOrderDO::getTenantId, tenant)
                        .like(SalesOrderDO::getSoNo, kw)
                        .last("LIMIT 500"))
                .stream().map(SalesOrderDO::getId).toList();
        if (!soIds.isEmpty()) {
            outboundMapper.selectList(new LambdaQueryWrapper<OutboundOrderDO>().select(OutboundOrderDO::getId).in(OutboundOrderDO::getSoId, soIds))
                    .forEach(o -> ids.add(o.getId()));
        }
        itemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItemDO>()
                        .select(OutboundOrderItemDO::getOutboundId)
                        .eq(OutboundOrderItemDO::getTenantId, tenant)
                        .like(OutboundOrderItemDO::getModel, kw)
                        .last("LIMIT 500"))
                .forEach(i -> ids.add(i.getOutboundId()));
        List<Long> waybillIds = waybillMapper.selectList(new LambdaQueryWrapper<CourierWaybillDO>()
                        .select(CourierWaybillDO::getId)
                        .eq(CourierWaybillDO::getTenantId, tenant)
                        .like(CourierWaybillDO::getTrackingNo, kw)
                        .last("LIMIT 500"))
                .stream().map(CourierWaybillDO::getId).toList();
        if (!waybillIds.isEmpty()) {
            outboundMapper.selectList(new LambdaQueryWrapper<OutboundOrderDO>().select(OutboundOrderDO::getId)
                    .in(OutboundOrderDO::getCourierWaybillId, waybillIds)).forEach(o -> ids.add(o.getId()));
        }
        Map<Long, String> customers = support.customerNames(outboundMapper.selectList(new LambdaQueryWrapper<OutboundOrderDO>()
                        .select(OutboundOrderDO::getCustomerId)
                        .eq(OutboundOrderDO::getTenantId, tenant)
                        .isNull(OutboundOrderDO::getDeletedAt)
                        .groupBy(OutboundOrderDO::getCustomerId))
                .stream().map(OutboundOrderDO::getCustomerId).toList());
        List<Long> matched = customers.entrySet().stream().filter(e -> e.getValue() != null && e.getValue().toLowerCase().contains(kw.toLowerCase()))
                .map(Map.Entry::getKey).toList();
        if (!matched.isEmpty()) {
            outboundMapper.selectList(new LambdaQueryWrapper<OutboundOrderDO>().select(OutboundOrderDO::getId)
                    .in(OutboundOrderDO::getCustomerId, matched)).forEach(o -> ids.add(o.getId()));
        }
        return ids;
    }

    private long countByStatus(int status) {
        return outboundMapper.selectCount(new LambdaQueryWrapper<OutboundOrderDO>()
                .eq(OutboundOrderDO::getTenantId, PiStore.tenantId())
                .eq(OutboundOrderDO::getSource, LogisticsConstants.OB_NOTICE)
                .eq(OutboundOrderDO::getStatus, status)
                .isNull(OutboundOrderDO::getDeletedAt));
    }

    private List<SalesOrderItemDO> soItems(Long soId) {
        return soItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                .eq(SalesOrderItemDO::getSoId, soId)
                .isNull(SalesOrderItemDO::getDeletedAt)
                .orderByAsc(SalesOrderItemDO::getLineNo));
    }

    private SalesOrderDO lockOrder(Long soId) {
        support.activeOrder(soId, true);
        soMapper.selectOne(new LambdaQueryWrapper<SalesOrderDO>().eq(SalesOrderDO::getId, soId).last("FOR UPDATE"));
        return support.activeOrder(soId, true);
    }

    /** scoped：业务员侧按订单所属业务员校验；仓库侧只看本租户 */
    public OutboundOrderDO visible(Long id, boolean scoped) {
        OutboundOrderDO ob = id == null ? null : outboundMapper.selectById(id);
        if (ob == null || ob.getDeletedAt() != null || !Objects.equals(ob.getTenantId(), PiStore.tenantId())
                || (scoped && !support.scope().canSee(ob.getOwnerId()))) {
            throw new BizException("出库单不存在");
        }
        return ob;
    }

    public OutboundOrderDO lock(Long id) {
        outboundMapper.lockById(id);
        return outboundMapper.selectById(id);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}

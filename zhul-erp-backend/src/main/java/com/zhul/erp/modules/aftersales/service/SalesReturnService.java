package com.zhul.erp.modules.aftersales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.aftersales.constants.AfterSalesConstants;
import com.zhul.erp.modules.aftersales.dto.DisposeReturnRequest;
import com.zhul.erp.modules.aftersales.dto.ReceiveReturnRequest;
import com.zhul.erp.modules.aftersales.dto.ReturnQuery;
import com.zhul.erp.modules.aftersales.dto.ReturnVO;
import com.zhul.erp.modules.aftersales.dto.SaveReturnRequest;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnItemDO;
import com.zhul.erp.modules.aftersales.repository.AfterSalesMapper;
import com.zhul.erp.modules.aftersales.repository.SalesReturnDispositionMapper;
import com.zhul.erp.modules.aftersales.repository.SalesReturnItemMapper;
import com.zhul.erp.modules.aftersales.repository.SalesReturnMapper;
import com.zhul.erp.modules.aftersales.support.AfterSalesLogs;
import com.zhul.erp.modules.aftersales.support.ReturnCost;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.logistics.support.LogisticsQty;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.purchase.support.PurchaseCalc;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.StockHoldDO;
import com.zhul.erp.modules.warehouse.repository.StockHoldMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 退货单（spec warehouse/sales-return）：售后单发起 → 仓库验收 → 采购员处理去向（退回供应商 / 转闲置库存 / 报废）。
 * 去向冲减原订单成本：退回供应商按登记的供应商退款，转闲置按 数量 × 原采购成本（HALF_UP 两位，处理时落库），报废不冲减。
 */
@Service
@RequiredArgsConstructor
public class SalesReturnService {

    private final SalesReturnMapper returnMapper;
    private final SalesReturnItemMapper itemMapper;
    private final SalesReturnDispositionMapper dispositionMapper;
    private final AfterSalesMapper afterSalesMapper;
    private final AfterSalesService afterSalesService;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final SupplierMapper supplierMapper;
    private final StockHoldMapper holdMapper;
    private final LogisticsQty logisticsQty;
    private final ReturnCost returnCost;
    private final DocumentNumberService documentNumberService;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final AttachmentService attachments;
    private final LogService logService;
    private final AfterSalesLogs logs;

    // ---------------------------------------------------------------- 发起、修改、作废

    @Transactional(rollbackFor = Exception.class)
    public ReturnVO create(SaveReturnRequest req) {
        if (req.getAfterSalesId() == null) {
            throw new BizException("请从售后单发起退货");
        }
        afterSalesService.detail(req.getAfterSalesId());
        afterSalesMapper.lockById(req.getAfterSalesId());
        AfterSalesDO a = afterSalesMapper.selectById(req.getAfterSalesId());
        if (a.getStatus() == AfterSalesConstants.AS_CLOSED || a.getStatus() == AfterSalesConstants.AS_CANCELLED) {
            throw new BizException("售后单" + AfterSalesConstants.AS_STATUS_NAMES.get(a.getStatus()) + "，不能发起退货");
        }
        if (a.getSoId() == null) {
            throw new BizException("售后单没有关联订单，不能发起退货");
        }
        SalesOrderDO o = lockOrder(a.getSoId());
        List<SalesReturnItemDO> rows = buildItems(o, req.getItems(), null);
        SalesReturnDO r = new SalesReturnDO();
        r.setTenantId(o.getTenantId());
        r.setReturnNo(documentNumberService.next(DocumentType.TH));
        r.setAfterSalesId(a.getId());
        r.setSoId(o.getId());
        r.setCarrier(trim(req.getCarrier()));
        r.setTrackingNo(trim(req.getTrackingNo()));
        r.setRemark(trim(req.getRemark()));
        r.setStatus(AfterSalesConstants.RT_PENDING);
        r.setReceiveNote("");
        r.setVoidReason("");
        returnMapper.insert(r);
        for (SalesReturnItemDO x : rows) {
            x.setReturnId(r.getId());
            itemMapper.insert(x);
        }
        afterSalesMapper.updateById(a);
        String text = summary(rows);
        logs.add(a.getId(), AfterSalesConstants.LOG_RETURN, "发起退货单 " + r.getReturnNo() + "：" + text, me());
        logService.recordOperateLog(AfterSalesConstants.MENU, "发起退货", null, Map.of("returnNo", r.getReturnNo(), "asNo", a.getAsNo(), "items", text));
        return detail(r.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public ReturnVO update(Long id, SaveReturnRequest req) {
        SalesReturnDO r = lock(id);
        if (r.getStatus() != AfterSalesConstants.RT_PENDING) {
            throw new BizException("退货单已入库，不能修改");
        }
        SalesOrderDO o = lockOrder(r.getSoId());
        List<SalesReturnItemDO> rows = buildItems(o, req.getItems(), r.getId());
        itemsOf(List.of(id)).getOrDefault(id, List.of()).forEach(x -> {
            x.setDeletedAt(java.time.LocalDateTime.now());
            itemMapper.updateById(x);
        });
        for (SalesReturnItemDO x : rows) {
            x.setReturnId(r.getId());
            itemMapper.insert(x);
        }
        r.setCarrier(trim(req.getCarrier()));
        r.setTrackingNo(trim(req.getTrackingNo()));
        r.setRemark(trim(req.getRemark()));
        returnMapper.updateById(r);
        logs.add(r.getAfterSalesId(), AfterSalesConstants.LOG_RETURN, "修改退货单 " + r.getReturnNo() + "：" + summary(rows)
                + (r.getTrackingNo().isEmpty() ? "" : "，快递 " + r.getCarrier() + " " + r.getTrackingNo()), me());
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReturnVO voidReturn(Long id, String reason) {
        String why = trim(reason);
        if (why.isEmpty()) {
            throw new BizException("请填写作废原因");
        }
        SalesReturnDO r = lock(id);
        if (r.getStatus() != AfterSalesConstants.RT_PENDING) {
            throw new BizException("退货单已入库，不能作废");
        }
        r.setStatus(AfterSalesConstants.RT_VOID);
        r.setVoidReason(why.length() > 200 ? why.substring(0, 200) : why);
        returnMapper.updateById(r);
        logs.add(r.getAfterSalesId(), AfterSalesConstants.LOG_RETURN, "作废退货单 " + r.getReturnNo() + "：" + r.getVoidReason(), me());
        logService.recordOperateLog(AfterSalesConstants.MENU, "作废退货单", null, Map.of("returnNo", r.getReturnNo(), "reason", r.getVoidReason()));
        return detail(id);
    }

    /** 退货行：可退 = 已出运 − 其他有效退货单（不含 excludeId）的数量；调用方已锁订单 */
    private List<SalesReturnItemDO> buildItems(SalesOrderDO o, List<SaveReturnRequest.Line> lines, Long excludeId) {
        Map<Long, SalesOrderItemDO> soItems = orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                        .eq(SalesOrderItemDO::getSoId, o.getId())
                        .isNull(SalesOrderItemDO::getDeletedAt))
                .stream().collect(Collectors.toMap(SalesOrderItemDO::getId, i -> i));
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (SaveReturnRequest.Line l : lines) {
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
            throw new BizException("请填写退回数量");
        }
        Map<Long, LogisticsQty.Qty> out = logisticsQty.bySoItem(wanted.keySet());
        Map<Long, Integer> returned = returnedQty(wanted.keySet(), excludeId);
        List<SalesReturnItemDO> rows = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            SalesOrderItemDO i = soItems.get(e.getKey());
            int max = Math.max(0, out.getOrDefault(i.getId(), LogisticsQty.Qty.ZERO).shipped() - returned.getOrDefault(i.getId(), 0));
            if (e.getValue() > max) {
                throw new BizException(max == 0 ? i.getModel() + " 还没有可以退的数量（已出运的才能退）"
                        : i.getModel() + " 最多还能退 " + max + " 个");
            }
            SalesReturnItemDO x = new SalesReturnItemDO();
            x.setTenantId(o.getTenantId());
            x.setSoItemId(i.getId());
            x.setModel(i.getModel());
            x.setBrand(Objects.requireNonNullElse(i.getBrand(), ""));
            x.setQuantity(e.getValue());
            rows.add(x);
        }
        return rows;
    }

    /** 订单型号行在有效退货单（非作废）上的数量 */
    private Map<Long, Integer> returnedQty(Collection<Long> soItemIds, Long excludeId) {
        List<SalesReturnItemDO> rows = itemMapper.selectList(new LambdaQueryWrapper<SalesReturnItemDO>()
                .in(SalesReturnItemDO::getSoItemId, soItemIds)
                .isNull(SalesReturnItemDO::getDeletedAt));
        if (rows.isEmpty()) {
            return Map.of();
        }
        Set<Long> valid = returnMapper.selectBatchIds(rows.stream().map(SalesReturnItemDO::getReturnId).distinct().toList()).stream()
                .filter(r -> r.getDeletedAt() == null && r.getStatus() != AfterSalesConstants.RT_VOID && !r.getId().equals(excludeId))
                .map(SalesReturnDO::getId).collect(Collectors.toSet());
        Map<Long, Integer> m = new HashMap<>();
        rows.stream().filter(x -> valid.contains(x.getReturnId())).forEach(x -> m.merge(x.getSoItemId(), x.getQuantity(), Integer::sum));
        return m;
    }

    // ---------------------------------------------------------------- 验收

    @Transactional(rollbackFor = Exception.class)
    public ReturnVO receive(Long id, ReceiveReturnRequest req) {
        SalesReturnDO r = lock(id);
        if (r.getStatus() != AfterSalesConstants.RT_PENDING) {
            throw new BizException(r.getStatus() == AfterSalesConstants.RT_VOID ? "退货单已作废" : "退货单已验收");
        }
        if (req.getReceivedDate().isAfter(LocalDate.now())) {
            throw new BizException("收货日期不能晚于今天");
        }
        Map<Long, SalesReturnItemDO> items = itemsOf(List.of(id)).getOrDefault(id, List.of()).stream()
                .collect(Collectors.toMap(SalesReturnItemDO::getId, x -> x));
        Map<Long, ReceiveReturnRequest.Line> byItem = new HashMap<>();
        for (ReceiveReturnRequest.Line l : req.getItems()) {
            if (!items.containsKey(l.getItemId()) || byItem.put(l.getItemId(), l) != null) {
                throw new BizException("退货行不正确");
            }
        }
        if (byItem.size() != items.size()) {
            throw new BizException("请填写每个型号的验收数量");
        }
        int total = 0;
        List<String> text = new ArrayList<>();
        for (SalesReturnItemDO x : items.values()) {
            ReceiveReturnRequest.Line l = byItem.get(x.getId());
            if (l.getReceivedQty() < 0 || l.getQualifiedQty() < 0) {
                throw new BizException(x.getModel() + " 的数量不能为负数");
            }
            if (l.getReceivedQty() > x.getQuantity()) {
                throw new BizException(x.getModel() + " 实收不能多于退货数量 " + x.getQuantity());
            }
            if (l.getQualifiedQty() > l.getReceivedQty()) {
                throw new BizException(x.getModel() + "：合格数量不能多于实收");
            }
            x.setReceivedQty(l.getReceivedQty());
            x.setQualifiedQty(l.getQualifiedQty());
            itemMapper.updateById(x);
            total += l.getReceivedQty();
            int bad = l.getReceivedQty() - l.getQualifiedQty();
            text.add(x.getModel() + " 实收 " + l.getReceivedQty() + "、合格 " + l.getQualifiedQty() + (bad > 0 ? "、不良 " + bad : ""));
        }
        if (total == 0) {
            throw new BizException("实收数量都是 0，客户还没寄回时请不要验收");
        }
        r.setStatus(AfterSalesConstants.RT_RECEIVED);
        r.setReceivedDate(req.getReceivedDate());
        r.setReceivedBy(me());
        r.setReceiveNote(trim(req.getNote()));
        returnMapper.updateById(r);
        attachments.attach(AttachmentService.SALES_RETURN, r.getId(), req.getAttachmentIds());
        logs.add(r.getAfterSalesId(), AfterSalesConstants.LOG_RETURN, "退货单 " + r.getReturnNo() + " 已入库：" + String.join("；", text), me());
        logService.recordOperateLog(AfterSalesConstants.MENU_RETURN, "退货验收", null, Map.of("returnNo", r.getReturnNo(), "items", text));
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReturnVO unreceive(Long id, String reason) {
        String why = trim(reason);
        if (why.isEmpty()) {
            throw new BizException("请填写撤回原因");
        }
        SalesReturnDO r = lock(id);
        if (r.getStatus() != AfterSalesConstants.RT_RECEIVED) {
            throw new BizException(r.getStatus() == AfterSalesConstants.RT_DONE ? "退货已处理去向，不能撤回入库" : "退货单还没入库");
        }
        itemsOf(List.of(id)).getOrDefault(id, List.of()).forEach(x -> {
            x.setReceivedQty(null);
            x.setQualifiedQty(null);
            itemMapper.updateById(x);
        });
        r.setStatus(AfterSalesConstants.RT_PENDING);
        r.setReceivedDate(null);
        r.setReceivedBy(null);
        r.setReceiveNote("");
        returnMapper.updateById(r);
        logs.add(r.getAfterSalesId(), AfterSalesConstants.LOG_RETURN, "退货单 " + r.getReturnNo() + " 撤回入库：" + why, me());
        logService.recordOperateLog(AfterSalesConstants.MENU_RETURN, "撤回退货入库", null, Map.of("returnNo", r.getReturnNo(), "reason", why));
        return detail(id);
    }

    // ---------------------------------------------------------------- 去向

    @Transactional(rollbackFor = Exception.class)
    public ReturnVO dispose(Long id, DisposeReturnRequest req) {
        SalesReturnDO r = lock(id);
        if (r.getStatus() != AfterSalesConstants.RT_RECEIVED) {
            throw new BizException(r.getStatus() == AfterSalesConstants.RT_DONE ? "退货已处理去向" : "退货单还没验收");
        }
        Map<Long, SalesReturnItemDO> items = itemsOf(List.of(id)).getOrDefault(id, List.of()).stream()
                .collect(Collectors.toMap(SalesReturnItemDO::getId, x -> x));
        Map<Long, Integer> sum = new HashMap<>();
        Map<Long, Integer> toHold = new HashMap<>();
        for (DisposeReturnRequest.Line l : req.getLines()) {
            SalesReturnItemDO x = items.get(l.getItemId());
            if (x == null) {
                throw new BizException("退货行不正确");
            }
            if (!AfterSalesConstants.DISP_NAMES.containsKey(l.getDisposition())) {
                throw new BizException("去向不正确");
            }
            if (l.getQuantity() == null || l.getQuantity() < 0) {
                throw new BizException(x.getModel() + " 的数量不能为负数");
            }
            if (l.getDisposition() == AfterSalesConstants.DISP_SUPPLIER && l.getQuantity() > 0 && l.getSupplierId() == null) {
                throw new BizException("退回供应商时请选择供应商");
            }
            if (nz(l.getFreightCny()).signum() < 0 || nz(l.getSupplierRefundCny()).signum() < 0) {
                throw new BizException("金额不能为负数");
            }
            sum.merge(x.getId(), l.getQuantity(), Integer::sum);
            if (l.getDisposition() == AfterSalesConstants.DISP_HOLD) {
                toHold.merge(x.getId(), l.getQuantity(), Integer::sum);
            }
        }
        for (SalesReturnItemDO x : items.values()) {
            int received = Objects.requireNonNullElse(x.getReceivedQty(), 0);
            if (sum.getOrDefault(x.getId(), 0) != received) {
                throw new BizException(x.getModel() + " 的去向数量合计要等于实收 " + received + " 个");
            }
            int qualified = Objects.requireNonNullElse(x.getQualifiedQty(), 0);
            if (toHold.getOrDefault(x.getId(), 0) > qualified) {
                throw new BizException("只有合格的才能转闲置库存（合格 " + qualified + " 个）");
            }
        }
        Map<Long, ReturnCost.Origin> origins = returnCost.originsOf(items.values().stream().map(SalesReturnItemDO::getSoItemId).toList());
        Long operator = me();
        List<String> text = new ArrayList<>();
        Map<Long, String> suppliers = supplierNames(req.getLines().stream().map(DisposeReturnRequest.Line::getSupplierId).toList());
        for (DisposeReturnRequest.Line l : req.getLines()) {
            if (l.getQuantity() == 0) {
                continue;
            }
            SalesReturnItemDO x = items.get(l.getItemId());
            ReturnCost.Origin origin = origins.getOrDefault(x.getSoItemId(), ReturnCost.Origin.NONE);
            SalesReturnDispositionDO d = new SalesReturnDispositionDO();
            d.setTenantId(r.getTenantId());
            d.setReturnId(r.getId());
            d.setReturnItemId(x.getId());
            d.setSoId(r.getSoId());
            d.setSoItemId(x.getSoItemId());
            d.setDisposition(l.getDisposition());
            d.setQuantity(l.getQuantity());
            d.setTrackingNo("");
            d.setFreightCny(BigDecimal.ZERO.setScale(2));
            d.setSupplierRefundCny(BigDecimal.ZERO.setScale(2));
            d.setCostCny(BigDecimal.ZERO.setScale(2));
            d.setRemark(trim(l.getRemark()));
            d.setOperatorId(operator);
            String line;
            if (l.getDisposition() == AfterSalesConstants.DISP_SUPPLIER) {
                if (!suppliers.containsKey(l.getSupplierId())) {
                    throw new BizException("供应商不存在");
                }
                d.setSupplierId(l.getSupplierId());
                d.setTrackingNo(trim(l.getTrackingNo()));
                d.setFreightCny(PurchaseCalc.money(nz(l.getFreightCny())));
                d.setSupplierRefundCny(PurchaseCalc.money(nz(l.getSupplierRefundCny())));
                d.setCostCny(d.getSupplierRefundCny());
                line = x.getModel() + " × " + l.getQuantity() + " 退回 " + suppliers.get(l.getSupplierId()) + "，供应商退款 ¥"
                        + d.getSupplierRefundCny() + (d.getFreightCny().signum() > 0 ? "，运费 ¥" + d.getFreightCny() : "");
            } else if (l.getDisposition() == AfterSalesConstants.DISP_HOLD) {
                BigDecimal unit = origin.unitCost();
                d.setCostCny(unit.multiply(BigDecimal.valueOf(l.getQuantity())).setScale(2, RoundingMode.HALF_UP));
                StockHoldDO h = new StockHoldDO();
                h.setTenantId(r.getTenantId());
                h.setSourceType(WarehouseConstants.HOLD_SRC_AFTER_SALES);
                h.setDiscrepancyId(0L);
                h.setReceiptId(0L);
                h.setPoId(origin.poId() == null ? 0L : origin.poId());
                h.setSoId(r.getSoId());
                h.setSoItemId(x.getSoItemId());
                h.setReturnId(r.getId());
                h.setModel(x.getModel());
                h.setBrand(x.getBrand());
                h.setCategory("");
                h.setQuantity(l.getQuantity());
                h.setCostPrice(unit.setScale(2, RoundingMode.HALF_UP));
                h.setLocationNote("");
                h.setStatus(WarehouseConstants.HOLD_ACTIVE);
                h.setReturnCarrier("");
                h.setReturnTrackingNo("");
                h.setHandleNote("");
                holdMapper.insert(h);
                d.setHoldId(h.getId());
                line = x.getModel() + " × " + l.getQuantity() + " 转闲置库存，冲减 ¥" + d.getCostCny();
            } else {
                line = x.getModel() + " × " + l.getQuantity() + " 报废" + (d.getRemark().isEmpty() ? "" : "：" + d.getRemark());
            }
            dispositionMapper.insert(d);
            text.add(line);
        }
        r.setStatus(AfterSalesConstants.RT_DONE);
        returnMapper.updateById(r);
        logs.add(r.getAfterSalesId(), AfterSalesConstants.LOG_RETURN, "退货单 " + r.getReturnNo() + " 已处理：" + String.join("；", text), operator);
        logService.recordOperateLog(AfterSalesConstants.MENU_RETURN, "处理退货去向", null, Map.of("returnNo", r.getReturnNo(), "lines", text));
        return detail(id);
    }

    // ---------------------------------------------------------------- 查看

    public PageResult<ReturnVO> page(ReturnQuery q) {
        LambdaQueryWrapper<SalesReturnDO> w = new LambdaQueryWrapper<SalesReturnDO>()
                .eq(SalesReturnDO::getTenantId, PiStore.tenantId())
                .isNull(SalesReturnDO::getDeletedAt);
        if (q.getStatus() != null) {
            w.eq(SalesReturnDO::getStatus, q.getStatus());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            Set<Long> soIds = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>().select(SalesOrderDO::getId)
                            .eq(SalesOrderDO::getTenantId, PiStore.tenantId()).like(SalesOrderDO::getSoNo, kw).last("LIMIT 200"))
                    .stream().map(SalesOrderDO::getId).collect(Collectors.toSet());
            Set<Long> asIds = afterSalesMapper.selectList(new LambdaQueryWrapper<AfterSalesDO>().select(AfterSalesDO::getId)
                            .eq(AfterSalesDO::getTenantId, PiStore.tenantId()).like(AfterSalesDO::getAsNo, kw).last("LIMIT 200"))
                    .stream().map(AfterSalesDO::getId).collect(Collectors.toSet());
            w.and(x -> {
                x.like(SalesReturnDO::getReturnNo, kw).or().like(SalesReturnDO::getTrackingNo, kw);
                if (!soIds.isEmpty()) {
                    x.or().in(SalesReturnDO::getSoId, soIds);
                }
                if (!asIds.isEmpty()) {
                    x.or().in(SalesReturnDO::getAfterSalesId, asIds);
                }
            });
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = returnMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        w.last("ORDER BY update_time DESC, id DESC LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toVos(returnMapper.selectList(w), false));
    }

    public Map<String, Long> counts() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (int s : List.of(AfterSalesConstants.RT_PENDING, AfterSalesConstants.RT_RECEIVED)) {
            m.put(s == AfterSalesConstants.RT_PENDING ? "pending" : "received", returnMapper.selectCount(new LambdaQueryWrapper<SalesReturnDO>()
                    .eq(SalesReturnDO::getTenantId, PiStore.tenantId())
                    .eq(SalesReturnDO::getStatus, s)
                    .isNull(SalesReturnDO::getDeletedAt)));
        }
        return m;
    }

    public ReturnVO detail(Long id) {
        return toVos(List.of(require(id)), true).get(0);
    }

    private List<ReturnVO> toVos(List<SalesReturnDO> rows, boolean full) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(SalesReturnDO::getId).toList();
        Map<Long, List<SalesReturnItemDO>> items = itemsOf(ids);
        Map<Long, AfterSalesDO> afterSales = afterSalesMapper.selectBatchIds(rows.stream().map(SalesReturnDO::getAfterSalesId).distinct().toList())
                .stream().collect(Collectors.toMap(AfterSalesDO::getId, a -> a));
        Map<Long, SalesOrderDO> orders = orderMapper.selectBatchIds(rows.stream().map(SalesReturnDO::getSoId).distinct().toList())
                .stream().collect(Collectors.toMap(SalesOrderDO::getId, o -> o));
        Map<Long, CustomerDO> customers = lookups.customers(orders.values().stream().map(SalesOrderDO::getCustomerId).distinct().toList());
        List<SalesReturnDispositionDO> disps = dispositionMapper.selectList(new LambdaQueryWrapper<SalesReturnDispositionDO>()
                .in(SalesReturnDispositionDO::getReturnId, ids)
                .isNull(SalesReturnDispositionDO::getDeletedAt)
                .orderByAsc(SalesReturnDispositionDO::getId));
        Map<Long, List<SalesReturnDispositionDO>> dispByReturn = disps.stream().collect(Collectors.groupingBy(SalesReturnDispositionDO::getReturnId));
        Map<Long, String> users = lookups.userNames(rows.stream().map(SalesReturnDO::getReceivedBy).filter(Objects::nonNull).toList());
        Map<Long, ReturnCost.Origin> origins = full ? returnCost.originsOf(items.values().stream().flatMap(List::stream)
                .map(SalesReturnItemDO::getSoItemId).toList()) : Map.of();
        List<Long> supplierIds = new ArrayList<>(disps.stream().map(SalesReturnDispositionDO::getSupplierId).filter(Objects::nonNull).toList());
        origins.values().stream().map(ReturnCost.Origin::supplierId).filter(Objects::nonNull).forEach(supplierIds::add);
        Map<Long, String> suppliers = supplierNames(supplierIds);
        Map<Long, String> operators = full ? lookups.userNames(disps.stream().map(SalesReturnDispositionDO::getOperatorId).toList()) : Map.of();
        List<ReturnVO> out = new ArrayList<>();
        for (SalesReturnDO r : rows) {
            ReturnVO v = new ReturnVO();
            AfterSalesDO a = afterSales.get(r.getAfterSalesId());
            SalesOrderDO o = orders.get(r.getSoId());
            v.setId(r.getId());
            v.setReturnNo(r.getReturnNo());
            v.setAfterSalesId(r.getAfterSalesId());
            v.setAsNo(a == null ? null : a.getAsNo());
            v.setSoId(r.getSoId());
            v.setSoNo(o == null ? null : o.getSoNo());
            v.setCustomerName(o == null ? null : InquiryLookups.customerName(customers.get(o.getCustomerId())));
            v.setCarrier(r.getCarrier());
            v.setTrackingNo(r.getTrackingNo());
            v.setRemark(r.getRemark());
            v.setStatus(r.getStatus());
            v.setStatusName(AfterSalesConstants.RT_STATUS_NAMES.get(r.getStatus()));
            v.setReceivedDate(r.getReceivedDate());
            v.setReceivedByName(r.getReceivedBy() == null ? null : users.get(r.getReceivedBy()));
            v.setReceiveNote(r.getReceiveNote());
            v.setVoidReason(r.getVoidReason());
            List<SalesReturnItemDO> mine = items.getOrDefault(r.getId(), List.of());
            v.setItemSummary(summary(mine));
            List<SalesReturnDispositionDO> myDisps = dispByReturn.getOrDefault(r.getId(), List.of());
            v.setCostCny(myDisps.stream().map(SalesReturnDispositionDO::getCostCny).reduce(BigDecimal.ZERO, BigDecimal::add));
            v.setFreightCny(myDisps.stream().map(SalesReturnDispositionDO::getFreightCny).reduce(BigDecimal.ZERO, BigDecimal::add));
            v.setItems(mine.stream().map(x -> {
                ReturnVO.Item i = new ReturnVO.Item();
                i.setId(x.getId());
                i.setSoItemId(x.getSoItemId());
                i.setModel(x.getModel());
                i.setBrand(x.getBrand());
                i.setQuantity(x.getQuantity());
                i.setReceivedQty(x.getReceivedQty());
                i.setQualifiedQty(x.getQualifiedQty());
                ReturnCost.Origin og = origins.get(x.getSoItemId());
                if (og != null) {
                    i.setUnitCost(og.unitCost());
                    i.setSupplierId(og.supplierId());
                    i.setSupplierName(og.supplierId() == null ? null : suppliers.get(og.supplierId()));
                    i.setPoNo(og.poNo());
                }
                return i;
            }).toList());
            Map<Long, String> models = mine.stream().collect(Collectors.toMap(SalesReturnItemDO::getId, SalesReturnItemDO::getModel));
            v.setDispositions(myDisps.stream().map(d -> {
                ReturnVO.Disposition x = new ReturnVO.Disposition();
                x.setId(d.getId());
                x.setItemId(d.getReturnItemId());
                x.setModel(models.get(d.getReturnItemId()));
                x.setDisposition(d.getDisposition());
                x.setDispositionName(AfterSalesConstants.DISP_NAMES.get(d.getDisposition()));
                x.setQuantity(d.getQuantity());
                x.setSupplierId(d.getSupplierId());
                x.setSupplierName(d.getSupplierId() == null ? null : suppliers.get(d.getSupplierId()));
                x.setTrackingNo(d.getTrackingNo());
                x.setFreightCny(d.getFreightCny());
                x.setSupplierRefundCny(d.getSupplierRefundCny());
                x.setCostCny(d.getCostCny());
                x.setHoldId(d.getHoldId());
                x.setRemark(d.getRemark());
                x.setOperatorName(operators.get(d.getOperatorId()));
                x.setCreateTime(d.getCreateTime());
                return x;
            }).toList());
            if (full) {
                v.setAttachments(attachments.listOf(AttachmentService.SALES_RETURN, r.getId()));
            }
            v.setCreateTime(r.getCreateTime());
            v.setCreateBy(r.getCreateBy());
            v.setUpdateTime(r.getUpdateTime());
            v.setUpdateBy(r.getUpdateBy());
            out.add(v);
        }
        return out;
    }

    /** 售后单详情用：这张售后单的退货单 */
    public List<ReturnVO> ofAfterSales(Long afterSalesId) {
        return toVos(returnMapper.selectList(new LambdaQueryWrapper<SalesReturnDO>()
                .eq(SalesReturnDO::getAfterSalesId, afterSalesId)
                .isNull(SalesReturnDO::getDeletedAt)
                .orderByDesc(SalesReturnDO::getId)), true);
    }

    /** 调用方保证 ids 非空 */
    private Map<Long, List<SalesReturnItemDO>> itemsOf(Collection<Long> ids) {
        return itemMapper.selectList(new LambdaQueryWrapper<SalesReturnItemDO>()
                        .in(SalesReturnItemDO::getReturnId, ids)
                        .isNull(SalesReturnItemDO::getDeletedAt)
                        .orderByAsc(SalesReturnItemDO::getId))
                .stream().collect(Collectors.groupingBy(SalesReturnItemDO::getReturnId, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<Long, String> supplierNames(Collection<Long> ids) {
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (keys.isEmpty()) {
            return Map.of();
        }
        return supplierMapper.selectBatchIds(keys).stream()
                .filter(s -> s.getDeletedAt() == null && Objects.equals(s.getTenantId(), PiStore.tenantId()))
                .collect(Collectors.toMap(SupplierDO::getId, SupplierDO::getName));
    }

    private SalesReturnDO require(Long id) {
        SalesReturnDO r = id == null ? null : returnMapper.selectById(id);
        if (r == null || r.getDeletedAt() != null || !Objects.equals(r.getTenantId(), PiStore.tenantId())) {
            throw new BizException("退货单不存在");
        }
        return r;
    }

    private SalesReturnDO lock(Long id) {
        require(id);
        returnMapper.lockById(id);
        return returnMapper.selectById(id);
    }

    private SalesOrderDO lockOrder(Long soId) {
        SalesOrderDO o = orderMapper.selectOne(new LambdaQueryWrapper<SalesOrderDO>().eq(SalesOrderDO::getId, soId).last("FOR UPDATE"));
        if (o == null || o.getDeletedAt() != null) {
            throw new BizException("销售订单不存在");
        }
        return o;
    }

    private static String summary(List<SalesReturnItemDO> rows) {
        return rows.stream().map(x -> x.getModel() + " × " + x.getQuantity()).collect(Collectors.joining("、"));
    }

    private Long me() {
        Long id = currentUser.resolve();
        return id == null ? 0L : id;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

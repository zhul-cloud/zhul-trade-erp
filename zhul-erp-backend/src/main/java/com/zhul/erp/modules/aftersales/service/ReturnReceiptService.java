package com.zhul.erp.modules.aftersales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.aftersales.constants.AfterSalesConstants;
import com.zhul.erp.modules.aftersales.dto.DisposeReturnRequest;
import com.zhul.erp.modules.aftersales.dto.ReceiveReturnRequest;
import com.zhul.erp.modules.aftersales.dto.ReturnQuery;
import com.zhul.erp.modules.aftersales.dto.ReturnReceiptVO;
import com.zhul.erp.modules.aftersales.dto.ReturnVO;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.aftersales.entity.ReturnReceiptDO;
import com.zhul.erp.modules.aftersales.entity.ReturnReceiptItemDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnItemDO;
import com.zhul.erp.modules.aftersales.repository.AfterSalesMapper;
import com.zhul.erp.modules.aftersales.repository.ReturnReceiptItemMapper;
import com.zhul.erp.modules.aftersales.repository.ReturnReceiptMapper;
import com.zhul.erp.modules.aftersales.repository.SalesReturnDispositionMapper;
import com.zhul.erp.modules.aftersales.repository.SalesReturnMapper;
import com.zhul.erp.modules.aftersales.support.AfterSalesLogs;
import com.zhul.erp.modules.aftersales.support.ReturnCost;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.finance.service.RefundRequestService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.purchasereturn.PurchaseReturnConstants;
import com.zhul.erp.modules.purchase.purchasereturn.dto.PurchaseReturnVO;
import com.zhul.erp.modules.purchase.purchasereturn.entity.PurchaseReturnDO;
import com.zhul.erp.modules.purchase.purchasereturn.service.PurchaseReturnService;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.StockHoldDO;
import com.zhul.erp.modules.warehouse.repository.StockHoldMapper;
import com.zhul.erp.modules.warehouse.support.ReceivingSupport;
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
 * 退货入库单 RR（spec warehouse/sales-return）：销售退货单确认后生成 → 仓库验收（销售退货单变已入库、待验收的退款申请进入下一步）
 * → 采购员逐行处理去向（退回供应商生成采购退货单、转闲置库存、报废，可以分几次处理）→ 全部处理完为已处理。
 * 转闲置冲减原订单成本 = 数量 × 原采购成本（HALF_UP 两位，处理时落库）；退回供应商的冲减与运费由采购退货单写回去向行。
 * 加锁顺序：退货入库单 → 销售退货单 → 采购退货单。
 */
@Service
@RequiredArgsConstructor
public class ReturnReceiptService {

    private final ReturnReceiptMapper mapper;
    private final ReturnReceiptItemMapper itemMapper;
    private final SalesReturnMapper returnMapper;
    private final SalesReturnDispositionMapper dispositionMapper;
    private final AfterSalesMapper afterSalesMapper;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final SupplierMapper supplierMapper;
    private final StockHoldMapper holdMapper;
    private final ReceivingSupport receivingSupport;
    private final ReturnCost returnCost;
    private final RefundRequestService refundRequests;
    private final PurchaseReturnService purchaseReturns;
    private final DocumentNumberService numbers;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final AttachmentService attachments;
    private final LogService logService;
    private final AfterSalesLogs logs;
    private final DocLogs docLogs;
    private final PermissionChecker perm;

    // ---------------------------------------------------------------- 生成与作废（销售退货单事务内）

    /** 确认销售退货单时生成；负责人取原订单型号的采购员，没有时为售后单负责人 */
    public ReturnReceiptDO createFor(SalesReturnDO r, List<SalesReturnItemDO> items, SalesOrderDO o) {
        Map<Long, SalesOrderItemDO> soItems = orderItemMapper.selectBatchIds(items.stream().map(SalesReturnItemDO::getSoItemId).toList())
                .stream().collect(Collectors.toMap(SalesOrderItemDO::getId, i -> i));
        Long owner = items.stream().map(x -> soItems.get(x.getSoItemId())).filter(Objects::nonNull)
                .map(SalesOrderItemDO::getPurchaserId).filter(x -> x != null && x > 0).findFirst()
                .orElseGet(() -> afterSalesMapper.selectById(r.getAfterSalesId()).getOwnerId());
        ReturnReceiptDO rr = new ReturnReceiptDO();
        rr.setTenantId(r.getTenantId());
        rr.setReceiptNo(numbers.next(DocumentType.RR));
        rr.setSalesReturnId(r.getId());
        rr.setAfterSalesId(r.getAfterSalesId());
        rr.setSoId(o.getId());
        rr.setOwnerId(owner == null ? 0L : owner);
        rr.setStatus(AfterSalesConstants.RR_PENDING);
        rr.setReceiveNote("");
        rr.setRemark("");
        rr.setVoidReason("");
        mapper.insert(rr);
        for (SalesReturnItemDO x : items) {
            ReturnReceiptItemDO i = new ReturnReceiptItemDO();
            i.setTenantId(r.getTenantId());
            i.setReceiptId(rr.getId());
            i.setReturnItemId(x.getId());
            i.setSoItemId(x.getSoItemId());
            i.setModel(x.getModel());
            i.setBrand(x.getBrand());
            i.setQuantity(x.getQuantity());
            i.setDisposedQty(0);
            i.setNote("");
            itemMapper.insert(i);
        }
        docLogs.add(DocumentType.RR, rr.getId(), "生成", "销售退货单 " + r.getReturnNo() + " 确认退货：" + SalesReturnService.summary(items));
        return rr;
    }

    /** 作废销售退货单时：待入库的退货入库单随之作废；已验收的不能作废 */
    public ReturnReceiptDO voidFor(SalesReturnDO r, String reason) {
        ReturnReceiptDO rr = mapper.selectOne(new LambdaQueryWrapper<ReturnReceiptDO>()
                .eq(ReturnReceiptDO::getSalesReturnId, r.getId())
                .ne(ReturnReceiptDO::getStatus, AfterSalesConstants.RR_VOID)
                .isNull(ReturnReceiptDO::getDeletedAt)
                .last("LIMIT 1"));
        if (rr == null) {
            return null;
        }
        mapper.lockById(rr.getId());
        rr = mapper.selectById(rr.getId());
        if (rr.getStatus() != AfterSalesConstants.RR_PENDING) {
            throw new BizException("已入库的退货单不能作废");
        }
        rr.setStatus(AfterSalesConstants.RR_VOID);
        rr.setVoidReason(reason);
        mapper.updateById(rr);
        docLogs.add(DocumentType.RR, rr.getId(), "作废", "销售退货单 " + r.getReturnNo() + " 作废：" + reason);
        return rr;
    }

    // ---------------------------------------------------------------- 验收

    @Transactional(rollbackFor = Exception.class)
    public ReturnReceiptVO receive(Long id, ReceiveReturnRequest req) {
        ReturnReceiptDO rr = lock(id);
        if (rr.getStatus() != AfterSalesConstants.RR_PENDING) {
            throw new BizException(rr.getStatus() == AfterSalesConstants.RR_VOID ? "退货入库单已作废" : "退货入库单已验收");
        }
        if (req.getReceivedDate().isAfter(LocalDate.now())) {
            throw new BizException("收货日期不能晚于今天");
        }
        Map<Long, ReturnReceiptItemDO> items = itemsOf(List.of(id)).getOrDefault(id, List.of()).stream()
                .collect(Collectors.toMap(ReturnReceiptItemDO::getId, x -> x));
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
        for (ReturnReceiptItemDO x : items.values()) {
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
            x.setNote(trim(l.getNote()));
            itemMapper.updateById(x);
            total += l.getReceivedQty();
            int bad = l.getReceivedQty() - l.getQualifiedQty();
            text.add(x.getModel() + " 实收 " + l.getReceivedQty() + "、合格 " + l.getQualifiedQty() + (bad > 0 ? "、不良 " + bad : ""));
        }
        if (total == 0) {
            throw new BizException("实收数量都是 0，客户还没寄回时请不要验收");
        }
        boolean allDisposed = items.values().stream().allMatch(x -> x.getReceivedQty() == 0);
        rr.setStatus(allDisposed ? AfterSalesConstants.RR_DONE : AfterSalesConstants.RR_RECEIVED);
        rr.setReceivedDate(req.getReceivedDate());
        rr.setReceivedBy(me());
        rr.setReceiveNote(trim(req.getNote()));
        mapper.updateById(rr);
        attachments.attach(AttachmentService.RETURN_RECEIPT, rr.getId(), req.getAttachmentIds());
        SalesReturnDO r = lockReturn(rr.getSalesReturnId());
        r.setStatus(AfterSalesConstants.RT_RECEIVED);
        returnMapper.updateById(r);
        List<String> refunds = refundRequests.onReturnReceived(r);
        String tail = refunds.isEmpty() ? "" : "；" + String.join("；", refunds);
        docLogs.add(DocumentType.RR, id, "验收", String.join("；", text));
        docLogs.add(DocumentType.TH, r.getId(), "已入库", "退货入库单 " + rr.getReceiptNo() + " 验收：" + String.join("；", text) + tail);
        logs.add(rr.getAfterSalesId(), AfterSalesConstants.LOG_RETURN, "退货入库单 " + rr.getReceiptNo() + " 已验收：" + String.join("；", text)
                + tail, me());
        logService.recordOperateLog(AfterSalesConstants.MENU_RETURN, "退货验收", null, Map.of("receiptNo", rr.getReceiptNo(), "items", text));
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReturnReceiptVO unreceive(Long id, String reason) {
        String why = trim(reason);
        if (why.isEmpty()) {
            throw new BizException("请填写撤回原因");
        }
        ReturnReceiptDO rr = lock(id);
        if (rr.getStatus() != AfterSalesConstants.RR_RECEIVED && rr.getStatus() != AfterSalesConstants.RR_DONE) {
            throw new BizException("退货入库单还没入库");
        }
        List<ReturnReceiptItemDO> items = itemsOf(List.of(id)).getOrDefault(id, List.of());
        if (items.stream().anyMatch(x -> x.getDisposedQty() > 0)) {
            throw new BizException("已经处理过去向，不能撤回入库");
        }
        items.forEach(x -> {
            x.setReceivedQty(null);
            x.setQualifiedQty(null);
            itemMapper.updateById(x);
        });
        rr.setStatus(AfterSalesConstants.RR_PENDING);
        rr.setReceivedDate(null);
        rr.setReceivedBy(null);
        rr.setReceiveNote("");
        mapper.updateById(rr);
        SalesReturnDO r = lockReturn(rr.getSalesReturnId());
        r.setStatus(AfterSalesConstants.RT_PENDING);
        returnMapper.updateById(r);
        List<String> refunds = refundRequests.onReturnUnreceived(r);
        String tail = refunds.isEmpty() ? "" : "；" + String.join("；", refunds);
        docLogs.add(DocumentType.RR, id, "撤回入库", why);
        docLogs.add(DocumentType.TH, r.getId(), "回到待入库", "退货入库单 " + rr.getReceiptNo() + " 撤回入库：" + why + tail);
        logs.add(rr.getAfterSalesId(), AfterSalesConstants.LOG_RETURN, "退货入库单 " + rr.getReceiptNo() + " 撤回入库：" + why + tail, me());
        logService.recordOperateLog(AfterSalesConstants.MENU_RETURN, "撤回退货入库", null, Map.of("receiptNo", rr.getReceiptNo(), "reason", why));
        return detail(id);
    }

    // ---------------------------------------------------------------- 去向

    /** 逐行选择去向，可以分几次处理，每行累计不超过实收；转闲置累计不超过合格数量 */
    @Transactional(rollbackFor = Exception.class)
    public ReturnReceiptVO dispose(Long id, DisposeReturnRequest req) {
        ReturnReceiptDO rr = lock(id);
        if (rr.getStatus() != AfterSalesConstants.RR_RECEIVED) {
            throw new BizException(rr.getStatus() == AfterSalesConstants.RR_DONE ? "退货已处理完" : "退货入库单还没验收");
        }
        Map<Long, ReturnReceiptItemDO> items = itemsOf(List.of(id)).getOrDefault(id, List.of()).stream()
                .collect(Collectors.toMap(ReturnReceiptItemDO::getId, x -> x, (a, b) -> a, LinkedHashMap::new));
        Map<Long, Integer> sum = new HashMap<>();
        Map<Long, Integer> toHold = new HashMap<>();
        for (DisposeReturnRequest.Line l : req.getLines()) {
            ReturnReceiptItemDO x = items.get(l.getItemId());
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
            sum.merge(x.getId(), l.getQuantity(), Integer::sum);
            if (l.getDisposition() == AfterSalesConstants.DISP_HOLD) {
                toHold.merge(x.getId(), l.getQuantity(), Integer::sum);
            }
        }
        if (sum.values().stream().mapToInt(Integer::intValue).sum() == 0) {
            throw new BizException("请填写去向数量");
        }
        Map<Long, Integer> heldBefore = heldQty(id);
        for (ReturnReceiptItemDO x : items.values()) {
            int received = Objects.requireNonNullElse(x.getReceivedQty(), 0);
            int left = received - x.getDisposedQty();
            if (sum.getOrDefault(x.getId(), 0) > left) {
                throw new BizException(x.getModel() + " 还有 " + left + " 个没处理，去向数量不能多于它");
            }
            int qualified = Objects.requireNonNullElse(x.getQualifiedQty(), 0);
            int canHold = qualified - heldBefore.getOrDefault(x.getId(), 0);
            if (toHold.getOrDefault(x.getId(), 0) > canHold) {
                throw new BizException("只有合格的才能转闲置库存（合格 " + Math.max(canHold, 0) + " 个）");
            }
        }
        Map<Long, ReturnCost.Origin> origins = returnCost.originsOf(items.values().stream().map(ReturnReceiptItemDO::getSoItemId).toList());
        Map<Long, String> suppliers = supplierNames(req.getLines().stream().map(DisposeReturnRequest.Line::getSupplierId).toList());
        Long operator = me();
        List<String> text = new ArrayList<>();
        List<PurchaseReturnService.AfterSalesLine> toSupplier = new ArrayList<>();
        for (DisposeReturnRequest.Line l : req.getLines()) {
            if (l.getQuantity() == 0) {
                continue;
            }
            ReturnReceiptItemDO x = items.get(l.getItemId());
            ReturnCost.Origin origin = origins.getOrDefault(x.getSoItemId(), ReturnCost.Origin.NONE);
            SalesReturnDispositionDO d = new SalesReturnDispositionDO();
            d.setTenantId(rr.getTenantId());
            d.setReturnId(rr.getSalesReturnId());
            d.setReturnItemId(x.getReturnItemId());
            d.setReceiptId(rr.getId());
            d.setReceiptItemId(x.getId());
            d.setSoId(rr.getSoId());
            d.setSoItemId(x.getSoItemId());
            d.setDisposition(l.getDisposition());
            d.setQuantity(l.getQuantity());
            d.setTrackingNo("");
            d.setFreightCny(BigDecimal.ZERO.setScale(2));
            d.setSupplierRefundCny(BigDecimal.ZERO.setScale(2));
            d.setCostCny(BigDecimal.ZERO.setScale(2));
            d.setRemark(trim(l.getRemark()));
            d.setOperatorId(operator);
            if (l.getDisposition() == AfterSalesConstants.DISP_SUPPLIER) {
                PurchaseOrderDO po = l.getPoId() == null ? null : receivingSupport.po(l.getPoId(), false);
                // 线上店铺采购的采购订单没有供应商（supplier_id 为 0），退回原店铺
                boolean shop = l.getSupplierId() == 0 && po != null && Objects.equals(po.getSupplierId(), 0L);
                String name = shop ? receivingSupport.counterparties(List.of(po)).get(po.getId()) : suppliers.get(l.getSupplierId());
                if (!shop && name == null) {
                    throw new BizException("供应商不存在");
                }
                d.setSupplierId(l.getSupplierId());
                dispositionMapper.insert(d);
                toSupplier.add(new PurchaseReturnService.AfterSalesLine(l.getSupplierId(), l.getPoId(), x, d));
                text.add(x.getModel() + " × " + l.getQuantity() + " 退回 " + name);
            } else if (l.getDisposition() == AfterSalesConstants.DISP_HOLD) {
                BigDecimal unit = origin.unitCost();
                d.setCostCny(unit.multiply(BigDecimal.valueOf(l.getQuantity())).setScale(2, RoundingMode.HALF_UP));
                StockHoldDO h = new StockHoldDO();
                h.setTenantId(rr.getTenantId());
                h.setSourceType(WarehouseConstants.HOLD_SRC_AFTER_SALES);
                h.setDiscrepancyId(0L);
                h.setReceiptId(0L);
                h.setPoId(origin.poId() == null ? 0L : origin.poId());
                h.setSoId(rr.getSoId());
                h.setSoItemId(x.getSoItemId());
                h.setReturnId(rr.getId());
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
                dispositionMapper.insert(d);
                text.add(x.getModel() + " × " + l.getQuantity() + " 转闲置库存，冲减 ¥" + d.getCostCny());
            } else {
                dispositionMapper.insert(d);
                text.add(x.getModel() + " × " + l.getQuantity() + " 报废" + (d.getRemark().isEmpty() ? "" : "：" + d.getRemark()));
            }
            x.setDisposedQty(x.getDisposedQty() + l.getQuantity());
        }
        items.values().forEach(itemMapper::updateById);
        if (!toSupplier.isEmpty()) {
            List<PurchaseReturnDO> prs = purchaseReturns.createForAfterSales(rr, toSupplier);
            text.add("生成采购退货单 " + prs.stream().map(PurchaseReturnDO::getReturnNo).collect(Collectors.joining("、")));
        }
        boolean done = items.values().stream().allMatch(x -> x.getDisposedQty() >= Objects.requireNonNullElse(x.getReceivedQty(), 0));
        if (done) {
            rr.setStatus(AfterSalesConstants.RR_DONE);
        }
        mapper.updateById(rr);
        docLogs.add(DocumentType.RR, id, "处理去向", String.join("；", text) + (done ? "（已全部处理）" : ""));
        logs.add(rr.getAfterSalesId(), AfterSalesConstants.LOG_RETURN, "退货入库单 " + rr.getReceiptNo() + " 处理去向：" + String.join("；", text),
                operator);
        logService.recordOperateLog(AfterSalesConstants.MENU_RETURN, "处理退货去向", null, Map.of("receiptNo", rr.getReceiptNo(), "lines", text));
        return detail(id);
    }

    /** 退货入库单行已转闲置的数量（有效去向） */
    private Map<Long, Integer> heldQty(Long receiptId) {
        Map<Long, Integer> m = new HashMap<>();
        dispositionMapper.selectList(new LambdaQueryWrapper<SalesReturnDispositionDO>()
                        .eq(SalesReturnDispositionDO::getReceiptId, receiptId)
                        .eq(SalesReturnDispositionDO::getDisposition, AfterSalesConstants.DISP_HOLD)
                        .isNull(SalesReturnDispositionDO::getDeletedAt))
                .forEach(d -> m.merge(d.getReceiptItemId(), d.getQuantity(), Integer::sum));
        return m;
    }

    // ---------------------------------------------------------------- 备注与附件

    @Transactional(rollbackFor = Exception.class)
    public ReturnReceiptVO remark(Long id, String remark) {
        ReturnReceiptDO rr = lock(id);
        requireNotVoid(rr);
        rr.setRemark(trim(remark));
        mapper.updateById(rr);
        docLogs.add(DocumentType.RR, id, "修改备注", rr.getRemark().isEmpty() ? "清空备注" : rr.getRemark());
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReturnReceiptVO attach(Long id, List<Long> ids) {
        requireNotVoid(require(id));
        attachments.attach(AttachmentService.RETURN_RECEIPT, id, ids);
        docLogs.add(DocumentType.RR, id, "上传附件", ids.size() + " 个");
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReturnReceiptVO removeAttachment(Long id, Long attachmentId) {
        requireNotVoid(require(id));
        attachments.remove(AttachmentService.RETURN_RECEIPT, id, attachmentId);
        docLogs.add(DocumentType.RR, id, "删除附件", "");
        return detail(id);
    }

    // ---------------------------------------------------------------- 查看

    public PageResult<ReturnReceiptVO> page(ReturnQuery q) {
        LambdaQueryWrapper<ReturnReceiptDO> w = new LambdaQueryWrapper<ReturnReceiptDO>()
                .eq(ReturnReceiptDO::getTenantId, PiStore.tenantId())
                .isNull(ReturnReceiptDO::getDeletedAt);
        if (q.getStatus() != null) {
            w.eq(ReturnReceiptDO::getStatus, q.getStatus());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            Set<Long> soIds = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>().select(SalesOrderDO::getId)
                            .eq(SalesOrderDO::getTenantId, PiStore.tenantId()).like(SalesOrderDO::getSoNo, kw).last("LIMIT 200"))
                    .stream().map(SalesOrderDO::getId).collect(Collectors.toSet());
            Set<Long> returnIds = returnMapper.selectList(new LambdaQueryWrapper<SalesReturnDO>().select(SalesReturnDO::getId)
                            .eq(SalesReturnDO::getTenantId, PiStore.tenantId())
                            .and(x -> x.like(SalesReturnDO::getReturnNo, kw).or().like(SalesReturnDO::getTrackingNo, kw)).last("LIMIT 200"))
                    .stream().map(SalesReturnDO::getId).collect(Collectors.toSet());
            Set<Long> asIds = afterSalesMapper.selectList(new LambdaQueryWrapper<AfterSalesDO>().select(AfterSalesDO::getId)
                            .eq(AfterSalesDO::getTenantId, PiStore.tenantId()).like(AfterSalesDO::getAsNo, kw).last("LIMIT 200"))
                    .stream().map(AfterSalesDO::getId).collect(Collectors.toSet());
            Set<Long> byModel = itemMapper.selectList(new LambdaQueryWrapper<ReturnReceiptItemDO>().select(ReturnReceiptItemDO::getReceiptId)
                            .eq(ReturnReceiptItemDO::getTenantId, PiStore.tenantId()).like(ReturnReceiptItemDO::getModel, kw)
                            .isNull(ReturnReceiptItemDO::getDeletedAt).last("LIMIT 500"))
                    .stream().map(ReturnReceiptItemDO::getReceiptId).collect(Collectors.toSet());
            w.and(x -> {
                x.like(ReturnReceiptDO::getReceiptNo, kw);
                if (!soIds.isEmpty()) {
                    x.or().in(ReturnReceiptDO::getSoId, soIds);
                }
                if (!returnIds.isEmpty()) {
                    x.or().in(ReturnReceiptDO::getSalesReturnId, returnIds);
                }
                if (!byModel.isEmpty()) {
                    x.or().in(ReturnReceiptDO::getId, byModel);
                }
                if (!asIds.isEmpty()) {
                    x.or().in(ReturnReceiptDO::getAfterSalesId, asIds);
                }
            });
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = mapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        w.last("ORDER BY update_time DESC, id DESC LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toVos(mapper.selectList(w), false));
    }

    public Map<String, Long> counts() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (int s : List.of(AfterSalesConstants.RR_PENDING, AfterSalesConstants.RR_RECEIVED)) {
            m.put(s == AfterSalesConstants.RR_PENDING ? "pending" : "received", mapper.selectCount(new LambdaQueryWrapper<ReturnReceiptDO>()
                    .eq(ReturnReceiptDO::getTenantId, PiStore.tenantId())
                    .eq(ReturnReceiptDO::getStatus, s)
                    .isNull(ReturnReceiptDO::getDeletedAt)));
        }
        return m;
    }

    public ReturnReceiptVO detail(Long id) {
        return toVos(List.of(visible(id)), true).get(0);
    }

    private List<ReturnReceiptVO> toVos(List<ReturnReceiptDO> rows, boolean full) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(ReturnReceiptDO::getId).toList();
        Map<Long, List<ReturnReceiptItemDO>> items = itemsOf(ids);
        Map<Long, SalesReturnDO> returns = returnMapper.selectBatchIds(rows.stream().map(ReturnReceiptDO::getSalesReturnId).distinct().toList())
                .stream().collect(Collectors.toMap(SalesReturnDO::getId, r -> r));
        Map<Long, AfterSalesDO> afterSales = afterSalesMapper.selectBatchIds(rows.stream().map(ReturnReceiptDO::getAfterSalesId).distinct().toList())
                .stream().collect(Collectors.toMap(AfterSalesDO::getId, a -> a));
        Map<Long, SalesOrderDO> orders = orderMapper.selectBatchIds(rows.stream().map(ReturnReceiptDO::getSoId).distinct().toList())
                .stream().collect(Collectors.toMap(SalesOrderDO::getId, o -> o));
        Map<Long, CustomerDO> customers = lookups.customers(orders.values().stream().map(SalesOrderDO::getCustomerId).distinct().toList());
        List<Long> soItemIds = items.values().stream().flatMap(List::stream).map(ReturnReceiptItemDO::getSoItemId).distinct().toList();
        Map<Long, String> categories = soItemIds.isEmpty() ? Map.of() : orderItemMapper.selectBatchIds(soItemIds).stream()
                .collect(Collectors.toMap(SalesOrderItemDO::getId, i -> Objects.requireNonNullElse(i.getCategory(), "")));
        List<SalesReturnDispositionDO> disps = dispositionMapper.selectList(new LambdaQueryWrapper<SalesReturnDispositionDO>()
                .in(SalesReturnDispositionDO::getReceiptId, ids)
                .isNull(SalesReturnDispositionDO::getDeletedAt)
                .orderByAsc(SalesReturnDispositionDO::getId));
        Map<Long, List<SalesReturnDispositionDO>> dispByReceipt = disps.stream().collect(Collectors.groupingBy(SalesReturnDispositionDO::getReceiptId));
        Map<Long, String> users = lookups.userNames(rows.stream().flatMap(r -> java.util.stream.Stream.of(r.getReceivedBy(), r.getOwnerId()))
                .filter(Objects::nonNull).distinct().toList());
        Map<Long, ReturnCost.Origin> origins = full && !soItemIds.isEmpty() ? returnCost.originsOf(soItemIds) : Map.of();
        List<Long> supplierIds = new ArrayList<>(disps.stream().map(SalesReturnDispositionDO::getSupplierId).filter(Objects::nonNull).toList());
        origins.values().stream().map(ReturnCost.Origin::supplierId).filter(Objects::nonNull).forEach(supplierIds::add);
        Map<Long, String> suppliers = supplierNames(supplierIds);
        Map<Long, String> shops = shopNames(origins.values());
        Map<Long, String> operators = full ? lookups.userNames(disps.stream().map(SalesReturnDispositionDO::getOperatorId).toList()) : Map.of();
        Map<Long, String> prNos = purchaseReturns.numbers(disps.stream().map(SalesReturnDispositionDO::getPurchaseReturnId).toList());
        List<ReturnReceiptVO> out = new ArrayList<>();
        for (ReturnReceiptDO rr : rows) {
            ReturnReceiptVO v = new ReturnReceiptVO();
            SalesReturnDO r = returns.get(rr.getSalesReturnId());
            AfterSalesDO a = afterSales.get(rr.getAfterSalesId());
            SalesOrderDO o = orders.get(rr.getSoId());
            CustomerDO c = o == null ? null : customers.get(o.getCustomerId());
            v.setId(rr.getId());
            v.setReceiptNo(rr.getReceiptNo());
            v.setSalesReturnId(rr.getSalesReturnId());
            v.setReturnNo(r == null ? null : r.getReturnNo());
            v.setAfterSalesId(rr.getAfterSalesId());
            v.setAsNo(a == null ? null : a.getAsNo());
            v.setSoId(rr.getSoId());
            v.setSoNo(o == null ? null : o.getSoNo());
            v.setCustomerName(InquiryLookups.customerName(c));
            v.setCustomerCountry(c == null ? null : c.getCountry());
            v.setCustomerType(o == null ? null : o.getCustomerType());
            v.setCustomerContact(InquiryLookups.contactName(c));
            v.setCarrier(r == null ? null : r.getCarrier());
            v.setTrackingNo(r == null ? null : r.getTrackingNo());
            v.setOwnerId(rr.getOwnerId());
            v.setOwnerName(users.get(rr.getOwnerId()));
            v.setStatus(rr.getStatus());
            v.setStatusName(AfterSalesConstants.RR_STATUS_NAMES.get(rr.getStatus()));
            v.setReceivedDate(rr.getReceivedDate());
            v.setReceivedByName(rr.getReceivedBy() == null ? null : users.get(rr.getReceivedBy()));
            v.setReceiveNote(rr.getReceiveNote());
            v.setRemark(rr.getRemark());
            v.setVoidReason(rr.getVoidReason());
            List<ReturnReceiptItemDO> mine = items.getOrDefault(rr.getId(), List.of());
            v.setItemSummary(mine.stream().map(x -> x.getModel() + " × " + x.getQuantity()).collect(Collectors.joining("、")));
            v.setTotalQty(mine.stream().mapToInt(ReturnReceiptItemDO::getQuantity).sum());
            if (rr.getReceivedDate() != null) {
                v.setReceivedQty(mine.stream().mapToInt(x -> Objects.requireNonNullElse(x.getReceivedQty(), 0)).sum());
                v.setQualifiedQty(mine.stream().mapToInt(x -> Objects.requireNonNullElse(x.getQualifiedQty(), 0)).sum());
            }
            v.setItems(mine.stream().map(x -> {
                ReturnReceiptVO.Item i = new ReturnReceiptVO.Item();
                i.setId(x.getId());
                i.setReturnItemId(x.getReturnItemId());
                i.setSoItemId(x.getSoItemId());
                i.setModel(x.getModel());
                i.setBrand(x.getBrand());
                i.setCategory(categories.getOrDefault(x.getSoItemId(), ""));
                i.setQuantity(x.getQuantity());
                i.setReceivedQty(x.getReceivedQty());
                i.setQualifiedQty(x.getQualifiedQty());
                i.setDisposedQty(x.getDisposedQty());
                i.setNote(x.getNote());
                ReturnCost.Origin og = origins.get(x.getSoItemId());
                if (og != null) {
                    i.setUnitCost(og.unitCost());
                    i.setSupplierId(og.supplierId());
                    i.setSupplierName(supplierName(og.supplierId(), og.poId(), suppliers, shops));
                    i.setPoId(og.poId());
                    i.setPoNo(og.poNo());
                }
                return i;
            }).toList());
            v.setBrands(v.getItems().stream().map(ReturnReceiptVO.Item::getBrand).filter(StringUtils::hasText).distinct().toList());
            v.setCategories(v.getItems().stream().map(ReturnReceiptVO.Item::getCategory).filter(StringUtils::hasText).distinct().toList());
            Map<Long, String> models = mine.stream().collect(Collectors.toMap(ReturnReceiptItemDO::getId, ReturnReceiptItemDO::getModel));
            List<SalesReturnDispositionDO> myDisps = dispByReceipt.getOrDefault(rr.getId(), List.of());
            v.setCostCny(myDisps.stream().map(SalesReturnDispositionDO::getCostCny).reduce(BigDecimal.ZERO, BigDecimal::add));
            v.setFreightCny(myDisps.stream().map(SalesReturnDispositionDO::getFreightCny).reduce(BigDecimal.ZERO, BigDecimal::add));
            v.setDispositions(myDisps.stream().map(d -> {
                ReturnReceiptVO.Disposition x = new ReturnReceiptVO.Disposition();
                x.setId(d.getId());
                x.setItemId(d.getReceiptItemId());
                x.setModel(models.get(d.getReceiptItemId()));
                x.setDisposition(d.getDisposition());
                x.setDispositionName(AfterSalesConstants.DISP_NAMES.get(d.getDisposition()));
                x.setQuantity(d.getQuantity());
                x.setSupplierId(d.getSupplierId());
                x.setSupplierName(supplierName(d.getSupplierId(),
                        origins.getOrDefault(d.getSoItemId(), ReturnCost.Origin.NONE).poId(), suppliers, shops));
                x.setFreightCny(d.getFreightCny());
                x.setCostCny(d.getCostCny());
                x.setHoldId(d.getHoldId());
                x.setPurchaseReturnId(d.getPurchaseReturnId());
                x.setPurchaseReturnNo(prNos.get(d.getPurchaseReturnId()));
                x.setRemark(d.getRemark());
                x.setOperatorName(operators.get(d.getOperatorId()));
                x.setCreateTime(d.getCreateTime());
                return x;
            }).toList());
            if (full) {
                v.setPurchaseReturns(purchaseReturns.ofReturnReceipt(rr.getId()).stream().map(ReturnReceiptService::brief).toList());
                v.setAttachments(attachments.listOf(AttachmentService.RETURN_RECEIPT, rr.getId()));
                v.setLogs(docLogs.list(DocumentType.RR, rr.getId()));
            }
            v.setCreateTime(rr.getCreateTime());
            v.setCreateBy(rr.getCreateBy());
            v.setUpdateTime(rr.getUpdateTime());
            v.setUpdateBy(rr.getUpdateBy());
            out.add(v);
        }
        return out;
    }

    private static ReturnVO.DocBrief brief(PurchaseReturnVO p) {
        ReturnVO.DocBrief b = new ReturnVO.DocBrief();
        b.setId(p.getId());
        b.setNo(p.getReturnNo());
        b.setStatus(p.getStatus());
        b.setStatusName(p.getStatusName());
        return b;
    }

    private Map<Long, List<ReturnReceiptItemDO>> itemsOf(Collection<Long> ids) {
        return itemMapper.selectList(new LambdaQueryWrapper<ReturnReceiptItemDO>()
                        .in(ReturnReceiptItemDO::getReceiptId, ids)
                        .isNull(ReturnReceiptItemDO::getDeletedAt)
                        .orderByAsc(ReturnReceiptItemDO::getId))
                .stream().collect(Collectors.groupingBy(ReturnReceiptItemDO::getReceiptId, LinkedHashMap::new, Collectors.toList()));
    }

    /** 线上店铺采购的原采购订单（supplier_id 为 0）的店铺名，按采购订单 */
    private Map<Long, String> shopNames(Collection<ReturnCost.Origin> origins) {
        List<Long> poIds = origins.stream().filter(o -> o.poId() != null && Objects.equals(o.supplierId(), 0L))
                .map(ReturnCost.Origin::poId).distinct().toList();
        return poIds.isEmpty() ? Map.of() : receivingSupport.counterparties(receivingSupport.pos(poIds).values());
    }

    private static String supplierName(Long supplierId, Long poId, Map<Long, String> suppliers, Map<Long, String> shops) {
        if (supplierId == null) {
            return null;
        }
        return supplierId == 0 ? (poId == null ? null : shops.get(poId)) : suppliers.get(supplierId);
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

    private ReturnReceiptDO require(Long id) {
        ReturnReceiptDO rr = id == null ? null : mapper.selectById(id);
        if (rr == null || rr.getDeletedAt() != null || !Objects.equals(rr.getTenantId(), PiStore.tenantId())) {
            throw new BizException("退货入库单不存在");
        }
        return rr;
    }

    /**
     * 按 ID 查看（audit SEC-015）：退货入库菜单（仓库）看本租户全部；只从销售退货、售后进来的人
     * 与售后单详情一致，售后单负责人或订单负责人在数据范围内才能看
     */
    private ReturnReceiptDO visible(Long id) {
        ReturnReceiptDO rr = require(id);
        if (perm.canAccessMenu("/warehouse/returns")) {
            return rr;
        }
        DataScope scope = receivingSupport.scope();
        AfterSalesDO a = rr.getAfterSalesId() == null ? null : afterSalesMapper.selectById(rr.getAfterSalesId());
        SalesOrderDO o = rr.getSoId() == null ? null : orderMapper.selectById(rr.getSoId());
        if ((a == null || !scope.canSee(a.getOwnerId())) && (o == null || !scope.canSee(o.getOwnerId()))) {
            throw new BizException("退货入库单不存在");
        }
        return rr;
    }

    private ReturnReceiptDO lock(Long id) {
        require(id);
        mapper.lockById(id);
        return mapper.selectById(id);
    }

    private SalesReturnDO lockReturn(Long id) {
        returnMapper.lockById(id);
        return returnMapper.selectById(id);
    }

    private static void requireNotVoid(ReturnReceiptDO rr) {
        if (rr.getStatus() == AfterSalesConstants.RR_VOID) {
            throw new BizException("退货入库单已作废，只能查看");
        }
    }

    private Long me() {
        Long id = currentUser.resolve();
        return id == null ? 0L : id;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}

package com.zhul.erp.modules.purchase.purchasereturn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.aftersales.constants.AfterSalesConstants;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.aftersales.entity.ReturnReceiptDO;
import com.zhul.erp.modules.aftersales.entity.ReturnReceiptItemDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO;
import com.zhul.erp.modules.aftersales.repository.AfterSalesMapper;
import com.zhul.erp.modules.aftersales.repository.ReturnReceiptItemMapper;
import com.zhul.erp.modules.aftersales.repository.ReturnReceiptMapper;
import com.zhul.erp.modules.aftersales.repository.SalesReturnDispositionMapper;
import com.zhul.erp.modules.aftersales.support.AfterSalesLogs;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.finance.service.PayableSyncService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.purchasereturn.PurchaseReturnConstants;
import com.zhul.erp.modules.purchase.purchasereturn.dto.CompletePurchaseReturnRequest;
import com.zhul.erp.modules.purchase.purchasereturn.dto.PurchaseReturnQtyRequest;
import com.zhul.erp.modules.purchase.purchasereturn.dto.PurchaseReturnQuery;
import com.zhul.erp.modules.purchase.purchasereturn.dto.PurchaseReturnVO;
import com.zhul.erp.modules.purchase.purchasereturn.dto.ShipPurchaseReturnRequest;
import com.zhul.erp.modules.purchase.purchasereturn.entity.PurchaseReturnDO;
import com.zhul.erp.modules.purchase.purchasereturn.entity.PurchaseReturnItemDO;
import com.zhul.erp.modules.purchase.purchasereturn.repository.PurchaseReturnItemMapper;
import com.zhul.erp.modules.purchase.purchasereturn.repository.PurchaseReturnMapper;
import com.zhul.erp.modules.purchase.support.PurchaseCalc;
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
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.ReceivingDiscrepancyDO;
import com.zhul.erp.modules.warehouse.repository.PurchaseReceiptMapper;
import com.zhul.erp.modules.warehouse.repository.ReceivingDiscrepancyMapper;
import com.zhul.erp.modules.warehouse.service.DiscrepancyService;
import com.zhul.erp.modules.warehouse.support.ReceivingSupport;
import org.springframework.context.annotation.Lazy;
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
import java.util.stream.Stream;

/**
 * 采购退货单（spec purchase/purchase-return）：售后退货的退回供应商、到货差异的退货换货 / 退货不补 / 多发退回都生成采购退货单，
 * 跟踪寄出、签收与供应商退款或抵扣。
 * <p>
 * 金额：运费人民币；退款或抵扣按采购订单币种录入，人民币 = 原币 × 采购订单汇率，HALF_UP 两位（没有采购订单为人民币、汇率 1）。
 * 订单成本：售后来源把运费（寄出后）与退款人民币（完成后）按数量分摊写回去向行的 freight_cny / cost_cny（尾差放最后一行），
 * 订单成本的「售后退货冲减」照旧读去向行；到货差异来源把运费写回差异的 return_freight。
 * 应付：售后来源且有采购订单、非历史迁移的，完成 / 重新打开时重算该采购订单应付（见 PayableSyncService）。
 * 事务边界在本类公开方法上；被到货差异、退货入库调用的方法在调用方事务内执行。加锁顺序：退货入库单 → 采购退货单 → 采购订单。
 */
@Service
public class PurchaseReturnService {

    private final PurchaseReturnMapper mapper;
    private final PurchaseReturnItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final ReceivingSupport support;
    private final ReturnReceiptMapper receiptMapper;
    private final ReturnReceiptItemMapper receiptItemMapper;
    private final SalesReturnDispositionMapper dispositionMapper;
    private final ReceivingDiscrepancyMapper discrepancyMapper;
    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final AfterSalesMapper afterSalesMapper;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final InquiryLookups lookups;
    private final AttachmentService attachments;
    private final DocLogs docLogs;
    private final AfterSalesLogs afterSalesLogs;
    private final PayableSyncService payables;
    private final DocumentNumberService numbers;
    private final CurrentUserResolver currentUser;
    private final LogService logService;
    private final DiscrepancyService discrepancyService;

    @SuppressWarnings("java:S107")
    public PurchaseReturnService(PurchaseReturnMapper mapper, PurchaseReturnItemMapper itemMapper, SupplierMapper supplierMapper,
                                 ReceivingSupport support, ReturnReceiptMapper receiptMapper, ReturnReceiptItemMapper receiptItemMapper,
                                 SalesReturnDispositionMapper dispositionMapper, ReceivingDiscrepancyMapper discrepancyMapper,
                                 PurchaseReceiptMapper purchaseReceiptMapper, AfterSalesMapper afterSalesMapper, SalesOrderMapper orderMapper,
                                 SalesOrderItemMapper orderItemMapper, InquiryLookups lookups, AttachmentService attachments, DocLogs docLogs,
                                 AfterSalesLogs afterSalesLogs, PayableSyncService payables,
                                 DocumentNumberService numbers, CurrentUserResolver currentUser, LogService logService,
                                 @Lazy DiscrepancyService discrepancyService) {
        this.mapper = mapper;
        this.itemMapper = itemMapper;
        this.supplierMapper = supplierMapper;
        this.support = support;
        this.receiptMapper = receiptMapper;
        this.receiptItemMapper = receiptItemMapper;
        this.dispositionMapper = dispositionMapper;
        this.discrepancyMapper = discrepancyMapper;
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.afterSalesMapper = afterSalesMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.lookups = lookups;
        this.attachments = attachments;
        this.docLogs = docLogs;
        this.afterSalesLogs = afterSalesLogs;
        this.payables = payables;
        this.numbers = numbers;
        this.currentUser = currentUser;
        this.logService = logService;
        this.discrepancyService = discrepancyService;
    }

    // ---------------------------------------------------------------- 生成（调用方事务内）

    /** 售后退货的一行退回供应商 */
    public record AfterSalesLine(Long supplierId, Long poId, ReturnReceiptItemDO receiptItem, SalesReturnDispositionDO disposition) {
    }

    /** 退货入库单去向选退回供应商：同一次处理里同一供应商、同一采购订单的行合并成一张；调用方已锁退货入库单 */
    public List<PurchaseReturnDO> createForAfterSales(ReturnReceiptDO rr, List<AfterSalesLine> lines) {
        Map<String, List<AfterSalesLine>> groups = new LinkedHashMap<>();
        for (AfterSalesLine l : lines) {
            groups.computeIfAbsent(l.supplierId() + ":" + l.poId(), k -> new ArrayList<>()).add(l);
        }
        List<PurchaseReturnDO> out = new ArrayList<>();
        for (List<AfterSalesLine> g : groups.values()) {
            AfterSalesLine first = g.get(0);
            PurchaseOrderDO po = first.poId() == null ? null : support.po(first.poId(), false);
            PurchaseReturnDO p = newReturn(rr.getTenantId(), first.supplierId(), PurchaseReturnConstants.SRC_AFTER_SALES, po);
            p.setReturnReceiptId(rr.getId());
            p.setAfterSalesId(rr.getAfterSalesId());
            p.setSoId(rr.getSoId());
            p.setOwnerId(me());
            mapper.insert(p);
            Map<Long, Long> poItems = po == null ? Map.of() : support.poItems(po.getId()).stream()
                    .filter(i -> i.getSoItemId() != null)
                    .collect(Collectors.toMap(com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO::getSoItemId,
                            com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO::getId, (a, b) -> a));
            for (AfterSalesLine l : g) {
                PurchaseReturnItemDO i = new PurchaseReturnItemDO();
                i.setTenantId(p.getTenantId());
                i.setPurchaseReturnId(p.getId());
                i.setReceiptItemId(l.receiptItem().getId());
                i.setDispositionId(l.disposition().getId());
                i.setSoItemId(l.receiptItem().getSoItemId());
                i.setPoItemId(poItems.get(l.receiptItem().getSoItemId()));
                i.setModel(l.receiptItem().getModel());
                i.setBrand(l.receiptItem().getBrand());
                i.setQuantity(l.disposition().getQuantity());
                itemMapper.insert(i);
                l.disposition().setPurchaseReturnId(p.getId());
                dispositionMapper.updateById(l.disposition());
            }
            docLogs.add(DocumentType.PR, p.getId(), "生成", "由退货入库单 " + rr.getReceiptNo() + " 退回供应商生成："
                    + g.stream().map(l -> l.receiptItem().getModel() + " × " + l.disposition().getQuantity()).collect(Collectors.joining("、")));
            out.add(p);
        }
        return out;
    }

    /** 到货差异选退货换货、退货不补、退回供应商：生成一张采购退货单；调用方已锁采购订单与差异 */
    public PurchaseReturnDO createForDiscrepancy(ReceivingDiscrepancyDO d, PurchaseOrderDO po) {
        int source = switch (d.getResolution()) {
            case WarehouseConstants.RES_RETURN_EXCHANGE -> PurchaseReturnConstants.SRC_DEFECT_EXCHANGE;
            case WarehouseConstants.RES_RETURN_NO_RESEND -> PurchaseReturnConstants.SRC_DEFECT_RETURN;
            default -> PurchaseReturnConstants.SRC_OVER_RETURN;
        };
        PurchaseReturnDO p = newReturn(d.getTenantId(), po.getSupplierId() == null ? 0L : po.getSupplierId(), source, po);
        p.setPurchaseReceiptId(d.getReceiptId());
        p.setOwnerId(d.getPurchaserId() == null || d.getPurchaserId() == 0 ? me() : d.getPurchaserId());
        mapper.insert(p);
        PurchaseReturnItemDO i = new PurchaseReturnItemDO();
        i.setTenantId(p.getTenantId());
        i.setPurchaseReturnId(p.getId());
        i.setDiscrepancyId(d.getId());
        i.setPoItemId(d.getPoItemId());
        i.setModel(d.getModel());
        i.setBrand(d.getBrand());
        i.setQuantity(d.getQuantity());
        itemMapper.insert(i);
        docLogs.add(DocumentType.PR, p.getId(), "生成", "由到货差异「" + WarehouseConstants.RESOLUTION_NAMES.get(d.getResolution()) + "」生成："
                + d.getModel() + " × " + d.getQuantity());
        return p;
    }

    /** 到货差异重新打开：对应的采购退货单必须还在待寄出，随之作废；已作废的跳过 */
    public void cancelForDiscrepancy(ReceivingDiscrepancyDO d) {
        if (d.getPurchaseReturnId() == null) {
            return;
        }
        mapper.lockById(d.getPurchaseReturnId());
        PurchaseReturnDO p = mapper.selectById(d.getPurchaseReturnId());
        if (p == null || p.getDeletedAt() != null || p.getStatus() == PurchaseReturnConstants.VOID) {
            return;
        }
        if (p.getStatus() != PurchaseReturnConstants.PENDING) {
            throw new BizException("采购退货单 " + p.getReturnNo() + " 已" + PurchaseReturnConstants.STATUS_NAMES.get(p.getStatus()).substring(1)
                    + "，不能重新打开");
        }
        p.setStatus(PurchaseReturnConstants.VOID);
        p.setVoidReason("到货差异重新打开");
        mapper.updateById(p);
        docLogs.add(DocumentType.PR, p.getId(), "作废", "到货差异重新打开，随之作废");
    }

    private PurchaseReturnDO newReturn(Integer tenantId, Long supplierId, int source, PurchaseOrderDO po) {
        PurchaseReturnDO p = new PurchaseReturnDO();
        p.setTenantId(tenantId);
        p.setReturnNo(numbers.next(DocumentType.PR));
        p.setSupplierId(supplierId == null ? 0L : supplierId);
        p.setSourceType(source);
        p.setPoId(po == null ? null : po.getId());
        p.setStatus(PurchaseReturnConstants.PENDING);
        p.setCarrier("");
        p.setTrackingNo("");
        p.setFreightCny(BigDecimal.ZERO.setScale(2));
        p.setCurrencyCode(po == null || !StringUtils.hasText(po.getCurrencyCode()) ? "CNY" : po.getCurrencyCode());
        p.setExchangeRate(po == null || po.getExchangeRate() == null ? BigDecimal.ONE.setScale(6) : po.getExchangeRate());
        p.setRefundAmount(BigDecimal.ZERO.setScale(2));
        p.setRefundCny(BigDecimal.ZERO.setScale(2));
        p.setSettleNote("");
        p.setRemark("");
        p.setVoidReason("");
        p.setMigrated(0);
        return p;
    }

    // ---------------------------------------------------------------- 状态操作

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturnVO updateQuantities(Long id, PurchaseReturnQtyRequest req) {
        PurchaseReturnDO p = visible(id);
        ReturnReceiptDO rr = lockReceipt(p);
        p = lock(id);
        requireStatus(p, PurchaseReturnConstants.PENDING, "寄出后不能修改数量");
        if (p.getSourceType() != PurchaseReturnConstants.SRC_AFTER_SALES) {
            throw new BizException("到货差异生成的采购退货单数量随差异，不能修改");
        }
        Map<Long, PurchaseReturnItemDO> items = itemsOf(List.of(id)).getOrDefault(id, List.of()).stream()
                .collect(Collectors.toMap(PurchaseReturnItemDO::getId, x -> x));
        List<String> text = new ArrayList<>();
        int total = 0;
        Map<Long, Integer> wanted = new HashMap<>();
        for (PurchaseReturnQtyRequest.Line l : req.getItems()) {
            if (!items.containsKey(l.getItemId()) || wanted.put(l.getItemId(), l.getQuantity()) != null) {
                throw new BizException("采购退货单行不正确");
            }
        }
        for (PurchaseReturnItemDO i : items.values()) {
            int q = wanted.getOrDefault(i.getId(), i.getQuantity());
            if (q < 0 || q > i.getQuantity()) {
                throw new BizException(i.getModel() + " 只能减少，不能多于 " + i.getQuantity() + " 个");
            }
            total += q;
        }
        if (total == 0) {
            throw new BizException("数量都为 0 时请作废采购退货单");
        }
        for (PurchaseReturnItemDO i : items.values()) {
            int q = wanted.getOrDefault(i.getId(), i.getQuantity());
            int less = i.getQuantity() - q;
            if (less == 0) {
                continue;
            }
            giveBack(rr, i, less);
            text.add(i.getModel() + " " + i.getQuantity() + " → " + q);
            i.setQuantity(q);
            if (q == 0) {
                i.setDeletedAt(java.time.LocalDateTime.now());
            }
            itemMapper.updateById(i);
        }
        if (!text.isEmpty()) {
            mapper.updateById(p);
            docLogs.add(DocumentType.PR, id, "修改数量", String.join("；", text) + "，减少的数量回到退货入库单待处理");
        }
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturnVO ship(Long id, ShipPurchaseReturnRequest req) {
        visible(id);
        PurchaseReturnDO p = lock(id);
        requireStatus(p, PurchaseReturnConstants.PENDING, "采购退货单" + PurchaseReturnConstants.STATUS_NAMES.get(p.getStatus()));
        if (req.getShipDate().isAfter(LocalDate.now())) {
            throw new BizException("寄出日期不能晚于今天");
        }
        BigDecimal freight = req.getFreightCny() == null ? BigDecimal.ZERO.setScale(2) : PurchaseCalc.money(req.getFreightCny());
        p.setShipDate(req.getShipDate());
        p.setCarrier(req.getCarrier().trim());
        p.setTrackingNo(req.getTrackingNo().trim());
        p.setFreightCny(freight);
        p.setPayerId(freight.signum() > 0 && !Boolean.TRUE.equals(req.getCompanyPaid())
                ? (req.getPayerId() != null ? req.getPayerId() : me()) : null);
        p.setStatus(PurchaseReturnConstants.SHIPPED);
        mapper.updateById(p);
        if (p.getPayerId() != null) {
            attachments.attach(AttachmentService.RETURN_PROOF, p.getId(), req.getProofIds());
        }
        syncCosts(p);
        String payer = p.getPayerId() == null ? (freight.signum() > 0 ? "，公司支付" : "")
                : "，垫付人 " + lookups.userNames(List.of(p.getPayerId())).getOrDefault(p.getPayerId(), "");
        String text = p.getCarrier() + " " + p.getTrackingNo() + (freight.signum() > 0 ? "，运费 ¥" + freight : "") + payer;
        docLogs.add(DocumentType.PR, id, "寄出", text);
        afterSales(p, "采购退货单 " + p.getReturnNo() + " 已寄出：" + text);
        logService.recordOperateLog(PurchaseReturnConstants.MENU, "寄出采购退货", null, Map.of("returnNo", p.getReturnNo(), "content", text));
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturnVO complete(Long id, CompletePurchaseReturnRequest req) {
        visible(id);
        PurchaseReturnDO p = lock(id);
        requireStatus(p, PurchaseReturnConstants.SHIPPED, p.getStatus() == PurchaseReturnConstants.PENDING ? "采购退货单还没寄出"
                : "采购退货单" + PurchaseReturnConstants.STATUS_NAMES.get(p.getStatus()));
        if (req.getSignedDate().isAfter(LocalDate.now())) {
            throw new BizException("签收日期不能晚于今天");
        }
        if (p.getShipDate() != null && req.getSignedDate().isBefore(p.getShipDate())) {
            throw new BizException("签收日期不能早于寄出日期");
        }
        boolean afterSales = p.getSourceType() == PurchaseReturnConstants.SRC_AFTER_SALES;
        if (afterSales && req.getRefundAmount() == null) {
            throw new BizException("请填写供应商退款或抵扣金额（没有就填 0）");
        }
        BigDecimal amount = req.getRefundAmount() == null ? BigDecimal.ZERO : req.getRefundAmount();
        if (amount.signum() < 0) {
            throw new BizException("退款或抵扣金额不能为负数");
        }
        PurchaseOrderDO po = p.getPoId() == null ? null : support.lockPo(p.getPoId(), false);
        p.setSignedDate(req.getSignedDate());
        p.setRefundAmount(PurchaseCalc.money(amount));
        p.setRefundCny(p.getRefundAmount().multiply(p.getExchangeRate()).setScale(2, RoundingMode.HALF_UP));
        p.setSettleNote(req.getSettleNote() == null ? "" : req.getSettleNote().trim());
        p.setStatus(PurchaseReturnConstants.DONE);
        mapper.updateById(p);
        syncCosts(p);
        if (po != null && afterSales) {
            payables.syncPurchaseOrder(po.getId());
        }
        String text = "供应商 " + p.getSignedDate() + " 签收" + (afterSales ? "，退款或抵扣 " + p.getCurrencyCode() + " " + p.getRefundAmount()
                + ("CNY".equals(p.getCurrencyCode()) ? "" : "（¥" + p.getRefundCny() + "）") : "")
                + (p.getSettleNote().isEmpty() ? "" : "；" + p.getSettleNote());
        docLogs.add(DocumentType.PR, id, "完成", text);
        afterSales(p, "采购退货单 " + p.getReturnNo() + " 已完成：" + text);
        logService.recordOperateLog(PurchaseReturnConstants.MENU, "完成采购退货", null, Map.of("returnNo", p.getReturnNo(), "content", text));
        return detail(id);
    }

    /** 已完成的金额需要更正：回到已寄出（运费不变），撤回成本冲减与应付冲减 */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturnVO reopen(Long id, String reason) {
        String why = trim(reason);
        if (why.isEmpty()) {
            throw new BizException("请填写重新打开的原因");
        }
        visible(id);
        PurchaseReturnDO p = lock(id);
        requireStatus(p, PurchaseReturnConstants.DONE, "只有已完成的采购退货单才能重新打开");
        if (Objects.equals(p.getMigrated(), 1)) {
            throw new BizException("历史迁移的采购退货单不能重新打开");
        }
        PurchaseOrderDO po = p.getPoId() == null ? null : support.lockPo(p.getPoId(), false);
        p.setStatus(PurchaseReturnConstants.SHIPPED);
        p.setSignedDate(null);
        p.setRefundAmount(BigDecimal.ZERO.setScale(2));
        p.setRefundCny(BigDecimal.ZERO.setScale(2));
        mapper.updateById(p);
        syncCosts(p);
        if (po != null && p.getSourceType() == PurchaseReturnConstants.SRC_AFTER_SALES) {
            payables.syncPurchaseOrder(po.getId());
        }
        docLogs.add(DocumentType.PR, id, "重新打开", why);
        afterSales(p, "采购退货单 " + p.getReturnNo() + " 重新打开：" + why);
        logService.recordOperateLog(PurchaseReturnConstants.MENU, "重新打开采购退货", null, Map.of("returnNo", p.getReturnNo(), "reason", why));
        return detail(id);
    }

    /** 作废（待寄出）：售后来源的数量回到退货入库单待处理；到货差异来源的差异重新打开 */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturnVO voidReturn(Long id, String reason) {
        String why = trim(reason);
        if (why.isEmpty()) {
            throw new BizException("请填写作废原因");
        }
        PurchaseReturnDO p = visible(id);
        requireStatus(p, PurchaseReturnConstants.PENDING, "已寄出的采购退货单不能作废");
        String voidReason = why.length() > 200 ? why.substring(0, 200) : why;
        if (p.getSourceType() != PurchaseReturnConstants.SRC_AFTER_SALES) {
            // 到货差异来源按「重新打开差异」处理（加锁顺序同差异页：采购订单 → 差异 → 采购退货单），差异重新打开时随之作废本单
            for (PurchaseReturnItemDO i : itemsOf(List.of(id)).getOrDefault(id, List.of())) {
                if (i.getDiscrepancyId() != null) {
                    discrepancyService.reopen(i.getDiscrepancyId());
                }
            }
            p = lock(id);
            p.setVoidReason(voidReason);
            mapper.updateById(p);
        } else {
            ReturnReceiptDO rr = lockReceipt(p);
            p = lock(id);
            requireStatus(p, PurchaseReturnConstants.PENDING, "已寄出的采购退货单不能作废");
            p.setStatus(PurchaseReturnConstants.VOID);
            p.setVoidReason(voidReason);
            mapper.updateById(p);
            for (PurchaseReturnItemDO i : itemsOf(List.of(id)).getOrDefault(id, List.of())) {
                giveBack(rr, i, i.getQuantity());
            }
        }
        docLogs.add(DocumentType.PR, id, "作废", p.getVoidReason());
        afterSales(p, "作废采购退货单 " + p.getReturnNo() + "：" + p.getVoidReason());
        logService.recordOperateLog(PurchaseReturnConstants.MENU, "作废采购退货", null, Map.of("returnNo", p.getReturnNo(), "reason", p.getVoidReason()));
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturnVO remark(Long id, String remark) {
        visible(id);
        PurchaseReturnDO p = lock(id);
        requireNotVoid(p);
        String text = trim(remark);
        p.setRemark(text);
        mapper.updateById(p);
        docLogs.add(DocumentType.PR, id, "修改备注", text.isEmpty() ? "清空备注" : text);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturnVO attach(Long id, List<Long> ids) {
        PurchaseReturnDO p = visible(id);
        requireNotVoid(p);
        attachments.attach(AttachmentService.PURCHASE_RETURN, id, ids);
        docLogs.add(DocumentType.PR, id, "上传附件", ids.size() + " 个");
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturnVO removeAttachment(Long id, Long attachmentId) {
        PurchaseReturnDO p = visible(id);
        requireNotVoid(p);
        attachments.remove(AttachmentService.PURCHASE_RETURN, id, attachmentId);
        docLogs.add(DocumentType.PR, id, "删除附件", "");
        return detail(id);
    }

    /** 售后来源的行减少数量：退货入库单行未处理数量增加，去向行同步减少；退货入库单回到待处理 */
    private void giveBack(ReturnReceiptDO rr, PurchaseReturnItemDO i, int qty) {
        if (rr == null || i.getReceiptItemId() == null || qty <= 0) {
            return;
        }
        ReturnReceiptItemDO ri = receiptItemMapper.selectById(i.getReceiptItemId());
        ri.setDisposedQty(Math.max(0, ri.getDisposedQty() - qty));
        receiptItemMapper.updateById(ri);
        if (i.getDispositionId() != null) {
            SalesReturnDispositionDO d = dispositionMapper.selectById(i.getDispositionId());
            d.setQuantity(d.getQuantity() - qty);
            if (d.getQuantity() <= 0) {
                d.setDeletedAt(java.time.LocalDateTime.now());
            }
            dispositionMapper.updateById(d);
        }
        if (rr.getStatus() == AfterSalesConstants.RR_DONE) {
            rr.setStatus(AfterSalesConstants.RR_RECEIVED);
            receiptMapper.updateById(rr);
            docLogs.add(DocumentType.RR, rr.getId(), "回到待处理", "采购退货单 " + mapper.selectById(i.getPurchaseReturnId()).getReturnNo()
                    + " 减少或作废，" + ri.getModel() + " × " + qty + " 回到待处理");
        }
    }

    /**
     * 把运费（寄出后）与退款人民币（完成后）写回来源：售后来源按数量分摊到去向行（HALF_UP 两位，尾差放最后一行），
     * 到货差异来源写差异的退货运费。
     */
    private void syncCosts(PurchaseReturnDO p) {
        List<PurchaseReturnItemDO> items = itemsOf(List.of(p.getId())).getOrDefault(p.getId(), List.of());
        boolean shipped = p.getStatus() == PurchaseReturnConstants.SHIPPED || p.getStatus() == PurchaseReturnConstants.DONE;
        BigDecimal freight = shipped ? p.getFreightCny() : BigDecimal.ZERO;
        BigDecimal refund = p.getStatus() == PurchaseReturnConstants.DONE ? p.getRefundCny() : BigDecimal.ZERO;
        if (p.getSourceType() == PurchaseReturnConstants.SRC_AFTER_SALES) {
            List<BigDecimal> f = split(freight, items);
            List<BigDecimal> r = split(refund, items);
            for (int k = 0; k < items.size(); k++) {
                PurchaseReturnItemDO i = items.get(k);
                if (i.getDispositionId() == null) {
                    continue;
                }
                SalesReturnDispositionDO d = dispositionMapper.selectById(i.getDispositionId());
                d.setFreightCny(f.get(k));
                d.setCostCny(r.get(k));
                dispositionMapper.updateById(d);
            }
            return;
        }
        for (PurchaseReturnItemDO i : items) {
            if (i.getDiscrepancyId() != null) {
                ReceivingDiscrepancyDO d = discrepancyMapper.selectById(i.getDiscrepancyId());
                d.setReturnFreight(shipped ? freight : null);
                discrepancyMapper.updateById(d);
            }
        }
    }

    /** 按数量分摊，HALF_UP 两位，尾差放最后一行 */
    static List<BigDecimal> split(BigDecimal total, List<PurchaseReturnItemDO> items) {
        List<BigDecimal> out = new ArrayList<>(items.size());
        int qty = items.stream().mapToInt(PurchaseReturnItemDO::getQuantity).sum();
        BigDecimal left = total.setScale(2, RoundingMode.HALF_UP);
        for (int k = 0; k < items.size(); k++) {
            if (k == items.size() - 1 || qty == 0) {
                out.add(k == items.size() - 1 ? left : BigDecimal.ZERO.setScale(2));
                continue;
            }
            BigDecimal part = total.multiply(BigDecimal.valueOf(items.get(k).getQuantity()))
                    .divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP);
            out.add(part);
            left = left.subtract(part);
        }
        return out;
    }

    private void afterSales(PurchaseReturnDO p, String text) {
        if (p.getAfterSalesId() != null) {
            afterSalesLogs.add(p.getAfterSalesId(), AfterSalesConstants.LOG_RETURN, text, me());
        }
    }

    // ---------------------------------------------------------------- 查看

    public PageResult<PurchaseReturnVO> page(PurchaseReturnQuery q) {
        LambdaQueryWrapper<PurchaseReturnDO> w = scoped();
        if (q.getStatus() != null) {
            w.eq(PurchaseReturnDO::getStatus, q.getStatus());
        }
        if (q.getSupplierId() != null) {
            w.eq(PurchaseReturnDO::getSupplierId, q.getSupplierId());
        }
        if (q.getSourceType() != null) {
            w.eq(PurchaseReturnDO::getSourceType, q.getSourceType());
        }
        if (q.getOwnerId() != null) {
            w.eq(PurchaseReturnDO::getOwnerId, q.getOwnerId());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            Set<Long> poIds = support.poIdsByKeyword(kw);
            Set<Long> byModel = itemMapper.selectList(new LambdaQueryWrapper<PurchaseReturnItemDO>()
                            .select(PurchaseReturnItemDO::getPurchaseReturnId)
                            .eq(PurchaseReturnItemDO::getTenantId, PiStore.tenantId())
                            .like(PurchaseReturnItemDO::getModel, kw)
                            .isNull(PurchaseReturnItemDO::getDeletedAt).last("LIMIT 500"))
                    .stream().map(PurchaseReturnItemDO::getPurchaseReturnId).collect(Collectors.toSet());
            Set<Long> rrIds = receiptMapper.selectList(new LambdaQueryWrapper<ReturnReceiptDO>().select(ReturnReceiptDO::getId)
                            .eq(ReturnReceiptDO::getTenantId, PiStore.tenantId()).like(ReturnReceiptDO::getReceiptNo, kw).last("LIMIT 200"))
                    .stream().map(ReturnReceiptDO::getId).collect(Collectors.toSet());
            Set<Long> grIds = purchaseReceiptMapper.selectList(new LambdaQueryWrapper<PurchaseReceiptDO>().select(PurchaseReceiptDO::getId)
                            .eq(PurchaseReceiptDO::getTenantId, PiStore.tenantId()).like(PurchaseReceiptDO::getGrNo, kw).last("LIMIT 200"))
                    .stream().map(PurchaseReceiptDO::getId).collect(Collectors.toSet());
            w.and(x -> {
                x.like(PurchaseReturnDO::getReturnNo, kw).or().like(PurchaseReturnDO::getTrackingNo, kw);
                if (!poIds.isEmpty()) {
                    x.or().in(PurchaseReturnDO::getPoId, poIds);
                }
                if (!byModel.isEmpty()) {
                    x.or().in(PurchaseReturnDO::getId, byModel);
                }
                if (!rrIds.isEmpty()) {
                    x.or().in(PurchaseReturnDO::getReturnReceiptId, rrIds);
                }
                if (!grIds.isEmpty()) {
                    x.or().in(PurchaseReturnDO::getPurchaseReceiptId, grIds);
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

    /** 页签数量（待寄出、已寄出）；菜单显示待寄出数量 */
    public Map<String, Long> counts() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("pending", mapper.selectCount(scoped().eq(PurchaseReturnDO::getStatus, PurchaseReturnConstants.PENDING)));
        m.put("shipped", mapper.selectCount(scoped().eq(PurchaseReturnDO::getStatus, PurchaseReturnConstants.SHIPPED)));
        return m;
    }

    public PurchaseReturnVO detail(Long id) {
        return toVos(List.of(visible(id)), true).get(0);
    }

    /** 采购订单详情：它的采购退货单 */
    public List<PurchaseReturnVO> ofPurchaseOrder(Long poId) {
        return toVos(mapper.selectList(new LambdaQueryWrapper<PurchaseReturnDO>()
                .eq(PurchaseReturnDO::getTenantId, PiStore.tenantId())
                .eq(PurchaseReturnDO::getPoId, poId)
                .isNull(PurchaseReturnDO::getDeletedAt)
                .orderByDesc(PurchaseReturnDO::getId)), false);
    }

    /** 售后单详情与结案判断：售后来源的采购退货单（不按数据范围，调用方已校验售后单可见） */
    public List<PurchaseReturnVO> ofAfterSales(Long afterSalesId) {
        return toVos(mapper.selectList(new LambdaQueryWrapper<PurchaseReturnDO>()
                .eq(PurchaseReturnDO::getTenantId, PiStore.tenantId())
                .eq(PurchaseReturnDO::getAfterSalesId, afterSalesId)
                .isNull(PurchaseReturnDO::getDeletedAt)
                .orderByDesc(PurchaseReturnDO::getId)), false);
    }

    /** 退货入库单详情：由它生成的采购退货单 */
    public List<PurchaseReturnVO> ofReturnReceipt(Long receiptId) {
        return toVos(mapper.selectList(new LambdaQueryWrapper<PurchaseReturnDO>()
                .eq(PurchaseReturnDO::getTenantId, PiStore.tenantId())
                .eq(PurchaseReturnDO::getReturnReceiptId, receiptId)
                .isNull(PurchaseReturnDO::getDeletedAt)
                .orderByDesc(PurchaseReturnDO::getId)), false);
    }

    /** 采购退货单号（到货差异页、退货入库单去向显示用） */
    public Map<Long, String> numbers(Collection<Long> ids) {
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, String> m = new HashMap<>();
        if (!keys.isEmpty()) {
            mapper.selectBatchIds(keys).forEach(p -> m.put(p.getId(), p.getReturnNo()));
        }
        return m;
    }

    private List<PurchaseReturnVO> toVos(List<PurchaseReturnDO> rows, boolean full) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(PurchaseReturnDO::getId).toList();
        Map<Long, List<PurchaseReturnItemDO>> items = itemsOf(ids);
        Map<Long, PurchaseOrderDO> pos = support.pos(rows.stream().map(PurchaseReturnDO::getPoId).filter(Objects::nonNull).toList());
        Map<Long, String> shopNames = support.counterparties(pos.values());
        List<Long> supplierIds = rows.stream().map(PurchaseReturnDO::getSupplierId).filter(x -> x != null && x > 0).distinct().toList();
        Map<Long, String> suppliers = supplierIds.isEmpty() ? Map.of() : supplierMapper.selectBatchIds(supplierIds).stream()
                .collect(Collectors.toMap(SupplierDO::getId, SupplierDO::getName));
        Map<Long, String> rrNos = byIds(rows.stream().map(PurchaseReturnDO::getReturnReceiptId).toList(), receiptMapper::selectBatchIds,
                ReturnReceiptDO::getId, ReturnReceiptDO::getReceiptNo);
        Map<Long, String> grNos = byIds(rows.stream().map(PurchaseReturnDO::getPurchaseReceiptId).toList(), purchaseReceiptMapper::selectBatchIds,
                PurchaseReceiptDO::getId, PurchaseReceiptDO::getGrNo);
        Map<Long, String> asNos = byIds(rows.stream().map(PurchaseReturnDO::getAfterSalesId).toList(), afterSalesMapper::selectBatchIds,
                AfterSalesDO::getId, AfterSalesDO::getAsNo);
        List<Long> soIds = rows.stream().map(PurchaseReturnDO::getSoId).filter(Objects::nonNull).distinct().toList();
        Map<Long, SalesOrderDO> orders = soIds.isEmpty() ? Map.of() : orderMapper.selectBatchIds(soIds).stream()
                .collect(Collectors.toMap(SalesOrderDO::getId, o -> o));
        Map<Long, CustomerDO> customers = lookups.customers(orders.values().stream().map(SalesOrderDO::getCustomerId).distinct().toList());
        List<Long> soItemIds = items.values().stream().flatMap(List::stream).map(PurchaseReturnItemDO::getSoItemId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, String> soCategories = soItemIds.isEmpty() ? Map.of() : orderItemMapper.selectBatchIds(soItemIds).stream()
                .collect(Collectors.toMap(SalesOrderItemDO::getId, i -> i.getCategory() == null ? "" : i.getCategory()));
        List<Long> diffIds = items.values().stream().flatMap(List::stream).map(PurchaseReturnItemDO::getDiscrepancyId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, ReceivingDiscrepancyDO> diffs = diffIds.isEmpty() ? Map.of() : discrepancyMapper.selectBatchIds(diffIds).stream()
                .collect(Collectors.toMap(ReceivingDiscrepancyDO::getId, d -> d));
        Map<Long, String> users = lookups.userNames(rows.stream().flatMap(p -> Stream.of(p.getOwnerId(), p.getPayerId()))
                .filter(Objects::nonNull).distinct().toList());
        List<PurchaseReturnVO> out = new ArrayList<>(rows.size());
        for (PurchaseReturnDO p : rows) {
            PurchaseReturnVO v = new PurchaseReturnVO();
            v.setId(p.getId());
            v.setReturnNo(p.getReturnNo());
            v.setSupplierId(p.getSupplierId());
            v.setSupplierName(p.getSupplierId() != null && p.getSupplierId() > 0 ? suppliers.get(p.getSupplierId())
                    : p.getPoId() == null ? null : shopNames.get(p.getPoId()));
            v.setSourceType(p.getSourceType());
            v.setSourceTypeName(PurchaseReturnConstants.SOURCE_NAMES.get(p.getSourceType()));
            boolean afterSales = p.getSourceType() == PurchaseReturnConstants.SRC_AFTER_SALES;
            v.setSourceId(afterSales ? p.getReturnReceiptId() : p.getPurchaseReceiptId());
            v.setSourceNo(afterSales ? rrNos.get(p.getReturnReceiptId()) : grNos.get(p.getPurchaseReceiptId()));
            v.setPoId(p.getPoId());
            PurchaseOrderDO po = p.getPoId() == null ? null : pos.get(p.getPoId());
            v.setPoNo(po == null ? null : po.getPoNo());
            v.setAfterSalesId(p.getAfterSalesId());
            v.setAsNo(p.getAfterSalesId() == null ? null : asNos.get(p.getAfterSalesId()));
            v.setSoId(p.getSoId());
            SalesOrderDO o = p.getSoId() == null ? null : orders.get(p.getSoId());
            v.setSoNo(o == null ? null : o.getSoNo());
            CustomerDO c = o == null ? null : customers.get(o.getCustomerId());
            v.setCustomerName(c == null ? null : InquiryLookups.customerName(c));
            v.setCustomerCountry(c == null ? null : c.getCountry());
            v.setOwnerId(p.getOwnerId());
            v.setOwnerName(users.get(p.getOwnerId()));
            v.setStatus(p.getStatus());
            v.setStatusName(PurchaseReturnConstants.STATUS_NAMES.get(p.getStatus()));
            v.setShipDate(p.getShipDate());
            v.setCarrier(p.getCarrier());
            v.setTrackingNo(p.getTrackingNo());
            v.setFreightCny(p.getFreightCny());
            v.setPayerId(p.getPayerId());
            v.setPayerName(p.getPayerId() == null ? null : users.get(p.getPayerId()));
            v.setSignedDate(p.getSignedDate());
            v.setCurrencyCode(p.getCurrencyCode());
            v.setExchangeRate(p.getExchangeRate());
            v.setRefundAmount(p.getRefundAmount());
            v.setRefundCny(p.getRefundCny());
            v.setSettleNote(p.getSettleNote());
            v.setRemark(p.getRemark());
            v.setVoidReason(p.getVoidReason());
            v.setMigrated(Objects.equals(p.getMigrated(), 1));
            List<PurchaseReturnItemDO> mine = items.getOrDefault(p.getId(), List.of());
            v.setItemSummary(mine.stream().map(i -> i.getModel() + " × " + i.getQuantity()).collect(Collectors.joining("、")));
            v.setTotalQty(mine.stream().mapToInt(PurchaseReturnItemDO::getQuantity).sum());
            List<PurchaseReturnVO.Item> lines = mine.stream().map(i -> {
                PurchaseReturnVO.Item x = new PurchaseReturnVO.Item();
                x.setId(i.getId());
                x.setModel(i.getModel());
                x.setBrand(i.getBrand());
                ReceivingDiscrepancyDO d = i.getDiscrepancyId() == null ? null : diffs.get(i.getDiscrepancyId());
                x.setCategory(d != null ? d.getCategory() : i.getSoItemId() == null ? "" : soCategories.getOrDefault(i.getSoItemId(), ""));
                x.setQuantity(i.getQuantity());
                x.setSoItemId(i.getSoItemId());
                x.setPoItemId(i.getPoItemId());
                x.setReceiptItemId(i.getReceiptItemId());
                x.setDiscrepancyId(i.getDiscrepancyId());
                x.setDiscrepancyName(d == null ? null : WarehouseConstants.DIFF_TYPE_NAMES.get(d.getType()) + " " + d.getQuantity());
                return x;
            }).toList();
            v.setItems(lines);
            v.setBrands(lines.stream().map(PurchaseReturnVO.Item::getBrand).filter(StringUtils::hasText).distinct().toList());
            v.setCategories(lines.stream().map(PurchaseReturnVO.Item::getCategory).filter(StringUtils::hasText).distinct().toList());
            if (full) {
                v.setAttachments(attachments.listOf(AttachmentService.PURCHASE_RETURN, p.getId()));
                v.setFreightProofs(attachments.listOf(AttachmentService.RETURN_PROOF, p.getId()));
                v.setLogs(docLogs.list(DocumentType.PR, p.getId()));
            }
            v.setCreateTime(p.getCreateTime());
            v.setCreateBy(p.getCreateBy());
            v.setUpdateTime(p.getUpdateTime());
            v.setUpdateBy(p.getUpdateBy());
            out.add(v);
        }
        return out;
    }

    private static <T> Map<Long, String> byIds(List<Long> ids, java.util.function.Function<List<Long>, List<T>> loader,
                                               java.util.function.Function<T, Long> idOf, java.util.function.Function<T, String> noOf) {
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> m = new HashMap<>();
        loader.apply(keys).forEach(x -> m.put(idOf.apply(x), noOf.apply(x)));
        return m;
    }

    private Map<Long, List<PurchaseReturnItemDO>> itemsOf(Collection<Long> ids) {
        return itemMapper.selectList(new LambdaQueryWrapper<PurchaseReturnItemDO>()
                        .in(PurchaseReturnItemDO::getPurchaseReturnId, ids)
                        .isNull(PurchaseReturnItemDO::getDeletedAt)
                        .orderByAsc(PurchaseReturnItemDO::getId))
                .stream().collect(Collectors.groupingBy(PurchaseReturnItemDO::getPurchaseReturnId, LinkedHashMap::new, Collectors.toList()));
    }

    private LambdaQueryWrapper<PurchaseReturnDO> scoped() {
        LambdaQueryWrapper<PurchaseReturnDO> w = new LambdaQueryWrapper<PurchaseReturnDO>()
                .eq(PurchaseReturnDO::getTenantId, PiStore.tenantId())
                .isNull(PurchaseReturnDO::getDeletedAt);
        support.scope().apply(w, PurchaseReturnDO::getOwnerId);
        return w;
    }

    private PurchaseReturnDO visible(Long id) {
        PurchaseReturnDO p = id == null ? null : mapper.selectById(id);
        if (p == null || p.getDeletedAt() != null || !Objects.equals(p.getTenantId(), PiStore.tenantId())
                || !support.scope().canSee(p.getOwnerId())) {
            throw new BizException("采购退货单不存在");
        }
        return p;
    }

    private PurchaseReturnDO lock(Long id) {
        mapper.lockById(id);
        return mapper.selectById(id);
    }

    /** 售后来源：先锁退货入库单（加锁顺序：退货入库单 → 采购退货单） */
    private ReturnReceiptDO lockReceipt(PurchaseReturnDO p) {
        if (p.getReturnReceiptId() == null) {
            return null;
        }
        receiptMapper.lockById(p.getReturnReceiptId());
        return receiptMapper.selectById(p.getReturnReceiptId());
    }

    private static void requireStatus(PurchaseReturnDO p, int status, String message) {
        if (p.getStatus() != status) {
            throw new BizException(message);
        }
    }

    private static void requireNotVoid(PurchaseReturnDO p) {
        if (p.getStatus() == PurchaseReturnConstants.VOID) {
            throw new BizException("采购退货单已作废，只能查看");
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

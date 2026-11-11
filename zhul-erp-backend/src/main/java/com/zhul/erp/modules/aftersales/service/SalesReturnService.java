package com.zhul.erp.modules.aftersales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.aftersales.constants.AfterSalesConstants;
import com.zhul.erp.modules.aftersales.dto.ConfirmReturnRequest;
import com.zhul.erp.modules.aftersales.dto.ReturnQuery;
import com.zhul.erp.modules.aftersales.dto.ReturnVO;
import com.zhul.erp.modules.aftersales.dto.SaveReturnRequest;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.aftersales.entity.ReturnReceiptDO;
import com.zhul.erp.modules.aftersales.entity.ReturnReceiptItemDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnItemDO;
import com.zhul.erp.modules.aftersales.repository.AfterSalesMapper;
import com.zhul.erp.modules.aftersales.repository.ReturnReceiptItemMapper;
import com.zhul.erp.modules.aftersales.repository.ReturnReceiptMapper;
import com.zhul.erp.modules.aftersales.repository.SalesReturnItemMapper;
import com.zhul.erp.modules.aftersales.repository.SalesReturnMapper;
import com.zhul.erp.modules.aftersales.support.AfterSalesLogs;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.entity.RefundRequestDO;
import com.zhul.erp.modules.finance.repository.RefundRequestMapper;
import com.zhul.erp.modules.finance.service.RefundRequestService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.logistics.support.LogisticsQty;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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
 * 销售退货单 TH（spec sales/sales-return）：售后单选「仅退货」「退货退款」生成，待确认 → 确认（同一事务生成退货入库单，
 * 退货退款同时生成退款申请）→ 待入库 → 退货入库单验收后已入库。作废时同一事务作废待入库的退货入库单、撤回还没执行的退款申请。
 * 加锁顺序：售后单 → 销售订单 → 销售退货单 → 退货入库单。
 */
@Service
@RequiredArgsConstructor
public class SalesReturnService {

    private final SalesReturnMapper returnMapper;
    private final SalesReturnItemMapper itemMapper;
    private final ReturnReceiptMapper receiptMapper;
    private final ReturnReceiptItemMapper receiptItemMapper;
    private final AfterSalesMapper afterSalesMapper;
    private final AfterSalesService afterSalesService;
    private final ReturnReceiptService receiptService;
    private final RefundRequestService refundRequests;
    private final RefundRequestMapper requestMapper;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final LogisticsQty logisticsQty;
    private final DocumentNumberService documentNumberService;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final AttachmentService attachments;
    private final LogService logService;
    private final AfterSalesLogs logs;
    private final DocLogs docLogs;

    // ---------------------------------------------------------------- 生成、修改

    /** 售后单处理方式「仅退货」「退货退款」：生成待确认的销售退货单 */
    @Transactional(rollbackFor = Exception.class)
    public ReturnVO create(SaveReturnRequest req) {
        if (req.getAfterSalesId() == null) {
            throw new BizException("请从售后单发起退货");
        }
        int handling = req.getHandling() == null ? 0 : req.getHandling();
        if (!AfterSalesConstants.HANDLING_NAMES.containsKey(handling)) {
            throw new BizException("请选择处理方式");
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
        if (handling == AfterSalesConstants.HANDLING_REFUND && refundRequests.refundable(o.getId()).getReceived().signum() <= 0) {
            throw new BizException("订单还没有到账，不能退货退款");
        }
        List<SalesReturnItemDO> rows = buildItems(o, req.getItems(), null);
        SalesReturnDO r = new SalesReturnDO();
        r.setTenantId(o.getTenantId());
        r.setReturnNo(documentNumberService.next(DocumentType.TH));
        r.setAfterSalesId(a.getId());
        r.setSoId(o.getId());
        r.setHandling(handling);
        r.setRefundFirst(handling == AfterSalesConstants.HANDLING_REFUND && Boolean.TRUE.equals(req.getRefundFirst()) ? 1 : 0);
        r.setCarrier(trim(req.getCarrier()));
        r.setTrackingNo(trim(req.getTrackingNo()));
        r.setRemark(trim(req.getRemark()));
        r.setStatus(AfterSalesConstants.RT_CONFIRMING);
        r.setReceiveNote("");
        r.setVoidReason("");
        returnMapper.insert(r);
        for (SalesReturnItemDO x : rows) {
            x.setReturnId(r.getId());
            itemMapper.insert(x);
        }
        afterSalesMapper.updateById(a);
        String text = AfterSalesConstants.HANDLING_NAMES.get(handling) + (r.getRefundFirst() == 1 ? "（先退款）" : "") + "：" + summary(rows);
        docLogs.add(DocumentType.TH, r.getId(), "生成", "由售后单 " + a.getAsNo() + " 生成，" + text);
        logs.add(a.getId(), AfterSalesConstants.LOG_RETURN, "生成销售退货单 " + r.getReturnNo() + "，" + text, me());
        logService.recordOperateLog(AfterSalesConstants.MENU, "生成销售退货单", null, Map.of("returnNo", r.getReturnNo(), "asNo", a.getAsNo(), "items", text));
        return detail(r.getId());
    }

    /** 待确认时改型号、数量、快递、备注；确认后到入库前只能补填快递（同步到退货入库单） */
    @Transactional(rollbackFor = Exception.class)
    public ReturnVO update(Long id, SaveReturnRequest req) {
        SalesReturnDO r = lockVisible(id);
        if (r.getStatus() == AfterSalesConstants.RT_CONFIRMING) {
            SalesOrderDO o = lockOrder(r.getSoId());
            List<SalesReturnItemDO> rows = buildItems(o, req.getItems(), r.getId());
            itemsOf(List.of(id)).getOrDefault(id, List.of()).forEach(x -> {
                x.setDeletedAt(LocalDateTime.now());
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
            docLogs.add(DocumentType.TH, id, "修改", summary(rows) + express(r));
            logs.add(r.getAfterSalesId(), AfterSalesConstants.LOG_RETURN, "修改销售退货单 " + r.getReturnNo() + "：" + summary(rows) + express(r), me());
            return detail(id);
        }
        ReturnReceiptDO rr = receiptOf(id);
        if (r.getStatus() != AfterSalesConstants.RT_PENDING || rr == null || rr.getStatus() != AfterSalesConstants.RR_PENDING) {
            throw new BizException("销售退货单" + AfterSalesConstants.RT_STATUS_NAMES.get(r.getStatus()) + "，不能修改");
        }
        r.setCarrier(trim(req.getCarrier()));
        r.setTrackingNo(trim(req.getTrackingNo()));
        returnMapper.updateById(r);
        docLogs.add(DocumentType.TH, id, "补填快递", express(r).replaceFirst("^，", ""));
        docLogs.add(DocumentType.RR, rr.getId(), "补填快递", express(r).replaceFirst("^，", ""));
        return detail(id);
    }

    /** 确认退货：待入库，生成退货入库单；退货退款同时生成退款申请（同一事务） */
    @Transactional(rollbackFor = Exception.class)
    public ReturnVO confirm(Long id, ConfirmReturnRequest req) {
        SalesReturnDO pre = visible(id);
        afterSalesMapper.lockById(pre.getAfterSalesId());
        AfterSalesDO a = afterSalesMapper.selectById(pre.getAfterSalesId());
        if (a.getStatus() == AfterSalesConstants.AS_CLOSED || a.getStatus() == AfterSalesConstants.AS_CANCELLED) {
            throw new BizException("售后单" + AfterSalesConstants.AS_STATUS_NAMES.get(a.getStatus()) + "，不能确认退货");
        }
        SalesOrderDO o = lockOrder(pre.getSoId());
        SalesReturnDO r = lock(id);
        if (r.getStatus() != AfterSalesConstants.RT_CONFIRMING) {
            throw new BizException("销售退货单" + AfterSalesConstants.RT_STATUS_NAMES.get(r.getStatus()) + "，不能确认");
        }
        List<SalesReturnItemDO> items = itemsOf(List.of(id)).getOrDefault(id, List.of());
        // 确认前再按当前已出运数量校验一次（待确认期间可能有其他退货单）
        buildItems(o, items.stream().map(x -> {
            SaveReturnRequest.Line l = new SaveReturnRequest.Line();
            l.setSoItemId(x.getSoItemId());
            l.setQuantity(x.getQuantity());
            return l;
        }).toList(), id);
        r.setStatus(AfterSalesConstants.RT_PENDING);
        r.setConfirmedAt(LocalDateTime.now());
        r.setConfirmedBy(me());
        returnMapper.updateById(r);
        ReturnReceiptDO rr = receiptService.createFor(r, items, o);
        String extra = "";
        if (r.getHandling() == AfterSalesConstants.HANDLING_REFUND) {
            if (req == null || req.getRefund() == null) {
                throw new BizException("请填写退款信息");
            }
            RefundRequestDO q = refundRequests.createForReturn(r, a, o, items, req.getRefund());
            extra = "，退款申请 " + q.getRequestNo() + "（" + FinanceConstants.RQ_STATUS_NAMES.get(q.getStatus()) + "）";
        }
        docLogs.add(DocumentType.TH, id, "确认退货", "生成退货入库单 " + rr.getReceiptNo() + extra);
        logs.add(r.getAfterSalesId(), AfterSalesConstants.LOG_RETURN, "确认销售退货单 " + r.getReturnNo() + "，生成退货入库单 " + rr.getReceiptNo()
                + extra, me());
        logService.recordOperateLog(AfterSalesConstants.MENU_SALES_RETURN, "确认退货", null, Map.of("returnNo", r.getReturnNo(),
                "receiptNo", rr.getReceiptNo()));
        return detail(id);
    }

    /** 作废：待确认，或待入库且退货入库单还没验收；同时作废退货入库单、撤回还没执行的退款申请 */
    @Transactional(rollbackFor = Exception.class)
    public ReturnVO voidReturn(Long id, String reason) {
        String why = trim(reason);
        if (why.isEmpty()) {
            throw new BizException("请填写作废原因");
        }
        SalesReturnDO pre = visible(id);
        afterSalesMapper.lockById(pre.getAfterSalesId());
        SalesReturnDO r = lock(id);
        if (r.getStatus() == AfterSalesConstants.RT_RECEIVED) {
            throw new BizException("已入库的退货单不能作废");
        }
        if (r.getStatus() == AfterSalesConstants.RT_VOID) {
            throw new BizException("销售退货单已作废");
        }
        String voidReason = why.length() > 200 ? why.substring(0, 200) : why;
        List<String> text = new ArrayList<>();
        if (r.getStatus() == AfterSalesConstants.RT_PENDING) {
            ReturnReceiptDO rr = receiptService.voidFor(r, voidReason);
            if (rr != null) {
                text.add("退货入库单 " + rr.getReceiptNo() + " 随之作废");
            }
            text.addAll(refundRequests.withdrawForReturn(r));
        }
        r.setStatus(AfterSalesConstants.RT_VOID);
        r.setVoidReason(voidReason);
        returnMapper.updateById(r);
        String tail = text.isEmpty() ? "" : "；" + String.join("；", text);
        docLogs.add(DocumentType.TH, id, "作废", r.getVoidReason() + tail);
        logs.add(r.getAfterSalesId(), AfterSalesConstants.LOG_RETURN, "作废销售退货单 " + r.getReturnNo() + "：" + r.getVoidReason() + tail, me());
        logService.recordOperateLog(AfterSalesConstants.MENU_SALES_RETURN, "作废销售退货单", null, Map.of("returnNo", r.getReturnNo(),
                "reason", r.getVoidReason()));
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReturnVO remark(Long id, String remark) {
        SalesReturnDO r = lockVisible(id);
        requireNotVoid(r);
        r.setRemark(trim(remark));
        returnMapper.updateById(r);
        docLogs.add(DocumentType.TH, id, "修改备注", r.getRemark().isEmpty() ? "清空备注" : r.getRemark());
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReturnVO attach(Long id, List<Long> ids) {
        requireNotVoid(visible(id));
        attachments.attach(AttachmentService.SALES_RETURN, id, ids);
        docLogs.add(DocumentType.TH, id, "上传附件", ids.size() + " 个");
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReturnVO removeAttachment(Long id, Long attachmentId) {
        requireNotVoid(visible(id));
        attachments.remove(AttachmentService.SALES_RETURN, id, attachmentId);
        docLogs.add(DocumentType.TH, id, "删除附件", "");
        return detail(id);
    }

    /** 退货行：可退 = 已出运 − 其他未作废销售退货单（不含 excludeId）的数量；调用方已锁订单 */
    private List<SalesReturnItemDO> buildItems(SalesOrderDO o, List<SaveReturnRequest.Line> lines, Long excludeId) {
        Map<Long, SalesOrderItemDO> soItems = orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                        .eq(SalesOrderItemDO::getSoId, o.getId())
                        .isNull(SalesOrderItemDO::getDeletedAt))
                .stream().collect(Collectors.toMap(SalesOrderItemDO::getId, i -> i));
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (SaveReturnRequest.Line l : lines == null ? List.<SaveReturnRequest.Line>of() : lines) {
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

    /** 订单型号行在未作废销售退货单上的数量 */
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

    // ---------------------------------------------------------------- 查看

    public PageResult<ReturnVO> page(ReturnQuery q) {
        LambdaQueryWrapper<SalesReturnDO> w = scoped();
        if (q.getStatus() != null) {
            w.eq(SalesReturnDO::getStatus, q.getStatus());
        }
        if (q.getHandling() != null) {
            w.eq(SalesReturnDO::getHandling, q.getHandling());
        }
        if (q.getCustomerId() != null) {
            List<Long> soIds = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>().select(SalesOrderDO::getId)
                            .eq(SalesOrderDO::getTenantId, PiStore.tenantId()).eq(SalesOrderDO::getCustomerId, q.getCustomerId()))
                    .stream().map(SalesOrderDO::getId).toList();
            if (soIds.isEmpty()) {
                return PageResult.of(0L, List.of());
            }
            w.in(SalesReturnDO::getSoId, soIds);
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            Set<Long> soIds = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>().select(SalesOrderDO::getId)
                            .eq(SalesOrderDO::getTenantId, PiStore.tenantId()).like(SalesOrderDO::getSoNo, kw).last("LIMIT 200"))
                    .stream().map(SalesOrderDO::getId).collect(Collectors.toSet());
            Set<Long> asIds = afterSalesMapper.selectList(new LambdaQueryWrapper<AfterSalesDO>().select(AfterSalesDO::getId)
                            .eq(AfterSalesDO::getTenantId, PiStore.tenantId()).like(AfterSalesDO::getAsNo, kw).last("LIMIT 200"))
                    .stream().map(AfterSalesDO::getId).collect(Collectors.toSet());
            Set<Long> byModel = itemMapper.selectList(new LambdaQueryWrapper<SalesReturnItemDO>().select(SalesReturnItemDO::getReturnId)
                            .eq(SalesReturnItemDO::getTenantId, PiStore.tenantId()).like(SalesReturnItemDO::getModel, kw)
                            .isNull(SalesReturnItemDO::getDeletedAt).last("LIMIT 500"))
                    .stream().map(SalesReturnItemDO::getReturnId).collect(Collectors.toSet());
            w.and(x -> {
                x.like(SalesReturnDO::getReturnNo, kw).or().like(SalesReturnDO::getTrackingNo, kw);
                if (!soIds.isEmpty()) {
                    x.or().in(SalesReturnDO::getSoId, soIds);
                }
                if (!asIds.isEmpty()) {
                    x.or().in(SalesReturnDO::getAfterSalesId, asIds);
                }
                if (!byModel.isEmpty()) {
                    x.or().in(SalesReturnDO::getId, byModel);
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
        m.put("confirming", returnMapper.selectCount(scoped().eq(SalesReturnDO::getStatus, AfterSalesConstants.RT_CONFIRMING)));
        m.put("pending", returnMapper.selectCount(scoped().eq(SalesReturnDO::getStatus, AfterSalesConstants.RT_PENDING)));
        return m;
    }

    public ReturnVO detail(Long id) {
        return toVos(List.of(visible(id)), true).get(0);
    }

    /** 售后单详情用：这张售后单的销售退货单 */
    public List<ReturnVO> ofAfterSales(Long afterSalesId) {
        afterSalesService.detail(afterSalesId);
        return toVos(returnMapper.selectList(new LambdaQueryWrapper<SalesReturnDO>()
                .eq(SalesReturnDO::getAfterSalesId, afterSalesId)
                .isNull(SalesReturnDO::getDeletedAt)
                .orderByDesc(SalesReturnDO::getId)), true);
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
        List<Long> soItemIds = items.values().stream().flatMap(List::stream).map(SalesReturnItemDO::getSoItemId).distinct().toList();
        Map<Long, SalesOrderItemDO> soItems = soItemIds.isEmpty() ? Map.of() : orderItemMapper.selectBatchIds(soItemIds).stream()
                .collect(Collectors.toMap(SalesOrderItemDO::getId, i -> i));
        Map<Long, ReturnReceiptDO> receipts = receiptMapper.selectList(new LambdaQueryWrapper<ReturnReceiptDO>()
                        .in(ReturnReceiptDO::getSalesReturnId, ids)
                        .ne(ReturnReceiptDO::getStatus, AfterSalesConstants.RR_VOID)
                        .isNull(ReturnReceiptDO::getDeletedAt))
                .stream().collect(Collectors.toMap(ReturnReceiptDO::getSalesReturnId, x -> x, (a, b) -> b));
        Map<Long, ReturnReceiptItemDO> receiptItems = receipts.isEmpty() ? Map.of() : receiptItemMapper.selectList(
                        new LambdaQueryWrapper<ReturnReceiptItemDO>()
                                .in(ReturnReceiptItemDO::getReceiptId, receipts.values().stream().map(ReturnReceiptDO::getId).toList())
                                .isNull(ReturnReceiptItemDO::getDeletedAt))
                .stream().collect(Collectors.toMap(ReturnReceiptItemDO::getReturnItemId, x -> x, (a, b) -> b));
        Map<Long, List<RefundRequestDO>> refunds = requestMapper.selectList(new LambdaQueryWrapper<RefundRequestDO>()
                        .in(RefundRequestDO::getSalesReturnId, ids)
                        .isNull(RefundRequestDO::getDeletedAt)
                        .orderByDesc(RefundRequestDO::getId))
                .stream().collect(Collectors.groupingBy(RefundRequestDO::getSalesReturnId));
        List<Long> userIds = new ArrayList<>();
        rows.stream().map(SalesReturnDO::getConfirmedBy).filter(Objects::nonNull).forEach(userIds::add);
        afterSales.values().stream().map(AfterSalesDO::getOwnerId).filter(Objects::nonNull).forEach(userIds::add);
        Map<Long, String> users = lookups.userNames(userIds.stream().distinct().toList());
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
            v.setCurrencyCode(o == null ? null : o.getCurrencyCode());
            CustomerDO c = o == null ? null : customers.get(o.getCustomerId());
            v.setCustomerId(o == null ? null : o.getCustomerId());
            v.setCustomerName(InquiryLookups.customerName(c));
            v.setCustomerCountry(c == null ? null : c.getCountry());
            v.setCustomerType(o == null ? null : o.getCustomerType());
            v.setCustomerContact(InquiryLookups.contactName(c));
            v.setHandling(r.getHandling());
            v.setHandlingName(AfterSalesConstants.HANDLING_NAMES.get(r.getHandling()));
            v.setRefundFirst(Objects.equals(r.getRefundFirst(), 1));
            v.setOwnerId(a == null ? null : a.getOwnerId());
            v.setOwnerName(a == null || a.getOwnerId() == null ? null : users.get(a.getOwnerId()));
            v.setCarrier(r.getCarrier());
            v.setTrackingNo(r.getTrackingNo());
            v.setRemark(r.getRemark());
            v.setStatus(r.getStatus());
            v.setStatusName(AfterSalesConstants.RT_STATUS_NAMES.get(r.getStatus()));
            v.setConfirmedAt(r.getConfirmedAt());
            v.setConfirmedByName(r.getConfirmedBy() == null ? null : users.get(r.getConfirmedBy()));
            v.setVoidReason(r.getVoidReason());
            List<SalesReturnItemDO> mine = items.getOrDefault(r.getId(), List.of());
            v.setItemSummary(summary(mine));
            v.setTotalQty(mine.stream().mapToInt(SalesReturnItemDO::getQuantity).sum());
            ReturnReceiptDO rr = receipts.get(r.getId());
            boolean received = rr != null && rr.getReceivedDate() != null;
            v.setItems(mine.stream().map(x -> {
                ReturnVO.Item i = new ReturnVO.Item();
                SalesOrderItemDO si = soItems.get(x.getSoItemId());
                ReturnReceiptItemDO ri = receiptItems.get(x.getId());
                i.setId(x.getId());
                i.setSoItemId(x.getSoItemId());
                i.setModel(x.getModel());
                i.setBrand(x.getBrand());
                i.setCategory(si == null ? "" : Objects.requireNonNullElse(si.getCategory(), ""));
                i.setQuantity(x.getQuantity());
                i.setUnitPrice(si == null ? null : si.getUnitPrice());
                i.setReceivedQty(received && ri != null ? ri.getReceivedQty() : null);
                i.setQualifiedQty(received && ri != null ? ri.getQualifiedQty() : null);
                return i;
            }).toList());
            v.setBrands(v.getItems().stream().map(ReturnVO.Item::getBrand).filter(StringUtils::hasText).distinct().toList());
            v.setCategories(v.getItems().stream().map(ReturnVO.Item::getCategory).filter(StringUtils::hasText).distinct().toList());
            if (received) {
                v.setReceivedDate(rr.getReceivedDate());
                v.setReceivedQty(v.getItems().stream().mapToInt(i -> Objects.requireNonNullElse(i.getReceivedQty(), 0)).sum());
                v.setQualifiedQty(v.getItems().stream().mapToInt(i -> Objects.requireNonNullElse(i.getQualifiedQty(), 0)).sum());
            }
            if (rr != null) {
                ReturnVO.DocBrief b = new ReturnVO.DocBrief();
                b.setId(rr.getId());
                b.setNo(rr.getReceiptNo());
                b.setStatus(rr.getStatus());
                b.setStatusName(AfterSalesConstants.RR_STATUS_NAMES.get(rr.getStatus()));
                v.setReceipt(b);
            }
            v.setRefunds(refunds.getOrDefault(r.getId(), List.of()).stream().map(q -> {
                ReturnVO.RefundBrief b = new ReturnVO.RefundBrief();
                b.setId(q.getId());
                b.setRequestNo(q.getRequestNo());
                b.setCurrencyCode(q.getCurrencyCode());
                b.setAmount(q.getAmount());
                b.setStatus(q.getStatus());
                b.setStatusName(FinanceConstants.RQ_STATUS_NAMES.get(q.getStatus()));
                return b;
            }).toList());
            if (full) {
                v.setAttachments(attachments.listOf(AttachmentService.SALES_RETURN, r.getId()));
                v.setLogs(docLogs.list(DocumentType.TH, r.getId()));
            }
            v.setCreateTime(r.getCreateTime());
            v.setCreateBy(r.getCreateBy());
            v.setUpdateTime(r.getUpdateTime());
            v.setUpdateBy(r.getUpdateBy());
            out.add(v);
        }
        return out;
    }

    /** 调用方保证 ids 非空 */
    private Map<Long, List<SalesReturnItemDO>> itemsOf(Collection<Long> ids) {
        return itemMapper.selectList(new LambdaQueryWrapper<SalesReturnItemDO>()
                        .in(SalesReturnItemDO::getReturnId, ids)
                        .isNull(SalesReturnItemDO::getDeletedAt)
                        .orderByAsc(SalesReturnItemDO::getId))
                .stream().collect(Collectors.groupingBy(SalesReturnItemDO::getReturnId, LinkedHashMap::new, Collectors.toList()));
    }

    private ReturnReceiptDO receiptOf(Long salesReturnId) {
        return receiptMapper.selectOne(new LambdaQueryWrapper<ReturnReceiptDO>()
                .eq(ReturnReceiptDO::getSalesReturnId, salesReturnId)
                .ne(ReturnReceiptDO::getStatus, AfterSalesConstants.RR_VOID)
                .isNull(ReturnReceiptDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    private LambdaQueryWrapper<SalesReturnDO> scoped() {
        LambdaQueryWrapper<SalesReturnDO> w = new LambdaQueryWrapper<SalesReturnDO>()
                .eq(SalesReturnDO::getTenantId, PiStore.tenantId())
                .isNull(SalesReturnDO::getDeletedAt);
        String visible = afterSalesService.visibleIdsSql();
        if (visible != null) {
            w.inSql(SalesReturnDO::getAfterSalesId, visible);
        }
        return w;
    }

    /** 能看到它的售后单才能看到销售退货单 */
    private SalesReturnDO visible(Long id) {
        SalesReturnDO r = id == null ? null : returnMapper.selectById(id);
        if (r == null || r.getDeletedAt() != null || !Objects.equals(r.getTenantId(), PiStore.tenantId())
                || !afterSalesService.canSee(r.getAfterSalesId())) {
            throw new BizException("销售退货单不存在");
        }
        return r;
    }

    private SalesReturnDO lock(Long id) {
        returnMapper.lockById(id);
        return returnMapper.selectById(id);
    }

    private SalesReturnDO lockVisible(Long id) {
        visible(id);
        return lock(id);
    }

    private SalesOrderDO lockOrder(Long soId) {
        SalesOrderDO o = orderMapper.selectOne(new LambdaQueryWrapper<SalesOrderDO>().eq(SalesOrderDO::getId, soId).last("FOR UPDATE"));
        if (o == null || o.getDeletedAt() != null) {
            throw new BizException("销售订单不存在");
        }
        return o;
    }

    private static void requireNotVoid(SalesReturnDO r) {
        if (r.getStatus() == AfterSalesConstants.RT_VOID) {
            throw new BizException("销售退货单已作废，只能查看");
        }
    }

    private static String express(SalesReturnDO r) {
        return r.getTrackingNo().isEmpty() ? "" : "，快递 " + (r.getCarrier() + " " + r.getTrackingNo()).trim();
    }

    static String summary(List<SalesReturnItemDO> rows) {
        return rows.stream().map(x -> x.getModel() + " × " + x.getQuantity()).collect(Collectors.joining("、"));
    }

    private Long me() {
        Long id = currentUser.resolve();
        return id == null ? 0L : id;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}

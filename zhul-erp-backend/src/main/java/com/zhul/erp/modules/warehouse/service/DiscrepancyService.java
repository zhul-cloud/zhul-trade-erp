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
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseRequirementMapper;
import com.zhul.erp.modules.purchase.support.OrderPurchaseProgress;
import com.zhul.erp.modules.purchase.support.PurchaseCalc;
import com.zhul.erp.modules.purchase.support.PurchaseDrafts;
import com.zhul.erp.modules.purchase.support.PurchaseLogs;
import com.zhul.erp.modules.purchase.support.RequirementQty;
import com.zhul.erp.modules.purchase.support.RequirementTouch;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.dto.DiscrepancyPageQuery;
import com.zhul.erp.modules.warehouse.dto.DiscrepancyVO;
import com.zhul.erp.modules.warehouse.dto.EvidenceVO;
import com.zhul.erp.modules.warehouse.dto.HandleDiscrepancyRequest;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptItemDO;
import com.zhul.erp.modules.warehouse.entity.ReceivingDiscrepancyDO;
import com.zhul.erp.modules.warehouse.entity.StockHoldDO;
import com.zhul.erp.modules.warehouse.repository.PurchaseReceiptMapper;
import com.zhul.erp.modules.warehouse.repository.ReceivingDiscrepancyMapper;
import com.zhul.erp.modules.warehouse.repository.StockHoldMapper;
import com.zhul.erp.modules.warehouse.support.ReceivingQty;
import com.zhul.erp.modules.warehouse.support.ReceivingSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 到货差异：验收时按入库行生成（少发 / 不良 / 多发），负责人为采购单的采购员，由采购员选择处理方式。
 * 事务边界：处理 / 重新打开 = 差异状态、采购单行数量与合计（不补）、暂存货（暂存）、订单进度、需求更新时间、采购单日志。
 * 金额精度：折价金额、退货运费按两位小数 HALF_UP 存储（PurchaseCalc.money）。
 */
@Service
@RequiredArgsConstructor
public class DiscrepancyService {

    private static final Set<Integer> RETURNS = Set.of(WarehouseConstants.RES_RETURN_EXCHANGE, WarehouseConstants.RES_RETURN_NO_RESEND,
            WarehouseConstants.RES_RETURN_OVER);
    private static final Set<Integer> REDUCE_ORDER = Set.of(WarehouseConstants.RES_NO_RESEND, WarehouseConstants.RES_RETURN_NO_RESEND);

    private final ReceivingDiscrepancyMapper discrepancyMapper;
    private final PurchaseReceiptMapper receiptMapper;
    private final StockHoldMapper holdMapper;
    private final PurchaseOrderItemMapper poItemMapper;
    private final PurchaseRequirementMapper requirementMapper;
    private final ReceivingSupport support;
    private final ReceivingQty receivingQty;
    private final RequirementQty requirementQty;
    private final PurchaseDrafts drafts;
    private final OrderPurchaseProgress progress;
    private final RequirementTouch touch;
    private final PurchaseLogs logs;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;
    private final AttachmentService attachments;
    private final com.zhul.erp.modules.warehouse.repository.PurchaseReceiptItemMapper receiptItemMapper;

    // ---------------------------------------------------------------- 生成与作废（入库单事务内）

    /** 按入库行生成差异，返回条数 */
    int createForReceipt(PurchaseReceiptDO r, List<PurchaseReceiptItemDO> items, PurchaseOrderDO p) {
        int n = 0;
        for (PurchaseReceiptItemDO i : items) {
            int shipped = i.getShippedQty();
            int received = i.getReceivedQty();
            if (received < shipped) {
                insert(r, i, p, WarehouseConstants.DIFF_SHORT, shipped - received);
                n++;
            }
            if (i.getDefectiveQty() > 0) {
                insert(r, i, p, WarehouseConstants.DIFF_DEFECTIVE, i.getDefectiveQty());
                n++;
            }
            if (received > shipped) {
                insert(r, i, p, WarehouseConstants.DIFF_OVER, received - shipped);
                n++;
            }
        }
        return n;
    }

    private void insert(PurchaseReceiptDO r, PurchaseReceiptItemDO i, PurchaseOrderDO p, int type, int qty) {
        ReceivingDiscrepancyDO d = new ReceivingDiscrepancyDO();
        d.setTenantId(r.getTenantId());
        d.setReceiptId(r.getId());
        d.setReceiptItemId(i.getId());
        d.setPoId(p.getId());
        d.setPoItemId(i.getPoItemId());
        d.setPurchaserId(p.getPurchaserId() == null ? 0L : p.getPurchaserId());
        d.setModel(i.getModel());
        d.setBrand(i.getBrand());
        d.setCategory(i.getCategory());
        d.setType(type);
        d.setQuantity(qty);
        d.setStatus(WarehouseConstants.DIFF_PENDING);
        d.setResolution(0);
        d.setReturnCarrier("");
        d.setReturnTrackingNo("");
        d.setFreeOfCharge(0);
        d.setNote("");
        discrepancyMapper.insert(d);
    }

    boolean allPending(Long receiptId) {
        return discrepancyMapper.selectCount(ofReceiptQuery(receiptId).ne(ReceivingDiscrepancyDO::getStatus, WarehouseConstants.DIFF_PENDING)) == 0;
    }

    void voidForReceipt(Long receiptId) {
        for (ReceivingDiscrepancyDO d : discrepancyMapper.selectList(ofReceiptQuery(receiptId))) {
            d.setDeletedAt(LocalDateTime.now());
            discrepancyMapper.updateById(d);
        }
    }

    List<DiscrepancyVO> ofReceipt(Long receiptId) {
        return toVos(discrepancyMapper.selectList(ofReceiptQuery(receiptId).orderByAsc(ReceivingDiscrepancyDO::getId)));
    }

    private LambdaQueryWrapper<ReceivingDiscrepancyDO> ofReceiptQuery(Long receiptId) {
        return new LambdaQueryWrapper<ReceivingDiscrepancyDO>()
                .eq(ReceivingDiscrepancyDO::getReceiptId, receiptId)
                .isNull(ReceivingDiscrepancyDO::getDeletedAt);
    }

    // ---------------------------------------------------------------- 列表

    public PageResult<DiscrepancyVO> page(DiscrepancyPageQuery q) {
        LambdaQueryWrapper<ReceivingDiscrepancyDO> w = scoped();
        if (q.getType() != null) {
            w.eq(ReceivingDiscrepancyDO::getType, q.getType());
        }
        if (q.getStatus() != null) {
            w.eq(ReceivingDiscrepancyDO::getStatus, q.getStatus());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            Set<Long> poIds = support.poIdsByKeyword(kw);
            List<Long> receiptIds = receiptMapper.selectList(new LambdaQueryWrapper<PurchaseReceiptDO>()
                            .select(PurchaseReceiptDO::getId)
                            .eq(PurchaseReceiptDO::getTenantId, PiStore.tenantId())
                            .like(PurchaseReceiptDO::getGrNo, kw)
                            .last("LIMIT 500"))
                    .stream().map(PurchaseReceiptDO::getId).toList();
            w.and(x -> {
                x.like(ReceivingDiscrepancyDO::getModel, kw);
                if (!poIds.isEmpty()) {
                    x.or().in(ReceivingDiscrepancyDO::getPoId, poIds);
                }
                if (!receiptIds.isEmpty()) {
                    x.or().in(ReceivingDiscrepancyDO::getReceiptId, receiptIds);
                }
            });
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = discrepancyMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        w.last("ORDER BY update_time DESC, id DESC LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toVos(discrepancyMapper.selectList(w)));
    }

    public long countPending() {
        return discrepancyMapper.selectCount(scoped().eq(ReceivingDiscrepancyDO::getStatus, WarehouseConstants.DIFF_PENDING));
    }

    private LambdaQueryWrapper<ReceivingDiscrepancyDO> scoped() {
        LambdaQueryWrapper<ReceivingDiscrepancyDO> w = new LambdaQueryWrapper<ReceivingDiscrepancyDO>()
                .eq(ReceivingDiscrepancyDO::getTenantId, PiStore.tenantId())
                .isNull(ReceivingDiscrepancyDO::getDeletedAt);
        support.scope().apply(w, ReceivingDiscrepancyDO::getPurchaserId);
        return w;
    }

    private List<DiscrepancyVO> toVos(List<ReceivingDiscrepancyDO> rows) {
        Map<Long, PurchaseOrderDO> pos = support.pos(rows.stream().map(ReceivingDiscrepancyDO::getPoId).toList());
        Map<Long, String> names = support.counterparties(pos.values());
        Map<Long, PurchaseReceiptDO> receipts = ReceivingSupport.byId(rows.stream().map(ReceivingDiscrepancyDO::getReceiptId).toList(),
                receiptMapper::selectBatchIds, PurchaseReceiptDO::getId);
        Map<Long, String> users = lookups.userNames(rows.stream()
                .flatMap(d -> java.util.stream.Stream.of(d.getPurchaserId(), d.getHandledBy())).toList());
        Map<Long, Long> holds = new HashMap<>();
        List<Long> ids = rows.stream().map(ReceivingDiscrepancyDO::getId).toList();
        if (!ids.isEmpty()) {
            holdMapper.selectList(new LambdaQueryWrapper<StockHoldDO>()
                            .select(StockHoldDO::getId, StockHoldDO::getDiscrepancyId)
                            .in(StockHoldDO::getDiscrepancyId, ids)
                            .isNull(StockHoldDO::getDeletedAt))
                    .forEach(h -> holds.put(h.getDiscrepancyId(), h.getId()));
        }
        List<DiscrepancyVO> out = new ArrayList<>(rows.size());
        for (ReceivingDiscrepancyDO d : rows) {
            PurchaseOrderDO p = pos.get(d.getPoId());
            PurchaseReceiptDO r = receipts.get(d.getReceiptId());
            DiscrepancyVO vo = new DiscrepancyVO();
            vo.setId(d.getId());
            vo.setReceiptId(d.getReceiptId());
            vo.setGrNo(r == null ? null : r.getGrNo());
            vo.setReceivedDate(r == null ? null : r.getReceivedDate());
            vo.setPoId(d.getPoId());
            vo.setPoNo(p == null ? null : p.getPoNo());
            vo.setSupplierName(names.get(d.getPoId()));
            vo.setCurrencyCode(p == null ? null : p.getCurrencyCode());
            vo.setModel(d.getModel());
            vo.setBrand(d.getBrand());
            vo.setCategory(d.getCategory());
            vo.setType(d.getType());
            vo.setTypeName(WarehouseConstants.DIFF_TYPE_NAMES.get(d.getType()));
            vo.setQuantity(d.getQuantity());
            vo.setStatus(d.getStatus());
            vo.setStatusName(d.getStatus() == WarehouseConstants.DIFF_PENDING ? "待处理" : "已处理");
            vo.setResolution(d.getResolution());
            vo.setResolutionName(WarehouseConstants.RESOLUTION_NAMES.get(d.getResolution()));
            vo.setDiscountAmount(d.getDiscountAmount());
            vo.setReturnCarrier(d.getReturnCarrier());
            vo.setReturnTrackingNo(d.getReturnTrackingNo());
            vo.setReturnFreight(d.getReturnFreight());
            vo.setFreeOfCharge(Objects.equals(d.getFreeOfCharge(), 1));
            vo.setNote(d.getNote());
            vo.setPurchaserId(d.getPurchaserId());
            vo.setPurchaserName(users.get(d.getPurchaserId()));
            vo.setHandledByName(users.get(d.getHandledBy()));
            vo.setHandledAt(d.getHandledAt());
            vo.setHoldId(holds.get(d.getId()));
            vo.setCreateTime(d.getCreateTime());
            vo.setCreateBy(d.getCreateBy());
            vo.setUpdateTime(d.getUpdateTime());
            vo.setUpdateBy(d.getUpdateBy());
            out.add(vo);
        }
        return out;
    }

    /** 仓库的差异说明（行说明 + 整单说明）与验收照片、视频 */
    public EvidenceVO evidence(Long id) {
        ReceivingDiscrepancyDO d = visible(id);
        PurchaseReceiptDO r = receiptMapper.selectById(d.getReceiptId());
        PurchaseReceiptItemDO i = receiptItemMapper.selectById(d.getReceiptItemId());
        List<String> notes = new ArrayList<>();
        if (i != null && StringUtils.hasText(i.getNote())) {
            notes.add(i.getNote());
        }
        if (r != null && StringUtils.hasText(r.getNote())) {
            notes.add(r.getNote());
        }
        EvidenceVO vo = new EvidenceVO();
        vo.setNote(String.join("；", notes));
        vo.setAttachments(attachments.listOf(AttachmentService.RECEIPT, d.getReceiptId()));
        return vo;
    }

    // ---------------------------------------------------------------- 处理与重新打开

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public DiscrepancyVO handle(Long id, HandleDiscrepancyRequest req) {
        ReceivingDiscrepancyDO d = visible(id);
        PurchaseOrderDO p = support.lockPo(d.getPoId(), false);
        d = lock(id);
        if (d.getStatus() != WarehouseConstants.DIFF_PENDING) {
            throw new BizException("差异已处理，需要更正请先重新打开");
        }
        int res = req.getResolution();
        if (!WarehouseConstants.RESOLUTIONS_BY_TYPE.get(d.getType()).contains(res)) {
            throw new BizException(WarehouseConstants.DIFF_TYPE_NAMES.get(d.getType()) + "不能选择「"
                    + WarehouseConstants.RESOLUTION_NAMES.getOrDefault(res, "未知") + "」");
        }
        PurchaseOrderItemDO item = poItemMapper.selectById(d.getPoItemId());
        List<String> detail = new ArrayList<>();
        if (REDUCE_ORDER.contains(res)) {
            int before = item.getQuantity();
            if (before < d.getQuantity()) {
                throw new BizException(d.getModel() + " 订购数量不足，不能再减 " + d.getQuantity() + " 个");
            }
            item.setQuantity(before - d.getQuantity());
            poItemMapper.updateById(item);
            drafts.recalc(p);
            touch.touch(List.of(item.getRequirementId()));
            detail.add("订购数量 " + before + " → " + item.getQuantity() + "，" + d.getQuantity() + " 个回到采购需求");
        }
        if (res == WarehouseConstants.RES_DISCOUNT) {
            if (req.getDiscountAmount() == null) {
                throw new BizException("请填写折价金额");
            }
            d.setDiscountAmount(PurchaseCalc.money(req.getDiscountAmount()));
            detail.add("折价 " + p.getCurrencyCode() + " " + d.getDiscountAmount());
        }
        if (RETURNS.contains(res)) {
            d.setReturnCarrier(ReceivingSupport.trim(req.getReturnCarrier()));
            d.setReturnTrackingNo(ReceivingSupport.trim(req.getReturnTrackingNo()));
            d.setReturnFreight(req.getReturnFreight() == null ? null : PurchaseCalc.money(req.getReturnFreight()));
            String t = (d.getReturnCarrier() + " " + d.getReturnTrackingNo()).trim();
            if (!t.isEmpty()) {
                detail.add("退货快递 " + t);
            }
            if (d.getReturnFreight() != null) {
                detail.add("运费 CNY " + d.getReturnFreight());
            }
        }
        if (res == WarehouseConstants.RES_HOLD) {
            boolean free = Boolean.TRUE.equals(req.getFreeOfCharge());
            d.setFreeOfCharge(free ? 1 : 0);
            StockHoldDO h = new StockHoldDO();
            h.setTenantId(d.getTenantId());
            h.setDiscrepancyId(d.getId());
            h.setReceiptId(d.getReceiptId());
            h.setPoId(d.getPoId());
            h.setModel(d.getModel());
            h.setBrand(d.getBrand());
            h.setCategory(d.getCategory());
            h.setQuantity(d.getQuantity());
            h.setCostPrice(free || item.getNetPriceCny() == null ? BigDecimal.ZERO.setScale(2) : PurchaseCalc.money(item.getNetPriceCny()));
            h.setLocationNote(ReceivingSupport.trim(req.getLocationNote()));
            h.setStatus(WarehouseConstants.HOLD_ACTIVE);
            h.setReturnCarrier("");
            h.setReturnTrackingNo("");
            h.setHandleNote("");
            holdMapper.insert(h);
            detail.add("进入暂存货" + (free ? "（供应商白送）" : "") + (h.getLocationNote().isEmpty() ? "" : "，位置 " + h.getLocationNote()));
        }
        Long operator = currentUser.resolve();
        d.setResolution(res);
        d.setStatus(WarehouseConstants.DIFF_HANDLED);
        d.setNote(ReceivingSupport.trim(req.getNote()));
        d.setHandledBy(operator);
        d.setHandledAt(LocalDateTime.now());
        discrepancyMapper.updateById(d);
        progress.sync(List.of(item.getSoItemId()));
        String text = d.getModel() + " " + WarehouseConstants.DIFF_TYPE_NAMES.get(d.getType()) + " " + d.getQuantity() + "：「"
                + WarehouseConstants.RESOLUTION_NAMES.get(res) + "」" + (detail.isEmpty() ? "" : "，" + String.join("，", detail))
                + (d.getNote().isEmpty() ? "" : "；" + d.getNote());
        logs.add(p.getId(), "处理到货差异", text, operator);
        logService.recordOperateLog(WarehouseConstants.MENU_SHIPMENT, "处理到货差异", null, Map.of("poNo", p.getPoNo(), "content", text));
        return toVos(List.of(d)).get(0);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public DiscrepancyVO reopen(Long id) {
        ReceivingDiscrepancyDO d = visible(id);
        PurchaseOrderDO p = support.lockPo(d.getPoId(), false);
        d = lock(id);
        if (d.getStatus() != WarehouseConstants.DIFF_HANDLED) {
            throw new BizException("差异还没处理");
        }
        int res = d.getResolution();
        PurchaseOrderItemDO item = poItemMapper.selectById(d.getPoItemId());
        if (res == WarehouseConstants.RES_HOLD) {
            StockHoldDO h = holdMapper.selectOne(new LambdaQueryWrapper<StockHoldDO>()
                    .eq(StockHoldDO::getDiscrepancyId, d.getId())
                    .isNull(StockHoldDO::getDeletedAt)
                    .last("LIMIT 1"));
            if (h != null && h.getStatus() != WarehouseConstants.HOLD_ACTIVE) {
                throw new BizException("暂存货已" + WarehouseConstants.HOLD_STATUS_NAMES.get(h.getStatus()).substring(1) + "，不能重新打开");
            }
            if (h != null) {
                holdMapper.lockById(h.getId());
                h.setDeletedAt(LocalDateTime.now());
                holdMapper.updateById(h);
            }
        }
        if (REDUCE_ORDER.contains(res)) {
            if (p.getStatus() != PurchaseConstants.PO_ORDERED) {
                throw new BizException("采购单已取消，不能重新打开");
            }
            requirementMapper.lockByIds(List.of(item.getRequirementId()));
            PurchaseRequirementDO r = requirementMapper.selectById(item.getRequirementId());
            int available = r == null || r.getStatus() != PurchaseConstants.REQ_ACTIVE ? 0
                    : RequirementQty.available(r, requirementQty.of(r.getId()));
            if (available < d.getQuantity()) {
                throw new BizException("这部分数量已经排到其他采购单，不能重新打开");
            }
            item.setQuantity(item.getQuantity() + d.getQuantity());
            poItemMapper.updateById(item);
            drafts.recalc(p);
            touch.touch(List.of(item.getRequirementId()));
        }
        if (res == WarehouseConstants.RES_RETURN_EXCHANGE) {
            // 退货换货的不良数量已计为未发；如果补发已经登记，重新打开会让已发超过订购
            int shipped = receivingQty.shippedByPoItem(List.of(item.getId())).getOrDefault(item.getId(), 0);
            if (shipped + d.getQuantity() > item.getQuantity()) {
                throw new BizException("补发已经登记，不能重新打开");
            }
        }
        String was = WarehouseConstants.RESOLUTION_NAMES.get(res);
        d.setStatus(WarehouseConstants.DIFF_PENDING);
        d.setResolution(0);
        d.setDiscountAmount(null);
        d.setReturnCarrier("");
        d.setReturnTrackingNo("");
        d.setReturnFreight(null);
        d.setFreeOfCharge(0);
        d.setHandledBy(null);
        d.setHandledAt(null);
        d.setNote("");
        discrepancyMapper.updateById(d);
        progress.sync(List.of(item.getSoItemId()));
        String text = d.getModel() + " " + WarehouseConstants.DIFF_TYPE_NAMES.get(d.getType()) + " " + d.getQuantity() + "：撤销「" + was + "」";
        logs.add(p.getId(), "重新打开差异", text, currentUser.resolve());
        logService.recordOperateLog(WarehouseConstants.MENU_SHIPMENT, "重新打开到货差异", null, Map.of("poNo", p.getPoNo(), "content", text));
        return toVos(List.of(d)).get(0);
    }

    private ReceivingDiscrepancyDO visible(Long id) {
        ReceivingDiscrepancyDO d = id == null ? null : discrepancyMapper.selectById(id);
        if (d == null || d.getDeletedAt() != null || !Objects.equals(d.getTenantId(), PiStore.tenantId())
                || !support.scope().canSee(d.getPurchaserId())) {
            throw new BizException("到货差异不存在");
        }
        return d;
    }

    private ReceivingDiscrepancyDO lock(Long id) {
        discrepancyMapper.lockById(id);
        return visible(id);
    }
}

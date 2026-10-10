package com.zhul.erp.modules.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.support.PurchaseCalc;
import com.zhul.erp.modules.purchase.support.PurchaseLogs;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.dto.HandleHoldRequest;
import com.zhul.erp.modules.warehouse.dto.HoldPageQuery;
import com.zhul.erp.modules.warehouse.dto.HoldVO;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.StockHoldDO;
import com.zhul.erp.modules.warehouse.repository.PurchaseReceiptMapper;
import com.zhul.erp.modules.warehouse.repository.StockHoldMapper;
import com.zhul.erp.modules.warehouse.support.ReceivingSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 暂存货：多发选择「暂存」时生成；仓库可以退回供应商、报废、转样品，处置不能撤回。 */
@Service
@RequiredArgsConstructor
public class HoldService {

    private static final Set<Integer> TARGETS = Set.of(WarehouseConstants.HOLD_RETURNED, WarehouseConstants.HOLD_SCRAPPED,
            WarehouseConstants.HOLD_SAMPLE);

    private final StockHoldMapper holdMapper;
    private final PurchaseReceiptMapper receiptMapper;
    private final ReceivingSupport support;
    private final PurchaseLogs logs;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;

    public PageResult<HoldVO> page(HoldPageQuery q) {
        LambdaQueryWrapper<StockHoldDO> w = new LambdaQueryWrapper<StockHoldDO>()
                .eq(StockHoldDO::getTenantId, PiStore.tenantId())
                .isNull(StockHoldDO::getDeletedAt);
        if (q.getStatus() != null) {
            w.eq(StockHoldDO::getStatus, q.getStatus());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            Set<Long> poIds = support.poIdsByKeyword(kw);
            w.and(x -> {
                x.like(StockHoldDO::getModel, kw).or().like(StockHoldDO::getBrand, kw);
                if (!poIds.isEmpty()) {
                    x.or().in(StockHoldDO::getPoId, poIds);
                }
            });
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = holdMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        w.last("ORDER BY update_time DESC, id DESC LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toVos(holdMapper.selectList(w)));
    }

    public long countActive() {
        return holdMapper.selectCount(new LambdaQueryWrapper<StockHoldDO>()
                .eq(StockHoldDO::getTenantId, PiStore.tenantId())
                .eq(StockHoldDO::getStatus, WarehouseConstants.HOLD_ACTIVE)
                .isNull(StockHoldDO::getDeletedAt));
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public HoldVO handle(Long id, HandleHoldRequest req) {
        visible(id);
        holdMapper.lockById(id);
        StockHoldDO h = visible(id);
        if (h.getStatus() != WarehouseConstants.HOLD_ACTIVE) {
            throw new BizException("暂存货已处置，不能再处置");
        }
        int target = req.getStatus();
        if (!TARGETS.contains(target)) {
            throw new BizException("处置方式不正确");
        }
        Long operator = currentUser.resolve();
        h.setStatus(target);
        h.setHandleNote(req.getNote().trim());
        h.setHandledBy(operator);
        h.setHandledAt(LocalDateTime.now());
        if (target == WarehouseConstants.HOLD_RETURNED) {
            h.setReturnCarrier(ReceivingSupport.trim(req.getReturnCarrier()));
            h.setReturnTrackingNo(ReceivingSupport.trim(req.getReturnTrackingNo()));
            h.setReturnFreight(req.getReturnFreight() == null ? null : PurchaseCalc.money(req.getReturnFreight()));
        }
        holdMapper.updateById(h);
        String name = WarehouseConstants.HOLD_STATUS_NAMES.get(target);
        String text = "暂存货 " + h.getModel() + " × " + h.getQuantity() + "：" + name + "；" + h.getHandleNote();
        logs.add(h.getPoId(), "处置暂存货", text, operator);
        logService.recordOperateLog(WarehouseConstants.MENU_HOLD, "处置暂存货", Map.of("model", h.getModel(), "status", "暂存中"),
                Map.of("model", h.getModel(), "status", name, "note", h.getHandleNote()));
        return toVos(List.of(h)).get(0);
    }

    private List<HoldVO> toVos(List<StockHoldDO> rows) {
        Map<Long, PurchaseOrderDO> pos = support.pos(rows.stream().map(StockHoldDO::getPoId).toList());
        Map<Long, String> names = support.counterparties(pos.values());
        Map<Long, PurchaseReceiptDO> receipts = ReceivingSupport.byId(rows.stream().map(StockHoldDO::getReceiptId).toList(),
                receiptMapper::selectBatchIds, PurchaseReceiptDO::getId);
        Map<Long, String> users = lookups.userNames(rows.stream().map(StockHoldDO::getHandledBy).toList());
        List<HoldVO> out = new ArrayList<>(rows.size());
        for (StockHoldDO h : rows) {
            PurchaseOrderDO p = pos.get(h.getPoId());
            PurchaseReceiptDO r = receipts.get(h.getReceiptId());
            HoldVO vo = new HoldVO();
            vo.setId(h.getId());
            vo.setModel(h.getModel());
            vo.setBrand(h.getBrand());
            vo.setCategory(h.getCategory());
            vo.setQuantity(h.getQuantity());
            vo.setCostPrice(h.getCostPrice());
            vo.setLocationNote(h.getLocationNote());
            vo.setStatus(h.getStatus());
            vo.setStatusName(WarehouseConstants.HOLD_STATUS_NAMES.get(h.getStatus()));
            vo.setReceiptId(h.getReceiptId());
            vo.setGrNo(r == null ? null : r.getGrNo());
            vo.setPoId(h.getPoId());
            vo.setPoNo(p == null ? null : p.getPoNo());
            vo.setSupplierName(names.get(h.getPoId()));
            vo.setReturnCarrier(h.getReturnCarrier());
            vo.setReturnTrackingNo(h.getReturnTrackingNo());
            vo.setReturnFreight(h.getReturnFreight());
            vo.setHandledByName(users.get(h.getHandledBy()));
            vo.setHandledAt(h.getHandledAt());
            vo.setHandleNote(h.getHandleNote());
            vo.setCreateTime(h.getCreateTime());
            vo.setCreateBy(h.getCreateBy());
            vo.setUpdateTime(h.getUpdateTime());
            vo.setUpdateBy(h.getUpdateBy());
            out.add(vo);
        }
        return out;
    }

    private StockHoldDO visible(Long id) {
        StockHoldDO h = id == null ? null : holdMapper.selectById(id);
        if (h == null || h.getDeletedAt() != null || !Objects.equals(h.getTenantId(), PiStore.tenantId())) {
            throw new BizException("暂存货不存在");
        }
        return h;
    }
}

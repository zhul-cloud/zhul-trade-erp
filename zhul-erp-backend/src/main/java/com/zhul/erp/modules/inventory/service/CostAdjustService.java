package com.zhul.erp.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.dto.CostAdjustQuery;
import com.zhul.erp.modules.inventory.dto.CostAdjustVO;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.entity.InvCostAdjustDO;
import com.zhul.erp.modules.inventory.entity.InvCostAdjustItemDO;
import com.zhul.erp.modules.inventory.entity.InvWarehouseDO;
import com.zhul.erp.modules.inventory.repository.InvBatchMapper;
import com.zhul.erp.modules.inventory.repository.InvCostAdjustItemMapper;
import com.zhul.erp.modules.inventory.repository.InvCostAdjustMapper;
import com.zhul.erp.modules.inventory.repository.InvWarehouseMapper;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成本调整单的查询、备注与附件（spec finance/inventory-cost「成本调整页」）。调整单由系统生成（{@link InventoryCosting}），
 * 没有新建、修改金额、作废的入口。
 */
@Service
@RequiredArgsConstructor
public class CostAdjustService {

    private final InvCostAdjustMapper mapper;
    private final InvCostAdjustItemMapper itemMapper;
    private final InvBatchMapper batchMapper;
    private final InvWarehouseMapper warehouseMapper;
    private final ProductMapper productMapper;
    private final SalesOrderItemMapper soItemMapper;
    private final SalesOrderMapper soMapper;
    private final InventoryProducts products;
    private final AttachmentService attachments;
    private final DocLogs docLogs;

    // ---------------------------------------------------------------- 查询

    public PageResult<CostAdjustVO> page(CostAdjustQuery q) {
        LambdaQueryWrapper<InvCostAdjustDO> w = scoped();
        if (StringUtils.hasText(q.getMonth())) {
            w.eq(InvCostAdjustDO::getPeriodMonth, q.getMonth().trim());
        }
        if (q.getReason() != null) {
            w.eq(InvCostAdjustDO::getReason, q.getReason());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            Set<Long> byItem = idsByModelOrBatch(kw);
            w.and(x -> {
                x.like(InvCostAdjustDO::getAdjustNo, kw).or().like(InvCostAdjustDO::getSourceDocNo, kw);
                if (!byItem.isEmpty()) {
                    x.or().in(InvCostAdjustDO::getId, byItem);
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
        return PageResult.of(total, mapper.selectList(w).stream().map(CostAdjustService::head).toList());
    }

    /** 型号、批次号命中的调整单 */
    private Set<Long> idsByModelOrBatch(String kw) {
        List<Long> productIds = productMapper.selectList(new LambdaQueryWrapper<ProductDO>().select(ProductDO::getId)
                .like(ProductDO::getMpnDisplay, kw).last("LIMIT 200")).stream().map(ProductDO::getId).toList();
        List<Long> batchIds = batchMapper.selectList(new LambdaQueryWrapper<InvBatchDO>().select(InvBatchDO::getId)
                .eq(InvBatchDO::getTenantId, PiStore.tenantId())
                .like(InvBatchDO::getBatchNo, kw).last("LIMIT 200")).stream().map(InvBatchDO::getId).toList();
        if (productIds.isEmpty() && batchIds.isEmpty()) {
            return Set.of();
        }
        LambdaQueryWrapper<InvCostAdjustItemDO> w = new LambdaQueryWrapper<InvCostAdjustItemDO>()
                .select(InvCostAdjustItemDO::getAdjustId)
                .eq(InvCostAdjustItemDO::getTenantId, PiStore.tenantId())
                .isNull(InvCostAdjustItemDO::getDeletedAt);
        w.and(x -> {
            if (!productIds.isEmpty()) {
                x.in(InvCostAdjustItemDO::getProductId, productIds);
            }
            if (!batchIds.isEmpty()) {
                x.or().in(InvCostAdjustItemDO::getBatchId, batchIds);
            }
        });
        return itemMapper.selectList(w.last("LIMIT 1000")).stream().map(InvCostAdjustItemDO::getAdjustId).collect(Collectors.toSet());
    }

    public CostAdjustVO detail(Long id) {
        InvCostAdjustDO a = require(id);
        CostAdjustVO v = head(a);
        List<InvCostAdjustItemDO> items = itemMapper.selectList(new LambdaQueryWrapper<InvCostAdjustItemDO>()
                .eq(InvCostAdjustItemDO::getTenantId, PiStore.tenantId())
                .eq(InvCostAdjustItemDO::getAdjustId, id)
                .isNull(InvCostAdjustItemDO::getDeletedAt)
                .orderByAsc(InvCostAdjustItemDO::getBatchId, InvCostAdjustItemDO::getPart, InvCostAdjustItemDO::getId));
        v.setBatches(batches(items));
        v.setAttachments(attachments.listOf(AttachmentService.COST_ADJUST, id));
        v.setLogs(docLogs.list(DocumentType.CA, id));
        return v;
    }

    private List<CostAdjustVO.Batch> batches(List<InvCostAdjustItemDO> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, InvBatchDO> batchById = batchMapper.selectBatchIds(items.stream().map(InvCostAdjustItemDO::getBatchId).distinct().toList())
                .stream().collect(Collectors.toMap(InvBatchDO::getId, Function.identity()));
        Map<Long, InventoryProducts.Label> labels = products.labels(items.stream().map(InvCostAdjustItemDO::getProductId).toList());
        List<Long> whIds = items.stream().map(InvCostAdjustItemDO::getWarehouseId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> whNames = whIds.isEmpty() ? Map.of() : warehouseMapper.selectBatchIds(whIds).stream()
                .collect(Collectors.toMap(InvWarehouseDO::getId, InvWarehouseDO::getName));
        Map<Long, SalesOrderDO> orders = ordersOf(items);
        Map<Long, CostAdjustVO.Batch> out = new LinkedHashMap<>();
        for (InvCostAdjustItemDO i : items) {
            CostAdjustVO.Batch b = out.computeIfAbsent(i.getBatchId(), k -> {
                CostAdjustVO.Batch x = new CostAdjustVO.Batch();
                InvBatchDO batch = batchById.get(k);
                InventoryProducts.Label l = labels.getOrDefault(i.getProductId(), InventoryProducts.Label.NONE);
                x.setBatchId(k);
                x.setBatchNo(batch == null ? "" : batch.getBatchNo());
                x.setReceiptId(batch != null && Objects.equals(batch.getSourceType(), InvConstants.BATCH_PURCHASE) ? batch.getSourceId() : null);
                x.setProductId(i.getProductId());
                x.setModel(l.model());
                x.setBrand(l.brand());
                x.setCategory(l.category());
                x.setOldUnitCost(i.getOldUnitCost());
                x.setNewUnitCost(i.getNewUnitCost());
                x.setDiffGoods(i.getDiffGoods());
                x.setDiffInvoice(i.getDiffInvoice());
                x.setDiffFee(i.getDiffFee());
                x.setDiffClaim(i.getDiffClaim());
                x.setQuantity(0);
                x.setAmountCny(BigDecimal.ZERO.setScale(2));
                x.setParts(new ArrayList<>());
                return x;
            });
            CostAdjustVO.Part p = new CostAdjustVO.Part();
            p.setId(i.getId());
            p.setPart(i.getPart());
            p.setPartName(InvConstants.PART_NAMES.get(i.getPart()));
            p.setWarehouseId(i.getWarehouseId());
            p.setWarehouseName(i.getWarehouseId() == null ? "" : whNames.getOrDefault(i.getWarehouseId(), ""));
            // 在库部分的 doc_type 记的是库存状态（DEFECTIVE 为不良），不是单据
            boolean stock = i.getPart() == InvConstants.PART_STOCK;
            p.setDocType(stock ? "" : i.getDocType());
            p.setDocId(stock ? null : i.getDocId());
            p.setDocNo(stock ? ("DEFECTIVE".equals(i.getDocType()) ? "不良" : "") : i.getDocNo());
            p.setSoItemId(i.getSoItemId());
            SalesOrderDO so = i.getSoItemId() == null ? null : orders.get(i.getSoItemId());
            p.setSoId(so == null ? null : so.getId());
            p.setSoNo(so == null ? "" : so.getSoNo());
            p.setQuantity(i.getQuantity());
            p.setAmountGoods(i.getAmountGoods());
            p.setAmountInvoice(i.getAmountInvoice());
            p.setAmountFee(i.getAmountFee());
            p.setAmountClaim(i.getAmountClaim());
            p.setAmountCny(i.getAmountCny());
            b.getParts().add(p);
            b.setQuantity(b.getQuantity() + i.getQuantity());
            b.setAmountCny(b.getAmountCny().add(i.getAmountCny()));
        }
        return new ArrayList<>(out.values());
    }

    /** 型号行 → 销售订单 */
    private Map<Long, SalesOrderDO> ordersOf(List<InvCostAdjustItemDO> items) {
        Set<Long> soItemIds = items.stream().map(InvCostAdjustItemDO::getSoItemId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (soItemIds.isEmpty()) {
            return Map.of();
        }
        List<SalesOrderItemDO> soItems = soItemMapper.selectBatchIds(soItemIds);
        Set<Long> soIds = new HashSet<>();
        soItems.forEach(i -> soIds.add(i.getSoId()));
        Map<Long, SalesOrderDO> sos = soIds.isEmpty() ? Map.of() : soMapper.selectBatchIds(soIds).stream()
                .collect(Collectors.toMap(SalesOrderDO::getId, Function.identity()));
        Map<Long, SalesOrderDO> out = new java.util.HashMap<>();
        soItems.forEach(i -> {
            SalesOrderDO so = sos.get(i.getSoId());
            if (so != null) {
                out.put(i.getId(), so);
            }
        });
        return out;
    }

    private static CostAdjustVO head(InvCostAdjustDO a) {
        CostAdjustVO v = new CostAdjustVO();
        v.setId(a.getId());
        v.setAdjustNo(a.getAdjustNo());
        v.setAdjustDate(a.getAdjustDate());
        v.setPeriodMonth(a.getPeriodMonth());
        v.setReason(a.getReason());
        v.setReasonName(InvConstants.CA_REASON_NAMES.get(a.getReason()));
        v.setSourceDocType(a.getSourceDocType());
        v.setSourceDocId(a.getSourceDocId());
        v.setSourceDocNo(a.getSourceDocNo());
        v.setBatchCount(a.getBatchCount());
        v.setFirstModel(a.getFirstModel());
        v.setCurrencyCode(a.getCurrencyCode());
        v.setStockAmount(a.getStockAmount());
        v.setCogsAmount(a.getCogsAmount());
        v.setOtherAmount(a.getOtherAmount());
        v.setTotalAmount(a.getStockAmount().add(a.getCogsAmount()).add(a.getOtherAmount()));
        v.setRemark(a.getRemark());
        v.setCreateTime(a.getCreateTime());
        v.setCreateBy(a.getCreateBy());
        v.setUpdateTime(a.getUpdateTime());
        v.setUpdateBy(a.getUpdateBy());
        return v;
    }

    // ---------------------------------------------------------------- 备注与附件

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CostAdjustVO remark(Long id, String remark) {
        InvCostAdjustDO a = require(id);
        String text = remark == null ? "" : remark.trim();
        InvCostAdjustDO u = new InvCostAdjustDO();
        u.setId(a.getId());
        u.setRemark(text);
        mapper.updateById(u);
        docLogs.add(DocumentType.CA, id, "修改备注", text.isEmpty() ? "清空备注" : text);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CostAdjustVO attach(Long id, List<Long> ids) {
        require(id);
        attachments.attach(AttachmentService.COST_ADJUST, id, ids);
        docLogs.add(DocumentType.CA, id, "上传附件", ids.size() + " 个");
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CostAdjustVO removeAttachment(Long id, Long attachmentId) {
        require(id);
        attachments.remove(AttachmentService.COST_ADJUST, id, attachmentId);
        docLogs.add(DocumentType.CA, id, "删除附件", "");
        return detail(id);
    }

    private LambdaQueryWrapper<InvCostAdjustDO> scoped() {
        return new LambdaQueryWrapper<InvCostAdjustDO>()
                .eq(InvCostAdjustDO::getTenantId, PiStore.tenantId())
                .isNull(InvCostAdjustDO::getDeletedAt);
    }

    private InvCostAdjustDO require(Long id) {
        InvCostAdjustDO a = id == null ? null : mapper.selectOne(scoped().eq(InvCostAdjustDO::getId, id));
        if (a == null) {
            throw new BizException("成本调整单不存在");
        }
        return a;
    }
}

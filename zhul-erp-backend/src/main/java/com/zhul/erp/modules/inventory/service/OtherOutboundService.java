package com.zhul.erp.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.dto.OtherOutboundQuery;
import com.zhul.erp.modules.inventory.dto.OtherOutboundRequest;
import com.zhul.erp.modules.inventory.dto.OtherOutboundVO;
import com.zhul.erp.modules.inventory.entity.InvBalanceDO;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.entity.InvMoveDO;
import com.zhul.erp.modules.inventory.entity.InvOtherOutboundDO;
import com.zhul.erp.modules.inventory.entity.InvOtherOutboundItemDO;
import com.zhul.erp.modules.inventory.entity.InvReservationDO;
import com.zhul.erp.modules.inventory.entity.InvWarehouseDO;
import com.zhul.erp.modules.inventory.repository.InvBalanceMapper;
import com.zhul.erp.modules.inventory.repository.InvBatchMapper;
import com.zhul.erp.modules.inventory.repository.InvOtherOutboundItemMapper;
import com.zhul.erp.modules.inventory.repository.InvOtherOutboundMapper;
import com.zhul.erp.modules.inventory.repository.InvReservationMapper;
import com.zhul.erp.modules.inventory.repository.InvWarehouseMapper;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Doc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Line;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Loc;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 其他出库单（spec warehouse/other-outbound）：报废、转样品、货代丢失、货损。保存即出库，作废反向。
 * 事务边界：新建 = 单据 + 明细 + 「其他出库」流水 + 操作日志；作废 = 状态 + 反向流水 + 操作日志。
 * 金额：明细金额 = 批次人民币单价 × 数量（HALF_UP 2 位，由过账计算），表头金额为明细合计。
 */
@Service
@RequiredArgsConstructor
public class OtherOutboundService {

    private static final Set<Integer> OWN_TYPES = Set.of(InvConstants.OO_SCRAP, InvConstants.OO_SAMPLE);
    private static final Set<Integer> FORWARDER_TYPES = Set.of(InvConstants.OO_LOST, InvConstants.OO_DAMAGED);

    private final InvOtherOutboundMapper mapper;
    private final InvOtherOutboundItemMapper itemMapper;
    private final InventoryPosting posting;
    private final DocumentNumberService numbers;
    private final DocLogs docLogs;
    private final InvWarehouseMapper warehouseMapper;
    private final InvBatchMapper batchMapper;
    private final InvBalanceMapper balanceMapper;
    private final InvReservationMapper reservationMapper;
    private final ProductMapper productMapper;
    private final InventoryProducts products;
    private final InventoryMoves moves;
    private final AttachmentService attachments;

    /** 出库的一行：某仓库某批次某状态的数量 */
    public record Pick(Long productId, Long batchId, int status, int qty) {
    }

    /** 来源单据（退货去向报废时为退货入库单与去向记录；手工新建为 null） */
    public record Source(String docType, Long docId, String docNo, Long refId) {
    }

    // ---------------------------------------------------------------- 新建

    /** 手工新建：校验类型与仓库、日期、可出数量后生成并过账 */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public OtherOutboundVO createManual(OtherOutboundRequest req) {
        int type = req.getOutboundType();
        if (!InvConstants.OO_TYPE_NAMES.containsKey(type)) {
            throw new BizException("出库类型不正确");
        }
        InvWarehouseDO w = warehouse(req.getWarehouseId());
        if (OWN_TYPES.contains(type) && w.getType() != InvConstants.WH_OWN) {
            throw new BizException(InvConstants.OO_TYPE_NAMES.get(type) + "只能从公司仓库出");
        }
        if (FORWARDER_TYPES.contains(type) && w.getType() != InvConstants.WH_FORWARDER) {
            throw new BizException(InvConstants.OO_TYPE_NAMES.get(type) + "只能从货代仓出");
        }
        LocalDate date = req.getOutboundDate() == null ? LocalDate.now() : req.getOutboundDate();
        if (date.isAfter(LocalDate.now())) {
            throw new BizException("出库日期不能晚于今天");
        }
        // 同一批次同一状态合并成一行
        Map<String, int[]> merged = new LinkedHashMap<>();
        Map<String, OtherOutboundRequest.Line> first = new LinkedHashMap<>();
        for (OtherOutboundRequest.Line l : req.getLines()) {
            if (!InvConstants.STATUS_NAMES.containsKey(l.getStockStatus())) {
                throw new BizException("库存状态不正确");
            }
            String k = l.getBatchId() + ":" + l.getStockStatus();
            merged.computeIfAbsent(k, x -> new int[1])[0] += l.getQuantity();
            first.putIfAbsent(k, l);
        }
        List<Pick> picks = new ArrayList<>(merged.size());
        for (Map.Entry<String, int[]> e : merged.entrySet()) {
            OtherOutboundRequest.Line l = first.get(e.getKey());
            InvBatchDO b = batchMapper.selectById(l.getBatchId());
            if (b == null || !Objects.equals(b.getTenantId(), PiStore.tenantId()) || b.getDeletedAt() != null) {
                throw new BizException("批次不存在");
            }
            Long productId = currentProduct(b.getProductId());
            requireFree(w, productId, b, l.getStockStatus(), e.getValue()[0]);
            picks.add(new Pick(productId, b.getId(), l.getStockStatus(), e.getValue()[0]));
        }
        InvOtherOutboundDO o = create(type, w.getId(), date, req.getReason().trim(), picks, null);
        return detail(o.getId());
    }

    /** 可出 = 结存 − 预占；不够时说明预占给了哪些单据 */
    private void requireFree(InvWarehouseDO w, Long productId, InvBatchDO b, int status, int qty) {
        InvBalanceDO bal = balanceMapper.selectOne(new LambdaQueryWrapper<InvBalanceDO>()
                .eq(InvBalanceDO::getTenantId, PiStore.tenantId())
                .eq(InvBalanceDO::getWarehouseId, w.getId())
                .eq(InvBalanceDO::getProductId, productId)
                .eq(InvBalanceDO::getBatchId, b.getId())
                .eq(InvBalanceDO::getStockStatus, status));
        int onHand = bal == null ? 0 : bal.getOnHand();
        int reserved = bal == null ? 0 : bal.getReserved();
        int free = onHand - reserved;
        if (qty <= free) {
            return;
        }
        String model = products.label(productId).model();
        String where = model + " 在" + w.getName() + "（批次 " + b.getBatchNo() + "，" + InvConstants.STATUS_NAMES.get(status) + "）";
        if (reserved <= 0) {
            throw new BizException("库存不足：" + where + "可出 " + Math.max(free, 0) + " 个，要出 " + qty);
        }
        List<InvReservationDO> rs = reservationMapper.selectList(new LambdaQueryWrapper<InvReservationDO>()
                .eq(InvReservationDO::getTenantId, PiStore.tenantId())
                .eq(InvReservationDO::getWarehouseId, w.getId())
                .eq(InvReservationDO::getBatchId, b.getId())
                .eq(InvReservationDO::getStockStatus, status)
                .gt(InvReservationDO::getQuantity, 0));
        Map<Integer, Set<Long>> byType = rs.stream().collect(Collectors.groupingBy(InvReservationDO::getTargetType,
                Collectors.mapping(InvReservationDO::getTargetId, Collectors.toSet())));
        Map<String, String> nos = moves.targetNos(byType);
        Set<String> soNos = new LinkedHashSet<>();
        Set<String> prNos = new LinkedHashSet<>();
        for (InvReservationDO r : rs) {
            String no = nos.getOrDefault(r.getTargetType() + ":" + r.getTargetId(), "");
            if (!no.isEmpty()) {
                (r.getTargetType() == InvConstants.TARGET_SO_ITEM ? soNos : prNos).add(no);
            }
        }
        String head = free <= 0 ? model + " 在" + w.getName() + "的货都预占给了"
                : where + "可出 " + free + " 个，其余预占给了";
        List<String> todo = new ArrayList<>(2);
        if (!soNos.isEmpty()) {
            todo.add("先对 " + String.join("、", soNos) + " 部分取消");
        }
        if (!prNos.isEmpty()) {
            todo.add("先处理采购退货单 " + String.join("、", prNos));
        }
        throw new BizException(head + (soNos.isEmpty() ? "采购退货单" : prNos.isEmpty() ? "订单" : "订单与采购退货单") + "，"
                + String.join("，", todo) + "再出库");
    }

    /** 商品已并入其他商品时，库存在主商品上 */
    private Long currentProduct(Long productId) {
        Long id = productId;
        for (int i = 0; i < 5; i++) {
            ProductDO p = productMapper.selectById(id);
            if (p == null || p.getMergedIntoId() == null) {
                return id;
            }
            id = p.getMergedIntoId();
        }
        return id;
    }

    /** 生成一张已出库的其他出库单并过账（调用方事务内）；返回出库单 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public InvOtherOutboundDO create(int type, Long warehouseId, LocalDate date, String reason, List<Pick> picks, Source source) {
        InvOtherOutboundDO o = new InvOtherOutboundDO();
        o.setTenantId(PiStore.tenantId());
        o.setOutboundNo(numbers.next(DocumentType.OO));
        o.setOutboundType(type);
        o.setWarehouseId(warehouseId);
        o.setOutboundDate(date == null ? LocalDate.now() : date);
        o.setReason(reason == null ? "" : reason);
        o.setStatus(InvConstants.OO_DONE);
        o.setCurrencyCode("CNY");
        o.setAmountCny(BigDecimal.ZERO.setScale(2));
        o.setSourceDocType(source == null ? "" : source.docType());
        o.setSourceDocId(source == null ? null : source.docId());
        o.setSourceDocNo(source == null ? "" : source.docNo());
        o.setSourceRefId(source == null ? null : source.refId());
        o.setRemark("");
        o.setVoidReason("");
        mapper.insert(o);
        Doc doc = Doc.of(InvConstants.BIZ_OTHER_OUT, "OO", o.getId(), o.getOutboundNo()).date(o.getOutboundDate())
                .label("其他出库 · " + InvConstants.OO_TYPE_NAMES.get(type));
        BigDecimal total = BigDecimal.ZERO;
        for (Pick p : picks) {
            InvOtherOutboundItemDO i = new InvOtherOutboundItemDO();
            i.setTenantId(o.getTenantId());
            i.setOtherOutboundId(o.getId());
            i.setProductId(p.productId());
            i.setBatchId(p.batchId());
            i.setStockStatus(p.status());
            i.setQuantity(p.qty());
            i.setCurrencyCode("CNY");
            itemMapper.insert(i);
            InvMoveDO m = posting.post(List.of(Line.of(new Loc(warehouseId, p.productId(), p.batchId(), p.status()), InvConstants.OP_OUT,
                    p.qty(), null, doc.item(i.getId())))).get(0);
            i.setUnitPriceCny(m.getUnitPriceCny());
            i.setAmountCny(m.getAmountCny().negate());
            itemMapper.updateById(i);
            total = total.add(i.getAmountCny());
        }
        o.setAmountCny(total);
        mapper.updateById(o);
        String text = InvConstants.OO_TYPE_NAMES.get(type) + "：" + picks.stream().mapToInt(Pick::qty).sum() + " 个，¥" + total
                + (o.getReason().isEmpty() ? "" : "，" + o.getReason())
                + (source == null ? "" : "（来源 " + source.docNo() + "）");
        if (source == null) {
            docLogs.add(DocumentType.OO, o.getId(), "新建", text);
        } else {
            docLogs.addBySystem(DocumentType.OO, o.getId(), "生成", text);
        }
        return o;
    }

    // ---------------------------------------------------------------- 作废、备注、附件

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public OtherOutboundVO voidOutbound(Long id, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException("请填写作废原因");
        }
        InvOtherOutboundDO o = lock(id);
        requireNotVoid(o);
        if (StringUtils.hasText(o.getSourceDocType())) {
            throw new BizException("由退货入库单 " + o.getSourceDocNo() + " 报废生成，不能单独作废");
        }
        posting.reverse(posting.activeMoves("OO", id, List.of(InvConstants.BIZ_OTHER_OUT)), InvConstants.BIZ_OTHER_OUT_VOID);
        o.setStatus(InvConstants.OO_VOID);
        o.setVoidReason(reason.trim());
        mapper.updateById(o);
        docLogs.add(DocumentType.OO, id, "作废", o.getVoidReason());
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public OtherOutboundVO remark(Long id, String remark) {
        InvOtherOutboundDO o = lock(id);
        requireNotVoid(o);
        String text = remark == null ? "" : remark.trim();
        o.setRemark(text);
        mapper.updateById(o);
        docLogs.add(DocumentType.OO, id, "修改备注", text.isEmpty() ? "清空备注" : text);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public OtherOutboundVO attach(Long id, List<Long> ids) {
        requireNotVoid(require(id));
        attachments.attach(AttachmentService.OTHER_OUTBOUND, id, ids);
        docLogs.add(DocumentType.OO, id, "上传附件", ids.size() + " 个");
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public OtherOutboundVO removeAttachment(Long id, Long attachmentId) {
        requireNotVoid(require(id));
        attachments.remove(AttachmentService.OTHER_OUTBOUND, id, attachmentId);
        docLogs.add(DocumentType.OO, id, "删除附件", "");
        return detail(id);
    }

    // ---------------------------------------------------------------- 查询

    public PageResult<OtherOutboundVO> page(OtherOutboundQuery q) {
        LambdaQueryWrapper<InvOtherOutboundDO> w = scoped();
        if (q.getOutboundType() != null) {
            w.eq(InvOtherOutboundDO::getOutboundType, q.getOutboundType());
        }
        if (q.getWarehouseId() != null) {
            w.eq(InvOtherOutboundDO::getWarehouseId, q.getWarehouseId());
        }
        if (q.getStatus() != null) {
            w.eq(InvOtherOutboundDO::getStatus, q.getStatus());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            List<Long> productIds = productMapper.selectList(new LambdaQueryWrapper<ProductDO>().select(ProductDO::getId)
                    .like(ProductDO::getMpnDisplay, kw).last("LIMIT 200")).stream().map(ProductDO::getId).toList();
            Set<Long> byModel = productIds.isEmpty() ? Set.of() : itemMapper.selectList(new LambdaQueryWrapper<InvOtherOutboundItemDO>()
                            .select(InvOtherOutboundItemDO::getOtherOutboundId)
                            .eq(InvOtherOutboundItemDO::getTenantId, PiStore.tenantId())
                            .in(InvOtherOutboundItemDO::getProductId, productIds)
                            .isNull(InvOtherOutboundItemDO::getDeletedAt).last("LIMIT 500"))
                    .stream().map(InvOtherOutboundItemDO::getOtherOutboundId).collect(Collectors.toSet());
            w.and(x -> {
                x.like(InvOtherOutboundDO::getOutboundNo, kw).or().like(InvOtherOutboundDO::getSourceDocNo, kw);
                if (!byModel.isEmpty()) {
                    x.or().in(InvOtherOutboundDO::getId, byModel);
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

    public OtherOutboundVO detail(Long id) {
        return toVos(List.of(require(id)), true).get(0);
    }

    /** 某来源单据生成的其他出库单（未删除） */
    public List<InvOtherOutboundDO> bySource(String docType, Long docId) {
        return mapper.selectList(scoped()
                .eq(InvOtherOutboundDO::getSourceDocType, docType)
                .eq(InvOtherOutboundDO::getSourceDocId, docId)
                .orderByAsc(InvOtherOutboundDO::getId));
    }

    private List<OtherOutboundVO> toVos(List<InvOtherOutboundDO> list, boolean full) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(InvOtherOutboundDO::getId).toList();
        Map<Long, List<InvOtherOutboundItemDO>> items = itemMapper.selectList(new LambdaQueryWrapper<InvOtherOutboundItemDO>()
                        .eq(InvOtherOutboundItemDO::getTenantId, PiStore.tenantId())
                        .in(InvOtherOutboundItemDO::getOtherOutboundId, ids)
                        .isNull(InvOtherOutboundItemDO::getDeletedAt)
                        .orderByAsc(InvOtherOutboundItemDO::getId))
                .stream().collect(Collectors.groupingBy(InvOtherOutboundItemDO::getOtherOutboundId));
        List<InvOtherOutboundItemDO> all = items.values().stream().flatMap(List::stream).toList();
        Map<Long, InventoryProducts.Label> labels = products.labels(all.stream().map(InvOtherOutboundItemDO::getProductId).toList());
        Map<Long, String> batchNos = all.isEmpty() ? Map.of() : batchMapper.selectBatchIds(all.stream()
                        .map(InvOtherOutboundItemDO::getBatchId).distinct().toList()).stream()
                .collect(Collectors.toMap(InvBatchDO::getId, InvBatchDO::getBatchNo));
        Map<Long, String> warehouses = warehouseMapper.selectBatchIds(list.stream().map(InvOtherOutboundDO::getWarehouseId).distinct().toList())
                .stream().collect(Collectors.toMap(InvWarehouseDO::getId, InvWarehouseDO::getName));
        return list.stream().map(o -> {
            OtherOutboundVO v = new OtherOutboundVO();
            v.setId(o.getId());
            v.setOutboundNo(o.getOutboundNo());
            v.setOutboundType(o.getOutboundType());
            v.setOutboundTypeName(InvConstants.OO_TYPE_NAMES.get(o.getOutboundType()));
            v.setWarehouseId(o.getWarehouseId());
            v.setWarehouseName(warehouses.getOrDefault(o.getWarehouseId(), ""));
            v.setOutboundDate(o.getOutboundDate());
            v.setReason(o.getReason());
            v.setStatus(o.getStatus());
            v.setStatusName(InvConstants.OO_STATUS_NAMES.get(o.getStatus()));
            v.setCurrencyCode(o.getCurrencyCode());
            v.setAmountCny(o.getAmountCny());
            v.setSourceDocType(o.getSourceDocType());
            v.setSourceDocId(o.getSourceDocId());
            v.setSourceDocNo(o.getSourceDocNo());
            v.setVoidable(o.getStatus() == InvConstants.OO_DONE && !StringUtils.hasText(o.getSourceDocType()));
            v.setVoidReason(o.getVoidReason());
            v.setRemark(o.getRemark());
            v.setCreateTime(o.getCreateTime());
            v.setCreateBy(o.getCreateBy());
            v.setUpdateTime(o.getUpdateTime());
            v.setUpdateBy(o.getUpdateBy());
            List<InvOtherOutboundItemDO> lines = items.getOrDefault(o.getId(), List.of());
            v.setModelCount((int) lines.stream().map(InvOtherOutboundItemDO::getProductId).distinct().count());
            v.setTotalQuantity(lines.stream().mapToInt(InvOtherOutboundItemDO::getQuantity).sum());
            if (!lines.isEmpty()) {
                InventoryProducts.Label l = labels.getOrDefault(lines.get(0).getProductId(), InventoryProducts.Label.NONE);
                v.setFirstModel(l.model());
                v.setFirstBrand(l.brand());
                v.setFirstCategory(l.category());
            }
            v.setItems(lines.stream().map(i -> {
                OtherOutboundVO.Item x = new OtherOutboundVO.Item();
                InventoryProducts.Label l = labels.getOrDefault(i.getProductId(), InventoryProducts.Label.NONE);
                x.setId(i.getId());
                x.setProductId(i.getProductId());
                x.setModel(l.model());
                x.setBrand(l.brand());
                x.setCategory(l.category());
                x.setBatchId(i.getBatchId());
                x.setBatchNo(batchNos.getOrDefault(i.getBatchId(), ""));
                x.setStockStatus(i.getStockStatus());
                x.setStockStatusName(InvConstants.STATUS_NAMES.get(i.getStockStatus()));
                x.setQuantity(i.getQuantity());
                x.setCurrencyCode(i.getCurrencyCode());
                x.setUnitPriceCny(i.getUnitPriceCny());
                x.setAmountCny(i.getAmountCny());
                return x;
            }).toList());
            if (full) {
                v.setAttachments(attachments.listOf(AttachmentService.OTHER_OUTBOUND, o.getId()));
                v.setMoves(moves.ofDoc("OO", o.getId()));
                v.setLogs(docLogs.list(DocumentType.OO, o.getId()));
            }
            return v;
        }).toList();
    }

    private LambdaQueryWrapper<InvOtherOutboundDO> scoped() {
        return new LambdaQueryWrapper<InvOtherOutboundDO>()
                .eq(InvOtherOutboundDO::getTenantId, PiStore.tenantId())
                .isNull(InvOtherOutboundDO::getDeletedAt);
    }

    private InvOtherOutboundDO require(Long id) {
        InvOtherOutboundDO o = id == null ? null : mapper.selectOne(scoped().eq(InvOtherOutboundDO::getId, id));
        if (o == null) {
            throw new BizException("其他出库单不存在");
        }
        return o;
    }

    private InvOtherOutboundDO lock(Long id) {
        InvOtherOutboundDO o = id == null ? null : mapper.selectOne(scoped().eq(InvOtherOutboundDO::getId, id).last("FOR UPDATE"));
        if (o == null) {
            throw new BizException("其他出库单不存在");
        }
        return o;
    }

    private static void requireNotVoid(InvOtherOutboundDO o) {
        if (o.getStatus() == InvConstants.OO_VOID) {
            throw new BizException("其他出库单已作废");
        }
    }

    private InvWarehouseDO warehouse(Long id) {
        InvWarehouseDO w = id == null ? null : warehouseMapper.selectOne(new LambdaQueryWrapper<InvWarehouseDO>()
                .eq(InvWarehouseDO::getTenantId, PiStore.tenantId())
                .eq(InvWarehouseDO::getId, id)
                .isNull(InvWarehouseDO::getDeletedAt));
        if (w == null) {
            throw new BizException("仓库不存在");
        }
        return w;
    }
}

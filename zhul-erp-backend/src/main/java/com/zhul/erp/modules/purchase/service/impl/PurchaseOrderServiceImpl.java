package com.zhul.erp.modules.purchase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.storage.PrivateFileStorage;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.dto.PaymentTermDTO;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.masterdata.support.PaymentTerms;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.dto.CancelPurchaseOrderRequest;
import com.zhul.erp.modules.purchase.dto.ConfirmPurchaseOrderRequest;
import com.zhul.erp.modules.purchase.dto.MoveItemsRequest;
import com.zhul.erp.modules.purchase.dto.PurchaseAttachmentFile;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderListVO;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderPageQuery;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderStatsVO;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderVO;
import com.zhul.erp.modules.purchase.dto.SavePurchaseOrderRequest;
import com.zhul.erp.modules.purchase.dto.ShipFields;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderAttachmentDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderFeeDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderLogDO;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderAttachmentMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderFeeMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderLogMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseRequirementMapper;
import com.zhul.erp.modules.purchase.service.PurchaseOrderService;
import com.zhul.erp.modules.masterdata.dto.CreateSupplierFromChannelRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierCreateResultVO;
import com.zhul.erp.modules.purchase.support.Counterparty;
import com.zhul.erp.modules.purchase.support.OrderPurchaseProgress;
import com.zhul.erp.modules.purchase.support.PurchaseAttachmentStorage;
import com.zhul.erp.modules.purchase.support.PurchaseCalc;
import com.zhul.erp.modules.purchase.support.PurchaseDrafts;
import com.zhul.erp.modules.purchase.support.PurchaseLogs;
import com.zhul.erp.modules.purchase.support.RequirementQty;
import com.zhul.erp.modules.quotation.support.ListSort;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.ExchangeRateService;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.support.ShipProgress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private static final int KEYWORD_LIMIT = 500;
    private static final String LIVE_ITEMS = "FROM purchase_order_item pi WHERE pi.po_id = purchase_order.id AND pi.deleted_at IS NULL";
    private static final Map<String, String> SORTS = Map.of(
            "itemCount", "(SELECT COUNT(*) " + LIVE_ITEMS + ")",
            "totalQuantity", "(SELECT COALESCE(SUM(pi.quantity), 0) " + LIVE_ITEMS + ")",
            "totalAmount", "total_amount_cny",
            "bargainAmount", "bargain_amount");

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final PurchaseOrderFeeMapper feeMapper;
    private final PurchaseOrderAttachmentMapper attachmentMapper;
    private final PurchaseOrderLogMapper logMapper;
    private final PurchaseRequirementMapper requirementMapper;
    private final SupplierMapper supplierMapper;
    private final SalesOrderMapper soMapper;
    private final PurchaseDrafts drafts;
    private final PurchaseLogs logs;
    private final RequirementQty qty;
    private final OrderPurchaseProgress progress;
    private final PurchaseAttachmentStorage storage;
    private final DocumentNumberService documentNumberService;
    private final ExchangeRateService exchangeRateService;
    private final DataScopeResolver dataScopeResolver;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;
    private final com.zhul.erp.modules.purchase.support.RequirementTouch touch;
    private final com.zhul.erp.modules.masterdata.service.SupplierService supplierService;
    private final com.zhul.erp.modules.warehouse.support.ReceivingQty receivingQty;
    private final com.zhul.erp.modules.warehouse.support.PurchaseReceivingLinks receivingLinks;
    private final ShipProgress shipProgress;

    // ---------------------------------------------------------------- 列表与统计

    @Override
    public PageResult<PurchaseOrderListVO> page(PurchaseOrderPageQuery q) {
        int tenant = PiStore.tenantId();
        LambdaQueryWrapper<PurchaseOrderDO> w = scoped();
        if (q.getStatus() != null) {
            w.eq(PurchaseOrderDO::getStatus, q.getStatus());
        }
        if (q.getSupplierId() != null) {
            w.eq(PurchaseOrderDO::getSupplierId, q.getSupplierId());
        }
        String shipCondition = ShipProgress.condition(q.getShipProgress());
        if (shipCondition != null) {
            w.apply(shipCondition);
        }
        if (q.getPurchaserId() != null) {
            w.eq(PurchaseOrderDO::getPurchaserId, q.getPurchaserId());
        }
        if (q.getOrderFrom() != null) {
            w.ge(PurchaseOrderDO::getOrderDate, q.getOrderFrom());
        }
        if (q.getOrderTo() != null) {
            w.le(PurchaseOrderDO::getOrderDate, q.getOrderTo());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            List<Long> supplierIds = supplierMapper.selectList(new LambdaQueryWrapper<SupplierDO>()
                            .select(SupplierDO::getId)
                            .eq(SupplierDO::getTenantId, tenant)
                            .and(x -> x.like(SupplierDO::getName, kw).or().like(SupplierDO::getShortName, kw))
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(SupplierDO::getId).toList();
            List<Long> soIds = soMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                            .select(SalesOrderDO::getId)
                            .eq(SalesOrderDO::getTenantId, tenant)
                            .like(SalesOrderDO::getSoNo, kw)
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(SalesOrderDO::getId).toList();
            List<Long> poIds = itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                            .select(PurchaseOrderItemDO::getPoId)
                            .eq(PurchaseOrderItemDO::getTenantId, tenant)
                            .isNull(PurchaseOrderItemDO::getDeletedAt)
                            .and(x -> {
                                x.like(PurchaseOrderItemDO::getModel, kw);
                                if (!soIds.isEmpty()) {
                                    x.or().in(PurchaseOrderItemDO::getSoId, soIds);
                                }
                            })
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(PurchaseOrderItemDO::getPoId).distinct().toList();
            w.and(x -> {
                x.like(PurchaseOrderDO::getPoNo, kw).or().like(PurchaseOrderDO::getShopName, kw);
                if (!supplierIds.isEmpty()) {
                    x.or().in(PurchaseOrderDO::getSupplierId, supplierIds);
                }
                if (!poIds.isEmpty()) {
                    x.or().in(PurchaseOrderDO::getId, poIds);
                }
            });
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = orderMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        // 列表页统一规范：默认按更新时间倒序；表头排序时按所选列
        String order = q.getSortField() != null && SORTS.containsKey(q.getSortField())
                ? ListSort.orderBy(q.getSortField(), q.getSortOrder(), SORTS) : "ORDER BY update_time DESC, id DESC";
        w.last(order + " LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toListVos(orderMapper.selectList(w)));
    }

    private List<PurchaseOrderListVO> toListVos(List<PurchaseOrderDO> rows) {
        List<Long> ids = rows.stream().map(PurchaseOrderDO::getId).toList();
        Map<Long, List<PurchaseOrderItemDO>> items = ids.isEmpty() ? Map.of()
                : itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                        .in(PurchaseOrderItemDO::getPoId, ids)
                        .isNull(PurchaseOrderItemDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(PurchaseOrderItemDO::getPoId));
        Map<Long, Long> attachments = ids.isEmpty() ? Map.of()
                : attachmentMapper.selectList(new LambdaQueryWrapper<PurchaseOrderAttachmentDO>()
                        .select(PurchaseOrderAttachmentDO::getPoId)
                        .in(PurchaseOrderAttachmentDO::getPoId, ids)
                        .isNull(PurchaseOrderAttachmentDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(PurchaseOrderAttachmentDO::getPoId, Collectors.counting()));
        Set<Long> cancelledReqs = orderCancelledRequirements(items.values().stream().flatMap(List::stream)
                .map(PurchaseOrderItemDO::getRequirementId).toList());
        Map<Long, String> soNos = soNos(items.values().stream().flatMap(List::stream).map(PurchaseOrderItemDO::getSoId).toList());
        Map<Long, String> suppliers = supplierNames(rows.stream().map(PurchaseOrderDO::getSupplierId).toList());
        Map<Long, String> users = lookups.userNames(rows.stream().map(PurchaseOrderDO::getPurchaserId).toList());
        Map<Long, List<PurchaseOrderItemDO>> orderedLines = new HashMap<>();
        rows.stream().filter(p -> p.getStatus() == PurchaseConstants.PO_ORDERED)
                .forEach(p -> orderedLines.put(p.getId(), items.getOrDefault(p.getId(), List.of())));
        Map<Long, ShipProgress.Result> progressByPo = shipProgress.of(orderedLines);
        LocalDateTime now = LocalDateTime.now();
        List<PurchaseOrderListVO> out = new ArrayList<>(rows.size());
        for (PurchaseOrderDO p : rows) {
            List<PurchaseOrderItemDO> lines = items.getOrDefault(p.getId(), List.of());
            PurchaseOrderListVO vo = new PurchaseOrderListVO();
            vo.setId(p.getId());
            vo.setPoNo(p.getPoNo());
            vo.setStatus(p.getStatus());
            vo.setStatusName(PurchaseConstants.PO_STATUS_NAMES.get(p.getStatus()));
            vo.setOrderDate(p.getOrderDate());
            fillShip(vo, p, progressByPo.get(p.getId()));
            vo.setCreateTime(p.getCreateTime());
            if (p.getStatus() == PurchaseConstants.PO_DRAFT && p.getCreateTime() != null) {
                long days = ChronoUnit.DAYS.between(p.getCreateTime().toLocalDate(), now.toLocalDate());
                vo.setStaleDays(days > PurchaseConstants.STALE_DRAFT_DAYS ? (int) days : null);
            }
            vo.setSupplierId(p.getSupplierId());
            Counterparty cp = Counterparty.of(p);
            vo.setShop(cp.isShop());
            vo.setChannel(cp.channel());
            vo.setShopName(cp.isShop() ? cp.shopName() : null);
            vo.setSupplierName(cp.isShop() ? cp.shopTitle() : suppliers.get(p.getSupplierId()));
            vo.setItemCount(lines.size());
            vo.setTotalQuantity(lines.stream().mapToInt(PurchaseOrderItemDO::getQuantity).sum());
            vo.setMissingPriceCount((int) lines.stream().filter(i -> i.getUnitPrice() == null).count());
            vo.setCurrencyCode(p.getCurrencyCode());
            vo.setTotalAmount(p.getTotalAmount());
            vo.setTotalAmountCny(p.getTotalAmountCny());
            vo.setBargainAmount(p.getTargetAmount().signum() == 0 ? null : p.getBargainAmount());
            vo.setBargainRate(PurchaseCalc.rate(p.getBargainAmount(), p.getTargetAmount()));
            vo.setPaymentTermsText(PaymentTerms.text(PaymentTerms.fromJson(p.getPaymentTerms())));
            vo.setAttachmentCount(attachments.getOrDefault(p.getId(), 0L).intValue());
            Map<Long, String> refs = new LinkedHashMap<>();
            lines.forEach(i -> refs.putIfAbsent(i.getSoId(), soNos.get(i.getSoId())));
            vo.setOrders(refs.entrySet().stream().map(e -> {
                PurchaseOrderListVO.SoRef r = new PurchaseOrderListVO.SoRef();
                r.setId(e.getKey());
                r.setSoNo(e.getValue());
                return r;
            }).toList());
            vo.setOrderCancelledCount((int) lines.stream().filter(i -> cancelledReqs.contains(i.getRequirementId())).count());
            vo.setPurchaserId(p.getPurchaserId());
            vo.setPurchaserName(users.get(p.getPurchaserId()));
            vo.setCancelReason(p.getCancelReason());
            vo.setCreateBy(p.getCreateBy());
            vo.setUpdateTime(p.getUpdateTime());
            vo.setUpdateBy(p.getUpdateBy());
            out.add(vo);
        }
        return out;
    }

    /** 预计发货日期与发货进度（列表与详情共用）；r 为空表示不是已下单 */
    private static void fillShip(ShipFields vo, PurchaseOrderDO p, ShipProgress.Result r) {
        vo.setExpectedShipDate(p.getExpectedShipDate());
        if (r == null) {
            return;
        }
        vo.setShipProgress(r.code());
        vo.setShipProgressName(r.name());
        vo.setShippedQty(r.shipped());
        vo.setTotalQty(r.total());
        vo.setEarliestArrival(r.earliestArrival());
        if (r.shipped() < r.total() && p.getExpectedShipDate() != null && p.getExpectedShipDate().isBefore(LocalDate.now())) {
            vo.setOverdueDays((int) ChronoUnit.DAYS.between(p.getExpectedShipDate(), LocalDate.now()));
        }
    }

    @Override
    public PurchaseOrderStatsVO stats() {
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        List<PurchaseOrderDO> month = orderMapper.selectList(scoped()
                .select(PurchaseOrderDO::getId, PurchaseOrderDO::getTotalAmountCny, PurchaseOrderDO::getBargainAmount, PurchaseOrderDO::getTargetAmount)
                .eq(PurchaseOrderDO::getStatus, PurchaseConstants.PO_ORDERED)
                .ge(PurchaseOrderDO::getOrderDate, first)
                .lt(PurchaseOrderDO::getOrderDate, first.plusMonths(1)));
        PurchaseOrderStatsVO vo = new PurchaseOrderStatsVO();
        vo.setMonthOrderedCount((long) month.size());
        vo.setMonthOrderedCny(month.stream().map(PurchaseOrderDO::getTotalAmountCny).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal bargain = month.stream().map(PurchaseOrderDO::getBargainAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setMonthBargain(bargain);
        vo.setMonthBargainRate(PurchaseCalc.rate(bargain, month.stream().map(PurchaseOrderDO::getTargetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        vo.setDrafts(orderMapper.selectCount(scoped().eq(PurchaseOrderDO::getStatus, PurchaseConstants.PO_DRAFT)));
        vo.setPendingShip(orderMapper.selectCount(scoped().apply(ShipProgress.pendingCondition())));
        vo.setOverdueShip(orderMapper.selectCount(scoped().apply(ShipProgress.condition(ShipProgress.OVERDUE))));
        vo.setStaleDrafts(orderMapper.selectCount(scoped().eq(PurchaseOrderDO::getStatus, PurchaseConstants.PO_DRAFT)
                .lt(PurchaseOrderDO::getCreateTime, LocalDate.now().minusDays(PurchaseConstants.STALE_DRAFT_DAYS).atStartOfDay())));
        vo.setOrderCancelled(orderMapper.selectCount(scoped().eq(PurchaseOrderDO::getStatus, PurchaseConstants.PO_ORDERED)
                .apply("EXISTS (SELECT 1 FROM purchase_order_item pi JOIN purchase_requirement r ON r.id = pi.requirement_id "
                        + "WHERE pi.po_id = purchase_order.id AND pi.deleted_at IS NULL AND r.status = {0})", PurchaseConstants.REQ_ORDER_CANCELLED)));
        return vo;
    }

    // ---------------------------------------------------------------- 详情

    @Override
    public PurchaseOrderVO detail(Long id) {
        PurchaseOrderDO p = visible(id);
        List<PurchaseOrderItemDO> lines = drafts.items(id);
        List<PurchaseRequirementDO> reqs = lines.isEmpty() ? List.of()
                : requirementMapper.selectBatchIds(lines.stream().map(PurchaseOrderItemDO::getRequirementId).distinct().toList());
        Map<Long, PurchaseRequirementDO> reqById = reqs.stream().collect(Collectors.toMap(PurchaseRequirementDO::getId, r -> r));
        Map<Long, RequirementQty.Qty> q = qty.byRequirement(reqById.keySet());
        Map<Long, SalesOrderDO> orders = new HashMap<>();
        List<Long> soIds = lines.stream().map(PurchaseOrderItemDO::getSoId).distinct().toList();
        if (!soIds.isEmpty()) {
            soMapper.selectBatchIds(soIds).forEach(o -> orders.put(o.getId(), o));
        }
        Map<Long, CustomerDO> customers = lookups.customers(orders.values().stream().map(SalesOrderDO::getCustomerId).toList());
        List<Long> lineIds = lines.stream().map(PurchaseOrderItemDO::getId).toList();
        Map<Long, Integer> shipped = receivingQty.shippedByPoItem(lineIds);
        Map<Long, Integer> received = receivingQty.qualifiedByPoItem(lineIds);
        List<PurchaseOrderAttachmentDO> files = attachmentMapper.selectList(new LambdaQueryWrapper<PurchaseOrderAttachmentDO>()
                .eq(PurchaseOrderAttachmentDO::getPoId, id)
                .isNull(PurchaseOrderAttachmentDO::getDeletedAt)
                .orderByAsc(PurchaseOrderAttachmentDO::getId));
        List<PurchaseOrderLogDO> logRows = logMapper.selectList(new LambdaQueryWrapper<PurchaseOrderLogDO>()
                .eq(PurchaseOrderLogDO::getPoId, id)
                .orderByDesc(PurchaseOrderLogDO::getId));
        Map<Long, String> users = lookups.userNames(Stream.of(
                        Stream.of(p.getPurchaserId(), p.getCancelledBy()),
                        files.stream().map(PurchaseOrderAttachmentDO::getUploadedBy),
                        logRows.stream().map(PurchaseOrderLogDO::getOperatorId))
                .flatMap(s -> s).toList());

        PurchaseOrderVO vo = new PurchaseOrderVO();
        vo.setId(p.getId());
        vo.setPoNo(p.getPoNo());
        vo.setStatus(p.getStatus());
        vo.setStatusName(PurchaseConstants.PO_STATUS_NAMES.get(p.getStatus()));
        vo.setSupplierId(p.getSupplierId());
        Counterparty cp = Counterparty.of(p);
        vo.setShop(cp.isShop());
        vo.setChannel(cp.channel());
        vo.setShopName(cp.isShop() ? cp.shopName() : null);
        vo.setSupplierName(cp.isShop() ? cp.shopTitle() : supplierNames(List.of(p.getSupplierId())).get(p.getSupplierId()));
        vo.setPurchaserId(p.getPurchaserId());
        vo.setPurchaserName(users.get(p.getPurchaserId()));
        vo.setOrderDate(p.getOrderDate());
        vo.setOrderedAt(p.getOrderedAt());
        fillShip(vo, p, p.getStatus() == PurchaseConstants.PO_ORDERED ? shipProgress.of(Map.of(id, lines)).get(id) : null);
        vo.setCreateTime(p.getCreateTime());
        vo.setCurrencyCode(p.getCurrencyCode());
        vo.setExchangeRate(p.getExchangeRate());
        vo.setTaxIncluded(Objects.equals(p.getTaxIncluded(), 1));
        vo.setTaxRate(p.getTaxRate());
        vo.setItemAmount(p.getItemAmount());
        vo.setFeeAmount(p.getFeeAmount());
        vo.setTotalAmount(p.getTotalAmount());
        vo.setTotalAmountCny(p.getTotalAmountCny());
        vo.setTargetAmount(p.getTargetAmount());
        vo.setBargainAmount(p.getBargainAmount());
        vo.setBargainRate(PurchaseCalc.rate(p.getBargainAmount(), p.getTargetAmount()));
        vo.setPaymentTerms(PaymentTerms.fromJson(p.getPaymentTerms()));
        vo.setPaymentTermsText(PaymentTerms.text(vo.getPaymentTerms()));
        vo.setContractNo(p.getContractNo());
        vo.setContractAmount(p.getContractAmount());
        vo.setContractDiff(p.getContractAmount() == null ? null : p.getContractAmount().subtract(p.getTotalAmount()));
        vo.setCancelReason(p.getCancelReason());
        vo.setCancelledByName(users.get(p.getCancelledBy()));
        vo.setCancelledAt(p.getCancelledAt());
        vo.setEditable(p.getStatus() != PurchaseConstants.PO_CANCELLED);
        vo.setItems(lines.stream().map(i -> {
            PurchaseOrderVO.Item x = new PurchaseOrderVO.Item();
            x.setId(i.getId());
            x.setRequirementId(i.getRequirementId());
            x.setSoId(i.getSoId());
            SalesOrderDO o = orders.get(i.getSoId());
            if (o != null) {
                x.setSoNo(o.getSoNo());
                CustomerDO c = customers.get(o.getCustomerId());
                x.setCustomerName(InquiryLookups.customerName(c));
                x.setCustomerCountry(c == null ? null : c.getCountry());
            }
            x.setModel(i.getModel());
            x.setBrand(i.getBrand());
            x.setQuantity(i.getQuantity());
            PurchaseRequirementDO r = reqById.get(i.getRequirementId());
            x.setCategory(r == null ? null : r.getCategory());
            int available = r == null || r.getStatus() != PurchaseConstants.REQ_ACTIVE ? 0
                    : RequirementQty.available(r, q.getOrDefault(r.getId(), RequirementQty.Qty.ZERO));
            x.setMaxQuantity(available + i.getQuantity());
            x.setUnitPrice(i.getUnitPrice());
            x.setNetPriceCny(i.getNetPriceCny());
            x.setTargetPrice(i.getTargetPrice());
            x.setAmount(i.getAmount());
            x.setBargainAmount(i.getBargainAmount());
            x.setBargainRate(PurchaseCalc.rate(i.getBargainAmount(), PurchaseCalc.targetAmount(i.getTargetPrice(), i.getQuantity())));
            x.setOrderCancelled(r != null && r.getStatus() == PurchaseConstants.REQ_ORDER_CANCELLED);
            x.setShippedQty(shipped.getOrDefault(i.getId(), 0));
            x.setReceivedQty(received.getOrDefault(i.getId(), 0));
            x.setUnshippedQty(Math.max(0, i.getQuantity() - x.getShippedQty()));
            return x;
        }).toList());
        vo.setFees(drafts.fees(id).stream().map(f -> {
            PurchaseOrderVO.Fee x = new PurchaseOrderVO.Fee();
            x.setId(f.getId());
            x.setFeeName(f.getFeeName());
            x.setAmount(f.getAmount());
            return x;
        }).toList());
        vo.setAttachments(files.stream().map(f -> {
            PurchaseOrderVO.Attachment x = new PurchaseOrderVO.Attachment();
            x.setId(f.getId());
            x.setFileName(f.getFileName());
            x.setFileSize(f.getFileSize());
            x.setContentType(f.getContentType());
            x.setUploadedByName(users.get(f.getUploadedBy()));
            x.setCreateTime(f.getCreateTime());
            return x;
        }).toList());
        vo.setLogs(logRows.stream().map(l -> {
            PurchaseOrderVO.Log x = new PurchaseOrderVO.Log();
            x.setAction(l.getAction());
            x.setContent(l.getContent());
            x.setOperatorName(l.getOperatorId() == null ? "系统" : users.get(l.getOperatorId()));
            x.setCreateTime(l.getCreateTime());
            return x;
        }).toList());
        vo.setShipments(receivingLinks.shipments(id));
        vo.setReceipts(receivingLinks.receipts(id));
        vo.setHasShipments(vo.getShipments().stream().anyMatch(s -> s.getStatus() != WarehouseConstants.SHIP_VOID));
        return vo;
    }

    // ---------------------------------------------------------------- 保存

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO save(Long id, SavePurchaseOrderRequest req) {
        PurchaseOrderDO p = lockVisible(id);
        if (p.getStatus() == PurchaseConstants.PO_CANCELLED) {
            throw new BizException("采购单已取消，不能修改");
        }
        boolean ordered = p.getStatus() == PurchaseConstants.PO_ORDERED;
        Long operator = currentUser.resolve();
        Snapshot before = snapshot(p);
        List<String> changes = new ArrayList<>();

        // 换采购对象统一走「改到其他供应商」（同步需求的渠道、合并到已有草稿），保存不改采购对象
        String currency = req.getCurrencyCode().trim().toUpperCase(Locale.ROOT);
        if (!currency.equals(p.getCurrencyCode()) || !PurchaseConstants.CNY.equals(currency)) {
            BigDecimal rate = PurchaseConstants.CNY.equals(currency) ? BigDecimal.ONE : exchangeRateService.require(currency).rate();
            p.setCurrencyCode(currency);
            p.setExchangeRate(rate);
        }
        boolean tax = Boolean.TRUE.equals(req.getTaxIncluded());
        p.setTaxIncluded(tax ? 1 : 0);
        p.setTaxRate(tax ? (req.getTaxRate() == null ? new BigDecimal(PurchaseConstants.DEFAULT_TAX_RATE) : req.getTaxRate()) : BigDecimal.ZERO);
        List<PaymentTermDTO> terms = PaymentTerms.normalize(req.getPaymentTerms());
        p.setPaymentTerms(PaymentTerms.toJson(terms));
        p.setContractNo(req.getContractNo() == null ? "" : req.getContractNo().trim());
        p.setContractAmount(req.getContractAmount() == null ? null : PurchaseCalc.money(req.getContractAmount()));
        LocalDate wantShip = ordered && req.getExpectedShipDate() == null ? p.getExpectedShipDate() : req.getExpectedShipDate();
        if (ordered && wantShip != null && p.getOrderDate() != null && wantShip.isBefore(p.getOrderDate())) {
            throw new BizException("预计发货日期不能早于下单日期");
        }
        if (!Objects.equals(wantShip, p.getExpectedShipDate())) {
            changes.add("预计发货日期 " + (p.getExpectedShipDate() == null ? "未填" : p.getExpectedShipDate()) + " → "
                    + (wantShip == null ? "未填" : wantShip));
            p.setExpectedShipDate(wantShip);
        }

        // 行：只能改本单已有的行；数量不超过需求可下单数量 + 本行原数量
        List<PurchaseOrderItemDO> lines = drafts.items(id);
        Map<Long, PurchaseOrderItemDO> byId = lines.stream().collect(Collectors.toMap(PurchaseOrderItemDO::getId, i -> i));
        Map<Long, SavePurchaseOrderRequest.Line> wanted = new LinkedHashMap<>();
        for (SavePurchaseOrderRequest.Line l : req.getItems()) {
            if (!byId.containsKey(l.getId())) {
                throw new BizException("型号不属于这张采购单");
            }
            wanted.put(l.getId(), l);
        }
        if (wanted.size() != lines.size()) {
            throw new BizException("采购单的型号已变化，请刷新后再保存");
        }
        List<Long> reqIds = lines.stream().map(PurchaseOrderItemDO::getRequirementId).distinct().toList();
        if (!reqIds.isEmpty()) {
            requirementMapper.lockByIds(reqIds);
        }
        Map<Long, PurchaseRequirementDO> reqs = reqIds.isEmpty() ? Map.of()
                : requirementMapper.selectBatchIds(reqIds).stream().collect(Collectors.toMap(PurchaseRequirementDO::getId, r -> r));
        Map<Long, RequirementQty.Qty> q = qty.byRequirement(reqIds);
        Map<Long, Integer> shipped = receivingQty.shippedByPoItem(lines.stream().map(PurchaseOrderItemDO::getId).toList());
        List<Long> qtyChanged = new ArrayList<>();
        for (PurchaseOrderItemDO i : lines) {
            SavePurchaseOrderRequest.Line l = wanted.get(i.getId());
            int newQty = l.getQuantity();
            if (newQty != i.getQuantity()) {
                qtyChanged.add(i.getRequirementId());
                PurchaseRequirementDO r = reqs.get(i.getRequirementId());
                int available = r == null || r.getStatus() != PurchaseConstants.REQ_ACTIVE ? 0
                        : RequirementQty.available(r, q.getOrDefault(r.getId(), RequirementQty.Qty.ZERO));
                int max = available + i.getQuantity();
                if (newQty > max) {
                    throw new BizException(i.getModel() + " 最多 " + max + " 个");
                }
                int sent = shipped.getOrDefault(i.getId(), 0);
                if (newQty < sent) {
                    throw new BizException(i.getModel() + " 已发 " + sent + " 个，数量不能小于 " + sent);
                }
                changes.add(i.getModel() + " 数量 " + i.getQuantity() + " → " + newQty);
                i.setQuantity(newQty);
            }
            BigDecimal price = l.getUnitPrice() == null ? null : PurchaseCalc.money(l.getUnitPrice());
            if (!sameMoney(price, i.getUnitPrice())) {
                changes.add(i.getModel() + " 单价 " + moneyText(p.getCurrencyCode(), i.getUnitPrice()) + " → " + moneyText(p.getCurrencyCode(), price));
                i.setUnitPrice(price);
            }
            itemMapper.updateById(i);
        }

        // 其他费用整体替换
        List<PurchaseOrderFeeDO> oldFees = drafts.fees(id);
        String feesBefore = feesText(oldFees.stream().map(f -> f.getFeeName() + " " + f.getAmount()).toList());
        oldFees.forEach(f -> {
            f.setDeletedAt(LocalDateTime.now());
            feeMapper.updateById(f);
        });
        List<String> newFeeText = new ArrayList<>();
        int sort = 1;
        for (SavePurchaseOrderRequest.Fee f : req.getFees() == null ? List.<SavePurchaseOrderRequest.Fee>of() : req.getFees()) {
            PurchaseOrderFeeDO x = new PurchaseOrderFeeDO();
            x.setTenantId(p.getTenantId());
            x.setPoId(id);
            x.setFeeName(f.getFeeName().trim());
            x.setAmount(PurchaseCalc.money(f.getAmount()));
            x.setSortOrder(sort++);
            feeMapper.insert(x);
            newFeeText.add(x.getFeeName() + " " + x.getAmount());
        }
        String feesAfter = feesText(newFeeText);
        if (!feesBefore.equals(feesAfter)) {
            changes.add("其他费用 " + feesBefore + " → " + feesAfter);
        }
        drafts.recalc(p);
        touch.touch(qtyChanged);
        Snapshot after = snapshot(p);
        changes.addAll(before.diff(after));
        if (!changes.isEmpty()) {
            logs.add(id, ordered ? "修改" : "编辑草稿", String.join("；", changes), operator);
        }
        if (ordered) {
            progress.sync(lines.stream().map(PurchaseOrderItemDO::getSoItemId).toList());
            if (!changes.isEmpty()) {
                logService.recordOperateLog(PurchaseConstants.MENU_ORDER, "修改采购单", null, Map.of("poNo", p.getPoNo(), "changes", changes));
            }
        }
        return detail(id);
    }

    /** 单头中需要记日志的字段 */
    private record Snapshot(String currency, String tax, String terms, String contractNo, String contractAmount) {
        List<String> diff(Snapshot o) {
            List<String> out = new ArrayList<>();
            if (!currency.equals(o.currency)) {
                out.add("币种 " + currency + " → " + o.currency);
            }
            if (!tax.equals(o.tax)) {
                out.add(tax + " → " + o.tax);
            }
            if (!terms.equals(o.terms)) {
                out.add("付款条件 " + (terms.isEmpty() ? "未填" : terms) + " → " + (o.terms.isEmpty() ? "未填" : o.terms));
            }
            if (!contractNo.equals(o.contractNo)) {
                out.add("合同编号 " + (contractNo.isEmpty() ? "无" : contractNo) + " → " + (o.contractNo.isEmpty() ? "无" : o.contractNo));
            }
            if (!contractAmount.equals(o.contractAmount)) {
                out.add("合同金额 " + contractAmount + " → " + o.contractAmount);
            }
            return out;
        }
    }

    private static Snapshot snapshot(PurchaseOrderDO p) {
        String tax = Objects.equals(p.getTaxIncluded(), 1) ? "含税 " + p.getTaxRate().stripTrailingZeros().toPlainString() + "%" : "不含税";
        return new Snapshot(p.getCurrencyCode(), tax, PaymentTerms.text(PaymentTerms.fromJson(p.getPaymentTerms())),
                Objects.requireNonNullElse(p.getContractNo(), ""),
                p.getContractAmount() == null ? "无" : p.getContractAmount().toPlainString());
    }

    private static boolean sameMoney(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static String moneyText(String currency, BigDecimal v) {
        return v == null ? "未填" : currency + " " + v.toPlainString();
    }

    private static String feesText(List<String> parts) {
        return parts.isEmpty() ? "无" : String.join("、", parts);
    }

    // ---------------------------------------------------------------- 草稿的行

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO removeItems(Long id, List<Long> itemIds) {
        PurchaseOrderDO p = lockVisible(id);
        requireDraft(p);
        List<PurchaseOrderItemDO> picked = pickItems(id, itemIds);
        Long operator = currentUser.resolve();
        for (PurchaseOrderItemDO i : picked) {
            i.setDeletedAt(LocalDateTime.now());
            itemMapper.updateById(i);
        }
        touch.touch(picked.stream().map(PurchaseOrderItemDO::getRequirementId).toList());
        logs.add(id, "移除型号", picked.stream().map(i -> i.getModel() + " × " + i.getQuantity()).collect(Collectors.joining("、"))
                + "（数量回到需求池）", operator);
        if (drafts.deleteIfEmpty(p, operator)) {
            return null;
        }
        drafts.recalc(p);
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long moveItems(Long id, MoveItemsRequest req) {
        PurchaseOrderDO p = lockVisible(id);
        requireDraft(p);
        Counterparty target = Counterparty.of(req.getSupplierId(), req.getChannel(), req.getShopName());
        if (target.key().equals(Counterparty.of(p).key())) {
            throw new BizException("已经是这个采购对象");
        }
        String targetName = target.isShop() ? target.shopTitle() : drafts.requireSupplier(target.supplierId()).getName();
        List<PurchaseOrderItemDO> picked = pickItems(id, req.getItemIds());
        Long operator = currentUser.resolve();
        Map<Long, PurchaseRequirementDO> reqs = requirementMapper.selectBatchIds(picked.stream().map(PurchaseOrderItemDO::getRequirementId).distinct().toList())
                .stream().collect(Collectors.toMap(PurchaseRequirementDO::getId, r -> r));
        List<PurchaseDrafts.Placement> moves = new ArrayList<>(picked.size());
        for (PurchaseOrderItemDO i : picked) {
            i.setDeletedAt(LocalDateTime.now());
            itemMapper.updateById(i);
            moves.add(new PurchaseDrafts.Placement(reqs.get(i.getRequirementId()), p.getPurchaserId(), target, i.getQuantity(),
                    i.getUnitPrice()));
        }
        // 需求的「向谁买」跟着改，采购需求页与草稿保持一致
        for (PurchaseRequirementDO r : reqs.values()) {
            if (r.getStatus() == PurchaseConstants.REQ_ACTIVE) {
                target.applyTo(r);
                requirementMapper.updateById(r);
            }
        }
        String models = picked.stream().map(i -> i.getModel() + " × " + i.getQuantity()).collect(Collectors.joining("、"));
        logs.add(id, "改到其他供应商", models + " 改到 " + targetName, operator);
        String sourceName = titleOf(p);
        if (!drafts.deleteIfEmpty(p, operator)) {
            drafts.recalc(p);
        }
        List<PurchaseOrderDO> placed = drafts.place(moves, "从 " + sourceName + " 的草稿改过来", null, operator);
        return placed.get(0).getId();
    }

    /** 采购对象的显示名：老供应商名称，或「淘宝 · 店铺名」 */
    private String titleOf(PurchaseOrderDO p) {
        Counterparty cp = Counterparty.of(p);
        return cp.isShop() ? cp.shopTitle() : supplierNames(List.of(p.getSupplierId())).get(p.getSupplierId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO convertShop(Long id) {
        PurchaseOrderDO p = lockVisible(id);
        Counterparty cp = Counterparty.of(p);
        if (!cp.isShop()) {
            throw new BizException("采购对象已经是供应商");
        }
        if (p.getStatus() == PurchaseConstants.PO_CANCELLED) {
            throw new BizException("采购单已取消");
        }
        CreateSupplierFromChannelRequest create = new CreateSupplierFromChannelRequest();
        create.setChannelName(cp.shopName());
        SupplierCreateResultVO res = supplierService.createFromChannel(create);
        Long supplierId = res.isDuplicate() && res.getExistingSupplier() != null ? res.getExistingSupplier().getId()
                : res.getCreatedSupplier().getId();
        Long operator = currentUser.resolve();
        List<PurchaseOrderDO> pos = orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrderDO>()
                .eq(PurchaseOrderDO::getTenantId, PiStore.tenantId())
                .eq(PurchaseOrderDO::getChannel, cp.channel())
                .eq(PurchaseOrderDO::getShopName, cp.shopName())
                .in(PurchaseOrderDO::getStatus, PurchaseConstants.PO_DRAFT, PurchaseConstants.PO_ORDERED)
                .isNull(PurchaseOrderDO::getDeletedAt));
        for (PurchaseOrderDO x : pos) {
            orderMapper.lockById(x.getId());
            x.setSupplierId(supplierId);
            x.setChannel(PurchaseConstants.CHANNEL_SUPPLIER);
            x.setShopName("");
            orderMapper.updateById(x);
            logs.add(x.getId(), "转为供应商", cp.shopTitle() + " 转为供应商「" + cp.shopName() + "」", operator);
        }
        List<PurchaseRequirementDO> reqs = requirementMapper.selectList(new LambdaQueryWrapper<PurchaseRequirementDO>()
                .eq(PurchaseRequirementDO::getTenantId, PiStore.tenantId())
                .eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                .isNull(PurchaseRequirementDO::getSuggestedSupplierId)
                .eq(PurchaseRequirementDO::getSuggestedChannel, cp.channel())
                .eq(PurchaseRequirementDO::getSuggestedShopName, cp.shopName())
                .isNull(PurchaseRequirementDO::getDeletedAt));
        for (PurchaseRequirementDO r : reqs) {
            Counterparty.supplier(supplierId).applyTo(r);
            requirementMapper.updateById(r);
        }
        logService.recordOperateLog(PurchaseConstants.MENU_ORDER, "店铺转为供应商", null,
                Map.of("shop", cp.shopTitle(), "supplierId", supplierId, "orders", pos.size(), "requirements", reqs.size()));
        return detail(id);
    }

    private List<PurchaseOrderItemDO> pickItems(Long poId, List<Long> itemIds) {
        Set<Long> wanted = new LinkedHashSet<>(itemIds == null ? List.of() : itemIds);
        List<PurchaseOrderItemDO> picked = drafts.items(poId).stream().filter(i -> wanted.contains(i.getId())).toList();
        if (picked.isEmpty() || picked.size() != wanted.size()) {
            throw new BizException("型号不属于这张采购单");
        }
        return picked;
    }

    // ---------------------------------------------------------------- 确认下单、取消、删除

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO confirm(Long id, ConfirmPurchaseOrderRequest req) {
        PurchaseOrderDO p = lockVisible(id);
        requireDraft(p);
        if (req.getOrderDate().isAfter(LocalDate.now())) {
            throw new BizException("下单日期不能晚于今天");
        }
        if (req.getExpectedShipDate().isBefore(req.getOrderDate())) {
            throw new BizException("预计发货日期不能早于下单日期");
        }
        List<PurchaseOrderItemDO> lines = drafts.items(id);
        if (lines.isEmpty()) {
            throw new BizException("采购单没有型号");
        }
        long missing = lines.stream().filter(i -> i.getUnitPrice() == null || i.getUnitPrice().signum() <= 0).count();
        if (missing > 0) {
            throw new BizException("还有 " + missing + " 行没填单价");
        }
        if (PaymentTerms.fromJson(p.getPaymentTerms()).isEmpty()) {
            throw new BizException("请填写付款条件");
        }
        if (!Counterparty.of(p).isShop()) {
            drafts.requireSupplier(p.getSupplierId());
        }
        requirementMapper.lockByIds(lines.stream().map(PurchaseOrderItemDO::getRequirementId).distinct().toList());
        drafts.recalc(p);
        p.setPoNo(documentNumberService.next(DocumentType.PO));
        p.setStatus(PurchaseConstants.PO_ORDERED);
        p.setOrderDate(req.getOrderDate());
        p.setExpectedShipDate(req.getExpectedShipDate());
        p.setOrderedAt(LocalDateTime.now());
        orderMapper.updateById(p);
        Long operator = currentUser.resolve();
        touch.touch(lines.stream().map(PurchaseOrderItemDO::getRequirementId).toList());
        logs.add(id, "确认下单", "下单日期 " + req.getOrderDate() + "，预计 " + req.getExpectedShipDate() + " 发货，编号 " + p.getPoNo(), operator);
        progress.sync(lines.stream().map(PurchaseOrderItemDO::getSoItemId).toList());
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("poNo", p.getPoNo());
        after.put("supplier", titleOf(p));
        after.put("total", p.getCurrencyCode() + " " + p.getTotalAmount());
        after.put("bargain", "CNY " + p.getBargainAmount());
        logService.recordOperateLog(PurchaseConstants.MENU_ORDER, "确认下单", null, after);
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO cancel(Long id, CancelPurchaseOrderRequest req) {
        PurchaseOrderDO p = lockVisible(id);
        if (p.getStatus() != PurchaseConstants.PO_ORDERED) {
            throw new BizException(p.getStatus() == PurchaseConstants.PO_DRAFT ? "草稿不用取消，可以直接删除" : "采购单已取消");
        }
        if (receivingLinks.hasShipments(id)) {
            throw new BizException("已有发货，请在到货差异里处理少发或退货");
        }
        String why = req.getReason().trim();
        p.setStatus(PurchaseConstants.PO_CANCELLED);
        p.setCancelReason(why);
        p.setCancelledBy(currentUser.resolve());
        p.setCancelledAt(LocalDateTime.now());
        orderMapper.updateById(p);
        List<PurchaseOrderItemDO> lines = drafts.items(id);
        closeEmptyCancelledRequirements(lines);
        touch.touch(lines.stream().map(PurchaseOrderItemDO::getRequirementId).toList());
        logs.add(id, "取消", "原因：" + why + "；各型号数量回到需求池", p.getCancelledBy());
        progress.sync(lines.stream().map(PurchaseOrderItemDO::getSoItemId).toList());
        logService.recordOperateLog(PurchaseConstants.MENU_ORDER, "取消采购单", Map.of("poNo", p.getPoNo(), "status", "已下单"),
                Map.of("poNo", p.getPoNo(), "status", "已取消", "reason", why));
        return detail(id);
    }

    /** 来源订单已取消的需求：采购单取消后已下单数量归零的，需求关闭 */
    private void closeEmptyCancelledRequirements(List<PurchaseOrderItemDO> lines) {
        List<Long> ids = lines.stream().map(PurchaseOrderItemDO::getRequirementId).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, RequirementQty.Qty> q = qty.byRequirement(ids);
        for (PurchaseRequirementDO r : requirementMapper.selectBatchIds(ids)) {
            if (r.getStatus() == PurchaseConstants.REQ_ORDER_CANCELLED && q.getOrDefault(r.getId(), RequirementQty.Qty.ZERO).ordered() == 0) {
                r.setStatus(PurchaseConstants.REQ_CLOSED);
                requirementMapper.updateById(r);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDraft(Long id) {
        PurchaseOrderDO p = lockVisible(id);
        requireDraft(p);
        Long operator = currentUser.resolve();
        LocalDateTime now = LocalDateTime.now();
        List<PurchaseOrderItemDO> removed = drafts.items(id);
        for (PurchaseOrderItemDO i : removed) {
            i.setDeletedAt(now);
            itemMapper.updateById(i);
        }
        touch.touch(removed.stream().map(PurchaseOrderItemDO::getRequirementId).toList());
        p.setDeletedAt(now);
        orderMapper.updateById(p);
        logs.add(id, "删除草稿", "各型号数量回到需求池", operator);
        logService.recordOperateLog(PurchaseConstants.MENU_ORDER, "删除草稿采购单", Map.of("id", id), null);
    }

    private static void requireDraft(PurchaseOrderDO p) {
        if (p.getStatus() != PurchaseConstants.PO_DRAFT) {
            throw new BizException("只有草稿可以这样操作");
        }
    }

    // ---------------------------------------------------------------- 合同附件

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO uploadAttachment(Long id, MultipartFile file) {
        PurchaseOrderDO p = lockVisible(id);
        if (p.getStatus() == PurchaseConstants.PO_CANCELLED) {
            throw new BizException("采购单已取消，不能上传合同");
        }
        if (Counterparty.of(p).isShop()) {
            throw new BizException("线上店铺的采购单不需要上传合同");
        }
        long count = attachmentMapper.selectCount(new LambdaQueryWrapper<PurchaseOrderAttachmentDO>()
                .eq(PurchaseOrderAttachmentDO::getPoId, id)
                .isNull(PurchaseOrderAttachmentDO::getDeletedAt));
        if (count >= PurchaseConstants.MAX_ATTACHMENTS) {
            throw new BizException("每张采购单最多 " + PurchaseConstants.MAX_ATTACHMENTS + " 个合同文件");
        }
        PrivateFileStorage.StoredFile stored = storage.store(p.getTenantId(), file);
        PurchaseOrderAttachmentDO x = new PurchaseOrderAttachmentDO();
        x.setTenantId(p.getTenantId());
        x.setPoId(id);
        x.setFileKey(stored.fileKey());
        x.setFileName(stored.fileName());
        x.setFileSize(stored.fileSize());
        x.setContentType(stored.contentType());
        x.setUploadedBy(currentUser.resolve());
        attachmentMapper.insert(x);
        logs.add(id, "上传合同", stored.fileName(), x.getUploadedBy());
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO deleteAttachment(Long id, Long attachmentId) {
        lockVisible(id);
        PurchaseOrderAttachmentDO x = attachmentOf(id, attachmentId);
        x.setDeletedAt(LocalDateTime.now());
        attachmentMapper.updateById(x);
        logs.add(id, "删除合同", x.getFileName(), currentUser.resolve());
        return detail(id);
    }

    @Override
    public PurchaseAttachmentFile attachmentFile(Long id, Long attachmentId) {
        PurchaseOrderDO p = visible(id);
        PurchaseOrderAttachmentDO x = attachmentOf(id, attachmentId);
        return new PurchaseAttachmentFile(storage.resolveOwned(x.getFileKey(), p.getTenantId()), x.getFileName(), x.getContentType());
    }

    private PurchaseOrderAttachmentDO attachmentOf(Long poId, Long attachmentId) {
        PurchaseOrderAttachmentDO x = attachmentId == null ? null : attachmentMapper.selectById(attachmentId);
        if (x == null || x.getDeletedAt() != null || !Objects.equals(x.getPoId(), poId)) {
            throw new BizException("合同文件不存在");
        }
        return x;
    }

    // ---------------------------------------------------------------- 通用

    private LambdaQueryWrapper<PurchaseOrderDO> scoped() {
        LambdaQueryWrapper<PurchaseOrderDO> w = new LambdaQueryWrapper<PurchaseOrderDO>()
                .eq(PurchaseOrderDO::getTenantId, PiStore.tenantId())
                .isNull(PurchaseOrderDO::getDeletedAt);
        dataScopeResolver.current().apply(w, PurchaseOrderDO::getPurchaserId);
        return w;
    }

    private PurchaseOrderDO visible(Long id) {
        PurchaseOrderDO p = id == null ? null : orderMapper.selectById(id);
        if (p == null || p.getDeletedAt() != null || !Objects.equals(p.getTenantId(), PiStore.tenantId())
                || !dataScopeResolver.current().canSee(p.getPurchaserId())) {
            throw new BizException("采购单不存在");
        }
        return p;
    }

    private PurchaseOrderDO lockVisible(Long id) {
        visible(id);
        orderMapper.lockById(id);
        return visible(id);
    }

    private Set<Long> orderCancelledRequirements(Collection<Long> ids) {
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (keys.isEmpty()) {
            return Set.of();
        }
        return requirementMapper.selectList(new LambdaQueryWrapper<PurchaseRequirementDO>()
                        .select(PurchaseRequirementDO::getId)
                        .in(PurchaseRequirementDO::getId, keys)
                        .eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ORDER_CANCELLED))
                .stream().map(PurchaseRequirementDO::getId).collect(Collectors.toSet());
    }

    private Map<Long, String> soNos(Collection<Long> ids) {
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, String> map = new HashMap<>(keys.size() * 2 + 1);
        if (!keys.isEmpty()) {
            soMapper.selectBatchIds(keys).forEach(o -> map.put(o.getId(), o.getSoNo()));
        }
        return map;
    }

    private Map<Long, String> supplierNames(Collection<Long> ids) {
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, String> map = new HashMap<>(keys.size() * 2 + 1);
        if (!keys.isEmpty()) {
            supplierMapper.selectBatchIds(keys).forEach(s -> map.put(s.getId(), s.getName()));
        }
        return map;
    }
}

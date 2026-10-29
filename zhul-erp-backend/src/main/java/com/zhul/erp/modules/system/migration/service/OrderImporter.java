package com.zhul.erp.modules.system.migration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.entity.LogisticsShipmentDO;
import com.zhul.erp.modules.logistics.repository.LogisticsShipmentMapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderLogDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderLogMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.ExchangeRateService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptItemDO;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentDO;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentItemDO;
import com.zhul.erp.modules.warehouse.repository.PurchaseReceiptItemMapper;
import com.zhul.erp.modules.warehouse.repository.PurchaseReceiptMapper;
import com.zhul.erp.modules.warehouse.repository.SupplierShipmentItemMapper;
import com.zhul.erp.modules.warehouse.repository.SupplierShipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.zhul.erp.modules.system.migration.service.MigrationRun.cut;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.date;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.integer;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.money;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.text;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.textOrNull;
import static com.zhul.erp.modules.system.migration.service.MigrationRun.time;

/** 订单与下游阶段：销售订单与型号行（关联配对上的询盘行）、采购单与采购行、供应商发货、出运单、采购入库单 */
@Component
@RequiredArgsConstructor
public class OrderImporter {

    static final String STAGE = "订单与下游";

    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final PurchaseOrderMapper poMapper;
    private final PurchaseOrderItemMapper poItemMapper;
    private final PurchaseOrderLogMapper logMapper;
    private final SupplierShipmentMapper shipmentMapper;
    private final SupplierShipmentItemMapper shipmentItemMapper;
    private final LogisticsShipmentMapper logisticsMapper;
    private final PurchaseReceiptMapper receiptMapper;
    private final PurchaseReceiptItemMapper receiptItemMapper;
    private final ExchangeRateService exchangeRates;

    void run(MigrationRun r, Integer businessUnit) {
        for (JsonNode o : r.list("orders")) {
            order(r, o, businessUnit);
        }
        for (JsonNode p : r.list("purchases")) {
            purchase(r, p);
        }
        for (JsonNode s : r.list("shipments")) {
            shipment(r, s);
        }
        for (JsonNode p : r.list("purchases")) {
            receipt(r, p);
        }
    }

    /** 切换日之前的采购单一律视为已全部入库：每张生成一张足量的采购入库单（发货进度据此算为已入库），入库日期取最晚的发货日期，没有发货时取下单日期 */
    private void receipt(MigrationRun r, JsonNode p) {
        String key = text(p, "key");
        Long poId = r.mapped("purchases", key, "purchase_order");
        if (poId == null) {
            return;
        }
        if (r.mapped("receipts", key, "purchase_receipt") != null) {
            r.skipped(STAGE, "采购入库单");
            return;
        }
        PurchaseOrderDO po = poMapper.selectById(poId);
        SupplierShipmentDO last = shipmentMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SupplierShipmentDO>()
                        .eq(SupplierShipmentDO::getPoId, poId).isNull(SupplierShipmentDO::getDeletedAt))
                .stream().max(Comparator.comparing(SupplierShipmentDO::getShipDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(SupplierShipmentDO::getId)).orElse(null);
        Map<Long, Long> shipmentItems = last == null ? Map.of()
                : shipmentItemMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SupplierShipmentItemDO>()
                        .eq(SupplierShipmentItemDO::getShipmentId, last.getId()).isNull(SupplierShipmentItemDO::getDeletedAt))
                .stream().collect(Collectors.toMap(SupplierShipmentItemDO::getPoItemId, SupplierShipmentItemDO::getId, (a, b) -> a));
        LocalDate receivedDate = last != null && last.getShipDate() != null ? last.getShipDate() : po.getOrderDate();
        PurchaseReceiptDO d = new PurchaseReceiptDO();
        d.setTenantId(r.tenantId);
        d.setGrNo(r.nextNo(DocumentType.GR, receivedDate));
        d.setShipmentId(last == null ? 0L : last.getId());
        d.setPoId(poId);
        d.setReceivedDate(receivedDate);
        d.setReceivedBy(0L);
        d.setStatus(WarehouseConstants.GR_VALID);
        d.setReverseReason("");
        d.setNote("历史迁移：切换日之前的采购单视为已全部入库");
        d.setCreateBy("migration");
        receiptMapper.insert(d);
        r.record("receipts", key, "purchase_receipt", d.getId());
        r.created(STAGE, "采购入库单");
        List<PurchaseOrderItemDO> lines = poItemMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PurchaseOrderItemDO>()
                .eq(PurchaseOrderItemDO::getPoId, poId).isNull(PurchaseOrderItemDO::getDeletedAt));
        for (PurchaseOrderItemDO pi : lines) {
            PurchaseReceiptItemDO i = new PurchaseReceiptItemDO();
            i.setTenantId(r.tenantId);
            i.setReceiptId(d.getId());
            i.setShipmentItemId(shipmentItems.getOrDefault(pi.getId(), 0L));
            i.setPoItemId(pi.getId());
            i.setRequirementId(0L);
            i.setSoItemId(pi.getSoItemId());
            i.setModel(pi.getModel());
            i.setBrand(pi.getBrand());
            i.setCategory("");
            i.setShippedQty(pi.getQuantity());
            i.setReceivedQty(pi.getQuantity());
            i.setQualifiedQty(pi.getQuantity());
            i.setDefectiveQty(0);
            i.setNote("");
            i.setCreateBy("migration");
            receiptItemMapper.insert(i);
            r.record("receiptLines", key + "#" + pi.getId(), "purchase_receipt_item", i.getId());
        }
    }

    private BigDecimal systemRate(String currency) {
        ExchangeRateService.Snapshot s = exchangeRates.find(currency);
        if (s == null) {
            throw new BizException("目标租户没有设置 " + currency + " 汇率，请先在汇率管理里设置");
        }
        return s.rate();
    }

    private void order(MigrationRun r, JsonNode o, Integer bu) {
        String key = text(o, "key");
        if (r.mapped("orders", key, "sales_order") != null) {
            r.skipped(STAGE, "销售订单");
            return;
        }
        Long customerId = r.mapped("customers", text(o, "customerKey"), "customer");
        if (customerId == null) {
            r.failed(STAGE, "销售订单（客户没迁移）");
            return;
        }
        String currency = text(o, "currency");
        BigDecimal rate = textOrNull(o, "exchangeRate") == null ? systemRate(currency) : new BigDecimal(text(o, "exchangeRate"));
        BigDecimal total = money(o, "totalAmount");
        LocalDate salesDate = date(o, "salesDate");
        LocalDateTime created = time(o, "createTime");
        String sourceProgress = textOrNull(o, "progress") == null ? SalesConstants.PROGRESS_COMPLETED : text(o, "progress");
        // 切换日之前的单据一律视为已完结：进度记为已完成、收款记为已结清（到账金额仍按实际），旧状态写进备注
        String progress = SalesConstants.PROGRESS_COMPLETED;
        BigDecimal received = BigDecimal.ZERO;
        for (JsonNode rc : o.path("receipts")) {
            received = received.add(money(rc, "amount"));
        }

        SalesOrderDO d = new SalesOrderDO();
        d.setTenantId(r.tenantId);
        d.setBusinessUnitId(bu);
        d.setSoNo(r.nextNo(DocumentType.SO, salesDate));
        d.setSource(SalesConstants.SO_MANUAL);
        d.setSalesDate(salesDate);
        d.setPiId(0L);
        d.setPiVersionNo(0);
        d.setCustomerId(customerId);
        d.setCustomerType(integer(o, "customerType", 1));
        d.setStockType(integer(o, "stockType", SalesConstants.STOCK_SPOT));
        Long owner = r.user(text(o, "owner"));
        d.setOwnerId(owner == null ? 0L : owner);
        d.setCurrencyCode(currency);
        d.setExchangeRate(rate);
        d.setItemAmount(total);
        d.setFeeAmount(BigDecimal.ZERO);
        d.setDiscountAmount(BigDecimal.ZERO);
        d.setDiscountAmountCny(BigDecimal.ZERO);
        d.setTotalAmount(total);
        d.setTotalAmountCny(total.multiply(rate).setScale(2, RoundingMode.HALF_UP));
        d.setStatus(SalesConstants.SO_ACTIVE);
        d.setProgressCode(progress);
        d.setCompletedAt(created);
        d.setReceiptStatus(SalesConstants.RECEIPT_PAID);
        d.setReceivedAmount(received);
        d.setFeeDiffAmount(BigDecimal.ZERO);
        String paymentMethod = text(o, "paymentMethod");
        d.setPaymentTerm(paymentMethod.isEmpty() ? "" : cut("付款方式：" + paymentMethod, 200));
        String remark = text(o, "remark");
        if (!SalesConstants.PROGRESS_COMPLETED.equals(sourceProgress) || "已退款".equals(text(o, "rawStatus"))) {
            remark = (remark.isEmpty() ? "" : remark + "；") + "旧系统状态：" + text(o, "rawStatus");
        }
        d.setRemark(cut(remark, 500));
        d.setMigrated(1);
        d.setCreateTime(created);
        d.setCreateBy(r.username(text(o, "createdBy")));
        orderMapper.insert(d);
        r.record("orders", key, "sales_order", d.getId());
        r.created(STAGE, "销售订单");

        Long purchaser = r.user(text(o, "purchaser"));
        for (JsonNode l : o.path("lines")) {
            SalesOrderItemDO i = new SalesOrderItemDO();
            i.setTenantId(r.tenantId);
            i.setSoId(d.getId());
            i.setLineNo(l.path("lineNo").asInt());
            i.setPiItemId(0L);
            i.setQuotationId(0L);
            i.setQuotationItemId(0L);
            JsonNode pair = l.path("inquiryLine");
            Long inquiryId = null;
            Long inquiryItemId = null;
            if (pair.isObject()) {
                inquiryId = r.mapped("inquiries", text(pair, "inquiryKey"), "customer_inquiry");
                inquiryItemId = r.mapped("inquiryLines", text(pair, "inquiryKey") + "#" + pair.path("lineNo").asInt(), "inquiry_item");
            }
            i.setCustomerInquiryId(inquiryId == null ? 0L : inquiryId);
            i.setInquiryItemId(inquiryItemId == null ? 0L : inquiryItemId);
            i.setModel(cut(text(l, "model"), 128));
            i.setBrand(cut(text(l, "brand"), 64));
            i.setDescription(cut(text(l, "description"), 300));
            i.setStockType(d.getStockType());
            i.setProgressCode(progress);
            i.setPurchaserId(purchaser);
            int qty = Math.max(1, l.path("quantity").asInt(1));
            i.setQuantity(qty);
            i.setUnitPrice(money(l, "unitPrice"));
            i.setAmount(money(l, "amount"));
            i.setAmountCny(i.getAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP));
            i.setCreateTime(created);
            i.setCreateBy(d.getCreateBy());
            orderItemMapper.insert(i);
            r.record("orderLines", key + "#" + i.getLineNo(), "sales_order_item", i.getId());
        }
    }

    private void purchase(MigrationRun r, JsonNode p) {
        String key = text(p, "key");
        if (r.mapped("purchases", key, "purchase_order") != null) {
            r.skipped(STAGE, "采购单");
            return;
        }
        String soKey = text(p, "soKey");
        Long soId = r.mapped("orders", soKey, "sales_order");
        if (soId == null) {
            r.failed(STAGE, "采购单（订单没迁移）");
            return;
        }
        LocalDateTime created = time(p, "createTime");
        LocalDate orderDate = date(p, "orderDate");
        if (orderDate == null) {
            orderDate = created == null ? LocalDate.now() : created.toLocalDate();
        }
        BigDecimal total = money(p, "totalAmount");
        PurchaseOrderDO d = new PurchaseOrderDO();
        d.setTenantId(r.tenantId);
        d.setPoNo(r.nextNo(DocumentType.PO, orderDate));
        String supplierKey = text(p, "supplierKey");
        Long supplierId = supplierKey.isEmpty() ? null : r.mapped("suppliers", supplierKey, "supplier");
        d.setSupplierId(supplierId == null ? 0L : supplierId);
        d.setChannel(supplierId == null ? integer(p, "channel", 5) : 4);
        d.setShopName(supplierId == null ? cut(text(p, "shopName"), 100) : "");
        Long purchaser = r.user(text(p, "purchaser"));
        d.setPurchaserId(purchaser == null ? 0L : purchaser);
        d.setStatus(PurchaseConstants.PO_ORDERED);
        d.setOrderDate(orderDate);
        d.setOrderedAt(created);
        d.setExpectedShipDate(date(p, "expectedShipDate"));
        d.setCurrencyCode("CNY");
        d.setExchangeRate(BigDecimal.ONE);
        d.setItemAmount(total);
        d.setFeeAmount(BigDecimal.ZERO);
        d.setTotalAmount(total);
        d.setTotalAmountCny(total);
        BigDecimal paid = money(p, "paidAmount");
        String remark = text(p, "remark");
        if (paid != null && paid.signum() > 0) {
            remark = (remark.isEmpty() ? "" : remark + "；") + "旧系统已付 ¥" + paid.toPlainString();
        }
        d.setContractNo("");
        d.setCancelReason("");
        d.setMigrated(1);
        d.setCreateTime(created);
        d.setCreateBy(r.username(text(p, "createdBy")));
        poMapper.insert(d);
        // 采购单没有备注字段：旧系统的备注与已付金额记一条操作日志
        PurchaseOrderLogDO log = new PurchaseOrderLogDO();
        log.setTenantId(r.tenantId);
        log.setPoId(d.getId());
        log.setAction("历史迁移");
        log.setContent(cut(remark.isEmpty() ? "从旧系统迁移" : remark, 1000));
        log.setCreateTime(created);
        log.setCreateBy("migration");
        logMapper.insert(log);
        r.record("purchases", key, "purchase_order", d.getId());
        r.created(STAGE, "采购单");

        int sort = 0;
        for (JsonNode l : p.path("lines")) {
            PurchaseOrderItemDO i = new PurchaseOrderItemDO();
            i.setTenantId(r.tenantId);
            i.setPoId(d.getId());
            i.setRequirementId(0L);
            i.setSoId(soId);
            Long soItemId = r.mapped("orderLines", soKey + "#" + l.path("soLineNo").asInt(), "sales_order_item");
            i.setSoItemId(soItemId == null ? 0L : soItemId);
            i.setModel(cut(text(l, "model"), 128));
            i.setBrand(cut(text(l, "brand"), 64));
            i.setQuantity(Math.max(1, l.path("quantity").asInt(1)));
            i.setUnitPrice(money(l, "unitPrice"));
            i.setNetPriceCny(i.getUnitPrice());
            i.setAmount(money(l, "amount"));
            i.setSortOrder(++sort);
            i.setCreateTime(created);
            i.setCreateBy(d.getCreateBy());
            poItemMapper.insert(i);
            r.record("purchaseLines", key + "#" + sort, "purchase_order_item", i.getId());
        }
    }

    private void shipment(MigrationRun r, JsonNode s) {
        String key = text(s, "key");
        if (r.mapped("shipments", key, "supplier_shipment") != null) {
            r.skipped(STAGE, "供应商发货");
            return;
        }
        String poKey = text(s, "poKey");
        Long poId = r.mapped("purchases", poKey, "purchase_order");
        Long soId = r.mapped("orders", text(s, "soKey"), "sales_order");
        if (poId == null || soId == null) {
            r.failed(STAGE, "供应商发货（采购单没迁移）");
            return;
        }
        LocalDate shipDate = date(s, "shipDate");
        JsonNode track = s.path("supplierTracks").isArray() && !s.path("supplierTracks").isEmpty() ? s.path("supplierTracks").get(0)
                : s.path("domestic").isArray() && !s.path("domestic").isEmpty() ? s.path("domestic").get(0) : null;
        SupplierShipmentDO d = new SupplierShipmentDO();
        d.setTenantId(r.tenantId);
        d.setSdNo(r.nextNo(DocumentType.SD, shipDate == null ? LocalDate.now() : shipDate));
        d.setPoId(poId);
        d.setSource(WarehouseConstants.SHIP_BY_BUYER);
        d.setCarrier(track == null ? "" : cut(text(track, "carrier"), 32));
        d.setTrackingNo(track == null ? "" : cut(text(track, "trackingNo"), 64));
        d.setShipDate(shipDate);
        d.setStatus(WarehouseConstants.SHIP_RECEIVED);
        d.setNote(cut(text(s, "note"), 300));
        d.setMigrated(1);
        d.setCreateBy("migration");
        shipmentMapper.insert(d);
        r.record("shipments", key, "supplier_shipment", d.getId());
        r.created(STAGE, "供应商发货");

        PurchaseOrderDO po = poMapper.selectById(poId);
        for (PurchaseOrderItemDO pi : poItemMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PurchaseOrderItemDO>()
                .eq(PurchaseOrderItemDO::getPoId, po.getId()))) {
            SupplierShipmentItemDO i = new SupplierShipmentItemDO();
            i.setTenantId(r.tenantId);
            i.setShipmentId(d.getId());
            i.setPoItemId(pi.getId());
            i.setRequirementId(0L);
            i.setSoItemId(pi.getSoItemId());
            i.setModel(pi.getModel());
            i.setBrand(pi.getBrand());
            i.setQuantity(pi.getQuantity());
            shipmentItemMapper.insert(i);
        }

        JsonNode intl = s.path("international");
        if (intl.isObject() && (!text(intl, "waybillNo").isEmpty() || textOrNull(intl, "freight") != null)) {
            SalesOrderDO so = orderMapper.selectById(soId);
            LogisticsShipmentDO l = new LogisticsShipmentDO();
            l.setTenantId(r.tenantId);
            l.setShNo(r.nextNo(DocumentType.SH, shipDate == null ? LocalDate.now() : shipDate));
            l.setCustomerId(so.getCustomerId());
            l.setForwarderId(r.mapped("suppliers", MasterDataImporter.FORWARDER_KEY, "supplier"));
            l.setOwnerId(so.getOwnerId());
            l.setStatus(LogisticsConstants.SH_SHIPPED);
            l.setCarrier(cut(text(intl, "carrier"), 32));
            l.setWaybillNo(cut(text(intl, "waybillNo"), 64));
            l.setShippedDate(shipDate);
            l.setFreight(money(intl, "freight"));
            l.setReconciled(1);
            l.setNote(cut(text(intl, "raw"), 300));
            l.setMigrated(1);
            l.setCreateBy("migration");
            logisticsMapper.insert(l);
            r.record("shipmentsIntl", key, "logistics_shipment", l.getId());
            r.created(STAGE, "出运单");
        }
    }
}

package com.zhul.erp.modules.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import com.zhul.erp.modules.quotation.repository.QuotationItemMapper;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import com.zhul.erp.modules.quotation.support.QuotationDeals;
import com.zhul.erp.modules.quotation.support.QuotationRenderModels;
import com.zhul.erp.modules.quotation.support.QuotationStore;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.ChainVO;
import com.zhul.erp.modules.sales.dto.PartyDTO;
import com.zhul.erp.modules.sales.dto.PiFeeVO;
import com.zhul.erp.modules.sales.dto.PiItemVO;
import com.zhul.erp.modules.sales.dto.SalesOrderListVO;
import com.zhul.erp.modules.sales.dto.SalesOrderPageQuery;
import com.zhul.erp.modules.sales.dto.SalesOrderVO;
import com.zhul.erp.modules.sales.entity.PiFeeDO;
import com.zhul.erp.modules.sales.entity.PiItemDO;
import com.zhul.erp.modules.sales.entity.PiVersionDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderFeeDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.PiItemMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderFeeMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.service.PiService;
import com.zhul.erp.modules.sales.service.SalesOrderService;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.sales.support.OrderProgress;
import com.zhul.erp.modules.sales.dto.ConvertOrderRequest;
import com.zhul.erp.modules.sales.dto.CreateOrderRequest;
import com.zhul.erp.modules.sales.dto.OrderItemsRequest;
import com.zhul.erp.modules.sales.dto.ReceiptVO;
import com.zhul.erp.modules.sales.dto.SalesOrderItemVO;
import com.zhul.erp.modules.sales.dto.SalesOrderStatsVO;
import com.zhul.erp.modules.sales.dto.OrderCandidatePiVO;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesOrderServiceImpl implements SalesOrderService {

    private static final int KEYWORD_LIMIT = 500;
    private static final Map<Integer, String> SO_STATUS_NAMES = Map.of(SalesConstants.SO_ACTIVE, "有效", SalesConstants.SO_CANCELLED, "已取消");
    private static final Map<Integer, String> INQUIRY_STATUS_NAMES = Map.of(1, "待解析", 2, "解析中", 3, "待确认", 4, "解析失败",
            5, "询价中", 6, "可报价", 7, "已报价", 8, "已成交", 9, "未成交", 10, "已取消");

    private final com.zhul.erp.modules.product.candidate.service.ProductArchiver productArchiver;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final SalesOrderFeeMapper orderFeeMapper;
    private final ProformaInvoiceMapper piMapper;
    private final PiItemMapper piItemMapper;
    private final QuotationMapper quotationMapper;
    private final QuotationItemMapper quotationItemMapper;
    private final CustomerInquiryMapper inquiryMapper;
    private final CustomerMapper customerMapper;
    private final PiStore piStore;
    private final PiService piService;
    private final QuotationStore quotationStore;
    private final QuotationDeals deals;
    private final DocumentNumberService documentNumberService;
    private final DataScopeResolver dataScopeResolver;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;
    private final com.zhul.erp.modules.sales.repository.PaymentReceiptMapper receiptMapper;
    private final com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper sourcingQuoteMapper;
    private final com.zhul.erp.modules.system.repository.UserBasicMapper userBasicMapper;
    private final com.zhul.erp.modules.system.service.ExchangeRateService exchangeRateService;
    private final com.zhul.erp.modules.sales.support.PiDefaults piDefaults;
    private final com.zhul.erp.modules.sales.support.ReceiptViews receiptViews;
    private final OrderProgress progress;
    private final com.zhul.erp.modules.purchase.support.RequirementLifecycle requirementLifecycle;
    private final com.zhul.erp.modules.purchase.support.PurchaseLinks purchaseLinks;

    // ---------------------------------------------------------------- 转订单与取消

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO convert(Long piId, ConvertOrderRequest req) {
        ProformaInvoiceDO pi = piStore.lockVisible(piId);
        SalesOrderDO existing = piStore.activeOrder(piId);
        if (existing != null) {
            throw new BizException("PI 已转成订单 " + existing.getSoNo());
        }
        if (pi.getStatus() != SalesConstants.PI_SENT) {
            throw new BizException(pi.getStatus() == SalesConstants.PI_VOID ? "PI 已作废，不能转成订单"
                    : pi.getStatus() == SalesConstants.PI_CLOSED ? SalesConstants.CLOSED_MESSAGE : "PI 还没有发送，不能转成订单");
        }
        if (pi.getEditingVersionNo() != null) {
            throw new BizException("PI 有未发送的新版本 Rev." + pi.getEditingVersionNo() + "，请先发送或放弃后再转成订单");
        }
        if (pi.getReceiptStatus() == SalesConstants.RECEIPT_NONE) {
            throw new BizException("请先上传客户的付款水单，或等财务登记到账");
        }
        PiVersionDO v = piStore.version(piId, pi.getCurrentVersionNo());
        List<PiItemDO> items = piStore.items(v.getId());
        List<PiFeeDO> fees = piStore.fees(v.getId());
        LocalDate salesDate = req == null || req.getSalesDate() == null ? defaultSalesDate(piId) : req.getSalesDate();
        requirePastOrToday(salesDate);
        Map<Long, Long> purchasers = purchasersOf(items.stream().map(PiItemDO::getQuotationItemId).toList());
        Integer customerType = lookups.customerTypes(Map.of(0L, items.stream().map(PiItemDO::getCustomerInquiryId).toList())).get(0L);

        SalesOrderDO o = new SalesOrderDO();
        o.setTenantId(pi.getTenantId());
        o.setSoNo(documentNumberService.next(DocumentType.SO));
        o.setSource(SalesConstants.SO_FROM_PI);
        o.setSalesDate(salesDate);
        o.setCustomerType(customerType == null ? InquiryConstants.CUSTOMER_NEW : customerType);
        o.setStockType(items.stream().anyMatch(i -> stockOf(i.getLeadTime()) == SalesConstants.STOCK_FUTURES)
                ? SalesConstants.STOCK_FUTURES : SalesConstants.STOCK_SPOT);
        o.setProgressCode(SalesConstants.PROGRESS_PENDING);
        o.setReceiptStatus(SalesConstants.RECEIPT_NONE);
        o.setReceivedAmount(BigDecimal.ZERO);
        o.setFeeDiffAmount(BigDecimal.ZERO);
        o.setPiId(piId);
        o.setPiVersionNo(v.getVersionNo());
        o.setCustomerId(pi.getCustomerId());
        o.setOwnerId(pi.getOwnerId());
        o.setCurrencyCode(pi.getCurrencyCode());
        o.setExchangeRate(pi.getExchangeRate());
        o.setBuyerJson(v.getBuyerJson());
        o.setConsigneeJson(v.getConsigneeJson());
        o.setDeliveryTime(v.getDeliveryTime());
        o.setPaymentTerm(v.getPaymentTerm());
        o.setIncoterm(v.getIncoterm());
        o.setIncotermPlace(v.getIncotermPlace());
        o.setPortOfShipment(v.getPortOfShipment());
        o.setRemark(v.getRemark());
        o.setDiscountAmount(v.getDiscountAmount());
        o.setDiscountAmountCny(v.getDiscountAmountCny());
        o.setItemAmount(v.getItemAmount());
        o.setFeeAmount(v.getFeeAmount());
        o.setTotalAmount(v.getTotalAmount());
        o.setTotalAmountCny(v.getTotalAmountCny());
        o.setNetProfit(v.getNetProfit());
        o.setNetProfitCny(v.getNetProfitCny());
        o.setMarginRate(v.getMarginRate());
        o.setStatus(SalesConstants.SO_ACTIVE);
        o.setCancelReason("");
        orderMapper.insert(o);
        List<SalesOrderItemDO> created = new ArrayList<>(items.size());
        for (PiItemDO i : items) {
            SalesOrderItemDO x = toOrderItem(o, i);
            x.setStockType(stockOf(i.getLeadTime()));
            x.setProgressCode(SalesConstants.PROGRESS_PENDING);
            x.setPurchaserId(purchasers.get(i.getQuotationItemId()));
            orderItemMapper.insert(x);
            created.add(x);
        }
        int sort = 1;
        for (PiFeeDO f : fees) {
            SalesOrderFeeDO x = new SalesOrderFeeDO();
            x.setTenantId(o.getTenantId());
            x.setSoId(o.getId());
            x.setFeeName(f.getFeeName());
            x.setAmount(f.getAmount());
            x.setAmountCny(f.getAmountCny());
            x.setRemark(f.getRemark());
            x.setSortOrder(sort++);
            orderFeeMapper.insert(x);
        }
        pi.setStatus(SalesConstants.PI_CONVERTED);
        piMapper.updateById(pi);
        // 同一事务：报价行成交 → 报价单状态 → 客户询盘（锁顺序：PI → 报价单 → 询盘）
        deals.apply(items.stream().map(PiItemDO::getQuotationItemId).collect(Collectors.toSet()), Set.of());
        // 同一事务：生成采购需求（重转时接回已下单数量）并自动排入草稿采购单
        requirementLifecycle.onOrderCreated(o, created);
        // 成交提升商品候选的可信度
        productArchiver.sync(created.stream().map(SalesOrderItemDO::getInquiryItemId).filter(Objects::nonNull).toList());

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("soNo", o.getSoNo());
        after.put("piNo", pi.getPiNo());
        after.put("version", "Rev." + v.getVersionNo());
        after.put("salesDate", salesDate.toString());
        after.put("total", o.getCurrencyCode() + " " + o.getTotalAmount());
        after.put("receiptStatus", SalesConstants.RECEIPT_STATUS_NAMES.get(pi.getReceiptStatus()));
        logService.recordOperateLog(SalesConstants.MENU_SO, "PI 转成销售订单", null, after);
        return detail(o.getId());
    }

    /** 销售日期默认值：这张 PI 最早一笔有效水单的付款日期或有效到账的到账日期，都没有时取当天 */
    private LocalDate defaultSalesDate(Long piId) {
        return receiptMapper.selectList(new LambdaQueryWrapper<PaymentReceiptDO>()
                        .select(PaymentReceiptDO::getReceiptDate)
                        .eq(PaymentReceiptDO::getPiId, piId)
                        .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                        .isNull(PaymentReceiptDO::getDeletedAt))
                .stream().map(PaymentReceiptDO::getReceiptDate).filter(Objects::nonNull).min(Comparator.naturalOrder())
                .orElse(LocalDate.now());
    }

    private static void requirePastOrToday(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            throw new BizException("销售日期不能晚于今天");
        }
    }

    /** 货期为「现货」的为现货，其余为期货 */
    private static int stockOf(Integer leadTime) {
        return Objects.equals(leadTime, SalesConstants.LEAD_TIME_SPOT) ? SalesConstants.STOCK_SPOT : SalesConstants.STOCK_FUTURES;
    }

    /** 报价行 → 采购员：被选为采购成本价的那条回价的询价人；手填成本价的没有 */
    private Map<Long, Long> purchasersOf(Collection<Long> quotationItemIds) {
        List<Long> ids = quotationItemIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> quoteOf = new HashMap<>(ids.size() * 2);
        quotationItemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                        .select(QuotationItemDO::getId, QuotationItemDO::getCostQuoteId)
                        .in(QuotationItemDO::getId, ids)
                        .isNotNull(QuotationItemDO::getCostQuoteId))
                .forEach(q -> quoteOf.put(q.getId(), q.getCostQuoteId()));
        if (quoteOf.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> quotedBy = new HashMap<>(quoteOf.size() * 2);
        sourcingQuoteMapper.selectBatchIds(new HashSet<>(quoteOf.values()))
                .forEach(q -> quotedBy.put(q.getId(), q.getQuotedBy()));
        Map<Long, Long> result = new HashMap<>(quoteOf.size() * 2);
        quoteOf.forEach((item, quote) -> {
            Long user = quotedBy.get(quote);
            if (user != null && user > 0) {
                result.put(item, user);
            }
        });
        return result;
    }

    // ---------------------------------------------------------------- 手动创建

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO create(CreateOrderRequest req) {
        int tenant = PiStore.tenantId();
        CustomerDO customer = customerMapper.selectById(req.getCustomerId());
        if (customer == null || customer.getDeletedAt() != null || !Objects.equals(customer.getTenantId(), tenant)) {
            throw new BizException("客户不存在");
        }
        List<CreateOrderRequest.Line> lines = req.getItems() == null ? List.of()
                : req.getItems().stream().filter(l -> l != null && StringUtils.hasText(l.getModel())).toList();
        if (lines.isEmpty()) {
            throw new BizException("请至少添加一个型号");
        }
        String currency = StringUtils.hasText(req.getCurrencyCode()) ? req.getCurrencyCode().trim().toUpperCase(Locale.ROOT) : "USD";
        BigDecimal rate = exchangeRateService.require(currency).rate();
        LocalDate salesDate = req.getSalesDate() == null ? LocalDate.now() : req.getSalesDate();
        requirePastOrToday(salesDate);
        Set<Long> purchaserIds = lines.stream().map(CreateOrderRequest.Line::getPurchaserId).filter(Objects::nonNull).collect(Collectors.toSet());
        requireUsers(purchaserIds);
        for (int n = 0; n < lines.size(); n++) {
            CreateOrderRequest.Line l = lines.get(n);
            if (l.getQuantity() == null || l.getQuantity() <= 0) {
                throw new BizException("第 " + (n + 1) + " 个型号的数量需要大于 0");
            }
            if (l.getUnitPrice() == null || l.getUnitPrice().signum() < 0) {
                throw new BizException("请填写第 " + (n + 1) + " 个型号的单价");
            }
            if (l.getCostPrice() != null && l.getCostPrice().signum() < 0) {
                throw new BizException("第 " + (n + 1) + " 个型号的采购成本价不能为负数");
            }
            if (l.getStockType() != null && !SalesConstants.STOCK_NAMES.containsKey(l.getStockType())) {
                throw new BizException("现货 / 期货不正确");
            }
        }
        boolean returning = orderMapper.selectCount(new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getTenantId, tenant)
                .eq(SalesOrderDO::getCustomerId, customer.getId())
                .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                .isNull(SalesOrderDO::getDeletedAt)) > 0;

        SalesOrderDO o = new SalesOrderDO();
        o.setTenantId(tenant);
        o.setSoNo(documentNumberService.next(DocumentType.SO));
        o.setSource(SalesConstants.SO_MANUAL);
        o.setSalesDate(salesDate);
        o.setPiId(0L);
        o.setPiVersionNo(0);
        o.setCustomerId(customer.getId());
        o.setCustomerType(returning ? InquiryConstants.CUSTOMER_RETURNING : InquiryConstants.CUSTOMER_NEW);
        o.setOwnerId(currentUserId());
        o.setCurrencyCode(currency);
        o.setExchangeRate(rate);
        o.setBuyerJson(piStore.toJson(piDefaults.buyer(customer)));
        o.setDeliveryTime("");
        o.setPaymentTerm("");
        o.setIncoterm("");
        o.setIncotermPlace("");
        o.setPortOfShipment("");
        o.setRemark(req.getRemark() == null ? "" : req.getRemark().trim());
        o.setDiscountAmount(BigDecimal.ZERO);
        o.setDiscountAmountCny(BigDecimal.ZERO);
        o.setFeeAmount(BigDecimal.ZERO);
        o.setStatus(SalesConstants.SO_ACTIVE);
        o.setProgressCode(SalesConstants.PROGRESS_PENDING);
        o.setReceiptStatus(SalesConstants.RECEIPT_NONE);
        o.setReceivedAmount(BigDecimal.ZERO);
        o.setFeeDiffAmount(BigDecimal.ZERO);
        o.setCancelReason("");
        BigDecimal total = BigDecimal.ZERO;
        List<SalesOrderItemDO> rows = new ArrayList<>(lines.size());
        int lineNo = 1;
        for (CreateOrderRequest.Line l : lines) {
            SalesOrderItemDO x = new SalesOrderItemDO();
            x.setTenantId(tenant);
            x.setLineNo(lineNo++);
            x.setPiItemId(0L);
            x.setQuotationId(0L);
            x.setQuotationItemId(0L);
            x.setCustomerInquiryId(0L);
            x.setInquiryItemId(0L);
            x.setModel(l.getModel().trim());
            x.setBrand(l.getBrand() == null ? "" : l.getBrand().trim());
            x.setCategory("");
            x.setDescription("");
            x.setDescriptionEn("");
            x.setItemCondition(0);
            x.setLeadTime(0);
            x.setWarranty("");
            x.setQuantity(l.getQuantity());
            x.setUnitPrice(l.getUnitPrice().setScale(2, RoundingMode.HALF_UP));
            x.setAmount(x.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())).setScale(2, RoundingMode.HALF_UP));
            x.setAmountCny(x.getAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP));
            x.setCostPrice(l.getCostPrice() == null ? null : l.getCostPrice().setScale(2, RoundingMode.HALF_UP));
            x.setHsCode("");
            x.setOriginCountry("");
            x.setRemark("");
            x.setStockType(l.getStockType() == null ? SalesConstants.STOCK_SPOT : l.getStockType());
            x.setProgressCode(SalesConstants.PROGRESS_PENDING);
            x.setPurchaserId(l.getPurchaserId());
            total = total.add(x.getAmount());
            rows.add(x);
        }
        o.setItemAmount(total);
        o.setTotalAmount(total);
        o.setTotalAmountCny(total.multiply(rate).setScale(2, RoundingMode.HALF_UP));
        o.setStockType(rows.stream().anyMatch(x -> x.getStockType() == SalesConstants.STOCK_FUTURES)
                ? SalesConstants.STOCK_FUTURES : SalesConstants.STOCK_SPOT);
        orderMapper.insert(o);
        for (SalesOrderItemDO x : rows) {
            x.setSoId(o.getId());
            orderItemMapper.insert(x);
        }
        requirementLifecycle.onOrderCreated(o, rows);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("soNo", o.getSoNo());
        after.put("customer", InquiryLookups.customerName(customer));
        after.put("salesDate", salesDate.toString());
        after.put("total", currency + " " + total);
        after.put("items", rows.size());
        logService.recordOperateLog(SalesConstants.MENU_SO, "手动创建销售订单", null, after);
        return detail(o.getId());
    }

    private void requireUsers(Set<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        long found = userBasicMapper.selectCount(new LambdaQueryWrapper<UserBasicDO>()
                .in(UserBasicDO::getId, ids.stream().map(Long::intValue).toList())
                .eq(UserBasicDO::getTenantId, PiStore.tenantId()));
        if (found < ids.size()) {
            throw new BizException("采购员不存在");
        }
    }

    private static SalesOrderItemDO toOrderItem(SalesOrderDO o, PiItemDO i) {
        SalesOrderItemDO x = new SalesOrderItemDO();
        x.setTenantId(o.getTenantId());
        x.setSoId(o.getId());
        x.setLineNo(i.getLineNo());
        x.setPiItemId(i.getId());
        x.setQuotationId(i.getQuotationId());
        x.setQuotationItemId(i.getQuotationItemId());
        x.setCustomerInquiryId(i.getCustomerInquiryId());
        x.setInquiryItemId(i.getInquiryItemId());
        x.setModel(i.getModel());
        x.setBrand(i.getBrand());
        x.setCategory(i.getCategory());
        x.setDescription(i.getDescription());
        x.setDescriptionEn(i.getDescriptionEn());
        x.setItemCondition(i.getItemCondition());
        x.setLeadTime(i.getLeadTime());
        x.setWarranty(i.getWarranty());
        x.setQuantity(i.getQuantity());
        x.setUnitPrice(i.getUnitPrice());
        x.setAmount(i.getAmount());
        x.setAmountCny(i.getAmountCny());
        x.setCostPrice(i.getCostPrice());
        x.setMarginRate(i.getMarginRate());
        x.setNetProfit(i.getNetProfit());
        x.setNetProfitCny(i.getNetProfitCny());
        x.setHsCode(i.getHsCode());
        x.setOriginCountry(i.getOriginCountry());
        x.setRemark(i.getRemark());
        return x;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO cancel(Long id, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw new BizException("请填写取消原因");
        }
        SalesOrderDO o = visible(id);
        boolean manual = Objects.equals(o.getSource(), SalesConstants.SO_MANUAL);
        ProformaInvoiceDO pi = manual ? null : piStore.lockVisible(o.getPiId());
        o = lockOrder(id);
        if (o.getStatus() != SalesConstants.SO_ACTIVE) {
            throw new BizException("订单已取消");
        }
        if (SalesConstants.PROGRESS_COMPLETED.equals(o.getProgressCode())) {
            throw new BizException("订单已完成，不能取消");
        }
        o.setStatus(SalesConstants.SO_CANCELLED);
        o.setCancelReason(why.length() > 200 ? why.substring(0, 200) : why);
        o.setCancelledBy(currentUserId());
        o.setCancelledAt(LocalDateTime.now());
        orderMapper.updateById(o);
        // 订单取消：商品候选去掉这张订单的成交来源
        productArchiver.sync(orderItems(id).stream().map(SalesOrderItemDO::getInquiryItemId).filter(Objects::nonNull).toList());
        // 没下单的采购需求关闭，已下单的标「订单已取消」交给采购员
        requirementLifecycle.onOrderCancelled(o, currentUserId());
        if (manual) {
            logService.recordOperateLog(SalesConstants.MENU_SO, "取消销售订单", Map.of("soNo", o.getSoNo(), "status", "有效"),
                    Map.of("soNo", o.getSoNo(), "status", "已取消", "reason", o.getCancelReason(),
                            "received", o.getCurrencyCode() + " " + o.getReceivedAmount()));
            return detail(id);
        }
        pi.setStatus(SalesConstants.PI_SENT);
        piMapper.updateById(pi);

        // 报价行仍在其他有效订单中的保持成交
        Set<Long> lines = orderItems(id).stream().map(SalesOrderItemDO::getQuotationItemId).collect(Collectors.toSet());
        Set<Long> stillWon = wonElsewhere(lines, id);
        Set<Long> lost = new HashSet<>(lines);
        lost.removeAll(stillWon);
        deals.apply(Set.of(), lost);

        logService.recordOperateLog(SalesConstants.MENU_SO, "取消销售订单", Map.of("soNo", o.getSoNo(), "status", "有效"),
                Map.of("soNo", o.getSoNo(), "status", "已取消", "reason", o.getCancelReason(), "piNo", pi.getPiNo(),
                        "received", pi.getCurrencyCode() + " " + pi.getReceivedAmount()));
        return detail(id);
    }

    private Set<Long> wonElsewhere(Set<Long> quotationItemIds, Long excludeOrderId) {
        if (quotationItemIds.isEmpty()) {
            return Set.of();
        }
        List<SalesOrderItemDO> rows = orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                .select(SalesOrderItemDO::getSoId, SalesOrderItemDO::getQuotationItemId)
                .in(SalesOrderItemDO::getQuotationItemId, quotationItemIds)
                .ne(SalesOrderItemDO::getSoId, excludeOrderId)
                .isNull(SalesOrderItemDO::getDeletedAt));
        if (rows.isEmpty()) {
            return Set.of();
        }
        Set<Long> active = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                        .select(SalesOrderDO::getId)
                        .in(SalesOrderDO::getId, rows.stream().map(SalesOrderItemDO::getSoId).collect(Collectors.toSet()))
                        .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                        .isNull(SalesOrderDO::getDeletedAt))
                .stream().map(SalesOrderDO::getId).collect(Collectors.toSet());
        return rows.stream().filter(r -> active.contains(r.getSoId())).map(SalesOrderItemDO::getQuotationItemId).collect(Collectors.toSet());
    }

    // ---------------------------------------------------------------- 跟单信息（成交内容不可改）

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO updateSalesDate(Long id, LocalDate salesDate) {
        visible(id);
        SalesOrderDO o = lockOrder(id);
        if (o.getStatus() != SalesConstants.SO_ACTIVE) {
            throw new BizException("订单已取消，不能修改");
        }
        requirePastOrToday(salesDate);
        LocalDate before = o.getSalesDate();
        o.setSalesDate(salesDate);
        orderMapper.updateById(o);
        logService.recordOperateLog(SalesConstants.MENU_SO, "修改销售日期",
                Map.of("soNo", o.getSoNo(), "salesDate", before == null ? "" : before.toString()),
                Map.of("soNo", o.getSoNo(), "salesDate", salesDate.toString()));
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO updateProgress(Long id, OrderItemsRequest req) {
        List<OrderProgress.Step> ordered = progress.ordered();
        OrderProgress.Step step = OrderProgress.require(ordered, req.getProgressCode() == null ? "" : req.getProgressCode().trim());
        if (com.zhul.erp.modules.purchase.support.OrderPurchaseProgress.AUTO.contains(step.code())) {
            throw new BizException("「待采购」「已下单」「已入库」「已交货代」「已出运」由采购、入库、出库与出运自动推进，不能手动选择");
        }
        Tracking t = trackable(id, req.getItemIds());
        int orderedRank = OrderProgress.rank(ordered, SalesConstants.PROGRESS_ORDERED);
        int receivedRank = OrderProgress.rank(ordered, SalesConstants.PROGRESS_RECEIVED);
        if (OrderProgress.rank(ordered, step.code()) > Math.min(orderedRank, receivedRank)) {
            Map<Long, com.zhul.erp.modules.purchase.support.PurchaseLinks.ItemPurchase> links =
                    purchaseLinks.forSoItems(t.selected().stream().map(SalesOrderItemDO::getId).toList());
            for (SalesOrderItemDO i : t.selected()) {
                var lp = links.get(i.getId());
                if (lp != null && lp.tracked() && lp.ordered() < i.getQuantity()) {
                    throw new BizException(i.getModel() + " 还有 " + (i.getQuantity() - lp.ordered()) + " 个没有下单");
                }
                boolean afterReceived = OrderProgress.rank(ordered, step.code()) > receivedRank;
                if (lp != null && lp.tracked() && afterReceived && lp.received() < i.getQuantity()) {
                    throw new BizException(i.getModel() + " 还有 " + (i.getQuantity() - lp.received()) + " 个没有入库");
                }
            }
        }
        Map<String, String> names = progress.names();
        List<String> changes = new ArrayList<>();
        for (SalesOrderItemDO i : t.selected()) {
            if (!step.code().equals(i.getProgressCode())) {
                changes.add(i.getModel() + " " + names.getOrDefault(i.getProgressCode(), i.getProgressCode()) + " → " + step.name());
                i.setProgressCode(step.code());
                orderItemMapper.updateById(i);
            }
        }
        String before = names.getOrDefault(t.order().getProgressCode(), t.order().getProgressCode());
        recompute(t.order(), t.all(), ordered);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("soNo", t.order().getSoNo());
        after.put("items", changes);
        after.put("orderStatus", before + " → " + names.getOrDefault(t.order().getProgressCode(), t.order().getProgressCode()));
        if (StringUtils.hasText(req.getNote())) {
            after.put("note", req.getNote().trim());
        }
        logService.recordOperateLog(SalesConstants.MENU_SO, "更新订单进度", null, after);
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO updatePurchaser(Long id, OrderItemsRequest req) {
        Long purchaser = req.getPurchaserId();
        if (purchaser != null) {
            requireUsers(Set.of(purchaser));
        }
        Tracking t = trackable(id, req.getItemIds());
        Map<Long, String> names = lookups.userNames(Stream.concat(t.selected().stream().map(SalesOrderItemDO::getPurchaserId),
                Stream.of(purchaser)).filter(Objects::nonNull).toList());
        List<String> changes = new ArrayList<>();
        for (SalesOrderItemDO i : t.selected()) {
            if (!Objects.equals(i.getPurchaserId(), purchaser)) {
                changes.add(i.getModel() + " 采购员 " + names.getOrDefault(i.getPurchaserId(), "未指定") + " → "
                        + names.getOrDefault(purchaser, "未指定"));
                i.setPurchaserId(purchaser);
                orderItemMapper.updateById(i);
            }
        }
        // 采购员以采购需求为准：改这些型号还没全部下单的需求，草稿行跟着移到新采购员名下
        requirementLifecycle.reassignSoItems(t.selected().stream().map(SalesOrderItemDO::getId).toList(), purchaser, currentUserId());
        logService.recordOperateLog(SalesConstants.MENU_SO, "指定采购员", null, Map.of("soNo", t.order().getSoNo(), "items", changes));
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO updateStockType(Long id, OrderItemsRequest req) {
        Integer type = req.getStockType();
        if (type == null || !SalesConstants.STOCK_NAMES.containsKey(type)) {
            throw new BizException("请选择现货或期货");
        }
        Tracking t = trackable(id, req.getItemIds());
        List<String> changes = new ArrayList<>();
        for (SalesOrderItemDO i : t.selected()) {
            if (!Objects.equals(i.getStockType(), type)) {
                changes.add(i.getModel() + " " + SalesConstants.STOCK_NAMES.get(i.getStockType()) + " → " + SalesConstants.STOCK_NAMES.get(type));
                i.setStockType(type);
                orderItemMapper.updateById(i);
            }
        }
        recompute(t.order(), t.all(), progress.ordered());
        logService.recordOperateLog(SalesConstants.MENU_SO, "修改现货 / 期货", null, Map.of("soNo", t.order().getSoNo(), "items", changes));
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO complete(Long id) {
        visible(id);
        SalesOrderDO o = lockOrder(id);
        requireTrackable(o);
        List<OrderProgress.Step> ordered = progress.ordered();
        OrderProgress.Step last = OrderProgress.last(ordered);
        int lastRank = OrderProgress.rank(ordered, last.code());
        long behind = orderItems(id).stream().filter(i -> OrderProgress.rank(ordered, i.getProgressCode()) < lastRank).count();
        if (behind > 0) {
            throw new BizException("还有 " + behind + " 个型号还没到「" + last.name() + "」，不能确认收货");
        }
        o.setProgressCode(SalesConstants.PROGRESS_COMPLETED);
        o.setCompletedAt(LocalDateTime.now());
        orderMapper.updateById(o);
        logService.recordOperateLog(SalesConstants.MENU_SO, "客户已收货", Map.of("soNo", o.getSoNo(), "status", last.name()),
                Map.of("soNo", o.getSoNo(), "status", progress.names().get(SalesConstants.PROGRESS_COMPLETED)));
        return detail(id);
    }

    private record Tracking(SalesOrderDO order, List<SalesOrderItemDO> all, List<SalesOrderItemDO> selected) {
    }

    /** 加锁取订单与选中的型号：有效且未完成的订单才能改跟单信息 */
    private Tracking trackable(Long id, List<Long> itemIds) {
        visible(id);
        SalesOrderDO o = lockOrder(id);
        requireTrackable(o);
        List<SalesOrderItemDO> all = orderItems(id);
        Set<Long> wanted = new HashSet<>(itemIds);
        List<SalesOrderItemDO> selected = all.stream().filter(i -> wanted.contains(i.getId())).toList();
        if (selected.size() != wanted.size()) {
            throw new BizException("型号不属于这张订单");
        }
        return new Tracking(o, all, selected);
    }

    private static void requireTrackable(SalesOrderDO o) {
        if (o.getStatus() != SalesConstants.SO_ACTIVE) {
            throw new BizException("订单已取消，不能修改");
        }
        if (SalesConstants.PROGRESS_COMPLETED.equals(o.getProgressCode())) {
            throw new BizException("订单已完成，不能修改");
        }
    }

    /** 订单级冗余：进度取最靠前的型号，任一型号期货即期货 */
    private void recompute(SalesOrderDO o, List<SalesOrderItemDO> items, List<OrderProgress.Step> ordered) {
        o.setProgressCode(items.stream().min(Comparator.comparingInt(i -> OrderProgress.rank(ordered, i.getProgressCode())))
                .map(SalesOrderItemDO::getProgressCode).orElse(SalesConstants.PROGRESS_PENDING));
        o.setStockType(items.stream().anyMatch(i -> Objects.equals(i.getStockType(), SalesConstants.STOCK_FUTURES))
                ? SalesConstants.STOCK_FUTURES : SalesConstants.STOCK_SPOT);
        orderMapper.updateById(o);
    }

    private SalesOrderDO lockOrder(Long id) {
        return orderMapper.selectOne(new LambdaQueryWrapper<SalesOrderDO>().eq(SalesOrderDO::getId, id).last("FOR UPDATE"));
    }

    // ---------------------------------------------------------------- 查看

    @Override
    public SalesOrderVO detail(Long id) {
        SalesOrderDO o = visible(id);
        ProformaInvoiceDO pi = piMapper.selectById(o.getPiId());
        CustomerDO customer = customerMapper.selectById(o.getCustomerId());
        Map<Long, String> users = lookups.userNames(List.of(o.getOwnerId(), o.getCancelledBy() == null ? 0L : o.getCancelledBy()));
        SalesOrderVO vo = new SalesOrderVO();
        vo.setId(o.getId());
        vo.setSoNo(o.getSoNo());
        vo.setSource(o.getSource());
        vo.setSalesDate(o.getSalesDate());
        vo.setCustomerType(o.getCustomerType());
        vo.setStockType(o.getStockType());
        vo.setCompletedAt(o.getCompletedAt());
        vo.setPiId(pi == null ? null : o.getPiId());
        vo.setPiNo(pi == null ? null : pi.getPiNo());
        vo.setPiVersionNo(o.getPiVersionNo());
        vo.setPiStatus(pi == null ? null : pi.getStatus());
        vo.setCustomerId(o.getCustomerId());
        vo.setCustomerName(InquiryLookups.customerName(customer));
        vo.setOwnerId(o.getOwnerId());
        vo.setOwnerName(users.get(o.getOwnerId()));
        vo.setCurrencyCode(o.getCurrencyCode());
        vo.setExchangeRate(o.getExchangeRate());
        vo.setBuyer(piStore.fromJson(o.getBuyerJson(), PartyDTO.class));
        vo.setConsignee(piStore.fromJson(o.getConsigneeJson(), PartyDTO.class));
        vo.setDeliveryTime(o.getDeliveryTime());
        vo.setPaymentTerm(o.getPaymentTerm());
        vo.setIncoterm(o.getIncoterm());
        vo.setIncotermPlace(o.getIncotermPlace());
        vo.setPortOfShipment(o.getPortOfShipment());
        vo.setRemark(o.getRemark());
        vo.setItemAmount(o.getItemAmount());
        vo.setFeeAmount(o.getFeeAmount());
        vo.setDiscountAmount(o.getDiscountAmount());
        vo.setTotalAmount(o.getTotalAmount());
        vo.setTotalAmountCny(o.getTotalAmountCny());
        vo.setNetProfit(o.getNetProfit());
        vo.setNetProfitCny(o.getNetProfitCny());
        vo.setMarginRate(o.getMarginRate());
        vo.setStatus(o.getStatus());
        vo.setStatusName(SO_STATUS_NAMES.get(o.getStatus()));
        vo.setCancelReason(StringUtils.hasText(o.getCancelReason()) ? o.getCancelReason() : null);
        vo.setCancelledByName(o.getCancelledBy() == null ? null : users.get(o.getCancelledBy()));
        vo.setCancelledAt(o.getCancelledAt());
        Receipts rc = receiptsOf(o, pi);
        vo.setReceiptStatus(rc.status());
        vo.setReceiptStatusName(SalesConstants.RECEIPT_STATUS_NAMES.get(rc.status()));
        vo.setReceivedAmount(rc.received());
        vo.setFeeDiffAmount(rc.feeDiff());
        vo.setRemainingAmount(o.getTotalAmount().subtract(rc.received()).subtract(rc.feeDiff()));
        vo.setReceipts(pi != null ? piService.receipts(pi.getId())
                : receiptViews.list(new LambdaQueryWrapper<PaymentReceiptDO>().eq(PaymentReceiptDO::getSoId, o.getId())));
        vo.setMethodTotals(methodTotals(vo.getReceipts(), o.getCurrencyCode()));
        QuotationRenderModels.Labels labels = quotationStore.labels();
        List<SalesOrderItemDO> items = orderItems(id);
        Map<Long, String> quotationNos = quotationNos(items.stream().map(SalesOrderItemDO::getQuotationId).toList());
        Map<Long, String> inquiryCodes = lookups.inquiryCodes(items.stream().map(SalesOrderItemDO::getCustomerInquiryId).toList());
        List<OrderProgress.Step> ordered = progress.ordered();
        Map<String, String> progressNames = progress.names();
        Map<Long, com.zhul.erp.modules.purchase.support.PurchaseLinks.ItemPurchase> links =
                purchaseLinks.forSoItems(items.stream().map(SalesOrderItemDO::getId).toList());
        // 型号的采购员：有采购需求的取需求的采购员（可能多位），存量没有需求的取型号行上的
        Map<Long, List<Long>> itemPurchasers = new HashMap<>();
        for (SalesOrderItemDO i : items) {
            var lp = links.get(i.getId());
            itemPurchasers.put(i.getId(), lp != null && lp.tracked() ? new ArrayList<>(lp.purchaserIds())
                    : i.getPurchaserId() == null ? List.of() : List.of(i.getPurchaserId()));
        }
        Map<Long, String> purchaserNames = lookups.userNames(itemPurchasers.values().stream().flatMap(List::stream).toList());
        vo.setItems(items.stream().map(i -> {
            SalesOrderItemVO x = new SalesOrderItemVO();
            x.setStockType(i.getStockType());
            x.setProgressCode(i.getProgressCode());
            x.setProgressName(progressNames.getOrDefault(i.getProgressCode(), i.getProgressCode()));
            List<Long> who = itemPurchasers.get(i.getId());
            x.setPurchaserId(who.isEmpty() ? null : who.get(0));
            x.setPurchaserName(who.isEmpty() ? null : purchaserNames.get(who.get(0)));
            x.setPurchaserNames(who.stream().map(purchaserNames::get).filter(Objects::nonNull).toList());
            var lp = links.get(i.getId());
            x.setPurchaseTracked(lp != null && lp.tracked());
            x.setPurchaseOrderedQty(lp == null ? 0 : lp.ordered());
            x.setPurchaseDraftQty(lp == null ? 0 : lp.draft());
            x.setPurchaseReceivedQty(lp == null ? 0 : lp.received());
            x.setPurchaseOrders(lp == null ? List.of() : lp.orders().stream().map(r -> {
                SalesOrderItemVO.PurchaseRef ref = new SalesOrderItemVO.PurchaseRef();
                ref.setId(r.id());
                ref.setPoNo(r.poNo());
                ref.setStatus(r.status());
                ref.setExpectedShipDate(r.expectedShipDate());
                return ref;
            }).toList());
            if (lp != null && lp.tracked()) {
                var g = lp.goods(i.getQuantity());
                x.setGoodsReceived(g.received());
                x.setGoodsInTransit(g.inTransit());
                x.setGoodsPendingShip(g.pendingShip());
                x.setGoodsPendingPurchase(g.pendingPurchase());
                x.setGoodsShipped(g.shippedOut());
                x.setGoodsHanded(g.handedOut());
                x.setGoodsInWarehouse(g.inWarehouse());
                x.setTransits(lp.transits().stream().map(t -> {
                    SalesOrderItemVO.Transit tr = new SalesOrderItemVO.Transit();
                    tr.setShipmentId(t.shipmentId());
                    tr.setSdNo(t.sdNo());
                    tr.setCarrier(t.carrier());
                    tr.setQuantity(t.quantity());
                    tr.setExpectedArrivalDate(t.expectedArrival());
                    return tr;
                }).toList());
            }
            x.setId(i.getId());
            x.setLineNo(i.getLineNo());
            x.setQuotationId(i.getQuotationId());
            x.setQuotationNo(quotationNos.get(i.getQuotationId()));
            x.setQuotationItemId(i.getQuotationItemId());
            x.setCustomerInquiryId(i.getCustomerInquiryId());
            x.setInquiryCode(inquiryCodes.get(i.getCustomerInquiryId()));
            x.setInquiryItemId(i.getInquiryItemId());
            x.setModel(i.getModel());
            x.setBrand(i.getBrand());
            x.setCategory(i.getCategory());
            x.setDescription(i.getDescription());
            x.setDescriptionEn(i.getDescriptionEn());
            x.setItemCondition(i.getItemCondition());
            x.setConditionName(labels.conditions().get(i.getItemCondition()));
            x.setLeadTime(i.getLeadTime());
            x.setLeadTimeName(labels.leadTimes().get(i.getLeadTime()));
            x.setWarranty(i.getWarranty());
            x.setQuantity(i.getQuantity());
            x.setUnitPrice(i.getUnitPrice());
            x.setAmount(i.getAmount());
            x.setAmountCny(i.getAmountCny());
            x.setCostPrice(i.getCostPrice());
            x.setMarginRate(i.getMarginRate());
            x.setBelowFloor(false);
            x.setNetProfit(i.getNetProfit());
            x.setNetProfitCny(i.getNetProfitCny());
            x.setHsCode(i.getHsCode());
            x.setOriginCountry(i.getOriginCountry());
            x.setRemark(i.getRemark());
            return x;
        }).toList());
        vo.setFees(orderFeeMapper.selectList(new LambdaQueryWrapper<SalesOrderFeeDO>()
                        .eq(SalesOrderFeeDO::getSoId, id)
                        .isNull(SalesOrderFeeDO::getDeletedAt)
                        .orderByAsc(SalesOrderFeeDO::getSortOrder))
                .stream().map(f -> {
                    PiFeeVO x = new PiFeeVO();
                    x.setFeeName(f.getFeeName());
                    x.setAmount(f.getAmount());
                    x.setAmountCny(f.getAmountCny());
                    x.setRemark(f.getRemark());
                    return x;
                }).toList());
        vo.setCreateTime(o.getCreateTime());
        vo.setCreateByName(o.getCreateBy());

        boolean active = o.getStatus() == SalesConstants.SO_ACTIVE;
        boolean completed = SalesConstants.PROGRESS_COMPLETED.equals(o.getProgressCode());
        vo.setProgressCode(active ? o.getProgressCode() : SalesConstants.PROGRESS_CANCELLED);
        vo.setProgressName(progressNames.getOrDefault(vo.getProgressCode(), vo.getProgressCode()));
        vo.setTrackable(active && !completed);
        int lastRank = OrderProgress.rank(ordered, OrderProgress.last(ordered).code());
        vo.setCompletable(active && !completed && items.stream().allMatch(i -> OrderProgress.rank(ordered, i.getProgressCode()) >= lastRank));
        vo.setSteps(ordered.stream().map(st -> {
            SalesOrderVO.Step x = new SalesOrderVO.Step();
            x.setCode(st.code());
            x.setName(st.name());
            x.setEnabled(st.enabled());
            return x;
        }).toList());
        Map<Long, Integer> byPurchaser = new LinkedHashMap<>();
        items.forEach(i -> {
            List<Long> who = itemPurchasers.get(i.getId());
            if (who.isEmpty()) {
                byPurchaser.merge(0L, 1, Integer::sum);
            } else {
                who.forEach(u -> byPurchaser.merge(u, 1, Integer::sum));
            }
        });
        vo.setPurchasers(byPurchaser.entrySet().stream().map(e -> {
            SalesOrderVO.Purchaser x = new SalesOrderVO.Purchaser();
            x.setUserId(e.getKey() == 0L ? null : e.getKey());
            x.setName(e.getKey() == 0L ? null : purchaserNames.get(e.getKey()));
            x.setItemCount(e.getValue());
            return x;
        }).toList());
        vo.setMargin(margin(o, items, rc));
        return vo;
    }

    /** 收款汇总：PI 转成的订单取 PI，手动创建的订单取订单自己的 */
    private record Receipts(int status, BigDecimal received, BigDecimal feeDiff, BigDecimal netCny) {
    }

    private Receipts receiptsOf(SalesOrderDO o, ProformaInvoiceDO pi) {
        LambdaQueryWrapper<PaymentReceiptDO> w = new LambdaQueryWrapper<PaymentReceiptDO>()
                .select(PaymentReceiptDO::getNetAmountCny)
                .eq(PaymentReceiptDO::getKind, SalesConstants.KIND_RECEIPT)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt);
        if (pi != null) {
            w.eq(PaymentReceiptDO::getPiId, pi.getId());
        } else {
            w.eq(PaymentReceiptDO::getSoId, o.getId());
        }
        BigDecimal netCny = receiptMapper.selectList(w).stream().map(PaymentReceiptDO::getNetAmountCny).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return pi != null ? new Receipts(pi.getReceiptStatus(), pi.getReceivedAmount(), pi.getFeeDiffAmount(), netCny)
                : new Receipts(o.getReceiptStatus(), o.getReceivedAmount(), o.getFeeDiffAmount(), netCny);
    }

    /** 收款按付款方式汇总（有效到账，线上在前、金额从大到小） */
    private static List<SalesOrderVO.MethodTotal> methodTotals(List<ReceiptVO> receipts, String currency) {
        Map<String, SalesOrderVO.MethodTotal> map = new LinkedHashMap<>();
        for (ReceiptVO r : receipts) {
            if (r.getKind() != SalesConstants.KIND_RECEIPT || r.getStatus() != SalesConstants.RECORD_VALID) {
                continue;
            }
            SalesOrderVO.MethodTotal m = map.computeIfAbsent(r.getPaymentMethod(), k -> {
                SalesOrderVO.MethodTotal x = new SalesOrderVO.MethodTotal();
                x.setPaymentMethod(r.getPaymentMethod());
                x.setPaymentMethodName(r.getPaymentMethodName());
                x.setChannel(r.getChannel());
                x.setCurrencyCode(currency);
                x.setAmount(BigDecimal.ZERO);
                return x;
            });
            m.setAmount(m.getAmount().add(r.getAmount()));
        }
        return map.values().stream()
                .sorted(Comparator.comparing((SalesOrderVO.MethodTotal m) -> m.getChannel() == null ? 0 : -m.getChannel())
                        .thenComparing(SalesOrderVO.MethodTotal::getAmount, Comparator.reverseOrder()))
                .toList();
    }

    /**
     * 订单毛利（CNY，HALF_UP 保留 2 位）：采购成本 = Σ 成本价 × 数量；
     * 已到账时毛利 = 实收人民币 − 采购成本，否则预计毛利 = 销售额折合人民币 − 采购成本
     */
    private static SalesOrderVO.Margin margin(SalesOrderDO o, List<SalesOrderItemDO> items, Receipts rc) {
        BigDecimal cost = BigDecimal.ZERO;
        int missing = 0;
        for (SalesOrderItemDO i : items) {
            if (i.getCostPrice() == null) {
                missing++;
            } else {
                cost = cost.add(i.getCostPrice().multiply(BigDecimal.valueOf(i.getQuantity() == null ? 0 : i.getQuantity())));
            }
        }
        boolean paid = rc.status() == SalesConstants.RECEIPT_PAID;
        BigDecimal base = paid ? rc.netCny() : o.getTotalAmountCny();
        SalesOrderVO.Margin m = new SalesOrderVO.Margin();
        m.setSalesAmountCny(o.getTotalAmountCny());
        m.setReceivedCny(rc.netCny().setScale(2, RoundingMode.HALF_UP));
        m.setCostCny(cost.setScale(2, RoundingMode.HALF_UP));
        m.setProfitCny(base.subtract(cost).setScale(2, RoundingMode.HALF_UP));
        m.setEstimated(!paid);
        m.setMissingCostCount(missing);
        return m;
    }

    @Override
    public PageResult<SalesOrderListVO> page(SalesOrderPageQuery q) {
        int tenant = PiStore.tenantId();
        LambdaQueryWrapper<SalesOrderDO> w = scoped();
        if (q.getStatus() != null) {
            w.eq(SalesOrderDO::getStatus, q.getStatus());
        }
        if (q.getOwnerId() != null) {
            w.eq(SalesOrderDO::getOwnerId, q.getOwnerId());
        }
        if (q.getCreatedFrom() != null) {
            w.ge(SalesOrderDO::getCreateTime, q.getCreatedFrom().atStartOfDay());
        }
        if (q.getCreatedTo() != null) {
            w.lt(SalesOrderDO::getCreateTime, q.getCreatedTo().plusDays(1).atStartOfDay());
        }
        if (q.getReceiptStatus() != null) {
            List<Long> piIds = piMapper.selectList(new LambdaQueryWrapper<ProformaInvoiceDO>()
                            .select(ProformaInvoiceDO::getId)
                            .eq(ProformaInvoiceDO::getTenantId, tenant)
                            .eq(ProformaInvoiceDO::getReceiptStatus, q.getReceiptStatus()))
                    .stream().map(ProformaInvoiceDO::getId).toList();
            // PI 转成的订单按 PI 的收款状态，手动创建的订单按订单自己的
            w.and(x -> {
                x.and(m -> m.eq(SalesOrderDO::getSource, SalesConstants.SO_MANUAL).eq(SalesOrderDO::getReceiptStatus, q.getReceiptStatus()));
                if (!piIds.isEmpty()) {
                    x.or().in(SalesOrderDO::getPiId, piIds);
                }
            });
        }
        if (q.getSalesFrom() != null) {
            w.ge(SalesOrderDO::getSalesDate, q.getSalesFrom());
        }
        if (q.getSalesTo() != null) {
            w.le(SalesOrderDO::getSalesDate, q.getSalesTo());
        }
        if (StringUtils.hasText(q.getProgressCode())) {
            if (SalesConstants.PROGRESS_CANCELLED.equals(q.getProgressCode().trim())) {
                w.eq(SalesOrderDO::getStatus, SalesConstants.SO_CANCELLED);
            } else {
                w.eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE).eq(SalesOrderDO::getProgressCode, q.getProgressCode().trim());
            }
        }
        if (q.getStockType() != null) {
            w.eq(SalesOrderDO::getStockType, q.getStockType());
        }
        if (q.getCustomerType() != null) {
            w.eq(SalesOrderDO::getCustomerType, q.getCustomerType());
        }
        if (StringUtils.hasText(q.getCurrencyCode())) {
            w.eq(SalesOrderDO::getCurrencyCode, q.getCurrencyCode().trim().toUpperCase(Locale.ROOT));
        }
        if (q.getPurchaserId() != null) {
            List<Long> byPurchaser = Stream.concat(orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                            .select(SalesOrderItemDO::getSoId)
                            .eq(SalesOrderItemDO::getTenantId, tenant)
                            .eq(SalesOrderItemDO::getPurchaserId, q.getPurchaserId())
                            .isNull(SalesOrderItemDO::getDeletedAt))
                    .stream().map(SalesOrderItemDO::getSoId), purchaseLinks.orderIdsByPurchaser(q.getPurchaserId()).stream())
                    .distinct().toList();
            if (byPurchaser.isEmpty()) {
                return PageResult.of(0L, List.of());
            }
            w.in(SalesOrderDO::getId, byPurchaser);
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            List<Long> customerIds = customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                            .select(CustomerDO::getId)
                            .eq(CustomerDO::getTenantId, tenant)
                            .and(x -> x.like(CustomerDO::getName, kw).or().like(CustomerDO::getShortName, kw))
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(CustomerDO::getId).toList();
            // PI 编号按包含匹配：客户水单上写的不带前缀的编号也能找到
            List<Long> piIds = piMapper.selectList(new LambdaQueryWrapper<ProformaInvoiceDO>()
                            .select(ProformaInvoiceDO::getId)
                            .eq(ProformaInvoiceDO::getTenantId, tenant)
                            .like(ProformaInvoiceDO::getPiNo, kw)
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(ProformaInvoiceDO::getId).toList();
            List<Long> byModel = orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                            .select(SalesOrderItemDO::getSoId)
                            .eq(SalesOrderItemDO::getTenantId, tenant)
                            .like(SalesOrderItemDO::getModel, kw)
                            .isNull(SalesOrderItemDO::getDeletedAt)
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(SalesOrderItemDO::getSoId).distinct().toList();
            w.and(x -> {
                x.like(SalesOrderDO::getSoNo, kw);
                if (!customerIds.isEmpty()) {
                    x.or().in(SalesOrderDO::getCustomerId, customerIds);
                }
                if (!piIds.isEmpty()) {
                    x.or().in(SalesOrderDO::getPiId, piIds);
                }
                if (!byModel.isEmpty()) {
                    x.or().in(SalesOrderDO::getId, byModel);
                }
            });
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = orderMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        // 列表页统一规范：默认按更新时间倒序
        w.orderByDesc(SalesOrderDO::getUpdateTime).orderByDesc(SalesOrderDO::getId)
                .last("LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toListVos(orderMapper.selectList(w)));
    }

    @Override
    public SalesOrderStatsVO stats() {
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        LocalDate last = first.plusMonths(1).minusDays(1);
        List<SalesOrderDO> active = orderMapper.selectList(scoped()
                .select(SalesOrderDO::getId, SalesOrderDO::getPiId, SalesOrderDO::getSource, SalesOrderDO::getSalesDate, SalesOrderDO::getProgressCode)
                .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE));
        SalesOrderStatsVO vo = new SalesOrderStatsVO();
        vo.setMonthCount(active.stream().filter(o -> o.getSalesDate() != null && !o.getSalesDate().isBefore(first)
                && !o.getSalesDate().isAfter(last)).count());
        List<SalesOrderDO> running = active.stream().filter(o -> !SalesConstants.PROGRESS_COMPLETED.equals(o.getProgressCode())).toList();
        vo.setInProgressCount((long) running.size());
        Map<String, Long> byCode = running.stream().collect(Collectors.groupingBy(SalesOrderDO::getProgressCode, Collectors.counting()));
        Map<String, String> names = progress.names();
        vo.setProgressCounts(progress.ordered().stream().filter(st -> byCode.containsKey(st.code())).map(st -> {
            SalesOrderStatsVO.ProgressCount c = new SalesOrderStatsVO.ProgressCount();
            c.setCode(st.code());
            c.setName(names.getOrDefault(st.code(), st.name()));
            c.setCount(byCode.get(st.code()));
            return c;
        }).toList());
        Set<Long> runningIds = running.stream().map(SalesOrderDO::getId).collect(Collectors.toSet());
        vo.setUnassignedCount(runningIds.isEmpty() ? 0L : orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                        .select(SalesOrderItemDO::getSoId)
                        .in(SalesOrderItemDO::getSoId, runningIds)
                        .isNull(SalesOrderItemDO::getPurchaserId)
                        .isNull(SalesOrderItemDO::getDeletedAt))
                .stream().map(SalesOrderItemDO::getSoId).distinct().count());
        List<Long> piIds = active.stream().filter(o -> o.getPiId() != null && o.getPiId() > 0).map(SalesOrderDO::getPiId).toList();
        List<Long> soIds = active.stream().filter(o -> Objects.equals(o.getSource(), SalesConstants.SO_MANUAL)).map(SalesOrderDO::getId).toList();
        BigDecimal online = BigDecimal.ZERO;
        BigDecimal offline = BigDecimal.ZERO;
        if (!piIds.isEmpty() || !soIds.isEmpty()) {
            for (PaymentReceiptDO r : receiptMapper.selectList(new LambdaQueryWrapper<PaymentReceiptDO>()
                    .select(PaymentReceiptDO::getChannel, PaymentReceiptDO::getNetAmountCny)
                    .and(x -> {
                        if (!piIds.isEmpty()) {
                            x.in(PaymentReceiptDO::getPiId, piIds);
                        }
                        if (!soIds.isEmpty()) {
                            x.or().in(PaymentReceiptDO::getSoId, soIds);
                        }
                    })
                    .eq(PaymentReceiptDO::getKind, SalesConstants.KIND_RECEIPT)
                    .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                    .between(PaymentReceiptDO::getReceiptDate, first, last)
                    .isNull(PaymentReceiptDO::getDeletedAt))) {
                BigDecimal v = r.getNetAmountCny() == null ? BigDecimal.ZERO : r.getNetAmountCny();
                if (Objects.equals(r.getChannel(), SalesConstants.CHANNEL_ONLINE)) {
                    online = online.add(v);
                } else {
                    offline = offline.add(v);
                }
            }
        }
        vo.setMonthOnlineCny(online);
        vo.setMonthOfflineCny(offline);
        vo.setMonthNetCny(online.add(offline));
        return vo;
    }

    @Override
    public List<OrderCandidatePiVO> candidatePis(String keyword) {
        int tenant = PiStore.tenantId();
        LambdaQueryWrapper<ProformaInvoiceDO> w = new LambdaQueryWrapper<ProformaInvoiceDO>()
                .eq(ProformaInvoiceDO::getTenantId, tenant)
                .eq(ProformaInvoiceDO::getStatus, SalesConstants.PI_SENT)
                .ne(ProformaInvoiceDO::getReceiptStatus, SalesConstants.RECEIPT_NONE)
                .isNull(ProformaInvoiceDO::getEditingVersionNo)
                .isNull(ProformaInvoiceDO::getDeletedAt)
                .notExists("SELECT 1 FROM sales_order so WHERE so.pi_id = proforma_invoice.id AND so.status = "
                        + SalesConstants.SO_ACTIVE + " AND so.deleted_at IS NULL");
        dataScopeResolver.current().apply(w, ProformaInvoiceDO::getOwnerId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            List<Long> customerIds = customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                            .select(CustomerDO::getId)
                            .eq(CustomerDO::getTenantId, tenant)
                            .and(x -> x.like(CustomerDO::getName, kw).or().like(CustomerDO::getShortName, kw)
                                    .or().like(CustomerDO::getContactName, kw))
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(CustomerDO::getId).toList();
            w.and(x -> {
                x.like(ProformaInvoiceDO::getPiNo, kw);
                if (!customerIds.isEmpty()) {
                    x.or().in(ProformaInvoiceDO::getCustomerId, customerIds);
                }
            });
        }
        w.orderByDesc(ProformaInvoiceDO::getSentAt).orderByDesc(ProformaInvoiceDO::getId).last("LIMIT 200");
        List<ProformaInvoiceDO> pis = piMapper.selectList(w);
        if (pis.isEmpty()) {
            return List.of();
        }
        Map<Long, PaymentReceiptDO> latest = new HashMap<>();
        receiptMapper.selectList(new LambdaQueryWrapper<PaymentReceiptDO>()
                        .in(PaymentReceiptDO::getPiId, pis.stream().map(ProformaInvoiceDO::getId).toList())
                        .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                        .isNull(PaymentReceiptDO::getDeletedAt)
                        .orderByAsc(PaymentReceiptDO::getReceiptDate)
                        .orderByAsc(PaymentReceiptDO::getId))
                .forEach(r -> latest.put(r.getPiId(), r));
        Map<Long, CustomerDO> customers = lookups.customers(pis.stream().map(ProformaInvoiceDO::getCustomerId).toList());
        Map<Long, String> users = lookups.userNames(pis.stream().map(ProformaInvoiceDO::getOwnerId).toList());
        return pis.stream().map(p -> {
            OrderCandidatePiVO vo = new OrderCandidatePiVO();
            vo.setId(p.getId());
            vo.setPiNo(p.getPiNo());
            vo.setVersionNo(p.getCurrentVersionNo());
            CustomerDO c = customers.get(p.getCustomerId());
            vo.setCustomerName(InquiryLookups.customerName(c));
            vo.setCustomerCountry(c == null ? null : c.getCountry());
            vo.setCurrencyCode(p.getCurrencyCode());
            vo.setTotalAmount(p.getTotalAmount());
            vo.setReceiptStatus(p.getReceiptStatus());
            vo.setReceiptStatusName(SalesConstants.RECEIPT_STATUS_NAMES.get(p.getReceiptStatus()));
            PaymentReceiptDO r = latest.get(p.getId());
            if (r != null) {
                vo.setLastKind(r.getKind());
                vo.setLastAmount(r.getAmount());
                vo.setLastDate(r.getReceiptDate());
                vo.setLastMethodName(r.getPaymentMethodName());
                vo.setLastPlatform(StringUtils.hasText(r.getPlatformOrderNo()));
            }
            vo.setOwnerName(users.get(p.getOwnerId()));
            return vo;
        }).toList();
    }

    private List<SalesOrderListVO> toListVos(List<SalesOrderDO> rows) {
        List<Long> ids = rows.stream().map(SalesOrderDO::getId).toList();
        Map<Long, Integer> counts = new HashMap<>();
        Map<Long, Integer> quantities = new HashMap<>();
        Map<Long, Set<Long>> purchasersByOrder = new HashMap<>();
        Map<Long, Integer> unassigned = new HashMap<>();
        List<SalesOrderItemDO> allItems = new ArrayList<>();
        for (SalesOrderItemDO i : orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                .select(SalesOrderItemDO::getId, SalesOrderItemDO::getSoId, SalesOrderItemDO::getQuantity, SalesOrderItemDO::getPurchaserId)
                .in(SalesOrderItemDO::getSoId, ids)
                .isNull(SalesOrderItemDO::getDeletedAt)
                .orderByAsc(SalesOrderItemDO::getLineNo))) {
            allItems.add(i);
            counts.merge(i.getSoId(), 1, Integer::sum);
            quantities.merge(i.getSoId(), i.getQuantity() == null ? 0 : i.getQuantity(), Integer::sum);
            if (i.getPurchaserId() == null) {
                unassigned.merge(i.getSoId(), 1, Integer::sum);
            } else {
                purchasersByOrder.computeIfAbsent(i.getSoId(), k -> new java.util.LinkedHashSet<>()).add(i.getPurchaserId());
            }
        }
        // 拆分给多位采购员的型号：需求上的采购员也列出
        purchaseLinks.purchasersByOrder(ids).forEach((so, who) ->
                purchasersByOrder.computeIfAbsent(so, k -> new java.util.LinkedHashSet<>()).addAll(who));
        Map<Long, String> purchaserNames = lookups.userNames(purchasersByOrder.values().stream().flatMap(Set::stream).toList());
        Map<String, String> progressNames = progress.names();
        Map<Long, ProformaInvoiceDO> pis = piMapper.selectBatchIds(rows.stream().map(SalesOrderDO::getPiId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProformaInvoiceDO::getId, p -> p));
        Map<Long, CustomerDO> customers = lookups.customers(rows.stream().map(SalesOrderDO::getCustomerId).toList());
        Map<Long, String> users = lookups.userNames(rows.stream().map(SalesOrderDO::getOwnerId).toList());
        Map<Long, SalesOrderListVO.Goods> goods = goodsByOrder(rows, allItems);
        List<SalesOrderListVO> list = new ArrayList<>(rows.size());
        for (SalesOrderDO o : rows) {
            ProformaInvoiceDO pi = pis.get(o.getPiId());
            SalesOrderListVO vo = new SalesOrderListVO();
            vo.setId(o.getId());
            vo.setSoNo(o.getSoNo());
            vo.setCustomerId(o.getCustomerId());
            vo.setCustomerName(InquiryLookups.customerName(customers.get(o.getCustomerId())));
            vo.setCustomerCountry(customers.get(o.getCustomerId()) == null ? null : customers.get(o.getCustomerId()).getCountry());
            vo.setCustomerType(o.getCustomerType());
            vo.setSource(o.getSource());
            vo.setSalesDate(o.getSalesDate());
            vo.setStockType(o.getStockType());
            String code = o.getStatus() == SalesConstants.SO_ACTIVE ? o.getProgressCode() : SalesConstants.PROGRESS_CANCELLED;
            vo.setProgressCode(code);
            vo.setProgressName(progressNames.getOrDefault(code, code));
            vo.setPurchaserNames(purchasersByOrder.getOrDefault(o.getId(), Set.of()).stream().map(purchaserNames::get)
                    .filter(Objects::nonNull).toList());
            vo.setUnassignedCount(unassigned.getOrDefault(o.getId(), 0));
            vo.setTotalQuantity(quantities.getOrDefault(o.getId(), 0));
            vo.setItemCount(counts.getOrDefault(o.getId(), 0));
            vo.setCurrencyCode(o.getCurrencyCode());
            vo.setTotalAmount(o.getTotalAmount());
            if (pi != null) {
                vo.setReceiptStatus(pi.getReceiptStatus());
                vo.setReceiptStatusName(SalesConstants.RECEIPT_STATUS_NAMES.get(pi.getReceiptStatus()));
                vo.setReceivedAmount(pi.getReceivedAmount());
                vo.setPiNo(pi.getPiNo());
            } else {
                vo.setReceiptStatus(o.getReceiptStatus());
                vo.setReceiptStatusName(SalesConstants.RECEIPT_STATUS_NAMES.get(o.getReceiptStatus()));
                vo.setReceivedAmount(o.getReceivedAmount());
            }
            vo.setStatus(o.getStatus());
            vo.setStatusName(SO_STATUS_NAMES.get(o.getStatus()));
            vo.setPiId(pi == null ? null : o.getPiId());
            vo.setPiVersionNo(pi == null ? null : o.getPiVersionNo());
            vo.setOwnerId(o.getOwnerId());
            vo.setOwnerName(users.get(o.getOwnerId()));
            vo.setCancelReason(StringUtils.hasText(o.getCancelReason()) ? o.getCancelReason() : null);
            vo.setGoods(goods.get(o.getId()));
            vo.setCreateTime(o.getCreateTime());
            vo.setCreateBy(o.getCreateBy());
            vo.setUpdateTime(o.getUpdateTime());
            vo.setUpdateBy(o.getUpdateBy());
            list.add(vo);
        }
        return list;
    }

    /** 进行中的订单：有采购需求的型号按件数汇总货物状态 */
    private Map<Long, SalesOrderListVO.Goods> goodsByOrder(List<SalesOrderDO> rows, List<SalesOrderItemDO> items) {
        Set<Long> active = rows.stream().filter(o -> o.getStatus() == SalesConstants.SO_ACTIVE).map(SalesOrderDO::getId)
                .collect(Collectors.toSet());
        List<SalesOrderItemDO> live = items.stream().filter(i -> active.contains(i.getSoId())).toList();
        Map<Long, com.zhul.erp.modules.purchase.support.PurchaseLinks.ItemPurchase> links =
                purchaseLinks.forSoItems(live.stream().map(SalesOrderItemDO::getId).toList());
        Map<Long, SalesOrderListVO.Goods> out = new HashMap<>();
        for (SalesOrderItemDO i : live) {
            var lp = links.get(i.getId());
            if (lp == null || !lp.tracked()) {
                continue;
            }
            var g = lp.goods(i.getQuantity());
            SalesOrderListVO.Goods sum = out.computeIfAbsent(i.getSoId(), k -> {
                SalesOrderListVO.Goods x = new SalesOrderListVO.Goods();
                x.setReceived(0);
                x.setInTransit(0);
                x.setPendingShip(0);
                x.setPendingPurchase(0);
                x.setShipped(0);
                x.setHanded(0);
                x.setInWarehouse(0);
                x.setTotal(0);
                return x;
            });
            sum.setReceived(sum.getReceived() + g.received());
            sum.setInTransit(sum.getInTransit() + g.inTransit());
            sum.setPendingShip(sum.getPendingShip() + g.pendingShip());
            sum.setPendingPurchase(sum.getPendingPurchase() + g.pendingPurchase());
            sum.setShipped(sum.getShipped() + g.shippedOut());
            sum.setHanded(sum.getHanded() + g.handedOut());
            sum.setInWarehouse(sum.getInWarehouse() + g.inWarehouse());
            sum.setTotal(sum.getTotal() + i.getQuantity());
            lp.transits().stream().map(com.zhul.erp.modules.purchase.support.PurchaseLinks.Transit::expectedArrival)
                    .filter(Objects::nonNull)
                    .filter(d -> sum.getEarliestArrival() == null || d.isBefore(sum.getEarliestArrival()))
                    .forEach(sum::setEarliestArrival);
        }
        return out;
    }

    // ---------------------------------------------------------------- 来源 / 去向链路

    @Override
    public ChainVO chain(String type, Long id) {
        Set<Long> inquiryIds = new TreeSet<>();
        Set<Long> quotationIds = new TreeSet<>();
        Set<Long> piIds = new TreeSet<>();
        Set<Long> orderIds = new TreeSet<>();
        String t = type == null ? "" : type.trim().toLowerCase();
        switch (t) {
            case "inquiry" -> {
                CustomerInquiryDO i = inquiryMapper.selectById(id);
                if (i == null || i.getDeletedAt() != null || !Objects.equals(i.getTenantId(), PiStore.tenantId())
                        || !dataScopeResolver.current().canSee(i.getOwnerId())) {
                    throw new BizException("客户询盘不存在");
                }
                inquiryIds.add(id);
                quotationIds.addAll(quotationIdsByInquiry(inquiryIds));
                piIds.addAll(piIdsBy(PiItemDO::getCustomerInquiryId, inquiryIds));
                orderIds.addAll(orderIdsBy(SalesOrderItemDO::getCustomerInquiryId, inquiryIds));
            }
            case "quotation" -> {
                quotationStore.visible(id);
                quotationIds.add(id);
                inquiryIds.addAll(quotationItemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                                .select(QuotationItemDO::getCustomerInquiryId)
                                .eq(QuotationItemDO::getQuotationId, id)
                                .eq(QuotationItemDO::getIsCurrent, 1)
                .isNull(QuotationItemDO::getDeletedAt))
                        .stream().map(QuotationItemDO::getCustomerInquiryId).toList());
                piIds.addAll(piIdsBy(PiItemDO::getQuotationId, Set.of(id)));
                orderIds.addAll(orderIdsBy(SalesOrderItemDO::getQuotationId, Set.of(id)));
            }
            case "pi" -> {
                piStore.visible(id);
                piIds.add(id);
                for (PiItemDO i : piItemMapper.selectList(new LambdaQueryWrapper<PiItemDO>()
                        .select(PiItemDO::getQuotationId, PiItemDO::getCustomerInquiryId)
                        .eq(PiItemDO::getPiId, id)
                        .isNull(PiItemDO::getDeletedAt))) {
                    quotationIds.add(i.getQuotationId());
                    inquiryIds.add(i.getCustomerInquiryId());
                }
                orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>().select(SalesOrderDO::getId)
                        .eq(SalesOrderDO::getPiId, id).isNull(SalesOrderDO::getDeletedAt)).forEach(o -> orderIds.add(o.getId()));
            }
            case "order" -> {
                SalesOrderDO o = visible(id);
                orderIds.add(id);
                piIds.add(o.getPiId());
                for (SalesOrderItemDO i : orderItems(id)) {
                    quotationIds.add(i.getQuotationId());
                    inquiryIds.add(i.getCustomerInquiryId());
                }
            }
            default -> throw new BizException("单据类型不正确");
        }
        inquiryIds.remove(0L);
        quotationIds.remove(0L);
        piIds.remove(0L);
        ChainVO vo = new ChainVO();
        vo.setInquiries(inquiryIds.isEmpty() ? List.of() : inquiryMapper.selectBatchIds(inquiryIds).stream()
                .filter(i -> i.getDeletedAt() == null)
                .map(i -> node(i.getId(), i.getInquiryCode(), i.getStatus(), INQUIRY_STATUS_NAMES.get(i.getStatus()), "inquiry".equals(t) && i.getId().equals(id)))
                .toList());
        vo.setQuotations(quotationIds.isEmpty() ? List.of() : quotationMapper.selectBatchIds(quotationIds).stream()
                .filter(q -> q.getDeletedAt() == null)
                .map(q -> node(q.getId(), q.getQuotationNo(), q.getStatus(), QuotationConstants.STATUS_NAMES.get(q.getStatus()),
                        "quotation".equals(t) && q.getId().equals(id)))
                .toList());
        vo.setPis(piIds.isEmpty() ? List.of() : piMapper.selectBatchIds(piIds).stream()
                .filter(p -> p.getDeletedAt() == null)
                .map(p -> node(p.getId(), p.getPiNo(), p.getStatus(), SalesConstants.PI_STATUS_NAMES.get(p.getStatus()), "pi".equals(t) && p.getId().equals(id)))
                .toList());
        vo.setOrders(orderIds.isEmpty() ? List.of() : orderMapper.selectBatchIds(orderIds).stream()
                .filter(o -> o.getDeletedAt() == null)
                .map(o -> node(o.getId(), o.getSoNo(), o.getStatus(), SO_STATUS_NAMES.get(o.getStatus()), "order".equals(t) && o.getId().equals(id)))
                .toList());
        return vo;
    }

    private Set<Long> quotationIdsByInquiry(Collection<Long> inquiryIds) {
        return quotationItemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                        .select(QuotationItemDO::getQuotationId)
                        .in(QuotationItemDO::getCustomerInquiryId, inquiryIds)
                        .eq(QuotationItemDO::getIsCurrent, 1)
                .isNull(QuotationItemDO::getDeletedAt))
                .stream().map(QuotationItemDO::getQuotationId).collect(Collectors.toSet());
    }

    private Set<Long> piIdsBy(com.baomidou.mybatisplus.core.toolkit.support.SFunction<PiItemDO, ?> column, Collection<Long> ids) {
        return piItemMapper.selectList(new LambdaQueryWrapper<PiItemDO>()
                        .select(PiItemDO::getPiId)
                        .in(column, ids)
                        .isNull(PiItemDO::getDeletedAt))
                .stream().map(PiItemDO::getPiId).collect(Collectors.toSet());
    }

    private Set<Long> orderIdsBy(com.baomidou.mybatisplus.core.toolkit.support.SFunction<SalesOrderItemDO, ?> column, Collection<Long> ids) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                        .select(SalesOrderItemDO::getSoId)
                        .in(column, ids)
                        .isNull(SalesOrderItemDO::getDeletedAt))
                .stream().map(SalesOrderItemDO::getSoId).collect(Collectors.toSet());
    }

    private static ChainVO.Node node(Long id, String no, Integer status, String statusName, boolean current) {
        ChainVO.Node n = new ChainVO.Node();
        n.setId(id);
        n.setNo(no);
        n.setStatus(status);
        n.setStatusName(statusName);
        n.setCurrent(current);
        return n;
    }

    // ---------------------------------------------------------------- 内部

    private SalesOrderDO visible(Long id) {
        SalesOrderDO o = id == null ? null : orderMapper.selectById(id);
        if (o == null || o.getDeletedAt() != null || !Objects.equals(o.getTenantId(), PiStore.tenantId())
                || !dataScopeResolver.current().canSee(o.getOwnerId())) {
            throw new BizException("销售订单不存在");
        }
        return o;
    }

    private LambdaQueryWrapper<SalesOrderDO> scoped() {
        LambdaQueryWrapper<SalesOrderDO> w = new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getTenantId, PiStore.tenantId())
                .isNull(SalesOrderDO::getDeletedAt);
        return dataScopeResolver.current().apply(w, SalesOrderDO::getOwnerId);
    }

    private List<SalesOrderItemDO> orderItems(Long soId) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                .eq(SalesOrderItemDO::getSoId, soId)
                .isNull(SalesOrderItemDO::getDeletedAt)
                .orderByAsc(SalesOrderItemDO::getLineNo));
    }

    private Map<Long, String> quotationNos(Collection<Long> ids) {
        Set<Long> unique = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (unique.isEmpty()) {
            return Map.of();
        }
        return quotationMapper.selectBatchIds(unique).stream().collect(Collectors.toMap(QuotationDO::getId, QuotationDO::getQuotationNo));
    }

    private Long currentUserId() {
        Long id = currentUser.resolve();
        return id == null ? 0L : id;
    }
}

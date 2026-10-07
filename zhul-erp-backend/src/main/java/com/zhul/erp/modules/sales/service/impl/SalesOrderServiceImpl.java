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
import com.zhul.erp.modules.system.constants.DocumentType;
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

    // ---------------------------------------------------------------- 转订单与取消

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO convert(Long piId) {
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

        SalesOrderDO o = new SalesOrderDO();
        o.setTenantId(pi.getTenantId());
        o.setSoNo(documentNumberService.next(DocumentType.SO));
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
        for (PiItemDO i : items) {
            orderItemMapper.insert(toOrderItem(o, i));
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

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("soNo", o.getSoNo());
        after.put("piNo", pi.getPiNo());
        after.put("version", "Rev." + v.getVersionNo());
        after.put("total", o.getCurrencyCode() + " " + o.getTotalAmount());
        after.put("receiptStatus", SalesConstants.RECEIPT_STATUS_NAMES.get(pi.getReceiptStatus()));
        logService.recordOperateLog(SalesConstants.MENU_SO, "PI 转成销售订单", null, after);
        return detail(o.getId());
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
        ProformaInvoiceDO pi = piStore.lockVisible(o.getPiId());
        o = orderMapper.selectById(id);
        if (o.getStatus() != SalesConstants.SO_ACTIVE) {
            throw new BizException("订单已取消");
        }
        o.setStatus(SalesConstants.SO_CANCELLED);
        o.setCancelReason(why.length() > 200 ? why.substring(0, 200) : why);
        o.setCancelledBy(currentUserId());
        o.setCancelledAt(LocalDateTime.now());
        orderMapper.updateById(o);
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
        vo.setPiId(o.getPiId());
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
        if (pi != null) {
            vo.setReceiptStatus(pi.getReceiptStatus());
            vo.setReceiptStatusName(SalesConstants.RECEIPT_STATUS_NAMES.get(pi.getReceiptStatus()));
            vo.setReceivedAmount(pi.getReceivedAmount());
            vo.setFeeDiffAmount(pi.getFeeDiffAmount());
            vo.setRemainingAmount(o.getTotalAmount().subtract(pi.getReceivedAmount()).subtract(pi.getFeeDiffAmount()));
            vo.setReceipts(piService.receipts(pi.getId()));
        }
        QuotationRenderModels.Labels labels = quotationStore.labels();
        List<SalesOrderItemDO> items = orderItems(id);
        Map<Long, String> quotationNos = quotationNos(items.stream().map(SalesOrderItemDO::getQuotationId).toList());
        Map<Long, String> inquiryCodes = lookups.inquiryCodes(items.stream().map(SalesOrderItemDO::getCustomerInquiryId).toList());
        vo.setItems(items.stream().map(i -> {
            PiItemVO x = new PiItemVO();
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
        return vo;
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
            if (piIds.isEmpty()) {
                return PageResult.of(0L, List.of());
            }
            w.in(SalesOrderDO::getPiId, piIds);
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
        w.orderByDesc(SalesOrderDO::getCreateTime).orderByDesc(SalesOrderDO::getId)
                .last("LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toListVos(orderMapper.selectList(w)));
    }

    private List<SalesOrderListVO> toListVos(List<SalesOrderDO> rows) {
        List<Long> ids = rows.stream().map(SalesOrderDO::getId).toList();
        Map<Long, Integer> counts = new HashMap<>();
        Map<Long, Integer> quantities = new HashMap<>();
        Map<Long, Set<Long>> inquiriesByOrder = new HashMap<>();
        for (SalesOrderItemDO i : orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                .select(SalesOrderItemDO::getSoId, SalesOrderItemDO::getQuantity, SalesOrderItemDO::getCustomerInquiryId)
                .in(SalesOrderItemDO::getSoId, ids)
                .isNull(SalesOrderItemDO::getDeletedAt))) {
            counts.merge(i.getSoId(), 1, Integer::sum);
            quantities.merge(i.getSoId(), i.getQuantity() == null ? 0 : i.getQuantity(), Integer::sum);
            inquiriesByOrder.computeIfAbsent(i.getSoId(), k -> new TreeSet<>()).add(i.getCustomerInquiryId());
        }
        Map<Long, Integer> customerTypes = lookups.customerTypes(inquiriesByOrder);
        Map<Long, ProformaInvoiceDO> pis = piMapper.selectBatchIds(rows.stream().map(SalesOrderDO::getPiId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProformaInvoiceDO::getId, p -> p));
        Map<Long, CustomerDO> customers = lookups.customers(rows.stream().map(SalesOrderDO::getCustomerId).toList());
        Map<Long, String> users = lookups.userNames(rows.stream().map(SalesOrderDO::getOwnerId).toList());
        List<SalesOrderListVO> list = new ArrayList<>(rows.size());
        for (SalesOrderDO o : rows) {
            ProformaInvoiceDO pi = pis.get(o.getPiId());
            SalesOrderListVO vo = new SalesOrderListVO();
            vo.setId(o.getId());
            vo.setSoNo(o.getSoNo());
            vo.setCustomerId(o.getCustomerId());
            vo.setCustomerName(InquiryLookups.customerName(customers.get(o.getCustomerId())));
            vo.setCustomerCountry(customers.get(o.getCustomerId()) == null ? null : customers.get(o.getCustomerId()).getCountry());
            vo.setCustomerType(customerTypes.get(o.getId()));
            vo.setTotalQuantity(quantities.getOrDefault(o.getId(), 0));
            vo.setItemCount(counts.getOrDefault(o.getId(), 0));
            vo.setCurrencyCode(o.getCurrencyCode());
            vo.setTotalAmount(o.getTotalAmount());
            if (pi != null) {
                vo.setReceiptStatus(pi.getReceiptStatus());
                vo.setReceiptStatusName(SalesConstants.RECEIPT_STATUS_NAMES.get(pi.getReceiptStatus()));
                vo.setReceivedAmount(pi.getReceivedAmount());
                vo.setPiNo(pi.getPiNo());
            }
            vo.setStatus(o.getStatus());
            vo.setStatusName(SO_STATUS_NAMES.get(o.getStatus()));
            vo.setPiId(o.getPiId());
            vo.setPiVersionNo(o.getPiVersionNo());
            vo.setOwnerId(o.getOwnerId());
            vo.setOwnerName(users.get(o.getOwnerId()));
            vo.setCancelReason(StringUtils.hasText(o.getCancelReason()) ? o.getCancelReason() : null);
            vo.setCreateTime(o.getCreateTime());
            list.add(vo);
        }
        return list;
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

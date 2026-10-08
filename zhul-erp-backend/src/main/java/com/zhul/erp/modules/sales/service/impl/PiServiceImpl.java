package com.zhul.erp.modules.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.product.entity.ProductCustomsDO;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.entity.QuotationFeeDO;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import com.zhul.erp.modules.quotation.repository.QuotationItemMapper;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import com.zhul.erp.modules.quotation.support.QuotationPricing;
import com.zhul.erp.modules.quotation.support.QuotationRenderModels;
import com.zhul.erp.modules.quotation.support.QuotationStore;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.ClosePiRequest;
import com.zhul.erp.modules.sales.dto.OverduePiVO;
import com.zhul.erp.modules.sales.dto.ReopenPiRequest;
import com.zhul.erp.modules.sales.dto.AddPiItemsRequest;
import com.zhul.erp.modules.sales.dto.BankSnapshotDTO;
import com.zhul.erp.modules.sales.dto.CreatePiRequest;
import com.zhul.erp.modules.sales.dto.PartyDTO;
import com.zhul.erp.modules.sales.dto.PiCandidateCustomerVO;
import com.zhul.erp.modules.sales.dto.PiCandidateQuotationVO;
import com.zhul.erp.modules.sales.dto.PiFeeVO;
import com.zhul.erp.modules.sales.dto.PiItemVO;
import com.zhul.erp.modules.sales.dto.PiLineRequest;
import com.zhul.erp.modules.sales.dto.PartyOptionVO;
import com.zhul.erp.modules.sales.dto.PiListVO;
import com.zhul.erp.modules.sales.dto.PiPageQuery;
import com.zhul.erp.modules.sales.dto.PiStatsVO;
import com.zhul.erp.modules.sales.dto.PiVO;
import com.zhul.erp.modules.sales.dto.PiVersionVO;
import com.zhul.erp.modules.sales.dto.ReceiptVO;
import com.zhul.erp.modules.sales.dto.SavePiRequest;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.PiFeeDO;
import com.zhul.erp.modules.sales.entity.PiItemDO;
import com.zhul.erp.modules.sales.entity.PiSendLogDO;
import com.zhul.erp.modules.sales.entity.PiVersionDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.repository.PiFeeMapper;
import com.zhul.erp.modules.sales.repository.PiItemMapper;
import com.zhul.erp.modules.sales.repository.PiSendLogMapper;
import com.zhul.erp.modules.sales.repository.PiVersionMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.service.PiService;
import com.zhul.erp.modules.sales.support.PiCalculator;
import com.zhul.erp.modules.sales.support.PiClosing;
import com.zhul.erp.modules.sales.support.PiDefaults;
import com.zhul.erp.modules.sales.support.PiEditor;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.sales.support.ReceiptViews;
import com.zhul.erp.modules.sales.support.PiSummary;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.entity.BankAccountDO;
import com.zhul.erp.modules.system.repository.BankAccountMapper;
import com.zhul.erp.modules.system.service.BankAccountService;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.ExchangeRateService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PiServiceImpl implements PiService {

    private static final Set<Integer> PI_SOURCE_STATUSES = Set.of(QuotationConstants.STATUS_SENT, QuotationConstants.STATUS_PARTIAL);
    private static final int KEYWORD_LIMIT = 500;
    private static final int OVERDUE_TOP = 5;

    private final ProformaInvoiceMapper piMapper;
    private final PiVersionMapper versionMapper;
    private final PiItemMapper itemMapper;
    private final PiFeeMapper feeMapper;
    private final PiSendLogMapper sendLogMapper;
    private final PaymentReceiptMapper receiptMapper;
    private final SalesOrderMapper orderMapper;
    private final QuotationMapper quotationMapper;
    private final QuotationItemMapper quotationItemMapper;
    private final CustomerMapper customerMapper;
    private final ReceiptViews receiptViews;
    private final QuotationStore quotationStore;
    private final PiStore store;
    private final PiSummary summary;
    private final PiDefaults defaults;
    private final PiEditor editor;
    private final PiClosing closing;
    private final BankAccountService bankAccountService;
    private final DocumentNumberService documentNumberService;
    private final ExchangeRateService exchangeRateService;
    private final DataScopeResolver dataScopeResolver;
    private final CurrentUserResolver currentUser;
    private final com.zhul.erp.framework.security.PermissionChecker perm;
    private final InquiryLookups lookups;
    private final LogService logService;
    private final ObjectMapper objectMapper;

    // ---------------------------------------------------------------- 候选报价单

    @Override
    public List<PiCandidateCustomerVO> candidateCustomers(String keyword) {
        List<QuotationDO> rows = quotationMapper.selectList(scopedQuotations().select(QuotationDO::getCustomerId));
        Map<Long, Long> counts = rows.stream().collect(Collectors.groupingBy(QuotationDO::getCustomerId, LinkedHashMap::new, Collectors.counting()));
        if (counts.isEmpty()) {
            return List.of();
        }
        Map<Long, CustomerDO> customers = lookups.customers(counts.keySet());
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        List<PiCandidateCustomerVO> list = new ArrayList<>();
        counts.forEach((id, count) -> {
            CustomerDO c = customers.get(id);
            String name = InquiryLookups.customerName(c);
            if (!kw.isEmpty() && (name == null || !name.toLowerCase().contains(kw))) {
                return;
            }
            PiCandidateCustomerVO vo = new PiCandidateCustomerVO();
            vo.setCustomerId(id);
            vo.setCustomerName(name);
            vo.setCountry(c == null ? null : c.getCountry());
            vo.setQuotationCount(count);
            list.add(vo);
        });
        list.sort(Comparator.comparing(PiCandidateCustomerVO::getCustomerName, Comparator.nullsLast(String::compareTo)));
        return list;
    }

    @Override
    public List<PiCandidateQuotationVO> candidateQuotations(Long customerId) {
        List<QuotationDO> rows = quotationMapper.selectList(scopedQuotations()
                .eq(QuotationDO::getCustomerId, customerId)
                .orderByDesc(QuotationDO::getSentAt)
                .orderByDesc(QuotationDO::getId));
        return toCandidates(rows);
    }

    @Override
    public PiCandidateQuotationVO candidateQuotation(Long quotationId) {
        QuotationDO q = quotationStore.visible(quotationId);
        requireSource(q);
        return toCandidates(List.of(q)).get(0);
    }

    private LambdaQueryWrapper<QuotationDO> scopedQuotations() {
        LambdaQueryWrapper<QuotationDO> w = new LambdaQueryWrapper<QuotationDO>()
                .eq(QuotationDO::getTenantId, PiStore.tenantId())
                .in(QuotationDO::getStatus, PI_SOURCE_STATUSES)
                .isNull(QuotationDO::getDeletedAt);
        return dataScopeResolver.current().apply(w, QuotationDO::getOwnerId);
    }

    private List<PiCandidateQuotationVO> toCandidates(List<QuotationDO> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(QuotationDO::getId).toList();
        Map<Long, List<QuotationItemDO>> itemsByQuotation = quotationItemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                        .in(QuotationItemDO::getQuotationId, ids)
                        .eq(QuotationItemDO::getIsCurrent, 1)
                        // 无货行（无货且没有售价）不能开 PI
                        .and(x -> x.ne(QuotationItemDO::getNoStock, 1).or().gt(QuotationItemDO::getUnitPrice, 0))
                .isNull(QuotationItemDO::getDeletedAt)
                        .orderByAsc(QuotationItemDO::getLineNo))
                .stream().collect(Collectors.groupingBy(QuotationItemDO::getQuotationId));
        Map<Long, String> inPi = piNosByQuotationItem(itemsByQuotation.values().stream().flatMap(List::stream)
                .map(QuotationItemDO::getId).toList(), null);
        List<PiCandidateQuotationVO> list = new ArrayList<>();
        for (QuotationDO q : rows) {
            PiCandidateQuotationVO vo = new PiCandidateQuotationVO();
            vo.setQuotationId(q.getId());
            vo.setQuotationNo(q.getQuotationNo());
            vo.setStatus(q.getStatus());
            vo.setStatusName(QuotationConstants.STATUS_NAMES.get(q.getStatus()));
            vo.setCurrencyCode(q.getCurrencyCode());
            vo.setTotalAmount(q.getTotalAmount());
            vo.setSentAt(q.getSentAt());
            vo.setItems(itemsByQuotation.getOrDefault(q.getId(), List.of()).stream()
                    .filter(i -> i.getUnitPrice() != null && i.getUnitPrice().signum() > 0)
                    .map(i -> {
                        PiCandidateQuotationVO.Line l = new PiCandidateQuotationVO.Line();
                        l.setQuotationItemId(i.getId());
                        l.setLineNo(i.getLineNo());
                        l.setModel(i.getModel());
                        l.setBrand(i.getBrand());
                        l.setCategory(i.getCategory());
                        l.setQuantity(i.getQuantity());
                        l.setUnitPrice(i.getUnitPrice());
                        l.setAmount(i.getAmount());
                        l.setWon(Objects.equals(i.getWon(), 1));
                        l.setInPiNo(inPi.get(i.getId()));
                        return l;
                    }).toList());
            vo.setFees(quotationStore.fees(q.getId()).stream().map(f -> fee(f.getFeeName(), f.getAmount(), f.getAmountCny(), "")).toList());
            list.add(vo);
        }
        return list;
    }

    /** 报价行 → 所在的另一张未作废 PI 的编号（只看当前有效或编辑中的版本） */
    private Map<Long, String> piNosByQuotationItem(Collection<Long> quotationItemIds, Long excludePiId) {
        if (quotationItemIds.isEmpty()) {
            return Map.of();
        }
        List<PiItemDO> rows = itemMapper.selectList(new LambdaQueryWrapper<PiItemDO>()
                .select(PiItemDO::getPiId, PiItemDO::getVersionId, PiItemDO::getQuotationItemId)
                .in(PiItemDO::getQuotationItemId, quotationItemIds)
                .isNull(PiItemDO::getDeletedAt));
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProformaInvoiceDO> pis = piMapper.selectBatchIds(rows.stream().map(PiItemDO::getPiId).collect(Collectors.toSet()))
                .stream().filter(p -> p.getDeletedAt() == null && p.getStatus() != SalesConstants.PI_VOID
                        && p.getStatus() != SalesConstants.PI_CLOSED && !p.getId().equals(excludePiId))
                .collect(Collectors.toMap(ProformaInvoiceDO::getId, p -> p));
        if (pis.isEmpty()) {
            return Map.of();
        }
        Set<Long> liveVersions = versionMapper.selectList(new LambdaQueryWrapper<PiVersionDO>()
                        .select(PiVersionDO::getId, PiVersionDO::getPiId, PiVersionDO::getVersionNo)
                        .in(PiVersionDO::getPiId, pis.keySet())
                        .isNull(PiVersionDO::getDeletedAt))
                .stream().filter(v -> {
                    ProformaInvoiceDO p = pis.get(v.getPiId());
                    return Objects.equals(v.getVersionNo(), p.getCurrentVersionNo()) || Objects.equals(v.getVersionNo(), p.getEditingVersionNo());
                }).map(PiVersionDO::getId).collect(Collectors.toSet());
        Map<Long, String> result = new HashMap<>();
        for (PiItemDO r : rows) {
            ProformaInvoiceDO p = pis.get(r.getPiId());
            if (p != null && liveVersions.contains(r.getVersionId())) {
                result.putIfAbsent(r.getQuotationItemId(), p.getPiNo());
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- 新建

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO create(CreatePiRequest req) {
        Picked picked = pick(req.getItems(), null);
        CustomerDO customer = customerMapper.selectById(picked.customerId());
        if (customer == null || customer.getDeletedAt() != null) {
            throw new BizException("客户不存在");
        }
        ExchangeRateService.Snapshot rate = exchangeRateService.require(picked.currency());
        QuotationDO first = picked.quotations().values().iterator().next();

        ProformaInvoiceDO pi = new ProformaInvoiceDO();
        pi.setTenantId(PiStore.tenantId());
        pi.setPiNo(documentNumberService.next(DocumentType.PI));
        pi.setCustomerId(customer.getId());
        pi.setOwnerId(currentUserId());
        pi.setCurrencyCode(picked.currency());
        pi.setExchangeRate(rate.rate());
        pi.setRateTime(rate.rateTime());
        pi.setStatus(SalesConstants.PI_DRAFT);
        pi.setCurrentVersionNo(0);
        pi.setEditingVersionNo(1);
        pi.setItemCount(0);
        pi.setTotalAmount(BigDecimal.ZERO);
        pi.setTotalAmountCny(BigDecimal.ZERO);
        pi.setReceiptStatus(SalesConstants.RECEIPT_NONE);
        pi.setReceivedAmount(BigDecimal.ZERO);
        pi.setFeeDiffAmount(BigDecimal.ZERO);
        piMapper.insert(pi);

        PiVersionDO v = new PiVersionDO();
        v.setTenantId(pi.getTenantId());
        v.setPiId(pi.getId());
        v.setVersionNo(1);
        v.setStatus(SalesConstants.VERSION_EDITING);
        PartyDTO buyer = defaults.buyer(customer);
        // 没有默认收货人时与买方相同（页面标「同买方」）
        PartyDTO consignee = defaults.consignee(customer.getId());
        if (consignee == null) {
            consignee = PiDefaults.sameAs(buyer);
        }
        v.setBuyerJson(store.toJson(buyer));
        v.setBuyerPartyId(buyer.getPartyId());
        v.setConsigneeJson(store.toJson(consignee));
        v.setConsigneePartyId(consignee == null ? null : consignee.getPartyId());
        v.setDeliveryTime("");
        v.setPaymentTerm(defaults.defaultText(PiDefaults.DICT_PAYMENT_TERM, PiDefaults.FALLBACK_PAYMENT_TERM));
        boolean quotedTerms = StringUtils.hasText(first.getIncoterm());
        v.setIncoterm(quotedTerms ? first.getIncoterm() : QuotationConstants.DEFAULT_INCOTERM);
        v.setIncotermPlace(quotedTerms ? nz(first.getIncotermPlace()) : nz(customer.getCountry()));
        v.setPortOfShipment(defaults.defaultText(PiDefaults.DICT_PORT, PiDefaults.FALLBACK_PORT));
        v.setRemark("");
        v.setValidUntil(LocalDate.now().plusDays(SalesConstants.PI_VALID_DAYS));
        BankAccountDO bank = bankAccountService.defaultFor(picked.currency());
        v.setBankAccountId(bank == null ? null : bank.getId());
        v.setBankAccountJson(bank == null ? null : store.toJson(PiDefaults.bankSnapshot(bank)));
        v.setDiscountType(SalesConstants.DISCOUNT_NONE);
        v.setDiscountValue(BigDecimal.ZERO);
        versionMapper.insert(v);

        List<PiItemDO> items = newLines(pi, v, picked, 1);
        v.setDeliveryTime(defaults.deliveryTime(items.stream().map(PiItemDO::getLeadTime).toList()));
        List<PiFeeDO> fees = new ArrayList<>();
        if (picked.quotations().size() == 1 && !Boolean.FALSE.equals(req.getIncludeFees())) {
            for (QuotationFeeDO f : quotationStore.fees(first.getId())) {
                fees.add(newFee(pi, v, f.getFeeName(), f.getAmount(), ""));
            }
        }
        if (fees.isEmpty()) {
            fees.add(newFee(pi, v, "Shipping Cost", BigDecimal.ZERO, ""));
            fees.add(newFee(pi, v, "Bank Charge", BigDecimal.ZERO, ""));
        }
        calculate(pi, v, items, fees);
        items.forEach(itemMapper::insert);
        insertFees(fees);
        versionMapper.updateById(v);
        summary.refresh(pi);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("piNo", pi.getPiNo());
        after.put("quotations", picked.quotations().values().stream().map(QuotationDO::getQuotationNo).toList());
        after.put("items", items.size());
        logService.recordOperateLog(SalesConstants.MENU_PI, "新建 PI", null, after);
        return detail(pi.getId(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO addItems(Long id, AddPiItemsRequest req) {
        ProformaInvoiceDO pi = store.lockVisible(id);
        PiVersionDO v = store.requireEditing(pi);
        List<PiItemDO> items = new ArrayList<>(store.items(v.getId()));
        Set<Long> present = items.stream().map(PiItemDO::getQuotationItemId).collect(Collectors.toSet());
        for (PiLineRequest l : req.getItems()) {
            if (present.contains(l.getQuotationItemId())) {
                throw new BizException("所选型号已在这张 PI 中");
            }
        }
        Picked picked = pick(req.getItems(), pi);
        if (items.size() + picked.lines().size() > SalesConstants.MAX_ITEMS) {
            throw new BizException("一张 PI 最多 " + SalesConstants.MAX_ITEMS + " 个型号");
        }
        List<PiItemDO> added = newLines(pi, v, picked, items.size() + 1);
        items.addAll(added);
        List<PiFeeDO> fees = store.fees(v.getId());
        calculate(pi, v, items, fees);
        added.forEach(itemMapper::insert);
        items.stream().filter(i -> i.getId() != null).forEach(itemMapper::updateById);
        fees.forEach(feeMapper::updateById);
        versionMapper.updateById(v);
        summary.refresh(pi);
        return detail(id, null);
    }

    private record Picked(Long customerId, String currency, Map<Long, QuotationDO> quotations,
                          List<QuotationItemDO> lines, Map<Long, Integer> quantities) {
    }

    /** 校验选中的报价行：存在、可见、来源报价单可开 PI、同一客户与币种（追加时还须与 PI 一致） */
    private Picked pick(List<PiLineRequest> lines, ProformaInvoiceDO into) {
        if (lines == null || lines.isEmpty()) {
            throw new BizException("请选择型号");
        }
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (PiLineRequest l : lines) {
            if (quantities.put(l.getQuotationItemId(), l.getQuantity()) != null || quantities.size() > SalesConstants.MAX_ITEMS) {
                throw new BizException(quantities.size() > SalesConstants.MAX_ITEMS ? "一张 PI 最多 300 个型号" : "同一型号行不能重复选择");
            }
        }
        Map<Long, QuotationItemDO> byId = quotationItemMapper.selectBatchIds(quantities.keySet()).stream()
                .filter(i -> i.getDeletedAt() == null && Objects.equals(i.getTenantId(), PiStore.tenantId()))
                .collect(Collectors.toMap(QuotationItemDO::getId, i -> i));
        if (byId.size() != quantities.size()) {
            throw new BizException("部分型号不存在，请刷新后再试");
        }
        if (byId.values().stream().anyMatch(i -> !Objects.equals(i.getIsCurrent(), 1))) {
            throw new BizException("报价单已发出新版本，请刷新后重新选择型号");
        }
        List<QuotationItemDO> ordered = quantities.keySet().stream().map(byId::get).toList();
        Map<Long, QuotationDO> quotations = new LinkedHashMap<>();
        for (QuotationItemDO i : ordered) {
            if (!quotations.containsKey(i.getQuotationId())) {
                QuotationDO q = quotationStore.visible(i.getQuotationId());
                requireSource(q);
                quotations.put(q.getId(), q);
            }
            if (i.getUnitPrice() == null || i.getUnitPrice().signum() <= 0) {
                throw new BizException("型号 " + i.getModel() + " 没有售价，不能开 PI");
            }
        }
        Set<Long> customers = quotations.values().stream().map(QuotationDO::getCustomerId).collect(Collectors.toSet());
        if (into != null) {
            customers.add(into.getCustomerId());
        }
        if (customers.size() > 1) {
            throw new BizException("只能选择同一客户的报价单");
        }
        Set<String> currencies = new LinkedHashSet<>();
        if (into != null) {
            currencies.add(into.getCurrencyCode());
        }
        quotations.values().forEach(q -> currencies.add(q.getCurrencyCode()));
        if (currencies.size() > 1) {
            throw new BizException("所选报价单币种不同（" + String.join("、", currencies) + "），请分开开 PI");
        }
        return new Picked(customers.iterator().next(), currencies.iterator().next(), quotations, ordered, quantities);
    }

    private static void requireSource(QuotationDO q) {
        if (!PI_SOURCE_STATUSES.contains(q.getStatus())) {
            throw new BizException("报价单 " + q.getQuotationNo() + " 当前" + QuotationConstants.STATUS_NAMES.get(q.getStatus())
                    + "，只有已发送或部分成交的报价单可以开 PI");
        }
    }

    private List<PiItemDO> newLines(ProformaInvoiceDO pi, PiVersionDO v, Picked picked, int startLine) {
        Map<Long, ProductCustomsDO> customs = defaults.customsByInquiryItem(picked.lines().stream()
                .map(QuotationItemDO::getInquiryItemId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<PiItemDO> list = new ArrayList<>();
        int line = startLine;
        for (QuotationItemDO s : picked.lines()) {
            PiItemDO i = new PiItemDO();
            i.setTenantId(pi.getTenantId());
            i.setPiId(pi.getId());
            i.setVersionId(v.getId());
            i.setLineNo(line++);
            i.setQuotationId(s.getQuotationId());
            i.setQuotationItemId(s.getId());
            i.setCustomerInquiryId(s.getCustomerInquiryId());
            i.setInquiryItemId(s.getInquiryItemId());
            i.setModel(nz(s.getModel()));
            i.setBrand(nz(s.getBrand()));
            i.setCategory(nz(s.getCategory()));
            i.setDescription(nz(s.getDescription()));
            i.setDescriptionEn(nz(s.getDescriptionEn()));
            i.setItemCondition(nz(s.getItemCondition()));
            i.setLeadTime(nz(s.getLeadTime()));
            i.setWarranty(StringUtils.hasText(s.getWarranty()) ? s.getWarranty() : SalesConstants.DEFAULT_WARRANTY);
            Integer qty = picked.quantities().get(s.getId());
            i.setQuantity(qty == null ? s.getQuantity() : qty);
            i.setQuotedPrice(s.getUnitPrice());
            i.setUnitPrice(s.getUnitPrice());
            i.setCostPrice(s.getCostPrice());
            i.setFloorMargin(s.getFloorMargin());
            ProductCustomsDO c = customs.get(s.getInquiryItemId());
            i.setHsCode(c == null ? "" : nz(c.getHsCode()));
            i.setOriginCountry(c == null ? "" : nz(c.getOriginCountry()));
            i.setRemark("");
            list.add(i);
        }
        return list;
    }

    private static PiFeeDO newFee(ProformaInvoiceDO pi, PiVersionDO v, String name, BigDecimal amount, String remark) {
        PiFeeDO f = new PiFeeDO();
        f.setTenantId(pi.getTenantId());
        f.setPiId(pi.getId());
        f.setVersionId(v.getId());
        f.setFeeName(name);
        f.setAmount(amount == null ? BigDecimal.ZERO : amount);
        f.setRemark(remark == null ? "" : remark);
        return f;
    }

    private void insertFees(List<PiFeeDO> fees) {
        int sort = 1;
        for (PiFeeDO f : fees) {
            f.setSortOrder(sort++);
            feeMapper.insert(f);
        }
    }

    private static void calculate(ProformaInvoiceDO pi, PiVersionDO v, List<PiItemDO> items, List<PiFeeDO> fees) {
        for (PiItemDO i : items) {
            PiCalculator.applyLine(i, pi.getExchangeRate());
        }
        PiCalculator.applyTotals(v, items, fees, pi.getExchangeRate());
    }

    // ---------------------------------------------------------------- 编辑

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO save(Long id, SavePiRequest req) {
        ProformaInvoiceDO pi = store.lockVisible(id);
        PiVersionDO v = store.requireEditing(pi);
        List<PiItemDO> existing = store.items(v.getId());
        PiEditor.Applied applied = editor.apply(pi, v, existing, req, true);
        List<PiItemDO> items = applied.items();
        List<PiFeeDO> fees = applied.fees();
        Set<Long> seen = items.stream().map(PiItemDO::getId).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        for (PiItemDO old : existing) {
            if (!seen.contains(old.getId())) {
                old.setDeletedAt(now);
                itemMapper.updateById(old);
            }
        }
        items.forEach(itemMapper::updateById);
        for (PiFeeDO old : store.fees(v.getId())) {
            old.setDeletedAt(now);
            feeMapper.updateById(old);
        }
        insertFees(fees);
        versionMapper.updateById(v);
        summary.refresh(pi);
        return detail(id, null);
    }

    // ---------------------------------------------------------------- 状态与版本

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO markSent(Long id, int channel) {
        if (!SalesConstants.CHANNEL_NAMES.containsKey(channel)) {
            throw new BizException("发送方式不正确");
        }
        ProformaInvoiceDO pi = store.lockVisible(id);
        if (pi.getStatus() == SalesConstants.PI_VOID) {
            throw new BizException("PI 已作废，不能发送");
        }
        if (pi.getStatus() == SalesConstants.PI_CLOSED) {
            throw new BizException(SalesConstants.CLOSED_MESSAGE);
        }
        PiVersionDO editing = pi.getStatus() == SalesConstants.PI_CONVERTED ? null : store.version(id, pi.getEditingVersionNo());
        int versionNo;
        if (editing != null) {
            List<PiItemDO> items = store.items(editing.getId());
            if (items.isEmpty()) {
                throw new BizException("PI 没有型号，不能发送");
            }
            if (editing.getBankAccountId() == null) {
                throw new BizException("请先选择收款账户（可在「系统管理 → 收款账户」中维护）");
            }
            if (!StringUtils.hasText(editing.getBuyerJson())) {
                throw new BizException("请填写买方");
            }
            LocalDateTime now = LocalDateTime.now();
            editing.setStatus(SalesConstants.VERSION_SENT);
            editing.setSentAt(now);
            versionMapper.updateById(editing);
            boolean firstSend = pi.getStatus() == SalesConstants.PI_DRAFT;
            pi.setStatus(SalesConstants.PI_SENT);
            pi.setCurrentVersionNo(editing.getVersionNo());
            pi.setEditingVersionNo(null);
            pi.setSentAt(now);
            summary.refresh(pi);
            versionNo = editing.getVersionNo();
            logService.recordOperateLog(SalesConstants.MENU_PI, firstSend ? "发送 PI" : "发送 PI 新版本",
                    Map.of("piNo", pi.getPiNo()), Map.of("piNo", pi.getPiNo(), "version", "Rev." + versionNo,
                            "total", pi.getCurrencyCode() + " " + pi.getTotalAmount(), "channel", SalesConstants.CHANNEL_NAMES.get(channel)));
        } else {
            versionNo = pi.getCurrentVersionNo();
        }
        PiSendLogDO log = new PiSendLogDO();
        log.setTenantId(pi.getTenantId());
        log.setPiId(id);
        log.setVersionNo(versionNo);
        log.setChannel(channel);
        log.setSentBy(currentUserId());
        log.setSentAt(LocalDateTime.now());
        sendLogMapper.insert(log);
        return detail(id, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO revise(Long id) {
        ProformaInvoiceDO pi = store.lockVisible(id);
        if (pi.getStatus() == SalesConstants.PI_CONVERTED) {
            throw new BizException(store.convertedMessage(pi));
        }
        if (pi.getStatus() == SalesConstants.PI_VOID) {
            throw new BizException("PI 已作废，不能修改");
        }
        if (pi.getStatus() == SalesConstants.PI_CLOSED) {
            throw new BizException(SalesConstants.CLOSED_MESSAGE);
        }
        if (pi.getEditingVersionNo() != null) {
            return detail(id, null);
        }
        PiVersionDO cur = store.version(id, pi.getCurrentVersionNo());
        int next = store.versions(id).stream().mapToInt(PiVersionDO::getVersionNo).max().orElse(0) + 1;
        PiVersionDO v = new PiVersionDO();
        copyHeader(cur, v);
        v.setTenantId(pi.getTenantId());
        v.setPiId(id);
        v.setVersionNo(next);
        v.setStatus(SalesConstants.VERSION_EDITING);
        versionMapper.insert(v);
        for (PiItemDO s : store.items(cur.getId())) {
            s.setId(null);
            s.setVersionId(v.getId());
            s.setCreateTime(null);
            s.setCreateBy(null);
            s.setUpdateTime(null);
            s.setUpdateBy(null);
            itemMapper.insert(s);
        }
        List<PiFeeDO> fees = store.fees(cur.getId());
        for (PiFeeDO f : fees) {
            f.setId(null);
            f.setVersionId(v.getId());
            f.setCreateTime(null);
            f.setCreateBy(null);
            f.setUpdateTime(null);
            f.setUpdateBy(null);
            feeMapper.insert(f);
        }
        pi.setEditingVersionNo(next);
        piMapper.updateById(pi);
        logService.recordOperateLog(SalesConstants.MENU_PI, "修改 PI（生成新版本）", Map.of("piNo", pi.getPiNo(), "version", "Rev." + cur.getVersionNo()),
                Map.of("piNo", pi.getPiNo(), "version", "Rev." + next));
        return detail(id, null);
    }

    private static void copyHeader(PiVersionDO s, PiVersionDO t) {
        t.setBuyerJson(s.getBuyerJson());
        t.setConsigneeJson(s.getConsigneeJson());
        t.setBuyerPartyId(s.getBuyerPartyId());
        t.setConsigneePartyId(s.getConsigneePartyId());
        t.setDeliveryTime(s.getDeliveryTime());
        t.setPaymentTerm(s.getPaymentTerm());
        t.setIncoterm(s.getIncoterm());
        t.setIncotermPlace(s.getIncotermPlace());
        t.setPortOfShipment(s.getPortOfShipment());
        t.setRemark(s.getRemark());
        t.setValidUntil(s.getValidUntil());
        t.setBankAccountId(s.getBankAccountId());
        t.setBankAccountJson(s.getBankAccountJson());
        t.setDiscountType(s.getDiscountType());
        t.setDiscountValue(s.getDiscountValue());
        t.setDiscountAmount(s.getDiscountAmount());
        t.setDiscountAmountCny(s.getDiscountAmountCny());
        t.setItemAmount(s.getItemAmount());
        t.setFeeAmount(s.getFeeAmount());
        t.setTotalAmount(s.getTotalAmount());
        t.setTotalAmountCny(s.getTotalAmountCny());
        t.setNetProfit(s.getNetProfit());
        t.setNetProfitCny(s.getNetProfitCny());
        t.setMarginRate(s.getMarginRate());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO abandon(Long id) {
        ProformaInvoiceDO pi = store.lockVisible(id);
        if (pi.getEditingVersionNo() == null) {
            throw new BizException("没有正在修改的版本");
        }
        if (pi.getCurrentVersionNo() == null || pi.getCurrentVersionNo() == 0) {
            throw new BizException("草稿 PI 还没有发送过，不需要放弃版本，可以直接删除");
        }
        PiVersionDO v = store.version(id, pi.getEditingVersionNo());
        v.setStatus(SalesConstants.VERSION_ABANDONED);
        versionMapper.updateById(v);
        pi.setEditingVersionNo(null);
        summary.refresh(pi);
        logService.recordOperateLog(SalesConstants.MENU_PI, "放弃 PI 新版本", null,
                Map.of("piNo", pi.getPiNo(), "version", "Rev." + v.getVersionNo()));
        return detail(id, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO voidPi(Long id) {
        ProformaInvoiceDO pi = store.lockVisible(id);
        if (pi.getStatus() == SalesConstants.PI_CONVERTED) {
            throw new BizException("PI 已转成订单 " + soNo(pi) + "，不能作废");
        }
        if (pi.getStatus() == SalesConstants.PI_VOID) {
            throw new BizException("PI 已作废");
        }
        if (pi.getStatus() == SalesConstants.PI_CLOSED) {
            throw new BizException(SalesConstants.CLOSED_MESSAGE);
        }
        boolean hasReceipt = receiptMapper.selectCount(new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getPiId, id)
                .eq(PaymentReceiptDO::getKind, SalesConstants.KIND_RECEIPT)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt)) > 0;
        if (hasReceipt) {
            throw new BizException("已有到账记录的 PI 不能作废");
        }
        String before = SalesConstants.PI_STATUS_NAMES.get(pi.getStatus());
        if (pi.getEditingVersionNo() != null && pi.getCurrentVersionNo() > 0) {
            PiVersionDO v = store.version(id, pi.getEditingVersionNo());
            v.setStatus(SalesConstants.VERSION_ABANDONED);
            versionMapper.updateById(v);
            pi.setEditingVersionNo(null);
        }
        pi.setStatus(SalesConstants.PI_VOID);
        piMapper.updateById(pi);
        logService.recordOperateLog(SalesConstants.MENU_PI, "作废 PI", Map.of("piNo", pi.getPiNo(), "status", before),
                Map.of("piNo", pi.getPiNo(), "status", "已作废"));
        return detail(id, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO close(Long id, ClosePiRequest req) {
        ProformaInvoiceDO pi = store.lockVisible(id);
        List<String> notices = closing.close(pi, req);
        PiVO vo = detail(id, null);
        vo.setNotices(notices);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO reopen(Long id, ReopenPiRequest req) {
        ProformaInvoiceDO pi = store.lockVisible(id);
        closing.reopen(pi, req.getValidUntil());
        return detail(id, null);
    }

    @Override
    public OverduePiVO overdue() {
        List<ProformaInvoiceDO> rows = piMapper.selectList(expiredUnpaid(scoped())
                .orderByAsc(ProformaInvoiceDO::getValidUntil).orderByAsc(ProformaInvoiceDO::getId));
        OverduePiVO vo = new OverduePiVO();
        vo.setCount((long) rows.size());
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        rows.forEach(p -> totals.merge(p.getCurrencyCode(), nz(p.getTotalAmount()), BigDecimal::add));
        vo.setTotals(totals.entrySet().stream().map(e -> {
            OverduePiVO.CurrencyTotal t = new OverduePiVO.CurrencyTotal();
            t.setCurrencyCode(e.getKey());
            t.setAmount(e.getValue());
            return t;
        }).toList());
        vo.setTop(toListVos(rows.stream().limit(OVERDUE_TOP).toList()));
        return vo;
    }

    /** 已过期未收款：已发送、未付款（没有水单也没有到账）且有效期早于今天；列表筛选与工作台卡片共用 */
    private static LambdaQueryWrapper<ProformaInvoiceDO> expiredUnpaid(LambdaQueryWrapper<ProformaInvoiceDO> w) {
        return w.eq(ProformaInvoiceDO::getStatus, SalesConstants.PI_SENT)
                .eq(ProformaInvoiceDO::getReceiptStatus, SalesConstants.RECEIPT_NONE)
                .lt(ProformaInvoiceDO::getValidUntil, LocalDate.now());
    }

    /** 已过期天数：已发送、未付款且过了有效期时有值 */
    private static Long expiredDays(ProformaInvoiceDO p) {
        if (p.getStatus() != SalesConstants.PI_SENT || p.getReceiptStatus() != SalesConstants.RECEIPT_NONE
                || p.getValidUntil() == null || !p.getValidUntil().isBefore(LocalDate.now())) {
            return null;
        }
        return ChronoUnit.DAYS.between(p.getValidUntil(), LocalDate.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ProformaInvoiceDO pi = store.lockVisible(id);
        if (pi.getStatus() != SalesConstants.PI_DRAFT) {
            throw new BizException("只有草稿 PI 可以删除，已发送的 PI 请作废");
        }
        pi.setDeletedAt(LocalDateTime.now());
        piMapper.updateById(pi);
        logService.recordOperateLog(SalesConstants.MENU_PI, "删除草稿 PI", Map.of("piNo", pi.getPiNo()), null);
    }

    private String soNo(ProformaInvoiceDO pi) {
        SalesOrderDO o = store.activeOrder(pi.getId());
        return o == null ? "" : o.getSoNo();
    }

    // ---------------------------------------------------------------- 查看

    @Override
    public PiVO detail(Long id, Integer versionNo) {
        ProformaInvoiceDO pi = store.visible(id);
        Integer target = versionNo != null ? versionNo
                : pi.getEditingVersionNo() != null ? pi.getEditingVersionNo() : pi.getCurrentVersionNo();
        PiVersionDO v = store.version(id, target);
        if (v == null) {
            throw new BizException("PI 版本不存在");
        }
        CustomerDO customer = customerMapper.selectById(pi.getCustomerId());
        Map<Long, String> users = lookups.userNames(List.of(pi.getOwnerId()));

        PiVO vo = new PiVO();
        vo.setId(pi.getId());
        vo.setPiNo(pi.getPiNo());
        vo.setCustomerId(pi.getCustomerId());
        vo.setCustomerName(InquiryLookups.customerName(customer));
        vo.setCustomerCountry(customer == null ? null : customer.getCountry());
        vo.setOwnerId(pi.getOwnerId());
        vo.setOwnerName(users.get(pi.getOwnerId()));
        vo.setCurrencyCode(pi.getCurrencyCode());
        vo.setExchangeRate(pi.getExchangeRate());
        vo.setRateTime(pi.getRateTime());
        vo.setStatus(pi.getStatus());
        vo.setStatusName(SalesConstants.PI_STATUS_NAMES.get(pi.getStatus()));
        vo.setReceiptStatus(pi.getReceiptStatus());
        vo.setReceiptStatusName(SalesConstants.RECEIPT_STATUS_NAMES.get(pi.getReceiptStatus()));
        vo.setReceivedAmount(pi.getReceivedAmount());
        vo.setFeeDiffAmount(pi.getFeeDiffAmount());
        vo.setRemainingAmount(nz(pi.getTotalAmount()).subtract(nz(pi.getReceivedAmount())).subtract(nz(pi.getFeeDiffAmount())));
        vo.setCurrentVersionNo(pi.getCurrentVersionNo());
        vo.setEditingVersionNo(pi.getEditingVersionNo());
        vo.setEditable(Objects.equals(v.getVersionNo(), pi.getEditingVersionNo())
                && pi.getStatus() != SalesConstants.PI_CONVERTED && pi.getStatus() != SalesConstants.PI_VOID
                && pi.getStatus() != SalesConstants.PI_CLOSED);
        vo.setValidUntil(pi.getValidUntil());
        Long days = expiredDays(pi);
        vo.setExpired(days != null);
        vo.setExpiredDays(days);
        if (pi.getStatus() == SalesConstants.PI_CLOSED) {
            vo.setCloseReason(pi.getCloseReason());
            vo.setCloseReasonName(pi.getCloseReasonName());
            vo.setCloseNote(pi.getCloseNote());
            vo.setClosedAt(pi.getClosedAt());
            vo.setClosedByName(pi.getClosedBy() == null ? null : lookups.userNames(List.of(pi.getClosedBy())).get(pi.getClosedBy()));
        }
        List<PiItemDO> items = store.items(v.getId());
        vo.setVersion(toVersionVo(v, items, store.fees(v.getId())));
        vo.setVersions(store.versions(id).stream().map(x -> {
            PiVO.VersionBrief b = new PiVO.VersionBrief();
            b.setVersionNo(x.getVersionNo());
            b.setStatus(x.getStatus());
            b.setTotalAmount(x.getTotalAmount());
            b.setSentAt(x.getSentAt());
            b.setCreateTime(x.getCreateTime());
            return b;
        }).toList());
        vo.setSendLogs(sendLogs(id));
        vo.setReceipts(receipts(id));
        vo.setPlatformFeeAmount(vo.getReceipts().stream()
                .filter(r -> r.getKind() == SalesConstants.KIND_RECEIPT && r.getStatus() == SalesConstants.RECORD_VALID)
                .map(r -> r.getPlatformFee() == null ? BigDecimal.ZERO : r.getPlatformFee()).reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setNetAmountCny(vo.getReceipts().stream()
                .filter(r -> r.getKind() == SalesConstants.KIND_RECEIPT && r.getStatus() == SalesConstants.RECORD_VALID)
                .map(r -> r.getNetAmountCny() == null ? BigDecimal.ZERO : r.getNetAmountCny()).reduce(BigDecimal.ZERO, BigDecimal::add));
        SalesOrderDO order = store.activeOrder(id);
        if (order != null) {
            PiVO.OrderBrief o = new PiVO.OrderBrief();
            o.setId(order.getId());
            o.setSoNo(order.getSoNo());
            o.setStatus(order.getStatus());
            o.setCreateTime(order.getCreateTime());
            vo.setOrder(o);
        }
        Set<Long> quotationIds = items.stream().map(PiItemDO::getQuotationId).collect(Collectors.toCollection(TreeSet::new));
        vo.setQuotations(quotationIds.isEmpty() ? List.of() : quotationMapper.selectBatchIds(quotationIds).stream().map(q -> {
            PiVO.QuotationBrief b = new PiVO.QuotationBrief();
            b.setId(q.getId());
            b.setQuotationNo(q.getQuotationNo());
            b.setStatus(q.getStatus());
            return b;
        }).toList());
        vo.setHasBankAccount(bankAccountService.defaultFor(pi.getCurrencyCode()) != null || v.getBankAccountId() != null);
        vo.setCreateTime(pi.getCreateTime());
        return vo;
    }

    private PiVersionVO toVersionVo(PiVersionDO v, List<PiItemDO> items, List<PiFeeDO> fees) {
        QuotationRenderModels.Labels labels = quotationStore.labels();
        Map<Long, String> quotationNos = quotationNos(items.stream().map(PiItemDO::getQuotationId).toList());
        Map<Long, String> inquiryCodes = lookups.inquiryCodes(items.stream().map(PiItemDO::getCustomerInquiryId).toList());
        PiVersionVO vo = new PiVersionVO();
        vo.setVersionNo(v.getVersionNo());
        vo.setStatus(v.getStatus());
        vo.setBuyer(store.fromJson(v.getBuyerJson(), PartyDTO.class));
        vo.setConsignee(store.fromJson(v.getConsigneeJson(), PartyDTO.class));
        vo.setDeliveryTime(v.getDeliveryTime());
        vo.setPaymentTerm(v.getPaymentTerm());
        vo.setIncoterm(v.getIncoterm());
        vo.setIncotermPlace(v.getIncotermPlace());
        vo.setPortOfShipment(v.getPortOfShipment());
        vo.setRemark(v.getRemark());
        vo.setValidUntil(v.getValidUntil());
        vo.setBankAccount(store.fromJson(v.getBankAccountJson(), BankSnapshotDTO.class));
        vo.setDiscountType(v.getDiscountType());
        vo.setDiscountValue(v.getDiscountValue());
        vo.setDiscountAmount(v.getDiscountAmount());
        vo.setItemAmount(v.getItemAmount());
        vo.setFeeAmount(v.getFeeAmount());
        vo.setTotalAmount(v.getTotalAmount());
        vo.setTotalAmountCny(v.getTotalAmountCny());
        vo.setNetProfit(v.getNetProfit());
        vo.setNetProfitCny(v.getNetProfitCny());
        vo.setMarginRate(v.getMarginRate());
        vo.setSentAt(v.getSentAt());
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
            x.setDescriptionEn(i.getDescriptionEn());
            x.setItemCondition(i.getItemCondition());
            x.setConditionName(labels.conditions().get(i.getItemCondition()));
            x.setLeadTime(i.getLeadTime());
            x.setLeadTimeName(labels.leadTimes().get(i.getLeadTime()));
            x.setWarranty(i.getWarranty());
            x.setQuantity(i.getQuantity());
            x.setQuotedPrice(i.getQuotedPrice());
            x.setUnitPrice(i.getUnitPrice());
            x.setUnitPriceCny(i.getUnitPriceCny());
            x.setAmount(i.getAmount());
            x.setAmountCny(i.getAmountCny());
            x.setCostPrice(i.getCostPrice());
            x.setFloorMargin(i.getFloorMargin());
            x.setMarginRate(i.getMarginRate());
            x.setBelowFloor(QuotationPricing.belowFloor(i.getMarginRate(), i.getFloorMargin()));
            x.setNetProfit(i.getNetProfit());
            x.setNetProfitCny(i.getNetProfitCny());
            x.setHsCode(i.getHsCode());
            x.setOriginCountry(i.getOriginCountry());
            x.setRemark(i.getRemark());
            return x;
        }).toList());
        vo.setFees(fees.stream().map(f -> fee(f.getFeeName(), f.getAmount(), f.getAmountCny(), f.getRemark())).toList());
        return vo;
    }

    private static PiFeeVO fee(String name, BigDecimal amount, BigDecimal amountCny, String remark) {
        PiFeeVO f = new PiFeeVO();
        f.setFeeName(name);
        f.setAmount(amount);
        f.setAmountCny(amountCny);
        f.setRemark(remark);
        return f;
    }

    private Map<Long, String> quotationNos(Collection<Long> ids) {
        Set<Long> unique = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (unique.isEmpty()) {
            return Map.of();
        }
        return quotationMapper.selectBatchIds(unique).stream().collect(Collectors.toMap(QuotationDO::getId, QuotationDO::getQuotationNo));
    }

    private List<PiVO.SendLog> sendLogs(Long piId) {
        List<PiSendLogDO> rows = sendLogMapper.selectList(new LambdaQueryWrapper<PiSendLogDO>()
                .eq(PiSendLogDO::getPiId, piId)
                .isNull(PiSendLogDO::getDeletedAt)
                .orderByAsc(PiSendLogDO::getId));
        Map<Long, String> names = lookups.userNames(rows.stream().map(PiSendLogDO::getSentBy).toList());
        return rows.stream().map(r -> {
            PiVO.SendLog s = new PiVO.SendLog();
            s.setVersionNo(r.getVersionNo());
            s.setChannel(r.getChannel());
            s.setChannelName(SalesConstants.CHANNEL_NAMES.get(r.getChannel()));
            s.setSentByName(names.get(r.getSentBy()));
            s.setSentAt(r.getSentAt());
            return s;
        }).toList();
    }

    /** PI 的收款记录（含已作废，水单删除后不再显示） */
    @Override
    public List<ReceiptVO> receipts(Long piId) {
        return receiptViews.list(new LambdaQueryWrapper<PaymentReceiptDO>().eq(PaymentReceiptDO::getPiId, piId));
    }

    @Override
    public PageResult<PiListVO> page(PiPageQuery q) {
        int tenant = PiStore.tenantId();
        LambdaQueryWrapper<ProformaInvoiceDO> w = scoped();
        if (q.getStatus() != null) {
            w.eq(ProformaInvoiceDO::getStatus, q.getStatus());
        }
        if (q.getReceiptStatus() != null) {
            w.eq(ProformaInvoiceDO::getReceiptStatus, q.getReceiptStatus());
        }
        if (Boolean.TRUE.equals(q.getExpiredUnpaid())) {
            expiredUnpaid(w);
        }
        if (q.getOwnerId() != null) {
            w.eq(ProformaInvoiceDO::getOwnerId, q.getOwnerId());
        }
        if (q.getCreatedFrom() != null) {
            w.ge(ProformaInvoiceDO::getCreateTime, q.getCreatedFrom().atStartOfDay());
        }
        if (q.getCreatedTo() != null) {
            w.lt(ProformaInvoiceDO::getCreateTime, q.getCreatedTo().plusDays(1).atStartOfDay());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            List<Long> customerIds = customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                            .select(CustomerDO::getId)
                            .eq(CustomerDO::getTenantId, tenant)
                            .and(x -> x.like(CustomerDO::getName, kw).or().like(CustomerDO::getShortName, kw))
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(CustomerDO::getId).toList();
            List<Long> byModel = itemMapper.selectList(new LambdaQueryWrapper<PiItemDO>()
                            .select(PiItemDO::getPiId)
                            .eq(PiItemDO::getTenantId, tenant)
                            .like(PiItemDO::getModel, kw)
                            .isNull(PiItemDO::getDeletedAt)
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(PiItemDO::getPiId).distinct().toList();
            // 编号是「前缀 + 主体」，按包含匹配即同时兼容带与不带前缀的关键词
            w.and(x -> {
                x.like(ProformaInvoiceDO::getPiNo, kw);
                if (!customerIds.isEmpty()) {
                    x.or().in(ProformaInvoiceDO::getCustomerId, customerIds);
                }
                if (!byModel.isEmpty()) {
                    x.or().in(ProformaInvoiceDO::getId, byModel);
                }
            });
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = piMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        w.orderByDesc(ProformaInvoiceDO::getCreateTime).orderByDesc(ProformaInvoiceDO::getId)
                .last("LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toListVos(piMapper.selectList(w)));
    }

    @Override
    public List<PartyOptionVO> parties(Long id, Long customerId) {
        ProformaInvoiceDO pi = store.visible(id);
        CustomerDO customer = customerMapper.selectById(customerId == null ? pi.getCustomerId() : customerId);
        if (customer == null || customer.getDeletedAt() != null || !Objects.equals(customer.getTenantId(), PiStore.tenantId())
                || (customerId != null && !customerId.equals(pi.getCustomerId()) && !dataScopeResolver.current().canSee(customer.getOwnerId()))) {
            throw new BizException("客户不存在");
        }
        return defaults.options(customer);
    }

    @Override
    public PiStatsVO stats() {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        PiStatsVO vo = new PiStatsVO();
        vo.setDraftCount(piMapper.selectCount(scoped().eq(ProformaInvoiceDO::getStatus, SalesConstants.PI_DRAFT)));
        vo.setSlipOnlyCount(piMapper.selectCount(scoped().ne(ProformaInvoiceDO::getStatus, SalesConstants.PI_VOID)
                .eq(ProformaInvoiceDO::getReceiptStatus, SalesConstants.RECEIPT_SLIP_ONLY)));
        List<Long> piIds = piMapper.selectList(scoped().select(ProformaInvoiceDO::getId)).stream().map(ProformaInvoiceDO::getId).toList();
        List<PaymentReceiptDO> month = piIds.isEmpty() ? List.of() : receiptMapper.selectList(new LambdaQueryWrapper<PaymentReceiptDO>()
                .in(PaymentReceiptDO::getPiId, piIds)
                .eq(PaymentReceiptDO::getKind, SalesConstants.KIND_RECEIPT)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt)
                .ge(PaymentReceiptDO::getReceiptDate, monthStart.toLocalDate()));
        vo.setMonthReceivedUsd(month.stream().filter(r -> "USD".equals(r.getCurrencyCode())).map(PaymentReceiptDO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setMonthReceivedCny(month.stream().map(PaymentReceiptDO::getAmountCny).reduce(BigDecimal.ZERO, BigDecimal::add));
        LambdaQueryWrapper<SalesOrderDO> orders = new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getTenantId, PiStore.tenantId())
                .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                .ge(SalesOrderDO::getCreateTime, monthStart)
                .isNull(SalesOrderDO::getDeletedAt);
        dataScopeResolver.current().apply(orders, SalesOrderDO::getOwnerId);
        List<SalesOrderDO> monthOrders = orderMapper.selectList(orders);
        vo.setMonthOrderCount((long) monthOrders.size());
        Set<Long> orderPis = monthOrders.stream().map(SalesOrderDO::getPiId).collect(Collectors.toSet());
        vo.setMonthOrderUnpaid(orderPis.isEmpty() ? 0L : piMapper.selectCount(new LambdaQueryWrapper<ProformaInvoiceDO>()
                .in(ProformaInvoiceDO::getId, orderPis)
                .eq(ProformaInvoiceDO::getReceiptStatus, SalesConstants.RECEIPT_SLIP_ONLY)));
        return vo;
    }

    @Override
    public List<PiListVO> byQuotation(Long quotationId) {
        quotationStore.visible(quotationId);
        Set<Long> piIds = itemMapper.selectList(new LambdaQueryWrapper<PiItemDO>()
                        .select(PiItemDO::getPiId)
                        .eq(PiItemDO::getQuotationId, quotationId)
                        .isNull(PiItemDO::getDeletedAt))
                .stream().map(PiItemDO::getPiId).collect(Collectors.toSet());
        if (piIds.isEmpty()) {
            return List.of();
        }
        // 报价单可见即可看到由它开出的 PI 摘要（不受 PI 数据范围限制）
        return toListVos(piMapper.selectList(new LambdaQueryWrapper<ProformaInvoiceDO>()
                .in(ProformaInvoiceDO::getId, piIds)
                .isNull(ProformaInvoiceDO::getDeletedAt)
                .orderByDesc(ProformaInvoiceDO::getCreateTime)));
    }

    private LambdaQueryWrapper<ProformaInvoiceDO> scoped() {
        LambdaQueryWrapper<ProformaInvoiceDO> w = new LambdaQueryWrapper<ProformaInvoiceDO>()
                .eq(ProformaInvoiceDO::getTenantId, PiStore.tenantId())
                .isNull(ProformaInvoiceDO::getDeletedAt);
        return dataScopeResolver.current().apply(w, ProformaInvoiceDO::getOwnerId);
    }

    private List<PiListVO> toListVos(List<ProformaInvoiceDO> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(ProformaInvoiceDO::getId).toList();
        // 来源报价单：取当前有效版本（没有时取编辑中的版本）的行
        Map<Long, Integer> shownVersion = new HashMap<>();
        rows.forEach(p -> shownVersion.put(p.getId(), p.getCurrentVersionNo() != null && p.getCurrentVersionNo() > 0
                ? p.getCurrentVersionNo() : p.getEditingVersionNo()));
        Map<Long, Long> versionIdByPi = versionMapper.selectList(new LambdaQueryWrapper<PiVersionDO>()
                        .select(PiVersionDO::getId, PiVersionDO::getPiId, PiVersionDO::getVersionNo)
                        .in(PiVersionDO::getPiId, ids))
                .stream().filter(v -> Objects.equals(shownVersion.get(v.getPiId()), v.getVersionNo()))
                .collect(Collectors.toMap(PiVersionDO::getPiId, PiVersionDO::getId, (a, b) -> a));
        Map<Long, Set<Long>> quotationsByPi = new HashMap<>();
        Map<Long, Set<Long>> inquiriesByPi = new HashMap<>();
        Map<Long, Integer> quantities = new HashMap<>();
        if (!versionIdByPi.isEmpty()) {
            for (PiItemDO i : itemMapper.selectList(new LambdaQueryWrapper<PiItemDO>()
                    .select(PiItemDO::getPiId, PiItemDO::getQuotationId, PiItemDO::getCustomerInquiryId, PiItemDO::getQuantity)
                    .in(PiItemDO::getVersionId, versionIdByPi.values())
                    .isNull(PiItemDO::getDeletedAt))) {
                quotationsByPi.computeIfAbsent(i.getPiId(), k -> new TreeSet<>()).add(i.getQuotationId());
                inquiriesByPi.computeIfAbsent(i.getPiId(), k -> new TreeSet<>()).add(i.getCustomerInquiryId());
                quantities.merge(i.getPiId(), nz(i.getQuantity()), Integer::sum);
            }
        }
        Map<Long, Integer> customerTypes = lookups.customerTypes(inquiriesByPi);
        Map<Long, String> quotationNos = quotationNos(quotationsByPi.values().stream().flatMap(Set::stream).toList());
        Map<Long, SalesOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                        .in(SalesOrderDO::getPiId, ids)
                        .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                        .isNull(SalesOrderDO::getDeletedAt))
                .stream().collect(Collectors.toMap(SalesOrderDO::getPiId, o -> o, (a, b) -> a));
        Map<Long, CustomerDO> customers = lookups.customers(rows.stream().map(ProformaInvoiceDO::getCustomerId).toList());
        Map<Long, String> users = lookups.userNames(rows.stream().map(ProformaInvoiceDO::getOwnerId).toList());
        List<PiListVO> list = new ArrayList<>(rows.size());
        for (ProformaInvoiceDO p : rows) {
            PiListVO vo = new PiListVO();
            vo.setId(p.getId());
            vo.setPiNo(p.getPiNo());
            vo.setCustomerId(p.getCustomerId());
            vo.setCustomerName(InquiryLookups.customerName(customers.get(p.getCustomerId())));
            vo.setCustomerCountry(customers.get(p.getCustomerId()) == null ? null : customers.get(p.getCustomerId()).getCountry());
            vo.setCustomerType(customerTypes.get(p.getId()));
            vo.setTotalQuantity(quantities.getOrDefault(p.getId(), 0));
            vo.setItemCount(p.getItemCount());
            vo.setCurrencyCode(p.getCurrencyCode());
            vo.setTotalAmount(p.getTotalAmount());
            vo.setReceiptStatus(p.getReceiptStatus());
            vo.setReceiptStatusName(SalesConstants.RECEIPT_STATUS_NAMES.get(p.getReceiptStatus()));
            vo.setStatus(p.getStatus());
            vo.setStatusName(SalesConstants.PI_STATUS_NAMES.get(p.getStatus()));
            vo.setCurrentVersionNo(p.getCurrentVersionNo());
            vo.setRevising(p.getEditingVersionNo() != null && p.getCurrentVersionNo() != null && p.getCurrentVersionNo() > 0);
            vo.setQuotationNos(quotationsByPi.getOrDefault(p.getId(), Set.of()).stream().map(quotationNos::get).filter(Objects::nonNull).toList());
            SalesOrderDO o = orders.get(p.getId());
            vo.setOrderId(o == null ? null : o.getId());
            vo.setSoNo(o == null ? null : o.getSoNo());
            vo.setOwnerId(p.getOwnerId());
            vo.setOwnerName(users.get(p.getOwnerId()));
            vo.setCreateTime(p.getCreateTime());
            vo.setSentAt(p.getSentAt());
            vo.setValidUntil(p.getValidUntil());
            Long days = expiredDays(p);
            vo.setExpired(days != null);
            vo.setExpiredDays(days);
            vo.setCloseReasonName(p.getStatus() == SalesConstants.PI_CLOSED ? p.getCloseReasonName() : null);
            list.add(vo);
        }
        return list;
    }

    // ---------------------------------------------------------------- 内部

    private Long currentUserId() {
        Long id = currentUser.resolve();
        return id == null ? 0L : id;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}

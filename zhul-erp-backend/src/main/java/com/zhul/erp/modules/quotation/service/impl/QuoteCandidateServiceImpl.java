package com.zhul.erp.modules.quotation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.dto.PickCustomerVO;
import com.zhul.erp.modules.quotation.dto.PickInquiryQuery;
import com.zhul.erp.modules.quotation.dto.PickInquiryVO;
import com.zhul.erp.modules.quotation.dto.PickItemVO;
import com.zhul.erp.modules.quotation.dto.QuoteInquiryPageVO;
import com.zhul.erp.modules.quotation.dto.QuoteInquiryQuery;
import com.zhul.erp.modules.quotation.dto.QuoteInquiryVO;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.service.QuoteCandidateService;
import com.zhul.erp.modules.quotation.support.QuoteCandidates;
import com.zhul.erp.modules.quotation.support.QuotationLocks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuoteCandidateServiceImpl implements QuoteCandidateService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int KEYWORD_LIMIT = 500;
    private static final int CUSTOMER_LIMIT = 30;
    private static final List<Integer> QUOTE_STATUSES = List.of(InquiryConstants.STATUS_SOURCING, InquiryConstants.STATUS_READY_TO_QUOTE);
    private static final List<Integer> PICK_STATUSES = List.of(InquiryConstants.STATUS_READY_TO_QUOTE, InquiryConstants.STATUS_SOURCING,
            InquiryConstants.STATUS_QUOTED);
    /** 可报价在前、询价中其次、已报价最后，组内按询盘日期从新到旧 */
    private static final String PICK_ORDER = "ORDER BY FIELD(status, 6, 5, 7), inquiry_date DESC, id DESC";

    private final CustomerInquiryMapper inquiryMapper;
    private final CustomerMapper customerMapper;
    private final DataScopeResolver dataScopeResolver;
    private final InquiryLookups lookups;
    private final QuoteCandidates candidates;
    private final QuotationLocks locks;

    // ---------------------------------------------------------------- 按询盘报价

    @Override
    public QuoteInquiryPageVO quoteInquiries(QuoteInquiryQuery q) {
        DataScope scope = dataScopeResolver.current();
        LambdaQueryWrapper<CustomerInquiryDO> w = base(scope).in(CustomerInquiryDO::getStatus, QUOTE_STATUSES);
        if (Boolean.TRUE.equals(q.getReadyOnly())) {
            w.eq(CustomerInquiryDO::getStatus, InquiryConstants.STATUS_READY_TO_QUOTE);
        }
        if (Boolean.TRUE.equals(q.getDueToday())) {
            w.eq(CustomerInquiryDO::getQuoteDeadline, LocalDate.now());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            List<Long> customerIds = customerIdsByName(kw);
            w.and(x -> {
                x.like(CustomerInquiryDO::getInquiryCode, kw);
                if (!customerIds.isEmpty()) {
                    x.or().in(CustomerInquiryDO::getCustomerId, customerIds);
                }
            });
        }
        int size = pageSize(q.getPageSize(), 20);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = inquiryMapper.selectCount(w);
        w.orderByDesc(CustomerInquiryDO::getStatus).orderByDesc(CustomerInquiryDO::getInquiryDate).orderByDesc(CustomerInquiryDO::getId)
                .last("LIMIT " + (long) (page - 1) * size + ", " + size);
        List<CustomerInquiryDO> rows = total == 0 ? List.of() : inquiryMapper.selectList(w);

        QuoteInquiryPageVO vo = new QuoteInquiryPageVO();
        vo.setTotal(total);
        vo.setRecords(toCards(rows));
        vo.setReadyCount(inquiryMapper.selectCount(base(scope).eq(CustomerInquiryDO::getStatus, InquiryConstants.STATUS_READY_TO_QUOTE)));
        vo.setSourcingCount(inquiryMapper.selectCount(base(scope).eq(CustomerInquiryDO::getStatus, InquiryConstants.STATUS_SOURCING)));
        return vo;
    }

    // ---------------------------------------------------------------- 挑选型号报价

    @Override
    public List<PickCustomerVO> pickCustomers(String keyword) {
        List<CustomerInquiryDO> inquiries = inquiryMapper.selectList(base(dataScopeResolver.current())
                .select(CustomerInquiryDO::getCustomerId)
                .in(CustomerInquiryDO::getStatus, PICK_STATUSES));
        Map<Long, Long> counts = inquiries.stream().collect(Collectors.groupingBy(CustomerInquiryDO::getCustomerId, Collectors.counting()));
        if (counts.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<CustomerDO> cw = new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getTenantId, tenantId())
                .in(CustomerDO::getId, counts.keySet())
                .isNull(CustomerDO::getDeletedAt);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            cw.and(x -> x.like(CustomerDO::getName, kw).or().like(CustomerDO::getShortName, kw)
                    .or().like(CustomerDO::getContactName, kw));
        }
        return customerMapper.selectList(cw).stream()
                .sorted(Comparator.comparing((CustomerDO c) -> counts.get(c.getId())).reversed().thenComparing(CustomerDO::getName))
                .limit(CUSTOMER_LIMIT)
                .map(c -> {
                    PickCustomerVO vo = new PickCustomerVO();
                    vo.setCustomerId(c.getId());
                    vo.setCustomerName(InquiryLookups.customerName(c));
                    vo.setCountry(c.getCountry());
                    vo.setInquiryCount(counts.get(c.getId()));
                    return vo;
                }).toList();
    }

    @Override
    public PageResult<PickInquiryVO> pickInquiries(PickInquiryQuery q) {
        DataScope scope = dataScopeResolver.current();
        List<Integer> statuses = Boolean.TRUE.equals(q.getReadyOnly()) ? List.of(InquiryConstants.STATUS_READY_TO_QUOTE) : PICK_STATUSES;
        List<CustomerInquiryDO> all = inquiryMapper.selectList(base(scope)
                .select(CustomerInquiryDO::getId, CustomerInquiryDO::getInquiryCode)
                .eq(CustomerInquiryDO::getCustomerId, q.getCustomerId())
                .in(CustomerInquiryDO::getStatus, statuses));
        if (all.isEmpty()) {
            return PageResult.of(0L, List.of());
        }
        Set<Long> allIds = all.stream().map(CustomerInquiryDO::getId).collect(Collectors.toSet());
        String kw = StringUtils.hasText(q.getKeyword()) ? q.getKeyword().trim() : null;
        boolean unquotedOnly = Boolean.TRUE.equals(q.getUnquotedOnly());

        // 搜索与「只看未报过价的型号」都要看型号；一个客户的候选型号量有限，一次取出在内存里筛
        Map<Long, List<InquiryItemDO>> itemsByInquiry = new LinkedHashMap<>();
        Set<Long> wholeInquiries = new HashSet<>();
        Set<Long> matchedInquiries = allIds;
        if (kw != null || unquotedOnly) {
            List<InquiryItemDO> items = candidates.itemsOf(allIds);
            Set<Long> locked = unquotedOnly ? locks.lockedItemIds(items.stream().map(InquiryItemDO::getId).toList()) : Set.of();
            if (kw != null) {
                String lower = kw.toLowerCase();
                for (CustomerInquiryDO i : all) {
                    if (i.getInquiryCode() != null && i.getInquiryCode().toLowerCase().contains(lower)) {
                        wholeInquiries.add(i.getId());
                    }
                }
            }
            String modelKey = kw == null ? null : PriceKeys.model(kw);
            matchedInquiries = new HashSet<>();
            for (InquiryItemDO item : items) {
                if (locked.contains(item.getId())) {
                    continue;
                }
                boolean hit = kw == null || wholeInquiries.contains(item.getCustomerInquiryId()) || modelMatches(item, kw, modelKey);
                if (hit) {
                    itemsByInquiry.computeIfAbsent(item.getCustomerInquiryId(), k -> new ArrayList<>()).add(item);
                    matchedInquiries.add(item.getCustomerInquiryId());
                }
            }
            if (matchedInquiries.isEmpty()) {
                return PageResult.of(0L, List.of());
            }
        }
        int size = pageSize(q.getPageSize(), 10);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        List<CustomerInquiryDO> rows = inquiryMapper.selectList(new LambdaQueryWrapper<CustomerInquiryDO>()
                .in(CustomerInquiryDO::getId, matchedInquiries)
                .last(PICK_ORDER + " LIMIT " + (long) (page - 1) * size + ", " + size));
        List<Long> rowIds = rows.stream().map(CustomerInquiryDO::getId).toList();
        if (itemsByInquiry.isEmpty()) {
            for (InquiryItemDO item : candidates.itemsOf(rowIds)) {
                itemsByInquiry.computeIfAbsent(item.getCustomerInquiryId(), k -> new ArrayList<>()).add(item);
            }
        }
        List<InquiryItemDO> pageItems = rowIds.stream().flatMap(id -> itemsByInquiry.getOrDefault(id, List.of()).stream()).toList();
        Map<Long, QuoteCandidates.Candidate> evaluated = candidates.evaluate(pageItems, q.getCustomerId(), null);

        List<QuoteInquiryVO> cards = toCards(rows);
        List<PickInquiryVO> list = new ArrayList<>(cards.size());
        for (QuoteInquiryVO card : cards) {
            PickInquiryVO vo = new PickInquiryVO();
            copy(card, vo);
            vo.setItems(itemsByInquiry.getOrDefault(card.getInquiryId(), List.of()).stream()
                    .map(i -> toPickItem(evaluated.get(i.getId()))).toList());
            list.add(vo);
        }
        return PageResult.of((long) matchedInquiries.size(), list);
    }

    // ---------------------------------------------------------------- 内部

    private LambdaQueryWrapper<CustomerInquiryDO> base(DataScope scope) {
        LambdaQueryWrapper<CustomerInquiryDO> w = new LambdaQueryWrapper<CustomerInquiryDO>()
                .eq(CustomerInquiryDO::getTenantId, tenantId())
                .isNull(CustomerInquiryDO::getDeletedAt);
        return scope.apply(w, CustomerInquiryDO::getOwnerId);
    }

    private List<Long> customerIdsByName(String kw) {
        return customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                        .select(CustomerDO::getId)
                        .eq(CustomerDO::getTenantId, tenantId())
                        .isNull(CustomerDO::getDeletedAt)
                        .and(x -> x.like(CustomerDO::getName, kw).or().like(CustomerDO::getShortName, kw)
                                .or().like(CustomerDO::getContactName, kw))
                        .last("LIMIT " + KEYWORD_LIMIT))
                .stream().map(CustomerDO::getId).toList();
    }

    private static boolean modelMatches(InquiryItemDO item, String kw, String modelKey) {
        String lower = kw.toLowerCase();
        return (StringUtils.hasText(modelKey) && item.getModelKey() != null && item.getModelKey().contains(modelKey))
                || (item.getConfirmedModel() != null && item.getConfirmedModel().toLowerCase().contains(lower))
                || (item.getOriginalModel() != null && item.getOriginalModel().toLowerCase().contains(lower));
    }

    private List<QuoteInquiryVO> toCards(List<CustomerInquiryDO> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, CustomerDO> customers = lookups.customers(rows.stream().map(CustomerInquiryDO::getCustomerId).toList());
        Map<Long, String> drafts = draftNos(rows.stream().map(CustomerInquiryDO::getId).toList());
        List<QuoteInquiryVO> list = new ArrayList<>(rows.size());
        for (CustomerInquiryDO i : rows) {
            QuoteInquiryVO vo = new QuoteInquiryVO();
            vo.setInquiryId(i.getId());
            vo.setInquiryCode(i.getInquiryCode());
            vo.setCustomerId(i.getCustomerId());
            vo.setCustomerName(InquiryLookups.customerName(customers.get(i.getCustomerId())));
            vo.setCustomerType(i.getCustomerType());
            vo.setInquiryDate(i.getInquiryDate());
            vo.setQuoteDeadline(i.getQuoteDeadline());
            vo.setUrgent(Objects.equals(i.getUrgent(), 1));
            vo.setLevel(i.getLevel());
            vo.setStatus(i.getStatus());
            vo.setItemCount(i.getTotalItemCount());
            vo.setPricedCount(i.getPricedItemCount());
            vo.setDraftQuotationNo(drafts.get(i.getId()));
            list.add(vo);
        }
        return list;
    }

    /** 询盘 → 包含其型号的草稿报价单编号 */
    private Map<Long, String> draftNos(List<Long> inquiryIds) {
        List<InquiryItemDO> items = candidates.itemsOf(inquiryIds);
        Map<Long, List<QuotationDO>> byItem = locks.quotationsByItem(items.stream().map(InquiryItemDO::getId).toList(),
                List.of(QuotationConstants.STATUS_DRAFT));
        Map<Long, String> result = new HashMap<>();
        for (InquiryItemDO item : items) {
            List<QuotationDO> qs = byItem.get(item.getId());
            if (qs != null && !qs.isEmpty()) {
                result.putIfAbsent(item.getCustomerInquiryId(), qs.get(0).getQuotationNo());
            }
        }
        return result;
    }

    private static PickItemVO toPickItem(QuoteCandidates.Candidate c) {
        InquiryItemDO i = c.item();
        PickItemVO vo = new PickItemVO();
        vo.setItemId(i.getId());
        vo.setLineNo(i.getLineNo());
        vo.setModel(StringUtils.hasText(i.getConfirmedModel()) ? i.getConfirmedModel() : i.getOriginalModel());
        vo.setBrand(i.getBrand());
        vo.setCategory(i.getCategory());
        vo.setQuantity(i.getQuantity());
        vo.setCostPrice(c.costPrice());
        vo.setNoStock(c.noStock());
        vo.setPickable(c.pickable());
        vo.setDisabledReason(c.pickable() ? null : QuoteCandidates.PENDING_REASON);
        vo.setQuoted(c.quoted());
        vo.setDraftQuotationNo(c.draftNo());
        return vo;
    }

    private static void copy(QuoteInquiryVO from, QuoteInquiryVO to) {
        to.setInquiryId(from.getInquiryId());
        to.setInquiryCode(from.getInquiryCode());
        to.setCustomerId(from.getCustomerId());
        to.setCustomerName(from.getCustomerName());
        to.setCustomerType(from.getCustomerType());
        to.setInquiryDate(from.getInquiryDate());
        to.setQuoteDeadline(from.getQuoteDeadline());
        to.setUrgent(from.getUrgent());
        to.setLevel(from.getLevel());
        to.setStatus(from.getStatus());
        to.setItemCount(from.getItemCount());
        to.setPricedCount(from.getPricedCount());
        to.setDraftQuotationNo(from.getDraftQuotationNo());
    }

    private static int pageSize(Integer size, int fallback) {
        return size == null || size <= 0 ? fallback : Math.min(size, MAX_PAGE_SIZE);
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}

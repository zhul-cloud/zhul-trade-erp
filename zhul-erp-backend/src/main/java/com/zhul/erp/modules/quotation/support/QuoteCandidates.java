package com.zhul.erp.modules.quotation.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 型号明细的「可报价」判断：有采购成本价（选定的询价记录）或结果为无货才能报；
 * 同时给出采购成本价、是否已报出、是否已在草稿报价单中。
 */
@Component
@RequiredArgsConstructor
public class QuoteCandidates {

    public static final String PENDING_REASON = "还在询价，回价后才能报价";

    private final InquiryItemMapper itemMapper;
    private final SourcingQuoteMapper quoteMapper;
    private final QuotationLocks locks;

    /** 一个型号明细的报价依据 */
    public record Candidate(InquiryItemDO item, SourcingQuoteDO costQuote, boolean noStock, boolean quoted, String draftNo) {
        public boolean pickable() {
            return costQuote != null || noStock;
        }

        public BigDecimal costPrice() {
            return costQuote == null ? null : costQuote.getUnitPriceCny();
        }
    }

    public List<InquiryItemDO> itemsOf(Collection<Long> inquiryIds) {
        if (inquiryIds.isEmpty()) {
            return List.of();
        }
        return itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                .in(InquiryItemDO::getCustomerInquiryId, inquiryIds)
                .isNull(InquiryItemDO::getDeletedAt)
                .orderByAsc(InquiryItemDO::getCustomerInquiryId)
                .orderByAsc(InquiryItemDO::getLineNo));
    }

    /** customerId 用于判断「已在该客户另一张草稿报价单中」，excludeQuotationId 为当前正在编辑的报价单 */
    public Map<Long, Candidate> evaluate(List<InquiryItemDO> items, Long customerId, Long excludeQuotationId) {
        Map<Long, Candidate> result = new HashMap<>(items.size() * 2);
        if (items.isEmpty()) {
            return result;
        }
        Set<Long> quoteIds = items.stream().map(InquiryItemDO::getSelectedQuoteId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SourcingQuoteDO> quotes = quoteIds.isEmpty() ? Map.of() : quoteMapper.selectBatchIds(quoteIds).stream()
                .filter(q -> q.getDeletedAt() == null && !Objects.equals(q.getNoStock(), 1) && q.getUnitPriceCny() != null)
                .collect(Collectors.toMap(SourcingQuoteDO::getId, q -> q));
        List<Long> itemIds = items.stream().map(InquiryItemDO::getId).toList();
        Set<Long> quoted = locks.lockedItemIds(itemIds);
        Map<Long, List<QuotationDO>> drafts = locks.quotationsByItem(itemIds, List.of(QuotationConstants.STATUS_DRAFT));
        for (InquiryItemDO item : items) {
            SourcingQuoteDO cost = item.getSelectedQuoteId() == null ? null : quotes.get(item.getSelectedQuoteId());
            boolean noStock = cost == null && Objects.equals(item.getQuoteStatus(), InquiryConstants.ITEM_NO_STOCK);
            String draftNo = drafts.getOrDefault(item.getId(), List.of()).stream()
                    .filter(q -> !q.getId().equals(excludeQuotationId) && Objects.equals(q.getCustomerId(), customerId))
                    .map(QuotationDO::getQuotationNo).findFirst().orElse(null);
            result.put(item.getId(), new Candidate(item, cost, noStock, quoted.contains(item.getId()), draftNo));
        }
        return result;
    }
}

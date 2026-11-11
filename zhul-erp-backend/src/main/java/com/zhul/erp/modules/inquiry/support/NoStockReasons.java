package com.zhul.erp.modules.inquiry.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 询盘型号最近一次已提交的无货结果：内部无货原因与说明（报价单无货行旁边显示，spec quotation「无货行显示内部无货原因」） */
@Component
@RequiredArgsConstructor
public class NoStockReasons {

    private final SourcingQuoteMapper quoteMapper;
    private final QuoteDicts quoteDicts;

    /** 原因名称没有填时为空串 */
    public record Reason(String name, String note) {
    }

    public Map<Long, Reason> of(Collection<Long> inquiryItemIds) {
        List<Long> ids = inquiryItemIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<SourcingQuoteDO> quotes = quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                .select(SourcingQuoteDO::getInquiryItemId, SourcingQuoteDO::getNoStockReason, SourcingQuoteDO::getNote,
                        SourcingQuoteDO::getQuotedAt)
                .in(SourcingQuoteDO::getInquiryItemId, ids)
                .eq(SourcingQuoteDO::getNoStock, 1)
                .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED)
                .isNull(SourcingQuoteDO::getDeletedAt)
                .orderByDesc(SourcingQuoteDO::getQuotedAt));
        if (quotes.isEmpty()) {
            return Map.of();
        }
        QuoteDictSnapshot.Dict reasons = quoteDicts.noStockReasons();
        Map<Long, Reason> map = new HashMap<>(quotes.size() * 2);
        quotes.forEach(q -> map.putIfAbsent(q.getInquiryItemId(),
                new Reason(reasons.label(q.getNoStockReason()), q.getNote() == null ? "" : q.getNote())));
        return map;
    }
}

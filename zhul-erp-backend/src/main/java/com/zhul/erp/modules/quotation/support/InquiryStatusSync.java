package com.zhul.erp.modules.quotation.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.sourcing.service.impl.SourcingProgress;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import com.zhul.erp.modules.quotation.repository.QuotationItemMapper;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 报价单状态 → 客户询盘状态（spec inquiry/inquiry-intake「客户询盘状态」）：
 * 包含其型号的报价单有已成交 → 已成交；否则有已发送 → 已报价；否则有未成交 → 未成交；
 * 否则只剩已作废（或已作废加草稿）→ 作废视为没报过，回到报价前的状态（全部型号有价格或无货为可报价，否则询价中）；否则不变。
 * 只推进询价中 / 可报价 / 已报价 / 已成交 / 未成交的询盘，已取消的不动。
 * 调用方须在事务内；按询盘 ID 升序加行锁，与回价提交（同样先锁询盘行）不会互相死锁。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InquiryStatusSync {

    private static final Set<Integer> SYNCABLE = Set.of(InquiryConstants.STATUS_SOURCING, InquiryConstants.STATUS_READY_TO_QUOTE,
            InquiryConstants.STATUS_QUOTED, InquiryConstants.STATUS_WON, InquiryConstants.STATUS_LOST);
    /** target 的特殊结果：回到报价前的状态（按回价进度定可报价或询价中） */
    static final int BEFORE_QUOTE = 0;

    private final SourcingProgress progress;
    private final CustomerInquiryMapper inquiryMapper;
    private final QuotationItemMapper itemMapper;
    private final QuotationMapper quotationMapper;

    /** 报价单涉及的客户询盘 ID */
    public Set<Long> inquiryIdsOf(Long quotationId) {
        return itemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                        .select(QuotationItemDO::getCustomerInquiryId)
                        .eq(QuotationItemDO::getQuotationId, quotationId)
                        .eq(QuotationItemDO::getIsCurrent, 1)
                .isNull(QuotationItemDO::getDeletedAt))
                .stream().map(QuotationItemDO::getCustomerInquiryId).collect(Collectors.toCollection(TreeSet::new));
    }

    /** 先按 ID 升序锁住全部询盘（调用方在改报价单状态前调用） */
    public void lock(Collection<Long> inquiryIds) {
        for (Long id : new TreeSet<>(inquiryIds)) {
            progress.lock(id);
        }
    }

    public void sync(Collection<Long> inquiryIds) {
        for (Long id : new TreeSet<>(inquiryIds)) {
            CustomerInquiryDO inquiry = inquiryMapper.selectById(id);
            if (inquiry == null || inquiry.getDeletedAt() != null || !SYNCABLE.contains(inquiry.getStatus())) {
                continue;
            }
            Integer target = target(statusesOf(id));
            if (target != null && target == BEFORE_QUOTE) {
                target = beforeQuote(inquiry);
            }
            if (target != null && !target.equals(inquiry.getStatus())) {
                log.info("报价单推进客户询盘状态，inquiryId={}, {} -> {}", id, inquiry.getStatus(), target);
                inquiry.setStatus(target);
                inquiryMapper.updateById(inquiry);
            }
        }
    }

    static Integer target(Set<Integer> quotationStatuses) {
        if (quotationStatuses.contains(QuotationConstants.STATUS_WON) || quotationStatuses.contains(QuotationConstants.STATUS_PARTIAL)) {
            return InquiryConstants.STATUS_WON;
        }
        if (quotationStatuses.contains(QuotationConstants.STATUS_SENT)) {
            return InquiryConstants.STATUS_QUOTED;
        }
        if (quotationStatuses.contains(QuotationConstants.STATUS_LOST)) {
            return InquiryConstants.STATUS_LOST;
        }
        if (quotationStatuses.contains(QuotationConstants.STATUS_VOID)) {
            return BEFORE_QUOTE;
        }
        return null;
    }

    /** 报价前的状态：询价中、可报价的不动；其余按回价进度回到可报价或询价中 */
    private static Integer beforeQuote(CustomerInquiryDO inquiry) {
        int status = inquiry.getStatus();
        if (status == InquiryConstants.STATUS_SOURCING || status == InquiryConstants.STATUS_READY_TO_QUOTE) {
            return null;
        }
        int total = inquiry.getTotalItemCount() == null ? 0 : inquiry.getTotalItemCount();
        int priced = inquiry.getPricedItemCount() == null ? 0 : inquiry.getPricedItemCount();
        return total > 0 && priced >= total ? InquiryConstants.STATUS_READY_TO_QUOTE : InquiryConstants.STATUS_SOURCING;
    }

    private Set<Integer> statusesOf(Long inquiryId) {
        Set<Long> quotationIds = itemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                        .select(QuotationItemDO::getQuotationId)
                        .eq(QuotationItemDO::getCustomerInquiryId, inquiryId)
                        .eq(QuotationItemDO::getIsCurrent, 1)
                .isNull(QuotationItemDO::getDeletedAt))
                .stream().map(QuotationItemDO::getQuotationId).collect(Collectors.toSet());
        if (quotationIds.isEmpty()) {
            return Set.of();
        }
        List<QuotationDO> quotations = quotationMapper.selectList(new LambdaQueryWrapper<QuotationDO>()
                .select(QuotationDO::getStatus)
                .in(QuotationDO::getId, quotationIds)
                .isNull(QuotationDO::getDeletedAt));
        return quotations.stream().map(QuotationDO::getStatus).collect(Collectors.toSet());
    }
}

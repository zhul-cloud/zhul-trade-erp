package com.zhul.erp.modules.quotation.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import com.zhul.erp.modules.quotation.repository.QuotationItemMapper;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 型号级锁定：型号出现在已发送（及之后成交 / 未成交）的报价单中即已对客户报出，回价只读 */
@Component
@RequiredArgsConstructor
public class QuotationLocks {

    private final QuotationItemMapper itemMapper;
    private final QuotationMapper quotationMapper;

    /** 返回其中已报出的型号明细 ID */
    public Set<Long> lockedItemIds(Collection<Long> inquiryItemIds) {
        return quotationsByItem(inquiryItemIds, QuotationConstants.ITEM_LOCKING_STATUSES).keySet();
    }

    public boolean isLocked(Long inquiryItemId) {
        return !lockedItemIds(List.of(inquiryItemId)).isEmpty();
    }

    /** 型号明细 ID → 包含它、且状态在 statuses 中的报价单（未删除） */
    public Map<Long, List<QuotationDO>> quotationsByItem(Collection<Long> inquiryItemIds, Collection<Integer> statuses) {
        Map<Long, List<QuotationDO>> result = new HashMap<>();
        if (inquiryItemIds == null || inquiryItemIds.isEmpty()) {
            return result;
        }
        List<QuotationItemDO> rows = itemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                .select(QuotationItemDO::getInquiryItemId, QuotationItemDO::getQuotationId)
                .in(QuotationItemDO::getInquiryItemId, new HashSet<>(inquiryItemIds))
                .isNull(QuotationItemDO::getDeletedAt));
        if (rows.isEmpty()) {
            return result;
        }
        Set<Long> quotationIds = rows.stream().map(QuotationItemDO::getQuotationId).collect(Collectors.toSet());
        Map<Long, QuotationDO> quotations = quotationMapper.selectList(new LambdaQueryWrapper<QuotationDO>()
                        .in(QuotationDO::getId, quotationIds)
                        .in(QuotationDO::getStatus, statuses)
                        .isNull(QuotationDO::getDeletedAt))
                .stream().collect(Collectors.toMap(QuotationDO::getId, q -> q));
        for (QuotationItemDO r : rows) {
            QuotationDO q = quotations.get(r.getQuotationId());
            if (q != null) {
                List<QuotationDO> list = result.computeIfAbsent(r.getInquiryItemId(), k -> new java.util.ArrayList<>());
                if (list.stream().noneMatch(x -> x.getId().equals(q.getId()))) {
                    list.add(q);
                }
            }
        }
        return result;
    }
}

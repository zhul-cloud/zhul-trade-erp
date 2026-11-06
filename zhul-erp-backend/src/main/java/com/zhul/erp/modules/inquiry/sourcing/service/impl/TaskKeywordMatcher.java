package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** 询价任务关键词匹配：「我的询价」与询价分配共用 */
@Component
@RequiredArgsConstructor
public class TaskKeywordMatcher {

    private final CustomerInquiryMapper inquiryMapper;
    private final InquiryItemMapper itemMapper;

    /** 关键词匹配任务编号、品牌、品类、询盘单编号，或任务下型号（确认型号、原始型号，忽略大小写与分隔符） */
    public List<SourcingTaskDO> matching(List<SourcingTaskDO> tasks, String keyword) {
        if (tasks.isEmpty() || !StringUtils.hasText(keyword)) {
            return tasks;
        }
        String kw = keyword.trim();
        String lower = kw.toLowerCase(Locale.ROOT);
        String key = PriceKeys.model(kw);
        Set<Long> inquiryHits = inquiryMapper.selectList(new LambdaQueryWrapper<CustomerInquiryDO>()
                        .select(CustomerInquiryDO::getId)
                        .in(CustomerInquiryDO::getId, tasks.stream().map(SourcingTaskDO::getCustomerInquiryId).distinct().toList())
                        .like(CustomerInquiryDO::getInquiryCode, kw))
                .stream().map(CustomerInquiryDO::getId).collect(Collectors.toSet());
        Set<Long> itemHits = itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                        .select(InquiryItemDO::getSourcingTaskId, InquiryItemDO::getConfirmedModel, InquiryItemDO::getOriginalModel,
                                InquiryItemDO::getModelKey)
                        .in(InquiryItemDO::getSourcingTaskId, tasks.stream().map(SourcingTaskDO::getId).toList())
                        .isNull(InquiryItemDO::getDeletedAt))
                .stream().filter(i -> contains(i.getConfirmedModel(), lower) || contains(i.getOriginalModel(), lower)
                        || (StringUtils.hasText(key) && i.getModelKey() != null && i.getModelKey().contains(key)))
                .map(InquiryItemDO::getSourcingTaskId).collect(Collectors.toSet());
        return tasks.stream().filter(t -> contains(t.getTaskCode(), lower) || contains(t.getBrand(), lower) || contains(t.getCategory(), lower)
                || inquiryHits.contains(t.getCustomerInquiryId()) || itemHits.contains(t.getId())).toList();
    }

    private static boolean contains(String text, String lowerKeyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }
}

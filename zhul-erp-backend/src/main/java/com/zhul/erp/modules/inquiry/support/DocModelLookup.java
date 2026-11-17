package com.zhul.erp.modules.inquiry.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 按询盘型号 ID 批量取当前的单据型号与客户型号（openspec actual-model-and-description D1、D4） */
@Component
@RequiredArgsConstructor
public class DocModelLookup {

    private final InquiryItemMapper inquiryItemMapper;

    /** 只取型号相关的列 */
    public Map<Long, InquiryItemDO> items(Collection<Long> inquiryItemIds) {
        List<Long> ids = inquiryItemIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return inquiryItemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                        .select(InquiryItemDO::getId, InquiryItemDO::getOriginalModel, InquiryItemDO::getConfirmedModel,
                                InquiryItemDO::getActualModel)
                        .in(InquiryItemDO::getId, ids)).stream()
                .collect(Collectors.toMap(InquiryItemDO::getId, Function.identity()));
    }

    /** 询盘型号 ID → 当前的单据型号（真实型号优先），用于已发出单据的「真实型号已更新」提示 */
    public Map<Long, String> docModels(Collection<Long> inquiryItemIds) {
        return items(inquiryItemIds).values().stream().collect(Collectors.toMap(InquiryItemDO::getId, DocModels::docModel));
    }
}

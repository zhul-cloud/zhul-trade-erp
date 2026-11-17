package com.zhul.erp.modules.product.content.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.modules.product.content.dto.ContentAiJobVO;
import com.zhul.erp.modules.product.content.entity.ProductContentAiJobDO;
import com.zhul.erp.modules.product.content.repository.ProductContentAiJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 列表用：各商品本公司最近一次 AI 生成（只读，内容任务列表与 AI 生成服务共用） */
@Component
@RequiredArgsConstructor
public class ContentAiJobs {

    private final ProductContentAiJobMapper jobMapper;

    public Map<Long, ContentAiJobVO> latest(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProductContentAiJobDO> latest = new HashMap<>();
        for (ProductContentAiJobDO j : jobMapper.selectList(new LambdaQueryWrapper<ProductContentAiJobDO>()
                .select(ProductContentAiJobDO::getId, ProductContentAiJobDO::getProductId, ProductContentAiJobDO::getStatus,
                        ProductContentAiJobDO::getFailReason, ProductContentAiJobDO::getCreateTime,
                        ProductContentAiJobDO::getStartedAt, ProductContentAiJobDO::getCompletedAt,
                        ProductContentAiJobDO::getConfirmedAt, ProductContentAiJobDO::getConfirmedBy,
                        ProductContentAiJobDO::getFactsImportedAt, ProductContentAiJobDO::getMarketingImportedAt)
                .in(ProductContentAiJobDO::getProductId, productIds)
                .isNull(ProductContentAiJobDO::getDeletedAt)
                .orderByAsc(ProductContentAiJobDO::getId))) {
            latest.put(j.getProductId(), j);
        }
        List<ProductContentAiJobDO> queued = latest.values().stream()
                .filter(j -> j.getStatus() == ContentConstants.AI_QUEUED).toList();
        Map<Long, Integer> ahead = queued.isEmpty() ? Map.of() : queueAhead(queued);
        Map<Long, ContentAiJobVO> out = new HashMap<>();
        latest.forEach((productId, j) -> out.put(productId, toVo(j, ahead.get(j.getId()))));
        return out;
    }

    /** 排队中的记录前面还有几个（本公司更早排队的） */
    private Map<Long, Integer> queueAhead(List<ProductContentAiJobDO> queued) {
        List<Long> all = jobMapper.selectList(new LambdaQueryWrapper<ProductContentAiJobDO>()
                        .select(ProductContentAiJobDO::getId)
                        .eq(ProductContentAiJobDO::getStatus, ContentConstants.AI_QUEUED)
                        .isNull(ProductContentAiJobDO::getDeletedAt)
                        .orderByAsc(ProductContentAiJobDO::getId))
                .stream().map(ProductContentAiJobDO::getId).toList();
        Map<Long, Integer> out = new HashMap<>();
        queued.forEach(j -> out.put(j.getId(), Math.max(0, all.indexOf(j.getId()))));
        return out;
    }

    public static ContentAiJobVO toVo(ProductContentAiJobDO j, Integer queueAhead) {
        ContentAiJobVO vo = new ContentAiJobVO();
        vo.setId(j.getId());
        vo.setStatus(j.getStatus());
        vo.setQueueAhead(queueAhead);
        vo.setFailReason(j.getFailReason());
        vo.setCreateTime(j.getCreateTime());
        vo.setStartedAt(j.getStartedAt());
        vo.setCompletedAt(j.getCompletedAt());
        vo.setConfirmedAt(j.getConfirmedAt());
        vo.setConfirmedBy(j.getConfirmedBy());
        vo.setFactsImported(j.getFactsImportedAt() != null);
        vo.setMarketingImported(j.getMarketingImportedAt() != null);
        return vo;
    }
}

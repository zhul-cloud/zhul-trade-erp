package com.zhul.erp.modules.product.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.content.dto.ContentBatchResultVO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 资料任务的批量操作（openspec content-ai-batch-hs）：批量确认 AI 结果、批量资料已核。
 * 事务边界：逐个独立处理（各自的事务，同逐个操作），某个失败只记在结果里，不影响其他。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentBatchService {

    private static final String MENU = "资料任务";
    /** 批量确认一次最多处理的 AI 结果数（每个都要解析两份原文） */
    public static final int MAX_AI_CONFIRM = 50;

    private final ContentAiService aiService;
    private final FactTaskService factTaskService;
    private final ProductMapper productMapper;
    private final LogService logService;

    public ContentBatchResultVO confirmAi(List<Long> jobIds) {
        List<Long> ids = List.copyOf(new LinkedHashSet<>(jobIds));
        if (ids.size() > MAX_AI_CONFIRM) {
            throw new BizException("一次最多确认 " + MAX_AI_CONFIRM + " 个 AI 结果");
        }
        ContentBatchResultVO result = new ContentBatchResultVO();
        for (Long id : ids) {
            ContentBatchResultVO.Item item;
            try {
                item = aiService.confirmAll(id);
            } catch (BizException e) {
                item = new ContentBatchResultVO.Item();
                item.setId(id);
                item.setLabel(String.valueOf(id));
                item.setOutcome(ContentBatchResultVO.OUTCOME_FAILED);
                item.setMessage(e.getMessage());
            }
            result.add(item);
        }
        logService.recordOperateLog(MENU, "批量确认 AI 结果", null, Map.of("count", ids.size(), "done", result.getDone(),
                "partial", result.getPartial(), "failed", result.getFailed()));
        return result;
    }

    /** 已核对过且之后没有再导入的商品跳过并说明原因 */
    public ContentBatchResultVO verifyFacts(List<Long> productIds) {
        List<Long> ids = List.copyOf(new LinkedHashSet<>(productIds));
        Map<Long, String> labels = labels(ids);
        ContentBatchResultVO result = new ContentBatchResultVO();
        for (Long id : ids) {
            ContentBatchResultVO.Item item = new ContentBatchResultVO.Item();
            item.setId(id);
            item.setProductId(id);
            item.setLabel(labels.getOrDefault(id, String.valueOf(id)));
            try {
                factTaskService.verify(id);
                item.setOutcome(ContentBatchResultVO.OUTCOME_DONE);
                item.setMessage("资料已核");
            } catch (BizException e) {
                item.setOutcome(ContentBatchResultVO.OUTCOME_FAILED);
                item.setMessage(e.getMessage());
            }
            result.add(item);
        }
        logService.recordOperateLog(MENU, "批量资料已核", null, Map.of("count", ids.size(), "done", result.getDone(),
                "failed", result.getFailed()));
        return result;
    }

    private Map<Long, String> labels(List<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                        .select(ProductDO::getId, ProductDO::getMpnRaw, ProductDO::getMpnDisplay)
                        .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .in(ProductDO::getId, ids))
                .forEach(p -> out.put(p.getId(), StringUtils.hasText(p.getMpnDisplay()) ? p.getMpnDisplay() : p.getMpnRaw()));
        return out;
    }
}

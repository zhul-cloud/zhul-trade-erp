package com.zhul.erp.modules.product.content.service;

import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.service.AiTaskResultHandler;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** skill_id=product-content 的结果处理器：成功、失败、超时都在这里结束 AI 生成记录 */
@Component
@RequiredArgsConstructor
public class ContentAiResultHandler implements AiTaskResultHandler {

    private final ContentAiService aiService;

    @Override
    public boolean supports(String skillId) {
        return ContentConstants.AI_SKILL.equals(skillId);
    }

    @Override
    public void handle(AiTaskDO task) {
        aiService.apply(task);
    }
}

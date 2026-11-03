package com.zhul.erp.modules.masterdata.customerbackground.service;

import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.service.AiTaskResultHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.SKILL;

/** skill_id=customer-background 的结果处理器：成功、失败、超时都在这里结束背调报告 */
@Component
@RequiredArgsConstructor
public class CustomerBackgroundResultHandler implements AiTaskResultHandler {

    private final BackgroundResultService resultService;

    @Override
    public boolean supports(String skillId) {
        return SKILL.equals(skillId);
    }

    @Override
    public void handle(AiTaskDO task) {
        resultService.apply(task);
    }
}

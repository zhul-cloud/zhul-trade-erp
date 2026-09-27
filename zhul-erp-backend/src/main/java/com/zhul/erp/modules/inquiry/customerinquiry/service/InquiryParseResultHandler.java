package com.zhul.erp.modules.inquiry.customerinquiry.service;

import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.service.AiTaskResultHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * skill_id=inquiry-parse-and-split 的结果处理器（任务4.4）。声明为 @Component 后，
 * aitask 模块的 AiTaskResultDispatcher 会自动收集到它——不需要修改 aitask 模块任何
 * 已有代码，验证了 design.md 决策3"新增skill只需新增处理器"的核心诉求。
 */
@Component
@RequiredArgsConstructor
public class InquiryParseResultHandler implements AiTaskResultHandler {

    private static final String SKILL_ID = "inquiry-parse-and-split";

    private final CustomerInquiryService customerInquiryService;

    @Override
    public boolean supports(String skillId) {
        return SKILL_ID.equals(skillId);
    }

    @Override
    public void handle(AiTaskDO task) {
        if (task.getStatus() != null && task.getStatus() == AiTaskStatus.COMPLETED) {
            customerInquiryService.applyParseSuccess(task.getId(), task.getOutput());
        } else {
            customerInquiryService.applyParseFailure(task.getId(), task.getErrorMessage());
        }
    }
}

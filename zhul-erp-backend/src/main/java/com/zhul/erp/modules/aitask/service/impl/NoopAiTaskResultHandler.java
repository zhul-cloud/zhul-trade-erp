package com.zhul.erp.modules.aitask.service.impl;

import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.service.AiTaskResultHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 路由框架的占位验证处理器，只负责 skillId="noop-test" 的任务，用于证明
 * {@link com.zhul.erp.modules.aitask.service.AiTaskResultDispatcher} 的分发机制生效。
 * 真正的 inquiry-parse-and-split 处理器由任务组4实现，本类不处理该 skillId。
 */
@Slf4j
@Component
public class NoopAiTaskResultHandler implements AiTaskResultHandler {

    public static final String SKILL_ID = "noop-test";

    @Override
    public boolean supports(String skillId) {
        return SKILL_ID.equals(skillId);
    }

    @Override
    public void handle(AiTaskDO task) {
        log.info("NoopAiTaskResultHandler 已处理 taskId={}，status={}（仅用于验证路由分发，不产生业务副作用）",
                task.getId(), task.getStatus());
    }
}

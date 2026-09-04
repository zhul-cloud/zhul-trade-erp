package com.zhul.erp.modules.aitask.service;

import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按 skillId 把任务结果分发给匹配的 {@link AiTaskResultHandler}。
 * Spring 自动注入所有已声明的处理器实现（当前只有 {@link com.zhul.erp.modules.aitask.service.impl.NoopAiTaskResultHandler}
 * 作为路由验证用的占位实现；询盘解析对应的处理器由任务组4实现）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskResultDispatcher {

    private final List<AiTaskResultHandler> handlers;

    public void dispatch(AiTaskDO task) {
        for (AiTaskResultHandler handler : handlers) {
            if (handler.supports(task.getSkillId())) {
                handler.handle(task);
                return;
            }
        }
        log.warn("未找到 skillId={} 对应的 AiTaskResultHandler，taskId={} 的结果不会触发任何业务后续动作",
                task.getSkillId(), task.getId());
    }
}

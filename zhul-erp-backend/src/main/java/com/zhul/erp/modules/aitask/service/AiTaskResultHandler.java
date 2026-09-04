package com.zhul.erp.modules.aitask.service;

import com.zhul.erp.modules.aitask.entity.AiTaskDO;

/**
 * 按 skill_id 路由到具体业务处理器的扩展点。新增一个 skill 时，只需新增一个
 * 实现类（Spring 会自动收集），不需要修改分发框架本身或其他已有 skill 的处理逻辑，
 * 见 openspec/changes/add-inquiry-management/design.md 决策3。
 */
public interface AiTaskResultHandler {

    /** 该处理器是否负责处理这个 skillId 的任务结果 */
    boolean supports(String skillId);

    /** 任务状态与结果已经落库之后调用，处理该 skill 的业务后续动作（如更新客户询盘状态） */
    void handle(AiTaskDO task);
}

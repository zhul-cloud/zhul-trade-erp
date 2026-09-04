package com.zhul.erp.modules.aitask.service;

import com.zhul.erp.modules.aitask.dto.AiTaskCallbackRequest;
import com.zhul.erp.modules.aitask.dto.AiTaskVO;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;

public interface AiTaskService {

    /** 创建任务（status=排队中）并异步提交给 AI 编排服务；提交结果通过 webhook 回调异步返回 */
    AiTaskVO createAndSubmit(String skillId, String inputJson, Long requestedBy);

    /** 仅创建任务记录，不提交，供需要自行控制提交时机的调用方使用 */
    AiTaskDO create(String skillId, String inputJson, Long requestedBy);

    /** 处理 webhook 回调，更新任务状态/结果，并分发给对应的 AiTaskResultHandler */
    void handleCallback(Long taskId, AiTaskCallbackRequest req);

    /** 未找到时抛出 BizException，供业务方（如询盘拆单预览）读取任务结果 */
    AiTaskVO getById(Long id);
}

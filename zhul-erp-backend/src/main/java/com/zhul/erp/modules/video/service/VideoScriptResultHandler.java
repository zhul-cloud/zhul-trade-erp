package com.zhul.erp.modules.video.service;

import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.service.AiTaskResultHandler;
import com.zhul.erp.modules.video.constants.VideoConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** skill_id=video-script 的结果处理：写回视频任务的脚本。回调由 AiTaskServiceImpl 按任务所属租户执行 */
@Component
@RequiredArgsConstructor
public class VideoScriptResultHandler implements AiTaskResultHandler {

    private final VideoScriptService scripts;

    @Override
    public boolean supports(String skillId) {
        return VideoConstants.AI_SKILL.equals(skillId);
    }

    @Override
    public void handle(AiTaskDO task) {
        scripts.onResult(task);
    }
}

package com.zhul.erp.modules.aitask.service;

import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对应 specs/ai-platform/task-orchestration/spec.md "按 skill 标识路由处理结果"：
 * 新增 skill 只需新增处理器，未匹配到处理器时不报错、只记录警告。
 */
class AiTaskResultDispatcherTest {

    @Test
    void dispatch_routesToMatchingHandler() {
        AiTaskResultHandler matching = mock(AiTaskResultHandler.class);
        AiTaskResultHandler other = mock(AiTaskResultHandler.class);
        when(matching.supports("noop-test")).thenReturn(true);
        when(other.supports("noop-test")).thenReturn(false);

        AiTaskResultDispatcher dispatcher = new AiTaskResultDispatcher(List.of(other, matching));

        AiTaskDO task = new AiTaskDO();
        task.setId(1L);
        task.setSkillId("noop-test");
        dispatcher.dispatch(task);

        verify(matching, times(1)).handle(task);
        verify(other, never()).handle(any());
    }

    @Test
    void dispatch_unknownSkillId_doesNotThrow() {
        AiTaskResultHandler handler = mock(AiTaskResultHandler.class);
        when(handler.supports(any())).thenReturn(false);

        AiTaskResultDispatcher dispatcher = new AiTaskResultDispatcher(List.of(handler));

        AiTaskDO task = new AiTaskDO();
        task.setId(2L);
        task.setSkillId("unknown-skill");

        dispatcher.dispatch(task);

        verify(handler, never()).handle(any());
    }
}

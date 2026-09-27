package com.zhul.erp.modules.aitask.service;

import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.repository.AiTaskMapper;
import com.zhul.erp.modules.aitask.service.impl.AiTaskTimeoutWatchdog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对应 PRD 第7节"AI 处理超时（无回调）"：长时间没有收到结果的任务应被标记失败并
 * 转人工，而不是永远卡在"排队中/处理中"。
 */
@ExtendWith(MockitoExtension.class)
class AiTaskTimeoutWatchdogTest {

    @Mock
    private AiTaskMapper aiTaskMapper;
    @Mock
    private AiTaskResultDispatcher resultDispatcher;

    @Test
    void checkTimeouts_marksStaleTaskFailedAndDispatches() {
        AiTaskTimeoutWatchdog watchdog = new AiTaskTimeoutWatchdog(aiTaskMapper, resultDispatcher);
        ReflectionTestUtils.setField(watchdog, "timeoutMinutes", 5);

        AiTaskDO stale = new AiTaskDO();
        stale.setId(9L);
        stale.setTenantId(1);
        stale.setSkillId("inquiry-parse-and-split");
        stale.setStatus(AiTaskStatus.PROCESSING);
        when(aiTaskMapper.selectList(any())).thenReturn(List.of(stale));

        watchdog.checkTimeouts();

        ArgumentCaptor<AiTaskDO> updateCaptor = ArgumentCaptor.forClass(AiTaskDO.class);
        verify(aiTaskMapper, times(1)).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isEqualTo(9L);
        assertThat(updateCaptor.getValue().getStatus()).isEqualTo(AiTaskStatus.FAILED);
        assertThat(updateCaptor.getValue().getErrorMessage()).contains("超时");

        ArgumentCaptor<AiTaskDO> dispatchCaptor = ArgumentCaptor.forClass(AiTaskDO.class);
        verify(resultDispatcher, times(1)).dispatch(dispatchCaptor.capture());
        assertThat(dispatchCaptor.getValue().getId()).isEqualTo(9L);
        assertThat(dispatchCaptor.getValue().getSkillId()).isEqualTo("inquiry-parse-and-split");
        assertThat(dispatchCaptor.getValue().getStatus()).isEqualTo(AiTaskStatus.FAILED);
    }

    @Test
    void checkTimeouts_withNoStaleTasks_doesNothing() {
        AiTaskTimeoutWatchdog watchdog = new AiTaskTimeoutWatchdog(aiTaskMapper, resultDispatcher);
        ReflectionTestUtils.setField(watchdog, "timeoutMinutes", 5);
        when(aiTaskMapper.selectList(any())).thenReturn(List.of());

        watchdog.checkTimeouts();

        verify(aiTaskMapper, times(0)).updateById(any());
        verify(resultDispatcher, times(0)).dispatch(any());
    }
}

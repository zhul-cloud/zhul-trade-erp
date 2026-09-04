package com.zhul.erp.modules.aitask.service;

import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.service.impl.NoopAiTaskResultHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoopAiTaskResultHandlerTest {

    private final NoopAiTaskResultHandler handler = new NoopAiTaskResultHandler();

    @Test
    void supports_onlyMatchesItsOwnSkillId() {
        assertThat(handler.supports("noop-test")).isTrue();
        assertThat(handler.supports("inquiry-parse-and-split")).isFalse();
        assertThat(handler.supports("unknown-skill")).isFalse();
    }

    @Test
    void handle_doesNotThrow() {
        AiTaskDO task = new AiTaskDO();
        task.setId(1L);
        task.setStatus(3);
        handler.handle(task);
    }
}

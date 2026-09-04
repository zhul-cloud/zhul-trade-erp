package com.zhul.erp.modules.aitask.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.aitask.dto.AiTaskCallbackRequest;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 对应 specs/ai-platform/task-orchestration/spec.md "回调请求不使用用户态登录鉴权"：
 * webhook 回调改用共享密钥（X-AI-Callback-Token）校验，不匹配直接 401 且不进入业务处理。
 */
@ExtendWith(MockitoExtension.class)
class AiTaskControllerTest {

    private static final String CONFIGURED_TOKEN = "test-callback-secret";

    @Mock
    private AiTaskService aiTaskService;

    private AiTaskController controller;

    @BeforeEach
    void setUp() {
        controller = new AiTaskController(aiTaskService, new ObjectMapper());
        ReflectionTestUtils.setField(controller, "callbackToken", CONFIGURED_TOKEN);
    }

    @Test
    void callback_withMissingToken_returns401AndSkipsHandling() {
        AiTaskCallbackRequest req = new AiTaskCallbackRequest();
        req.setStatus("success");

        ResponseEntity<Result<Void>> response = controller.callback(1L, null, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(aiTaskService, never()).handleCallback(any(), any());
    }

    @Test
    void callback_withWrongToken_returns401AndSkipsHandling() {
        AiTaskCallbackRequest req = new AiTaskCallbackRequest();
        req.setStatus("success");

        ResponseEntity<Result<Void>> response = controller.callback(1L, "wrong-token", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(aiTaskService, never()).handleCallback(any(), any());
    }

    @Test
    void callback_withCorrectToken_processesAndReturns200() {
        AiTaskCallbackRequest req = new AiTaskCallbackRequest();
        req.setStatus("success");

        ResponseEntity<Result<Void>> response = controller.callback(1L, CONFIGURED_TOKEN, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(aiTaskService, times(1)).handleCallback(eq(1L), eq(req));
    }
}

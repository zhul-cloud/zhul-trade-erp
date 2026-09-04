package com.zhul.erp.modules.inquiry.customerinquiry.service;

import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * 对应任务4.4：验证处理器正确路由到 CustomerInquiryService 的成功/失败分支，
 * 且能被 aitask 模块的 AiTaskResultDispatcher 按 skillId 匹配（supports()）。
 */
@ExtendWith(MockitoExtension.class)
class InquiryParseResultHandlerTest {

    @Mock
    private CustomerInquiryService customerInquiryService;

    @Test
    void supports_onlyMatchesInquiryParseAndSplitSkill() {
        InquiryParseResultHandler handler = new InquiryParseResultHandler(customerInquiryService);

        assertThat(handler.supports("inquiry-parse-and-split")).isTrue();
        assertThat(handler.supports("some-other-skill")).isFalse();
    }

    @Test
    void handle_onCompletedTask_callsApplyParseSuccess() {
        InquiryParseResultHandler handler = new InquiryParseResultHandler(customerInquiryService);
        AiTaskDO task = new AiTaskDO();
        task.setId(1L);
        task.setStatus(AiTaskStatus.COMPLETED);
        task.setOutput("{\"groups\":[]}");

        handler.handle(task);

        verify(customerInquiryService).applyParseSuccess(1L, "{\"groups\":[]}");
    }

    @Test
    void handle_onFailedTask_callsApplyParseFailure() {
        InquiryParseResultHandler handler = new InquiryParseResultHandler(customerInquiryService);
        AiTaskDO task = new AiTaskDO();
        task.setId(2L);
        task.setStatus(AiTaskStatus.FAILED);
        task.setErrorMessage("联网搜索超时");

        handler.handle(task);

        verify(customerInquiryService).applyParseFailure(2L, "联网搜索超时");
    }
}

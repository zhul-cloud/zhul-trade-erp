package com.zhul.erp.modules.attachment;

import com.zhul.erp.framework.storage.VideoTranscoder;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * 视频转码在线程池里完成，回调线程没有租户上下文：按附件所属租户写回结果（audit SEC-011）。
 * 用模拟的转码器在另一个线程里执行回调，等同于真实线程池。
 */
class AttachmentTranscodeTenantContractTest extends IntegrationTestBase {

    private static final int TENANT = 99000094;

    @Autowired private AttachmentService attachmentService;
    @MockBean private VideoTranscoder transcoder;

    @BeforeEach
    void data() {
        cleanup();
        jdbc.update("insert into biz_attachment (tenant_id, owner_type, owner_id, kind, storage, file_key, url, file_name, "
                + "file_size, content_type, processing_status, uploaded_by) values (?, 'SALES_ORDER', 1, 3, 'LOCAL', "
                + "'it/transcode/a.mov', '', 'a.mov', 10, 'video/quicktime', ?, 0)", TENANT, VideoTranscoder.PROCESSING);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from biz_attachment where tenant_id = ?", TENANT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void transcodeCallbackWritesUnderAttachmentTenant() throws Exception {
        doAnswer(inv -> {
            Consumer<VideoTranscoder.Done> onSuccess = inv.getArgument(3);
            Thread t = new Thread(() -> onSuccess.accept(new VideoTranscoder.Done("it/transcode/a.mp4", 8, "video/mp4")));
            t.start();
            t.join();
            return null;
        }).when(transcoder).submit(anyString(), anyString(), anyBoolean(), any(), any());

        // 重启后把转码中的视频重新排队（启动任务入口）
        attachmentService.resumeTranscoding();

        assertEquals(VideoTranscoder.DONE, jdbc.queryForObject(
                "select processing_status from biz_attachment where tenant_id = ?", Integer.class, TENANT), "转码完成写回");
        assertEquals("it/transcode/a.mp4", jdbc.queryForObject(
                "select file_key from biz_attachment where tenant_id = ?", String.class, TENANT));
    }
}

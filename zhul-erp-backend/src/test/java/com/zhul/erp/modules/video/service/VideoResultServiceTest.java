package com.zhul.erp.modules.video.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VideoResultServiceTest {

    @Test
    void outputNameByModelAspectVersion() {
        assertEquals("DVP20EX200R_9x16_v3", VideoResultService.outputName("DVP20EX200R", "9:16", 3));
        assertEquals("6ES7321-1FH00-0AA0_16x9_v1", VideoResultService.outputName(" 6ES7321-1FH00-0AA0 ", "16:9", 1));
        assertEquals("A-B-C_9x16_v2", VideoResultService.outputName("A/B C", "9:16", 2), "斜杠、空格换成短横线");
        assertEquals("video_16x9_v1", VideoResultService.outputName(null, "16:9", 1));
    }
}

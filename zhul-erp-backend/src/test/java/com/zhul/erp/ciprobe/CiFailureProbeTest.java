package com.zhul.erp.ciprobe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 临时：验证 CI 能拦住失败的测试（audit DOC-005 失败注入），验证后随草稿 PR 一起删除，不合并 */
class CiFailureProbeTest {

    @Test
    void injectedFailure() {
        assertEquals(1, 2, "CI 失败注入");
    }
}

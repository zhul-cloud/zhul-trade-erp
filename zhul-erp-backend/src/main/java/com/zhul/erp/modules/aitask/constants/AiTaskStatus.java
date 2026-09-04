package com.zhul.erp.modules.aitask.constants;

/** ai_task.status 枚举值 */
public final class AiTaskStatus {
    private AiTaskStatus() {
    }

    public static final int QUEUED = 1;
    public static final int PROCESSING = 2;
    public static final int COMPLETED = 3;
    public static final int FAILED = 4;
}

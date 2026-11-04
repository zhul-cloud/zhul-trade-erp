package com.zhul.erp.modules.aitask.constants;

import java.util.List;

/** ai_task.status 枚举值 */
public final class AiTaskStatus {
    private AiTaskStatus() {
    }

    public static final int QUEUED = 1;
    public static final int PROCESSING = 2;
    public static final int COMPLETED = 3;
    public static final int FAILED = 4;

    /** 还没结束的状态：只有处于这些状态的任务可以被改为完成或失败 */
    public static final List<Integer> OPEN = List.of(QUEUED, PROCESSING);
}

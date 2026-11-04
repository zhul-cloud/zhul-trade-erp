package com.zhul.erp.modules.crm.followup.support;

import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;

/**
 * 主动开发阶段推导（第四期 design D4）：纯函数。取满足条件的最高阶段，再与当前阶段取较高者（只进不退）。
 */
public final class DevelopStageDeriver {

    /** 背调状态：1-背调中、2-已完成、3-失败 */
    public static final int REPORT_RUNNING = 1;
    public static final int REPORT_DONE = 2;
    /** 背调跟进优先级：1-立即重点跟进、2-正常跟进、3-低优先级、4-不建议跟进 */
    private static final int PRIORITY_NORMAL = 2;

    private DevelopStageDeriver() {
    }

    /**
     * @param potential      潜力星级 0–5（0 为未评）
     * @param reportRunning  有进行中的背调
     * @param reportPriority 最近一份已完成背调的跟进优先级，没有时为 null
     * @param contactable    有带邮箱、WhatsApp 或电话的联系人（含客户主联系人字段）
     * @param touchCount     触达次数
     * @param replied        选过「客户回复了」
     * @param signal         选过「出现采购信号」
     * @param current        当前阶段，新建时为空
     */
    public record Input(int potential, boolean reportRunning, Integer reportPriority, boolean contactable, int touchCount,
                        boolean replied, boolean signal, String current) {
    }

    /** @param unqualified 停在 D2 是因为资格不足（不是背调中），下一步提示停止或培育 */
    public record Result(String stage, boolean unqualified) {
    }

    public static Result derive(Input in) {
        boolean qualified = in.potential() >= 3
                || (in.reportPriority() != null && in.reportPriority() >= 1 && in.reportPriority() <= PRIORITY_NORMAL);
        boolean poor = (in.potential() >= 1 && in.potential() <= 2)
                || (in.reportPriority() != null && in.reportPriority() > PRIORITY_NORMAL);
        String derived;
        if (in.signal()) {
            derived = "D7";
        } else if (in.replied()) {
            derived = "D6";
        } else if (in.touchCount() >= 1) {
            derived = "D5";
        } else if (qualified) {
            derived = in.contactable() ? "D4" : "D3";
        } else if (in.reportRunning() || poor) {
            derived = "D2";
        } else {
            derived = "D1";
        }
        int d = FollowupConstants.DEVELOP_STAGES.indexOf(derived);
        int c = in.current() == null ? -1 : FollowupConstants.DEVELOP_STAGES.indexOf(in.current());
        String stage = c > d ? in.current() : derived;
        // 资格不足：停在 D2、没有背调在进行、也没有合格的依据
        boolean unqualified = "D2".equals(stage) && !in.reportRunning() && !qualified && poor;
        return new Result(stage, unqualified);
    }
}

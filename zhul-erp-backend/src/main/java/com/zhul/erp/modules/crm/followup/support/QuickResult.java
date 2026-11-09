package com.zhul.erp.modules.crm.followup.support;

import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.RecordFollowupRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 快速跟进结果（spec「快速跟进」，design D4）：每个结果对应一组阶段，预先定好客户情况或阶段答案。
 * 需要补充细节的答案（有问题、满意度、采购信号、明确拒绝、新需求、新品类、还没收到要填新送达日）不在这里。
 */
public enum QuickResult {

    S1_REPLIED("客户回复了", "S2", Set.of("S1"), DateRule.NORMAL, r -> r.setSituation(FollowupConstants.SITUATION_FEEDBACK)),
    TALKED("已和客户沟通", null, Set.of("S2", "S3"), DateRule.NORMAL, r -> r.setSituation(FollowupConstants.SITUATION_FEEDBACK)),
    FEEDBACK("客户给了反馈", "S5", Set.of("S4"), DateRule.NORMAL, r -> r.setSituation(FollowupConstants.SITUATION_FEEDBACK)),
    NEGOTIATING("客户在谈条件", "S6", Set.of("S5"), DateRule.TOMORROW,
            r -> r.setSituation(FollowupConstants.SITUATION_NEGOTIATING)),
    COMPARING("还在比较", null, Set.of("S5"), DateRule.NORMAL, r -> r.setSituation(FollowupConstants.SITUATION_FEEDBACK)),
    STILL_TALKING("还在谈", null, Set.of("S6"), DateRule.TOMORROW, r -> r.setSituation(FollowupConstants.SITUATION_NEGOTIATING)),
    PAYING("客户说在安排付款", null, Set.of("S7"), DateRule.NORMAL, r -> r.setSituation(FollowupConstants.SITUATION_FEEDBACK)),
    NO_REPLY("没回复", null, Set.of("S1", "S2", "S3", "S4", "S5", "S6", "S7"), DateRule.NO_REPLY,
            r -> r.setSituation(FollowupConstants.SITUATION_NO_REPLY)),

    SYNCED("已同步进度给客户", null, Set.of("F1", "F2", "F3", "F4", "F5", "F6", "A3", "A4"), DateRule.NORMAL, r -> { }),
    RECEIVED("已收到，没问题", "A2", Set.of("A1"), DateRule.NORMAL, r -> r.setReceiptAnswer(FollowupConstants.RECEIPT_OK)),
    USE_OK("正常使用", "A5", Set.of("A2"), DateRule.NORMAL, r -> r.setUseAnswer(FollowupConstants.USE_OK)),
    USE_NOT_YET("还没开始用", null, Set.of("A2"), DateRule.NORMAL, r -> r.setUseAnswer(FollowupConstants.USE_NOT_YET)),
    REVIEWED("已评价", null, Set.of("A6"), DateRule.NORMAL, r -> r.setReviewStatus(FollowupConstants.REVIEW_DONE)),
    REVIEW_DECLINED("婉拒评价", null, Set.of("A6"), DateRule.NORMAL, r -> r.setReviewStatus(FollowupConstants.REVIEW_DECLINED)),
    REVIEW_NOT_YET("还没评", null, Set.of("A6"), DateRule.NORMAL, r -> r.setReviewStatus(FollowupConstants.REVIEW_NOT_YET)),

    TOUCH_SENT("已发出触达", "D5", Set.of("D4"), DateRule.NORMAL, r -> {
        r.setTouchResult(FollowupConstants.TOUCH_SENT);
        // 已发出触达必须有方式；快速记录不问方式，记为「其他」
        r.setChannel(FollowupConstants.CHANNEL_OTHER);
    }),
    TOUCH_REPLIED("客户回复了", "D6", Set.of("D5"), DateRule.NORMAL, r -> r.setTouchResult(FollowupConstants.TOUCH_REPLIED)),
    KEEP_IN_TOUCH("保持联系", null, Set.of("D6"), DateRule.NORMAL, r -> r.setTouchResult(FollowupConstants.TOUCH_REPLIED)),
    TOUCH_NO_REPLY("没回复", null, Set.of("D5", "D6"), DateRule.NO_REPLY, r -> r.setTouchResult(FollowupConstants.TOUCH_NO_REPLY)),

    NO_PROJECT("近期没有项目", null, Set.of("G4", "G6", "G7"), DateRule.NORMAL, r -> {
        r.setAccountAnswer(FollowupConstants.ACCOUNT_NO_NEED);
        r.setNoNeedReason("NO_PROJECT");
    }),
    ACCOUNT_NO_REPLY("没回复", null, Set.of("G4", "G6", "G7"), DateRule.NO_REPLY,
            r -> r.setAccountAnswer(FollowupConstants.ACCOUNT_NO_REPLY));

    /** 推荐日期的修正：在谈条件改为明天；没回复最晚 3 天后 */
    public enum DateRule { NORMAL, TOMORROW, NO_REPLY }

    /** 没回复时下一步最晚隔几天 */
    public static final int NO_REPLY_MAX_DAYS = 3;

    private final String label;
    private final String advanceTo;
    private final Set<String> stages;
    private final DateRule dateRule;
    private final Consumer<RecordFollowupRequest> apply;

    QuickResult(String label, String advanceTo, Set<String> stages, DateRule dateRule, Consumer<RecordFollowupRequest> apply) {
        this.label = label;
        this.advanceTo = advanceTo;
        this.stages = stages;
        this.dateRule = dateRule;
        this.apply = apply;
    }

    public String label() {
        return label;
    }

    /** 选这个结果后推进到的阶段，不推进时为 null（只用于界面提示） */
    public String advanceTo() {
        return advanceTo;
    }

    public Set<String> stages() {
        return stages;
    }

    public DateRule dateRule() {
        return dateRule;
    }

    /** 把结果写进记跟进请求（客户情况或阶段答案） */
    public void applyTo(RecordFollowupRequest r) {
        apply.accept(r);
    }

    /** 某个阶段可选的结果，按定义顺序 */
    public static List<QuickResult> of(String stage) {
        return Arrays.stream(values()).filter(q -> q.stages.contains(stage)).toList();
    }
}

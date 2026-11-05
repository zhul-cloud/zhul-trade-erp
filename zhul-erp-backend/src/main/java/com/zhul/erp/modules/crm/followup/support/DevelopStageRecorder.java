package com.zhul.erp.modules.crm.followup.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.RecordFollowupRequest;
import com.zhul.erp.modules.crm.followup.entity.CustomerFollowupDO;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 主动开发的「记一次跟进」（第四期 design D6）：触达结果单选、采购信号。
 * 只校验和改内存里的对象，写库与重新推导由 FollowupService 在同一事务里完成；「明确拒绝」只保存，停止开发由前端接着完成。
 */
public final class DevelopStageRecorder {

    private static final Map<Integer, Integer> SITUATION_OF = Map.of(
            FollowupConstants.TOUCH_SENT, 0,
            FollowupConstants.TOUCH_REPLIED, FollowupConstants.SITUATION_REPLIED,
            FollowupConstants.TOUCH_SIGNAL, FollowupConstants.SITUATION_SIGNAL,
            FollowupConstants.TOUCH_NO_REPLY, FollowupConstants.SITUATION_NO_REPLY,
            FollowupConstants.TOUCH_REFUSED, FollowupConstants.SITUATION_REFUSED);

    private DevelopStageRecorder() {
    }

    public static boolean handles(FollowupItemDO item) {
        return item != null && item.getBoard() != null && item.getBoard() == FollowupConstants.BOARD_DEVELOP;
    }

    /** 主动开发必须选触达结果；其他板块不能带主动开发的字段（返回 FOLLOWUP_STAGE_MISMATCH） */
    public static void validate(RecordFollowupRequest req, FollowupItemDO item, int channel) {
        boolean any = req.getTouchResult() != null || hasText(req.getSignalText());
        if (!handles(item)) {
            if (any) {
                throw BizException.of(FollowupConstants.ERROR_STAGE_MISMATCH, "跟进对象已到其他阶段，请刷新后重试");
            }
            return;
        }
        Integer t = req.getTouchResult();
        if (t == null) {
            throw new BizException("请选择触达结果");
        }
        if (!FollowupConstants.TOUCH_NAMES.containsKey(t)) {
            throw new BizException("触达结果不正确");
        }
        if (t == FollowupConstants.TOUCH_SENT && channel == 0) {
            throw new BizException("已发出触达请选择方式");
        }
        if (hasText(req.getSignalText())) {
            if (t != FollowupConstants.TOUCH_SIGNAL) {
                throw new BizException("只有「出现采购信号」才填型号和数量");
            }
            if (req.getSignalText().trim().length() > FollowupConstants.PROSPECT_TEXT_MAX) {
                throw new BizException("型号和数量不能超过200个字");
            }
        }
    }

    /** 主动开发的客户情况位（覆盖前端传的 situation） */
    public static int situation(RecordFollowupRequest req) {
        return SITUATION_OF.getOrDefault(req.getTouchResult(), 0);
    }

    public static void fill(CustomerFollowupDO r, RecordFollowupRequest req) {
        r.setTouchResult(req.getTouchResult() == null ? 0 : req.getTouchResult());
        r.setSignalText(hasText(req.getSignalText()) ? req.getSignalText().trim() : "");
    }

    /** 按触达结果改计数和日期；阶段由随后的重新推导给出（D5 / D6 / D7） */
    public static void applyToItem(FollowupItemDO item, RecordFollowupRequest req, LocalDate today) {
        int t = req.getTouchResult();
        if (t == FollowupConstants.TOUCH_SENT || t == FollowupConstants.TOUCH_REPLIED) {
            item.setTouchCount(nz(item.getTouchCount()) + 1);
            if (item.getFirstTouchDate() == null) {
                item.setFirstTouchDate(today);
            }
        }
        // 采购信号也是客户回复，回复率按第一次回复算
        if ((t == FollowupConstants.TOUCH_REPLIED || t == FollowupConstants.TOUCH_SIGNAL) && item.getRepliedAt() == null) {
            item.setRepliedAt(LocalDateTime.now());
        }
        if (t == FollowupConstants.TOUCH_SIGNAL) {
            if (item.getSignalAt() == null) {
                item.setSignalAt(LocalDateTime.now());
            }
            if (hasText(req.getSignalText())) {
                item.setSignalText(req.getSignalText().trim());
            }
        }
        if (t == FollowupConstants.TOUCH_REPLIED || t == FollowupConstants.TOUCH_SIGNAL) {
            item.setNoReplyStreak(0);
            item.setFeedbackSeen(1);
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}

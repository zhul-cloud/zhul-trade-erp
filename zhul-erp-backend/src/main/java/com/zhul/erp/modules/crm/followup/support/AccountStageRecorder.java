package com.zhul.erp.modules.crm.followup.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.RecordFollowupRequest;
import com.zhul.erp.modules.crm.followup.entity.CustomerFollowupDO;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;

import java.util.Map;

/**
 * 客户经营的「记一次跟进」（第三期 design D8）：客户情况单选、新品类、没有需求的原因、季度回顾。
 * 只校验和改内存里的对象，写库与重新推导由 FollowupService 在同一事务里完成。
 */
public final class AccountStageRecorder {

    private static final Map<Integer, Integer> SITUATION_OF = Map.of(
            FollowupConstants.ACCOUNT_NEW_NEED, FollowupConstants.SITUATION_NEW_NEED,
            FollowupConstants.ACCOUNT_CROSS, FollowupConstants.SITUATION_CROSS,
            FollowupConstants.ACCOUNT_NO_NEED, FollowupConstants.SITUATION_NO_NEED,
            FollowupConstants.ACCOUNT_NO_REPLY, FollowupConstants.SITUATION_NO_REPLY);

    private AccountStageRecorder() {
    }

    public static boolean handles(FollowupItemDO item) {
        return item != null && item.getBoard() != null && item.getBoard() == FollowupConstants.BOARD_ACCOUNT;
    }

    /**
     * 客户经营必须选客户情况；其他板块不能带客户经营的字段（返回 FOLLOWUP_STAGE_MISMATCH）。
     *
     * @param strategic 客户是否战略客户（只有战略客户能勾「这次是季度回顾」）
     */
    public static void validate(RecordFollowupRequest req, FollowupItemDO item, boolean strategic) {
        boolean any = req.getAccountAnswer() != null || hasText(req.getCrossCategory()) || hasText(req.getNoNeedReason())
                || Boolean.TRUE.equals(req.getQuarterReview());
        if (!handles(item)) {
            if (any) {
                throw BizException.of(FollowupConstants.ERROR_STAGE_MISMATCH, "跟进对象已到其他阶段，请刷新后重试");
            }
            return;
        }
        Integer a = req.getAccountAnswer();
        if (a == null) {
            throw new BizException("请选择客户情况");
        }
        if (!FollowupConstants.ACCOUNT_ANSWER_NAMES.containsKey(a)) {
            throw new BizException("客户情况不正确");
        }
        if (hasText(req.getCrossCategory())) {
            if (a != FollowupConstants.ACCOUNT_CROSS) {
                throw new BizException("只有「聊了新品类」才填品类");
            }
            if (req.getCrossCategory().trim().length() > FollowupConstants.CROSS_CATEGORY_MAX) {
                throw new BizException("品类不能超过50个字");
            }
        }
        if (a == FollowupConstants.ACCOUNT_NO_NEED) {
            if (!hasText(req.getNoNeedReason()) || !FollowupConstants.NO_NEED_REASONS.containsKey(req.getNoNeedReason().trim())) {
                throw new BizException("请选择没有需求的原因");
            }
        } else if (hasText(req.getNoNeedReason())) {
            throw new BizException("只有「当前没有需求」才选原因");
        }
        if (Boolean.TRUE.equals(req.getQuarterReview()) && !strategic) {
            throw new BizException("只有战略客户才有季度回顾");
        }
    }

    /** 客户经营的客户情况位（覆盖前端传的 situation） */
    public static int situation(RecordFollowupRequest req) {
        return SITUATION_OF.getOrDefault(req.getAccountAnswer(), 0);
    }

    public static void fill(CustomerFollowupDO r, RecordFollowupRequest req) {
        r.setAccountAnswer(req.getAccountAnswer() == null ? 0 : req.getAccountAnswer());
        r.setCrossCategory(hasText(req.getCrossCategory()) ? req.getCrossCategory().trim() : "");
        r.setNoNeedReason(hasText(req.getNoNeedReason()) ? req.getNoNeedReason().trim() : "");
        r.setQuarterReview(Boolean.TRUE.equals(req.getQuarterReview()) ? 1 : 0);
    }

    /** 按答案改跟进对象的标记；阶段由随后的重新推导给出（G5 / G6 / G4） */
    public static void applyToItem(FollowupItemDO item, RecordFollowupRequest req) {
        switch (req.getAccountAnswer()) {
            case FollowupConstants.ACCOUNT_NEW_NEED -> item.setAccountNeed(1);
            case FollowupConstants.ACCOUNT_CROSS -> {
                item.setAccountCross(1);
                item.setCrossCategory(hasText(req.getCrossCategory()) ? req.getCrossCategory().trim() : "");
            }
            case FollowupConstants.ACCOUNT_NO_NEED -> {
                item.setAccountNeed(0);
                item.setAccountCross(0);
                item.setCrossCategory("");
            }
            default -> {
                // 没回复：连续没回复次数由通用逻辑按客户情况位计
            }
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}

package com.zhul.erp.modules.crm.followup.support;

import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 按客户汇总跟进对象（change followup-group-by-customer）：客户所处阶段、待办归类。
 * 概览、今日待办、团队视图共用，保证三处数字口径一致。
 */
public final class CustomerRollup {

    /** 各板块阶段从前到后；客户所处阶段取名下单据中最靠后的 */
    public static final List<String> STAGE_ORDER = List.of(
            "D1", "D2", "D3", "D4", "D5", "D6", "D7",
            "S1", "S2", "S3", "S4", "S5", "S6", "S7",
            "F1", "F2", "F3", "F4", "F5", "F6",
            "A1", "A2", "A3", "A4", "A5", "A6",
            "G4", "G5", "G6", "G7");

    /** 按客户分组显示的板块：商机成交、订单履约、售后评价 */
    public static final Set<Integer> GROUPED_BOARDS = Set.of(FollowupConstants.BOARD_SALES,
            FollowupConstants.BOARD_FULFIL, FollowupConstants.BOARD_AFTER_SALES);

    /** 记跟进必须回答阶段问题的阶段：不能批量跟进，选多张单据时阶段必须相同 */
    public static final Set<String> ANSWER_STAGES = Set.of("A1", "A2", "A5", "A6");

    public static final int NONE = 0;
    public static final int OVERDUE = 1;
    public static final int TODAY = 2;
    /** 今天已经记过跟进、下一步仍是今天：不计入待办 */
    public static final int HANDLED = 3;

    private CustomerRollup() {
    }

    /** 阶段序号，从 1 开始；不认识的阶段为 0 */
    public static int rank(String code) {
        return STAGE_ORDER.indexOf(code) + 1;
    }

    /** 名下单据中最靠后的阶段；没有单据返回空串 */
    public static String furthestStage(Collection<FollowupItemDO> items) {
        String best = "";
        for (FollowupItemDO i : items) {
            if (rank(i.getStageCode()) > rank(best)) {
                best = i.getStageCode();
            }
        }
        return best;
    }

    /** 算待办的单据：不在培育、有下一步日期、下一步不跟着售后单走（A3、A4） */
    public static boolean todo(FollowupItemDO i) {
        return i.getNurtureUntil() == null && i.getNextActionDate() != null
                && !Objects.equals(i.getNextActionSource(), FollowupConstants.NEXT_AFTER_SALES);
    }

    public static boolean handledToday(FollowupItemDO i, LocalDate today) {
        return today.equals(i.getNextActionDate()) && i.getLastFollowupAt() != null
                && today.equals(i.getLastFollowupAt().toLocalDate());
    }

    /** 单张单据的待办归类 */
    public static int classify(FollowupItemDO i, LocalDate today) {
        if (!todo(i) || i.getNextActionDate().isAfter(today)) {
            return NONE;
        }
        if (i.getNextActionDate().isBefore(today)) {
            return OVERDUE;
        }
        return handledToday(i, today) ? HANDLED : TODAY;
    }

    /** 客户的待办归类：有逾期算逾期；否则有今天没跟进的算今天；否则只剩今天已跟进的算已跟进 */
    public static int classify(Collection<FollowupItemDO> items, LocalDate today) {
        int out = NONE;
        for (FollowupItemDO i : items) {
            int c = classify(i, today);
            if (c == OVERDUE) {
                return OVERDUE;
            }
            if (c == TODAY || (c == HANDLED && out == NONE)) {
                out = c;
            }
        }
        return out;
    }

    /** 按客户分组，保持原顺序（先出现的客户在前） */
    public static Map<Long, List<FollowupItemDO>> byCustomer(Collection<FollowupItemDO> items) {
        Map<Long, List<FollowupItemDO>> out = new LinkedHashMap<>(Math.max(16, items.size()));
        for (FollowupItemDO i : items) {
            out.computeIfAbsent(i.getCustomerId(), k -> new ArrayList<>(4)).add(i);
        }
        return out;
    }
}

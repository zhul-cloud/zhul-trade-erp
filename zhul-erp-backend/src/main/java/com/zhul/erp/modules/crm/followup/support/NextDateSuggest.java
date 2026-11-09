package com.zhul.erp.modules.crm.followup.support;

import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 下一步日期推荐（spec「下一步日期推荐」，design D2）：记跟进时「什么时候」默认选的日期和理由。
 * 纯函数，不读库；客户情况（在谈条件、没回复）的修正由前端做。
 */
public final class NextDateSuggest {

    /** 参考天数为 0 或没有时的默认间隔 */
    static final int FALLBACK_DAYS = 3;
    /** 按参考天数推荐时的上下限 */
    static final int MIN_DAYS = 1;
    static final int MAX_DAYS = 14;
    /** 报价后各节点都过了：7 天后 */
    static final int AFTER_QUOTE_LATE_DAYS = 7;

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");

    private NextDateSuggest() {
    }

    public record Suggestion(LocalDate date, String reason) {
    }

    /**
     * @param quoteDays   报价后提醒节点天数，从小到大
     * @param piInterval  PI 后提醒间隔天数
     * @param sequence    主动开发触达序列天数（第 1 天为首次触达），从小到大
     * @param refDays     该阶段参考停留天数，可为空
     * @param accountDays 客户经营按等级的联系间隔，可为空
     */
    public static Suggestion of(FollowupItemDO i, LocalDate today, List<Integer> quoteDays, int piInterval,
                                List<Integer> sequence, Integer refDays, Integer accountDays) {
        int board = i.getBoard() == null ? 0 : i.getBoard();
        int group = i.getStageGroup() == null ? 0 : i.getStageGroup();
        String stage = Objects.requireNonNullElse(i.getStageCode(), "");
        if (board == FollowupConstants.BOARD_SALES && group == FollowupConstants.GROUP_AFTER_QUOTE) {
            LocalDate start = i.getSentAt() != null ? i.getSentAt().toLocalDate()
                    : i.getCadenceFrom() != null ? i.getCadenceFrom() : today;
            for (int d : quoteDays == null ? List.<Integer>of() : quoteDays) {
                if (start.plusDays(d).isAfter(today)) {
                    return new Suggestion(start.plusDays(d), "报价后第 " + d + " 天");
                }
            }
            return new Suggestion(today.plusDays(AFTER_QUOTE_LATE_DAYS), "报价后，隔 " + AFTER_QUOTE_LATE_DAYS + " 天再跟");
        }
        if (board == FollowupConstants.BOARD_SALES && group == FollowupConstants.GROUP_AFTER_PI) {
            if (i.getExpectedPayDate() != null && i.getExpectedPayDate().isAfter(today)) {
                return new Suggestion(i.getExpectedPayDate(), "客户说 " + i.getExpectedPayDate().format(MD) + " 付款");
            }
            int k = Math.max(MIN_DAYS, piInterval);
            return new Suggestion(today.plusDays(k), "PI 后每 " + k + " 天问一次付款");
        }
        if (board == FollowupConstants.BOARD_DEVELOP && sequence != null && sequence.size() >= 2) {
            if ("D4".equals(stage)) {
                // 今天发出首次触达，下一次按序列第二个节点
                int node = sequence.get(1);
                return new Suggestion(today.plusDays(node - 1L), "触达序列第 " + node + " 天");
            }
            if ("D5".equals(stage)) {
                LocalDate first = i.getFirstTouchDate() == null ? today : i.getFirstTouchDate();
                for (int node : sequence) {
                    LocalDate d = first.plusDays(node - 1L);
                    if (d.isAfter(today)) {
                        return new Suggestion(d, "触达序列第 " + node + " 天");
                    }
                }
            }
        }
        if (board == FollowupConstants.BOARD_ACCOUNT && accountDays != null && accountDays > 0) {
            return new Suggestion(today.plusDays(accountDays), "按客户等级每 " + accountDays + " 天联系一次");
        }
        if (refDays == null || refDays <= 0) {
            return new Suggestion(today.plusDays(FALLBACK_DAYS), FALLBACK_DAYS + " 天后");
        }
        int days = Math.max(MIN_DAYS, Math.min(MAX_DAYS, refDays));
        return new Suggestion(today.plusDays(days), stage + " 参考停留 " + refDays + " 天"
                + (days == refDays ? "" : "，最多隔 " + MAX_DAYS + " 天"));
    }
}

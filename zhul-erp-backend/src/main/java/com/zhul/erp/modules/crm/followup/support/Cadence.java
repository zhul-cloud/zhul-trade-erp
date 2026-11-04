package com.zhul.erp.modules.crm.followup.support;

import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 系统提醒节奏（design D6）：下一步来源为「系统」时，按分组算出提醒日期和提醒文字。
 * <ul>
 *   <li>报价前：起算日当天，首次回复</li>
 *   <li>报价后：起算日 + 1、3、7、14 天，取已经到达的最后一个节点（都没到取第一个）；没处理的节点逾期显示</li>
 *   <li>PI 后：有预计付款日用这天（过了保持逾期）；没有就从起算日起每 2 天一个节点，取已到达的最后一个</li>
 * </ul>
 * 纯函数，不读库；SOP 默认动作按「阶段 → 节点键 → 文字」传入，节点键为 d1、d3…、pi、default。
 */
public final class Cadence {

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");

    private Cadence() {
    }

    public record Reminder(LocalDate date, String text) {
    }

    public static Reminder of(int group, String stage, LocalDate from, LocalDate expectedPay, LocalDate today,
                              FollowupSettings.Params p, Map<String, Map<String, String>> actions) {
        LocalDate start = from == null ? today : from;
        Map<String, String> a = actions == null ? Map.of() : actions.getOrDefault(stage, Map.of());
        if (group == FollowupConstants.GROUP_AFTER_QUOTE) {
            List<Integer> days = p.quoteDays();
            int pick = days.get(0);
            for (int d : days) {
                if (!start.plusDays(d).isAfter(today)) {
                    pick = d;
                }
            }
            String text = a.getOrDefault("d" + pick, a.getOrDefault("default", "跟进报价，问清客户的反馈"));
            return new Reminder(start.plusDays(pick), "第 " + pick + " 天：" + text);
        }
        if (group == FollowupConstants.GROUP_AFTER_PI) {
            String text = a.getOrDefault("pi", a.getOrDefault("default", "确认付款进度"));
            if (expectedPay != null) {
                return new Reminder(expectedPay, "客户说 " + expectedPay.format(MD) + " 付款：" + text);
            }
            int k = p.piIntervalDays();
            long passed = ChronoUnit.DAYS.between(start, today);
            long n = Math.max(1, passed / k);
            return new Reminder(start.plusDays(n * k), "PI 发出第 " + (n * k) + " 天：" + text);
        }
        return new Reminder(start, a.getOrDefault("default", "首次回复客户"));
    }

    /**
     * 报价后是否建议转培育（spec「转培育」）：发出满设定天数且这期间没有一次跟进勾选过「客户给了反馈」，
     * 或连续「没回复」达到设定次数。
     */
    public static boolean suggestNurture(int group, LocalDate from, boolean feedbackSeen, int noReplyStreak,
                                         LocalDate today, FollowupSettings.Params p) {
        if (group != FollowupConstants.GROUP_AFTER_QUOTE) {
            return false;
        }
        boolean old = from != null && ChronoUnit.DAYS.between(from, today) >= p.nurtureQuoteDays() && !feedbackSeen;
        return old || noReplyStreak >= p.nurtureNoReplyTimes();
    }
}

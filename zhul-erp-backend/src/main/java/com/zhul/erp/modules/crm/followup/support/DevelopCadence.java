package com.zhul.erp.modules.crm.followup.support;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 主动开发的系统提醒（第四期 design D5）：纯函数。业务员定的日期、培育由调用方处理。
 * 当天提醒的阶段以进入该阶段的日期为准，过了就显示逾期。
 */
public final class DevelopCadence {

    public static final String SUGGEST_NURTURE = "建议转培育或停止开发";

    private DevelopCadence() {
    }

    /**
     * @param stage          D1–D7
     * @param unqualified    D2 资格不足
     * @param stageSince     进入当前阶段的日期
     * @param firstTouchDate 首次触达日，序列第 1 天
     * @param sequence       序列天数，从小到大，如 1,4,9,16
     * @param actions        该阶段作战卡的建议（default_actions），D5 的键为 d4、d9、d16
     */
    public record Input(String stage, boolean unqualified, LocalDate stageSince, LocalDate firstTouchDate,
                        List<Integer> sequence, Map<String, String> actions) {
    }

    /**
     * @param day           D5 当前在序列第几天（节点天数），其他阶段为 0
     * @param suggestNurture 序列发完仍没回复
     */
    public record Reminder(LocalDate date, String action, int day, boolean suggestNurture) {
    }

    /** @return 没有提醒（D2 背调中、D6 等业务员定日期）时日期为空 */
    public static Reminder of(Input in, LocalDate today) {
        Map<String, String> a = in.actions() == null ? Map.of() : in.actions();
        LocalDate since = in.stageSince() == null ? today : in.stageSince();
        switch (in.stage()) {
            case "D1":
                return new Reminder(since, a.getOrDefault("default", "做背调，判断值不值得开发"), 0, false);
            case "D2":
                return in.unqualified()
                        ? new Reminder(since, "资格不足，建议停止或培育", 0, false)
                        : new Reminder(null, "背调进行中，出结果后自动推进", 0, false);
            case "D3":
                return new Reminder(since, a.getOrDefault("default", "找采购、维修或技术负责人"), 0, false);
            case "D4":
                return new Reminder(since, a.getOrDefault("default", "发首封开发信"), 0, false);
            case "D5":
                return sequence(in, a, today);
            case "D7":
                return new Reminder(since, a.getOrDefault("default", "转商机"), 0, false);
            default:
                return new Reminder(null, a.getOrDefault("default", ""), 0, false);
        }
    }

    private static Reminder sequence(Input in, Map<String, String> a, LocalDate today) {
        List<Integer> seq = in.sequence();
        LocalDate first = in.firstTouchDate() == null ? today : in.firstTouchDate();
        if (seq == null || seq.size() < 2) {
            return new Reminder(first, a.getOrDefault("default", "换一个新价值点再跟一次"), 0, false);
        }
        int last = seq.get(seq.size() - 1);
        LocalDate lastDate = first.plusDays(last - 1L);
        if (today.isAfter(lastDate)) {
            return new Reminder(lastDate, SUGGEST_NURTURE, last, true);
        }
        // 第一个节点之后：取已到达的最后一个节点；还没到第二个节点时给第二个节点
        int node = seq.get(1);
        for (int i = 1; i < seq.size(); i++) {
            if (!first.plusDays(seq.get(i) - 1L).isAfter(today)) {
                node = seq.get(i);
            }
        }
        String hint = a.get("d" + node);
        String action = hint != null ? hint : "第 " + node + " 天：" + a.getOrDefault("default", "换一个新价值点再跟一次");
        return new Reminder(first.plusDays(node - 1L), action, node, false);
    }
}

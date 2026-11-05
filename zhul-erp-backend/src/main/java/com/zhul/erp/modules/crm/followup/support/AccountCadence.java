package com.zhul.erp.modules.crm.followup.support;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 客户经营的系统提醒（第三期 design D4、D5）：纯函数。按等级、预计采购、沉睡激活、季度回顾四种提醒取最早的一个，
 * 并给出触达理由和下一步。暂停（其他板块有进行中的对象）、业务员定的日期由调用方处理。
 */
public final class AccountCadence {

    private static final String[] GRADE_NAMES = {"", "S", "A", "B", "C"};

    private AccountCadence() {
    }

    /**
     * @param grade          经营等级 1–4（S–C）
     * @param followDays     S、A、B、C 的联系间隔
     * @param lastContact    上次联系日（任意板块最近一条跟进记录，没有时取最近一次下单日），可为空
     * @param patternNext    预计下次采购日，可为空
     * @param aheadDays      预计采购提前几天提醒
     * @param lastOrderDate  最近一次下单日，可为空（沉睡的触达理由带上天数）
     * @param dormantSince   开始沉睡的日期，不沉睡为空
     * @param dormantInterval 沉睡客户每隔几天提醒一次
     * @param strategic      是否战略客户
     * @param reviewFrom     季度回顾的起算日（上次回顾日，没有时取设为战略客户的日期），可为空
     * @param reviewDays     季度回顾间隔
     */
    public record Input(int grade, int[] followDays, LocalDate lastContact, LocalDate patternNext, int aheadDays,
                        LocalDate lastOrderDate, LocalDate dormantSince, int dormantInterval, boolean strategic, LocalDate reviewFrom,
                        int reviewDays) {
    }

    /** @param reason 触达理由（≤100 字），列表「经营建议」和作战卡显示；action 为下一步 */
    public record Reminder(LocalDate date, String reason, String action) {
    }

    /** @return 最早的一个提醒；一个都算不出来时为 null（如没有等级、也没有任何日期） */
    public static Reminder of(Input in, LocalDate today) {
        List<Reminder> all = new ArrayList<>(4);
        if (in.grade() >= 1 && in.grade() <= 4 && in.followDays() != null && in.followDays().length >= in.grade()) {
            int days = in.followDays()[in.grade() - 1];
            LocalDate from = in.lastContact() == null ? today : in.lastContact();
            String g = GRADE_NAMES[in.grade()];
            all.add(new Reminder(from.plusDays(days), g + " 级每 " + days + " 天联系",
                    "按 " + g + " 级节奏联系，问近期项目和备件计划"));
        }
        // 预计采购日已经在上次联系之前：这一轮已经联系过，不再按它提醒
        if (in.patternNext() != null && (in.lastContact() == null || in.patternNext().isAfter(in.lastContact()))) {
            int m = in.patternNext().getMonthValue();
            all.add(new Reminder(in.patternNext().minusDays(in.aheadDays()), "预计 " + m + " 月采购，提前问计划",
                    "预计 " + m + " 月采购，提前问备件和项目计划"));
        }
        if (in.dormantSince() != null && in.dormantInterval() > 0 && !in.dormantSince().isAfter(today)) {
            // 从开始沉睡起每隔 n 天一个节点，取已到达的最后一个；这个节点之后已经联系过的，顺延到下一个
            long k = ChronoUnit.DAYS.between(in.dormantSince(), today) / in.dormantInterval();
            LocalDate node = in.dormantSince().plusDays(k * in.dormantInterval());
            if (in.lastContact() != null && !in.lastContact().isBefore(node)) {
                node = node.plusDays(in.dormantInterval());
            }
            String reason = in.lastOrderDate() == null ? "沉睡客户，发同品类现货清单激活"
                    : "沉睡 " + ChronoUnit.DAYS.between(in.lastOrderDate(), today) + " 天，发同品类现货清单激活";
            all.add(new Reminder(node, reason, "发同品类现货清单或新到货，问近期计划"));
        }
        if (in.strategic() && in.reviewFrom() != null && in.reviewDays() > 0) {
            all.add(new Reminder(in.reviewFrom().plusDays(in.reviewDays()), "战略客户季度回顾",
                    "季度回顾：项目、重点品牌和采购计划"));
        }
        return all.stream().min(Comparator.comparing(Reminder::date)).orElse(null);
    }
}

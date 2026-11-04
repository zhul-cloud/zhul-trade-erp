package com.zhul.erp.modules.crm.followup.support;

import com.zhul.erp.modules.aftersales.constants.AfterSalesConstants;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

/**
 * 售后评价推导（design D7，spec「售后评价跟进对象与阶段」「售后评价提醒」「售后结束」）：只做计算、不查库。
 * 优先级：处理中的售后单 → 待客户确认的售后单 → 已确认满意度 → 使用正常或售后单已结案 → 已签收 → 签收确认。
 */
public final class AfterSalesDeriver {

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");
    /** 签收后第几天问使用情况 */
    public static final int USE_CHECK_DAYS = 7;

    private AfterSalesDeriver() {
    }

    /** 跟进对象上已记下的售后状态 */
    public record State(LocalDate signedDate, boolean usedOk, int satisfaction, int reviewStatus, String reviewPlatform,
                        LocalDate invitedAt, LocalDate etaDate) {
    }

    /**
     * @param doc     A3、A4 时对应的售后单，否则为 null
     * @param end     售后应当结束（满意度已定且不需要邀请、或评价提醒用完）
     * @param missed  结束原因是提醒用完仍未评价
     */
    public record Result(String stage, int group, OrderFacts.AfterSales doc, LocalDate signedDate, String situation,
                         boolean end, boolean missed) {
    }

    public static Result derive(OrderFacts f, State s, FollowupSettings.OrderParams p, LocalDate today) {
        LocalDate signed = s.signedDate() != null ? s.signedDate() : (f.completed() ? f.completedDate() : null);
        OrderFacts.AfterSales open = latest(f, AfterSalesConstants.AS_OPEN);
        OrderFacts.AfterSales waiting = latest(f, AfterSalesConstants.AS_WAITING);
        OrderFacts.AfterSales closed = latest(f, AfterSalesConstants.AS_CLOSED);
        if (open != null) {
            return result("A3", open, signed, "售后单 " + open.asNo() + " 处理中");
        }
        if (waiting != null) {
            return result("A4", waiting, signed, "售后单 " + waiting.asNo() + " 待客户确认");
        }
        if (s.satisfaction() == FollowupConstants.SATISFIED && s.reviewStatus() == FollowupConstants.REVIEW_INVITED) {
            LocalDate inv = s.invitedAt() == null ? today : s.invitedAt();
            boolean missed = !today.isBefore(inv.plusDays((long) p.reviewDays() * (p.reviewTimes() + 1)));
            String platform = s.reviewPlatform() == null || s.reviewPlatform().isBlank() ? "" : "（" + s.reviewPlatform() + "）";
            return new Result("A6", groupOf("A6"), null, signed, MD.format(inv) + " 已邀请评价" + platform, missed, missed);
        }
        if (s.satisfaction() > 0) {
            // 评价已处理（已评价 / 婉拒）是从 A6 结束的；其余（一般、不满意、先不邀请）在 A5 结束
            boolean viaReview = s.reviewStatus() == FollowupConstants.REVIEW_DONE
                    || s.reviewStatus() == FollowupConstants.REVIEW_DECLINED;
            String stage = viaReview ? "A6" : "A5";
            return new Result(stage, groupOf(stage), null, signed, "满意度："
                    + FollowupConstants.SATISFACTION_NAMES.getOrDefault(s.satisfaction(), ""), true, false);
        }
        boolean closedAfterSign = signed != null && closed != null;
        if (s.usedOk() || closedAfterSign) {
            String text = closedAfterSign && closed.closedAt() != null
                    ? "售后单 " + MD.format(closed.closedAt().toLocalDate()) + " 结案" : "使用正常";
            return result("A5", null, signed, text);
        }
        if (signed != null) {
            return result("A2", null, signed, MD.format(signed) + " 签收，签收 " + ChronoUnit.DAYS.between(signed, today) + " 天");
        }
        String eta = s.etaDate() == null ? "" : "预计 " + MD.format(s.etaDate()) + " 送达，";
        return result("A1", null, null, eta + "还没确认收到");
    }

    /**
     * 提醒节点（与订单履约同一套替换规则）：A1 预计送达日、A2 签收后第 7 天、A5 进入当天、
     * A6 邀请后每隔 n 天一次（最多 m 次，节点编码带次数）；A3、A4 跟售后单走，不生成节点。
     *
     * @return null 表示这个阶段没有系统节点
     */
    public static FulfilDeriver.Node node(Result r, State s, LocalDate stageSinceDate, FollowupSettings.OrderParams p,
                                          LocalDate today) {
        switch (r.stage()) {
            case "A1":
                return new FulfilDeriver.Node("A1", s.etaDate() == null ? stageSinceDate : s.etaDate());
            case "A2":
                return new FulfilDeriver.Node("A2", r.signedDate().plusDays(USE_CHECK_DAYS));
            case "A5":
                return new FulfilDeriver.Node("A5", stageSinceDate);
            case "A6": {
                LocalDate inv = s.invitedAt() == null ? stageSinceDate : s.invitedAt();
                long k = Math.min(p.reviewTimes(), ChronoUnit.DAYS.between(inv, today) / p.reviewDays());
                return k <= 0 ? new FulfilDeriver.Node("A6", inv)
                        : new FulfilDeriver.Node("A6#" + k, inv.plusDays(k * p.reviewDays()));
            }
            default:
                return null;
        }
    }

    /** A6 节点编码里的提醒次数 */
    public static int remindCountOf(String nodeCode) {
        if (nodeCode == null || !nodeCode.startsWith("A6#")) {
            return 0;
        }
        return Integer.parseInt(nodeCode.substring(3));
    }

    public static int groupOf(String stage) {
        return switch (stage) {
            case "A1" -> FollowupConstants.GROUP_SIGN;
            case "A2" -> FollowupConstants.GROUP_USE;
            case "A3", "A4" -> FollowupConstants.GROUP_ISSUE;
            default -> FollowupConstants.GROUP_REVIEW;
        };
    }

    private static Result result(String stage, OrderFacts.AfterSales doc, LocalDate signed, String situation) {
        return new Result(stage, groupOf(stage), doc, signed, situation, false, false);
    }

    private static OrderFacts.AfterSales latest(OrderFacts f, int status) {
        return f.afterSales().stream().filter(a -> a.status() == status)
                .max(Comparator.comparing(OrderFacts.AfterSales::createdAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(OrderFacts.AfterSales::id))
                .orElse(null);
    }

    /** 订单有没有还在处理的售后单（处理中或待客户确认） */
    public static boolean hasOpenCase(OrderFacts f) {
        return f.afterSales().stream().anyMatch(a -> a.status() == AfterSalesConstants.AS_OPEN
                || a.status() == AfterSalesConstants.AS_WAITING);
    }
}

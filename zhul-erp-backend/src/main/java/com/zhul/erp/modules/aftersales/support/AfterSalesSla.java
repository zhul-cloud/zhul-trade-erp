package com.zhul.erp.modules.aftersales.support;

import com.zhul.erp.modules.aftersales.constants.AfterSalesConstants;
import com.zhul.erp.modules.aftersales.constants.AfterSalesConstants.SlaRule;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;

import java.time.LocalDateTime;

/**
 * 售后单截止时间（spec sales/after-sales「处理时限」）。每次写售后单前调用 {@link #refresh}，结果落库供列表筛选与工作台计数。
 * 未首次响应：收到反馈时间 + 首次时限；已响应且处理中：起算时间 + 更新间隔；待客户确认只看手填时间；
 * 与手填的下次更新时间取较早者（相等取等级截止）；已结案、已撤销没有截止时间。
 */
public final class AfterSalesSla {

    private AfterSalesSla() {
    }

    public static void refresh(AfterSalesDO a) {
        a.setDueAt(null);
        a.setDueType(AfterSalesConstants.DUE_NONE);
        boolean open = a.getStatus() == AfterSalesConstants.AS_OPEN;
        if (!open && a.getStatus() != AfterSalesConstants.AS_WAITING) {
            return;
        }
        LocalDateTime level = open ? levelDue(a) : null;
        LocalDateTime manual = a.getNextUpdateAt();
        if (level != null && (manual == null || !manual.isBefore(level))) {
            a.setDueAt(level);
            a.setDueType(a.getRespondedAt() == null ? AfterSalesConstants.DUE_FIRST : AfterSalesConstants.DUE_UPDATE);
        } else if (manual != null) {
            a.setDueAt(manual);
            a.setDueType(AfterSalesConstants.DUE_MANUAL);
        }
    }

    /** 按等级算出的截止时间：处理中才有；S5 已响应后没有；没有起算时间（升级前的存量单）时只看手填 */
    public static LocalDateTime levelDue(AfterSalesDO a) {
        if (a.getStatus() != AfterSalesConstants.AS_OPEN) {
            return null;
        }
        SlaRule r = AfterSalesConstants.SLA_RULES.getOrDefault(a.getLevelCode(), AfterSalesConstants.SLA_DEFAULT);
        if (a.getRespondedAt() == null) {
            return plus(a.getReceivedAt(), r.firstMinutes(), r.natural());
        }
        if (r.intervalMinutes() <= 0 || a.getSlaStartAt() == null) {
            return null;
        }
        return plus(a.getSlaStartAt(), r.intervalMinutes(), r.natural());
    }

    /** OVERDUE / SOON / NORMAL；没有截止时间为空 */
    public static String state(LocalDateTime due, LocalDateTime now) {
        if (due == null) {
            return null;
        }
        if (due.isBefore(now)) {
            return "OVERDUE";
        }
        return due.isBefore(now.plusMinutes(AfterSalesConstants.DUE_SOON_MINUTES)) ? "SOON" : "NORMAL";
    }

    private static LocalDateTime plus(LocalDateTime from, long minutes, boolean natural) {
        return natural ? from.plusMinutes(minutes) : WorkHours.add(from, minutes);
    }
}

package com.zhul.erp.modules.masterdata.customerbackground.constants;

import java.util.Set;

/** 客户背调（spec crm/customer-background） */
public final class BackgroundConstants {

    /** AI 编排服务的技能 ID */
    public static final String SKILL = "customer-background";
    public static final String PERMISSION = "partner:customer:background";

    /** 来源（1-AI背调、2-上传） */
    public static final int SOURCE_AI = 1;
    public static final int SOURCE_UPLOAD = 2;

    /** 状态（1-背调中、2-已完成、3-失败） */
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_FAILED = 3;

    /** 24 小时内已有背调时提醒（只提醒不拦截） */
    public static final int REMIND_HOURS = 24;

    /** 上传：PDF 不超过 10MB，报告文字不超过 60000 字（与编排服务一致） */
    public static final long MAX_PDF_BYTES = 10L * 1024 * 1024;
    public static final int MAX_TEXT_CHARS = 60000;

    /**
     * 背调前检查：免费邮箱的后缀找不到公司，不算企业邮箱（spec「背调前检查」）。
     * yahoo 各国站点（yahoo.co.uk、yahoo.com.au 等）按前缀单独判断。
     */
    public static final Set<String> FREE_EMAIL_DOMAINS = Set.of(
            "gmail.com", "googlemail.com", "hotmail.com", "outlook.com", "live.com", "msn.com",
            "icloud.com", "me.com", "aol.com", "protonmail.com", "proton.me", "gmx.com", "gmx.de",
            "mail.ru", "yandex.ru", "yandex.com", "qq.com", "163.com", "126.com", "sina.com", "sohu.com",
            "foxmail.com");
    public static final String FREE_EMAIL_YAHOO_PREFIX = "yahoo.";

    public static final String ERR_RUNNING = "BACKGROUND_RUNNING";
    public static final String ERR_RECENT = "BACKGROUND_RECENT";

    private BackgroundConstants() {
    }
}

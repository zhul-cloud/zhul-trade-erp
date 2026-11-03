package com.zhul.erp.modules.masterdata.customerbackground.constants;

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

    public static final String ERR_RUNNING = "BACKGROUND_RUNNING";
    public static final String ERR_RECENT = "BACKGROUND_RECENT";

    private BackgroundConstants() {
    }
}

package com.zhul.erp.modules.system.notification.dto;

/** 铃铛角标：未处理的待办 + 未读的知会 */
public record NotificationCountVO(long todo, long unread, long total) {
}

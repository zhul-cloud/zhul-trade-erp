package com.zhul.erp.modules.system.notification.support;

import com.zhul.erp.modules.system.notification.constants.NotificationNode;

import java.util.Collection;
import java.util.Set;

/**
 * 待办型通知是否已处理，由各业务模块判断：返回 bizIds 中对收件人来说还没处理完的那部分。
 * 全部处理完（返回空）时通知标为已处理；部分处理时通知的数量随之更新。
 */
public interface NotificationTodoChecker {

    Set<NotificationNode> nodes();

    Set<Long> open(NotificationNode node, Long recipientId, Collection<Long> bizIds);
}

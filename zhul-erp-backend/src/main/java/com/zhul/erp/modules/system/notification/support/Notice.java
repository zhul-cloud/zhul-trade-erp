package com.zhul.erp.modules.system.notification.support;

import com.zhul.erp.modules.system.notification.constants.NotificationNode;

import java.util.Collection;
import java.util.List;

/**
 * 业务模块要发的一条通知。
 *
 * @param mergeKey 合并键：同一收件人、节点、合并键合并为一条（待办型合并到未处理的那条）；null 时只按收件人合并
 * @param bizIds   待办涉及的记录 ID（判断是否已处理用）；知会型可以放涉及的记录，用于计数
 * @param count    标题里的数量；null 时取 bizIds 的个数
 * @param owners   单据负责人；节点有岗位时，收货人岗位设为「采购订单的采购员」时把他们当作收件人
 */
public record Notice(NotificationNode node, String mergeKey, String bizNo, String link, List<Long> bizIds,
                     Integer count, String content, boolean urgent, List<Long> owners) {

    public static Notice of(NotificationNode node, String bizNo, String link) {
        return new Notice(node, null, bizNo, link, List.of(), null, "", false, List.of());
    }

    public Notice merge(Object key) {
        return new Notice(node, key == null ? null : String.valueOf(key), bizNo, link, bizIds, count, content, urgent, owners);
    }

    public Notice ids(Collection<Long> ids) {
        return new Notice(node, mergeKey, bizNo, link, List.copyOf(ids), count, content, urgent, owners);
    }

    public Notice count(int n) {
        return new Notice(node, mergeKey, bizNo, link, bizIds, n, content, urgent, owners);
    }

    public Notice content(String text) {
        return new Notice(node, mergeKey, bizNo, link, bizIds, count, text == null ? "" : text, urgent, owners);
    }

    public Notice urgent(boolean u) {
        return new Notice(node, mergeKey, bizNo, link, bizIds, count, content, u, owners);
    }

    public Notice to(Collection<Long> users) {
        return new Notice(node, mergeKey, bizNo, link, bizIds, count, content, urgent,
                users.stream().filter(u -> u != null && u > 0).distinct().toList());
    }

    public Notice to(Long user) {
        return to(user == null ? List.of() : List.of(user));
    }

    public int effectiveCount() {
        return count != null ? count : bizIds.size();
    }
}

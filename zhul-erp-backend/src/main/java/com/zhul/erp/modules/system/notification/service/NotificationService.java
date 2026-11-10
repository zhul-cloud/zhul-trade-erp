package com.zhul.erp.modules.system.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.system.notification.constants.NotificationNode;
import com.zhul.erp.modules.system.notification.dto.NotificationCountVO;
import com.zhul.erp.modules.system.notification.dto.NotificationVO;
import com.zhul.erp.modules.system.notification.entity.NotificationDO;
import com.zhul.erp.modules.system.notification.repository.NotificationMapper;
import com.zhul.erp.modules.system.notification.support.Notice;
import com.zhul.erp.modules.system.notification.support.NotificationTodoChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 站内通知（spec system/notification）：收件人解析与合并、待办自动处理、通知列表。
 */
@Slf4j
@Service
public class NotificationService {

    /** 通知保留天数 */
    static final int KEEP_DAYS = 90;
    /** biz_ids 列最多存的记录数（超出部分不再参与判断是否已处理） */
    private static final int MAX_IDS = 150;
    private static final int MAX_CONTENT = 500;

    private final NotificationMapper mapper;
    private final NotificationReceiverService receivers;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;
    private final Map<NotificationNode, NotificationTodoChecker> checkers = new EnumMap<>(NotificationNode.class);

    public NotificationService(NotificationMapper mapper, NotificationReceiverService receivers, CurrentUserResolver currentUser,
                               ObjectMapper objectMapper, List<NotificationTodoChecker> checkerBeans) {
        this.mapper = mapper;
        this.receivers = receivers;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
        for (NotificationTodoChecker c : checkerBeans) {
            c.nodes().forEach(n -> checkers.put(n, c));
        }
    }

    // ---------------------------------------------------------------- 发送

    private record Key(NotificationNode node, String mergeKey, Long recipient) {
    }

    private static final class Agg {
        final Set<Long> ids = new LinkedHashSet<>();
        Integer count;
        Notice last;
    }

    /** 业务事务提交后调用（见 Notifier）；同一事务里的通知按「节点 + 合并键 + 收件人」合并 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void deliver(List<Notice> notices, Long actor) {
        Map<Key, Agg> merged = new LinkedHashMap<>();
        for (Notice n : notices) {
            List<Long> to = n.node().post() != null ? receivers.resolve(n.node().post(), n.owners()) : n.owners();
            for (Long r : to) {
                if (r == null || r <= 0 || Objects.equals(r, actor)) {
                    continue;
                }
                Agg a = merged.computeIfAbsent(new Key(n.node(), n.mergeKey(), r), k -> new Agg());
                a.ids.addAll(n.bizIds());
                if (n.count() != null) {
                    a.count = (a.count == null ? 0 : a.count) + n.count();
                }
                a.last = n;
            }
        }
        merged.forEach((k, a) -> save(k, a));
    }

    private void save(Key k, Agg a) {
        Notice n = a.last;
        NotificationNode node = k.node();
        String mergeKey = k.mergeKey() == null ? "" : k.mergeKey();
        LambdaQueryWrapper<NotificationDO> same = new LambdaQueryWrapper<NotificationDO>()
                .eq(NotificationDO::getRecipientId, k.recipient())
                .eq(NotificationDO::getNodeCode, node.name())
                .eq(NotificationDO::getMergeKey, mergeKey)
                .isNull(NotificationDO::getDeletedAt);
        if (node == NotificationNode.ORDER_READY) {
            if (mapper.selectCount(same) > 0) {
                return;
            }
        }
        NotificationDO existing = k.mergeKey() == null ? null : mapper.selectOne(same
                .ge(NotificationDO::getCreateTime, LocalDateTime.now().minusDays(KEEP_DAYS))
                .isNull(node.todo() ? NotificationDO::getDoneAt : NotificationDO::getReadAt)
                .orderByDesc(NotificationDO::getId)
                .last("LIMIT 1"));
        if (existing == null) {
            NotificationDO d = new NotificationDO();
            d.setTenantId(TenantContext.getTenantId());
            d.setRecipientId(k.recipient());
            d.setNodeCode(node.name());
            d.setKind(node.kind().code());
            d.setMergeKey(mergeKey);
            fill(d, n, a.ids, a.count);
            mapper.insert(d);
            return;
        }
        Set<Long> before = new LinkedHashSet<>(ids(existing.getBizIds()));
        Set<Long> union = new LinkedHashSet<>(before);
        union.addAll(a.ids);
        boolean grew = union.size() > before.size() || a.count != null;
        Integer count = a.count == null ? null : existing.getItemCount() + a.count;
        fill(existing, n, union, count);
        existing.setUrgent(existing.getUrgent() == 1 || n.urgent() ? 1 : 0);
        if (grew) {
            existing.setReadAt(null);
        }
        existing.setUpdateTime(LocalDateTime.now());
        mapper.updateById(existing);
    }

    private void fill(NotificationDO d, Notice n, Set<Long> ids, Integer count) {
        d.setBizNo(cut(n.bizNo(), 64));
        d.setLink(cut(n.link(), 255));
        d.setBizIds(json(ids));
        d.setItemCount(count != null ? count : ids.size());
        d.setContent(cut(n.content(), MAX_CONTENT));
        d.setUrgent(n.urgent() ? 1 : 0);
    }

    // ---------------------------------------------------------------- 待办自动处理

    /** 收件人还没处理的待办：按业务现状重算，做完的标为已处理，部分做完的更新数量 */
    @Transactional(rollbackFor = Exception.class)
    public void refreshOpen(Long recipient) {
        List<NotificationDO> open = mapper.selectList(new LambdaQueryWrapper<NotificationDO>()
                .eq(NotificationDO::getRecipientId, recipient)
                .eq(NotificationDO::getKind, NotificationNode.Kind.TODO.code())
                .isNull(NotificationDO::getDoneAt)
                .isNull(NotificationDO::getDeletedAt)
                .ge(NotificationDO::getCreateTime, LocalDateTime.now().minusDays(KEEP_DAYS)));
        Map<NotificationNode, List<NotificationDO>> byNode = new EnumMap<>(NotificationNode.class);
        for (NotificationDO d : open) {
            NotificationNode node = NotificationNode.of(d.getNodeCode());
            if (node != null && checkers.containsKey(node)) {
                byNode.computeIfAbsent(node, x -> new ArrayList<>()).add(d);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        byNode.forEach((node, rows) -> {
            Set<Long> all = new HashSet<>();
            rows.forEach(d -> all.addAll(ids(d.getBizIds())));
            Set<Long> still = all.isEmpty() ? Set.of() : checkers.get(node).open(node, recipient, all);
            for (NotificationDO d : rows) {
                List<Long> mine = ids(d.getBizIds());
                List<Long> left = mine.stream().filter(still::contains).toList();
                if (left.isEmpty()) {
                    mapper.update(null, new LambdaUpdateWrapper<NotificationDO>()
                            .eq(NotificationDO::getId, d.getId())
                            .isNull(NotificationDO::getDoneAt)
                            .set(NotificationDO::getDoneAt, now)
                            .set(NotificationDO::getUpdateTime, now));
                } else if (left.size() < mine.size()) {
                    mapper.update(null, new LambdaUpdateWrapper<NotificationDO>()
                            .eq(NotificationDO::getId, d.getId())
                            .set(NotificationDO::getBizIds, json(left))
                            .set(NotificationDO::getItemCount, left.size()));
                }
            }
        });
    }

    // ---------------------------------------------------------------- 列表

    public NotificationCountVO count() {
        Long me = me();
        refreshOpen(me);
        long todo = mapper.selectCount(mine(me).eq(NotificationDO::getKind, NotificationNode.Kind.TODO.code())
                .isNull(NotificationDO::getDoneAt));
        long unread = mapper.selectCount(mine(me).eq(NotificationDO::getKind, NotificationNode.Kind.INFO.code())
                .isNull(NotificationDO::getReadAt));
        return new NotificationCountVO(todo, unread, todo + unread);
    }

    /** tab：todo-待处理（未处理的待办、未读的知会），all-全部 */
    public PageResult<NotificationVO> list(String tab, int page, int pageSize) {
        Long me = me();
        refreshOpen(me);
        LambdaQueryWrapper<NotificationDO> w = mine(me);
        if ("todo".equals(tab)) {
            w.and(x -> x.nested(t -> t.eq(NotificationDO::getKind, NotificationNode.Kind.TODO.code()).isNull(NotificationDO::getDoneAt))
                    .or(t -> t.eq(NotificationDO::getKind, NotificationNode.Kind.INFO.code()).isNull(NotificationDO::getReadAt)));
        }
        w.orderByDesc(NotificationDO::getUpdateTime).orderByDesc(NotificationDO::getId);
        Page<NotificationDO> p = mapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(pageSize, 1), 50)), w);
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void read(Long id) {
        NotificationDO d = mapper.selectById(id);
        if (d == null || d.getDeletedAt() != null || !Objects.equals(d.getRecipientId(), me())) {
            throw new BizException("通知不存在");
        }
        if (d.getReadAt() == null) {
            mapper.update(null, new LambdaUpdateWrapper<NotificationDO>()
                    .eq(NotificationDO::getId, id)
                    .set(NotificationDO::getReadAt, LocalDateTime.now()));
        }
    }

    /** 全部已读：只处理知会型，待办型仍留在待处理 */
    @Transactional(rollbackFor = Exception.class)
    public int readAll() {
        return mapper.update(null, new LambdaUpdateWrapper<NotificationDO>()
                .eq(NotificationDO::getRecipientId, me())
                .eq(NotificationDO::getKind, NotificationNode.Kind.INFO.code())
                .isNull(NotificationDO::getReadAt)
                .isNull(NotificationDO::getDeletedAt)
                .set(NotificationDO::getReadAt, LocalDateTime.now()));
    }

    private LambdaQueryWrapper<NotificationDO> mine(Long me) {
        return new LambdaQueryWrapper<NotificationDO>()
                .eq(NotificationDO::getRecipientId, me)
                .isNull(NotificationDO::getDeletedAt)
                .ge(NotificationDO::getCreateTime, LocalDateTime.now().minusDays(KEEP_DAYS));
    }

    private NotificationVO toVO(NotificationDO d) {
        NotificationNode node = NotificationNode.of(d.getNodeCode());
        NotificationVO vo = new NotificationVO();
        vo.setId(d.getId());
        vo.setNode(d.getNodeCode());
        vo.setNodeName(node == null ? d.getNodeCode() : node.label());
        vo.setKind(d.getKind() == NotificationNode.Kind.TODO.code() ? "TODO" : "INFO");
        vo.setTitle(node == null ? d.getBizNo() : node.title(d.getBizNo(), d.getItemCount()));
        vo.setContent(d.getContent());
        vo.setBizNo(d.getBizNo());
        vo.setLink(d.getLink());
        vo.setUrgent(d.getUrgent() == 1);
        vo.setRead(d.getReadAt() != null);
        vo.setDone(d.getDoneAt() != null);
        vo.setTime(d.getUpdateTime());
        return vo;
    }

    private Long me() {
        Long me = currentUser.resolve();
        if (me == null) {
            throw new BizException("请先登录");
        }
        return me;
    }

    // ---------------------------------------------------------------- 工具

    private String json(Collection<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids.stream().limit(MAX_IDS).toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("记录ID转 JSON 失败", e);
        }
    }

    private List<Long> ids(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() { });
        } catch (JsonProcessingException e) {
            log.warn("通知记录ID格式不对：{}", json);
            return List.of();
        }
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    /** 供测试与定时任务：某节点、合并键下是否已有通知（任何状态） */
    public boolean exists(NotificationNode node, String mergeKey) {
        return mapper.selectCount(new LambdaQueryWrapper<NotificationDO>()
                .eq(NotificationDO::getNodeCode, node.name())
                .eq(NotificationDO::getMergeKey, mergeKey)
                .isNull(NotificationDO::getDeletedAt)) > 0;
    }

    /** 已有通知的合并键（定时任务去重用） */
    public Set<String> existingKeys(NotificationNode node, Collection<String> mergeKeys) {
        if (mergeKeys.isEmpty()) {
            return Set.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<NotificationDO>()
                        .select(NotificationDO::getMergeKey)
                        .eq(NotificationDO::getNodeCode, node.name())
                        .in(NotificationDO::getMergeKey, mergeKeys)
                        .isNull(NotificationDO::getDeletedAt))
                .stream().map(NotificationDO::getMergeKey).collect(Collectors.toSet());
    }
}

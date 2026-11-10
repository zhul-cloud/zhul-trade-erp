package com.zhul.erp.modules.system.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.entity.ResourceDO;
import com.zhul.erp.modules.system.entity.RoleResourceDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.notification.constants.NotificationNode;
import com.zhul.erp.modules.system.notification.constants.NotificationPost;
import com.zhul.erp.modules.system.notification.dto.NotificationReceiverVO;
import com.zhul.erp.modules.system.notification.entity.NotificationReceiverDO;
import com.zhul.erp.modules.system.notification.repository.NotificationReceiverMapper;
import com.zhul.erp.modules.system.repository.ResourceMapper;
import com.zhul.erp.modules.system.repository.RoleResourceMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通知接收人（spec system/notification「通知接收人」）。
 * 岗位保存过（有任何一行，含已移除的）就按保存的人；从没保存过时按权限算默认值。
 */
@Service
@RequiredArgsConstructor
public class NotificationReceiverService {

    private static final int VALID = 1;
    private static final int REMOVED = 0;

    private final NotificationReceiverMapper receiverMapper;
    private final UserBasicMapper userBasicMapper;
    private final ResourceMapper resourceMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final LogService logService;

    /** 岗位的收件人；收货人设为「采购订单的采购员」时用 owners。结果只含本租户有效用户 */
    public List<Long> resolve(NotificationPost post, Collection<Long> owners) {
        Setting s = setting(post);
        Set<Long> users = new LinkedHashSet<>(s.users());
        if (s.poBuyer()) {
            users.addAll(owners);
        }
        return active(users);
    }

    public List<NotificationReceiverVO> list() {
        List<NotificationReceiverVO> out = new ArrayList<>();
        for (NotificationPost post : NotificationPost.values()) {
            Setting s = setting(post);
            NotificationReceiverVO vo = new NotificationReceiverVO();
            vo.setPost(post.name());
            vo.setPostName(post.label());
            vo.setPoBuyer(s.poBuyer());
            List<Long> ids = active(s.users());
            Map<Long, String> names = names(ids);
            vo.setUsers(ids.stream().map(id -> new NotificationReceiverVO.User(id, names.get(id))).toList());
            vo.setNodes(Arrays.stream(NotificationNode.values()).filter(n -> n.post() == post).map(NotificationNode::label)
                    .distinct().toList());
            out.add(vo);
        }
        return out;
    }

    public List<NotificationReceiverVO.User> candidates() {
        return userBasicMapper.selectList(new LambdaQueryWrapper<UserBasicDO>()
                        .select(UserBasicDO::getId, UserBasicDO::getName)
                        .eq(UserBasicDO::getTenantId, TenantContext.getTenantId())
                        .eq(UserBasicDO::getStatus, 1)
                        .orderByAsc(UserBasicDO::getId))
                .stream().map(u -> new NotificationReceiverVO.User(u.getId().longValue(), u.getName())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(String postCode, boolean poBuyer, List<Long> userIds) {
        NotificationPost post = post(postCode);
        if (poBuyer && post != NotificationPost.RECEIVER) {
            throw new BizException("只有收货人可以设为采购订单的采购员");
        }
        Set<Long> wanted = new LinkedHashSet<>(poBuyer ? List.of() : userIds == null ? List.of() : userIds);
        List<Long> valid = active(wanted);
        if (valid.size() != wanted.size()) {
            throw new BizException("接收人里有已停用或不存在的账号，请刷新后再选");
        }
        Setting before = setting(post);
        receiverMapper.update(null, new LambdaUpdateWrapper<NotificationReceiverDO>()
                .eq(NotificationReceiverDO::getPostCode, post.name())
                .eq(NotificationReceiverDO::getStatus, VALID)
                .set(NotificationReceiverDO::getStatus, REMOVED));
        List<Long> rows = poBuyer ? List.of(NotificationPost.PO_BUYER) : valid;
        // 一个都不选时也留一行已移除的记录，表示保存过（不再按权限取默认值）
        if (rows.isEmpty()) {
            insert(post, -1L, REMOVED);
        }
        for (Long id : rows) {
            insert(post, id, VALID);
        }
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("post", post.label());
        b.put("users", describe(before.poBuyer(), active(before.users())));
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("post", post.label());
        a.put("users", describe(poBuyer, valid));
        logService.recordOperateLog("通知接收人", "修改接收人", b, a);
    }

    private String describe(boolean poBuyer, List<Long> users) {
        if (poBuyer) {
            return "采购订单的采购员";
        }
        Map<Long, String> names = names(users);
        return users.isEmpty() ? "无" : users.stream().map(names::get).collect(Collectors.joining("、"));
    }

    private void insert(NotificationPost post, Long userId, int status) {
        NotificationReceiverDO r = new NotificationReceiverDO();
        r.setTenantId(TenantContext.getTenantId());
        r.setPostCode(post.name());
        r.setUserId(userId);
        r.setStatus(status);
        receiverMapper.insert(r);
    }

    private record Setting(boolean poBuyer, List<Long> users) {
    }

    private Setting setting(NotificationPost post) {
        List<NotificationReceiverDO> rows = receiverMapper.selectList(new LambdaQueryWrapper<NotificationReceiverDO>()
                .eq(NotificationReceiverDO::getPostCode, post.name()));
        if (rows.isEmpty()) {
            return post == NotificationPost.RECEIVER ? new Setting(true, List.of()) : new Setting(false, defaults(post));
        }
        List<NotificationReceiverDO> valid = rows.stream().filter(r -> r.getStatus() == VALID).toList();
        boolean poBuyer = valid.stream().anyMatch(r -> r.getUserId() == NotificationPost.PO_BUYER);
        return new Setting(poBuyer, valid.stream().map(NotificationReceiverDO::getUserId).filter(id -> id > 0).distinct().toList());
    }

    /** 有岗位权限的有效用户，去掉有系统设置权限的管理角色 */
    private List<Long> defaults(NotificationPost post) {
        Set<String> holders = rolesWith(post.permission());
        holders.removeAll(rolesWith(NotificationPost.ADMIN_PERMISSION));
        if (holders.isEmpty()) {
            return List.of();
        }
        return userBasicMapper.selectList(new LambdaQueryWrapper<UserBasicDO>()
                        .eq(UserBasicDO::getTenantId, TenantContext.getTenantId())
                        .eq(UserBasicDO::getStatus, 1)
                        .in(UserBasicDO::getRoleCode, holders)
                        .orderByAsc(UserBasicDO::getId))
                .stream().map(u -> u.getId().longValue()).toList();
    }

    private Set<String> rolesWith(String permission) {
        List<Integer> resources = resourceMapper.selectList(new LambdaQueryWrapper<ResourceDO>()
                        .eq(ResourceDO::getPermission, permission)
                        .eq(ResourceDO::getStatus, 1))
                .stream().map(ResourceDO::getId).toList();
        if (resources.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return roleResourceMapper.selectList(new LambdaQueryWrapper<RoleResourceDO>().in(RoleResourceDO::getResourceId, resources))
                .stream().map(RoleResourceDO::getRoleCode).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 本租户有效用户，保持顺序 */
    private List<Long> active(Collection<Long> ids) {
        List<Integer> keys = ids.stream().filter(Objects::nonNull).filter(id -> id > 0).map(Long::intValue).distinct().toList();
        if (keys.isEmpty()) {
            return List.of();
        }
        Set<Long> ok = userBasicMapper.selectList(new LambdaQueryWrapper<UserBasicDO>()
                        .in(UserBasicDO::getId, keys)
                        .eq(UserBasicDO::getTenantId, TenantContext.getTenantId())
                        .eq(UserBasicDO::getStatus, 1))
                .stream().map(u -> u.getId().longValue()).collect(Collectors.toSet());
        return keys.stream().map(Integer::longValue).filter(ok::contains).toList();
    }

    private Map<Long, String> names(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userBasicMapper.selectBatchIds(ids.stream().map(Long::intValue).toList()).stream()
                .collect(Collectors.toMap(u -> u.getId().longValue(), UserBasicDO::getName, (a, b) -> a));
    }

    private static NotificationPost post(String code) {
        try {
            return NotificationPost.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BizException("岗位不存在");
        }
    }
}

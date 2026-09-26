package com.zhul.erp.framework.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.system.entity.AccountDO;
import com.zhul.erp.modules.system.entity.DepartmentDO;
import com.zhul.erp.modules.system.entity.RoleDO;
import com.zhul.erp.modules.system.entity.RoleOrgDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.entity.UserDepartmentDO;
import com.zhul.erp.modules.system.repository.AccountMapper;
import com.zhul.erp.modules.system.repository.DepartmentMapper;
import com.zhul.erp.modules.system.repository.RoleMapper;
import com.zhul.erp.modules.system.repository.RoleOrgMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.repository.UserDepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按角色上的「数据权限范围」（role.permission_scope：0-无权限、1-全部、2-自定义、3-仅本人）
 * 计算当前用户能看到哪些负责人的数据：
 * <ul>
 *   <li>管理员账号（admin_flag=1）或 1-全部：同租户全部</li>
 *   <li>2-自定义：role_org 配置的部门及其全部下级部门里的用户（主部门或附属部门命中）+ 自己</li>
 *   <li>3-仅本人、未配置角色，以及 0-无权限：只有自己。「无权限」按仅本人处理，
 *       否则业务员连自己负责的数据都看不到</li>
 *   <li>非管理员且没有 user_basic 档案：什么都看不到</li>
 * </ul>
 * 不做缓存：角色、部门调整后要立即生效，每次解析只有几条小表查询。
 */
@Component
@RequiredArgsConstructor
public class DataScopeResolver {

    private static final int SCOPE_ALL = 1;
    private static final int SCOPE_CUSTOM = 2;

    private final AccountMapper accountMapper;
    private final UserBasicMapper userBasicMapper;
    private final RoleMapper roleMapper;
    private final RoleOrgMapper roleOrgMapper;
    private final DepartmentMapper departmentMapper;
    private final UserDepartmentMapper userDepartmentMapper;

    /** 当前登录用户的数据范围 */
    public DataScope current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !StringUtils.hasText(auth.getName())) {
            return new DataScope(DataScope.Type.NONE, null, Set.of());
        }
        return resolve(auth.getName());
    }

    public DataScope resolve(String username) {
        AccountDO account = accountMapper.selectOne(new LambdaQueryWrapper<AccountDO>()
                .eq(AccountDO::getUsername, username).last("LIMIT 1"));
        UserBasicDO user = userBasicMapper.selectOne(new LambdaQueryWrapper<UserBasicDO>()
                .eq(UserBasicDO::getUsername, username).last("LIMIT 1"));
        Long selfId = user != null ? user.getId().longValue() : null;

        if (account != null && Integer.valueOf(1).equals(account.getAdminFlag())) {
            return DataScope.all(selfId);
        }
        if (user == null) {
            return new DataScope(DataScope.Type.NONE, null, Set.of());
        }
        RoleDO role = StringUtils.hasText(user.getRoleCode())
                ? roleMapper.selectOne(new LambdaQueryWrapper<RoleDO>()
                        .eq(RoleDO::getCode, user.getRoleCode()).last("LIMIT 1"))
                : null;
        Integer scope = role != null ? role.getPermissionScope() : null;
        if (Integer.valueOf(SCOPE_ALL).equals(scope)) {
            return DataScope.all(selfId);
        }
        if (Integer.valueOf(SCOPE_CUSTOM).equals(scope)) {
            Set<Long> ownerIds = customOwnerIds(user);
            ownerIds.add(selfId);
            return new DataScope(DataScope.Type.CUSTOM, selfId, ownerIds);
        }
        return new DataScope(DataScope.Type.SELF, selfId, Set.of(selfId));
    }

    private Set<Long> customOwnerIds(UserBasicDO user) {
        Set<String> rootCodes = new HashSet<>();
        for (RoleOrgDO ro : roleOrgMapper.selectList(new LambdaQueryWrapper<RoleOrgDO>()
                .eq(RoleOrgDO::getRoleCode, user.getRoleCode()))) {
            rootCodes.add(ro.getOrgCode());
        }
        Set<Long> ownerIds = new HashSet<>();
        if (rootCodes.isEmpty()) {
            return ownerIds;
        }

        List<DepartmentDO> depts = departmentMapper.selectList(new LambdaQueryWrapper<DepartmentDO>()
                .eq(DepartmentDO::getTenantId, user.getTenantId())
                .isNull(DepartmentDO::getDeletedAt));
        Map<Integer, List<DepartmentDO>> children = new HashMap<>(depts.size() * 2);
        for (DepartmentDO d : depts) {
            children.computeIfAbsent(d.getPid(), k -> new ArrayList<>()).add(d);
        }
        Set<Integer> deptIds = new HashSet<>();
        Set<String> deptCodes = new HashSet<>();
        Deque<DepartmentDO> queue = new ArrayDeque<>();
        for (DepartmentDO d : depts) {
            if (rootCodes.contains(d.getCode())) {
                queue.add(d);
            }
        }
        while (!queue.isEmpty()) {
            DepartmentDO d = queue.poll();
            if (!deptIds.add(d.getId())) {
                continue;
            }
            deptCodes.add(d.getCode());
            queue.addAll(children.getOrDefault(d.getId(), List.of()));
        }
        if (deptIds.isEmpty()) {
            return ownerIds;
        }

        for (UserBasicDO u : userBasicMapper.selectList(new LambdaQueryWrapper<UserBasicDO>()
                .eq(UserBasicDO::getTenantId, user.getTenantId())
                .in(UserBasicDO::getDeptId, deptIds))) {
            ownerIds.add(u.getId().longValue());
        }
        for (UserDepartmentDO ud : userDepartmentMapper.selectList(new LambdaQueryWrapper<UserDepartmentDO>()
                .in(UserDepartmentDO::getDeptCode, deptCodes))) {
            ownerIds.add(ud.getUserId().longValue());
        }
        return ownerIds;
    }
}

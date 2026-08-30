package com.zhul.erp.framework.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.system.entity.AccountDO;
import com.zhul.erp.modules.system.entity.ResourceDO;
import com.zhul.erp.modules.system.entity.RoleResourceDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.AccountMapper;
import com.zhul.erp.modules.system.repository.ResourceMapper;
import com.zhul.erp.modules.system.repository.RoleResourceMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Component("perm")
@RequiredArgsConstructor
public class PermissionChecker {

    private final AccountMapper accountMapper;
    private final UserBasicMapper userBasicMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final ResourceMapper resourceMapper;

    /**
     * 检查当前用户是否有指定权限码。
     * 超级管理员（adminFlag=1）直接返回 true。
     */
    public boolean has(String permCode) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        String username = auth.getName();

        AccountDO account = accountMapper.selectOne(
            new LambdaQueryWrapper<AccountDO>()
                .eq(AccountDO::getUsername, username).last("LIMIT 1")
        );
        if (account == null) return false;
        if (account.getAdminFlag() != null && account.getAdminFlag() == 1) return true;

        UserBasicDO user = userBasicMapper.selectOne(
            new LambdaQueryWrapper<UserBasicDO>()
                .eq(UserBasicDO::getUsername, username).last("LIMIT 1")
        );
        if (user == null || !StringUtils.hasText(user.getRoleCode())) return false;

        List<Integer> resourceIds = roleResourceMapper.selectList(
            new LambdaQueryWrapper<RoleResourceDO>()
                .eq(RoleResourceDO::getRoleCode, user.getRoleCode())
        ).stream().map(RoleResourceDO::getResourceId).collect(Collectors.toList());

        if (resourceIds.isEmpty()) return false;

        long count = resourceMapper.selectCount(
            new LambdaQueryWrapper<ResourceDO>()
                .in(ResourceDO::getId, resourceIds)
                .eq(ResourceDO::getPermission, permCode)
                .eq(ResourceDO::getType, 3)
        );
        return count > 0;
    }
}

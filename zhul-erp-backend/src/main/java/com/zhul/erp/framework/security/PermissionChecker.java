package com.zhul.erp.framework.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.system.entity.ResourceDO;
import com.zhul.erp.modules.system.repository.ResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component("perm")
@RequiredArgsConstructor
public class PermissionChecker {

    private final ResourceMapper resourceMapper;
    private final EffectivePermissionResolver permissionResolver;

    /**
     * 检查当前用户是否有指定权限码：先按权限码找到对应的按钮资源，再看它是否落在
     * 该账号最终允许访问的 resource 范围内（平台超管/租户套餐/角色的综合判定见
     * {@link EffectivePermissionResolver}）。
     */
    public boolean has(String permCode) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        String username = auth.getName();

        ResourceDO resource = resourceMapper.selectOne(
                new LambdaQueryWrapper<ResourceDO>()
                        .eq(ResourceDO::getPermission, permCode)
                        .eq(ResourceDO::getType, 3)
                        .last("LIMIT 1"));
        if (resource == null) return false;

        Set<Integer> allowed = permissionResolver.resolveAllowedResourceIds(username);
        return allowed == null || allowed.contains(resource.getId());
    }

    /**
     * 检查当前用户能否访问某个菜单（按菜单路径找到 type=2 的菜单资源，再看是否在有效资源范围内）。
     * 用于没有单独按钮权限的「查看」类接口，如供应商附件下载。
     */
    public boolean canAccessMenu(String path) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;

        ResourceDO menu = resourceMapper.selectOne(
                new LambdaQueryWrapper<ResourceDO>()
                        .eq(ResourceDO::getPath, path)
                        .eq(ResourceDO::getType, 2)
                        .last("LIMIT 1"));
        if (menu == null) return false;

        Set<Integer> allowed = permissionResolver.resolveAllowedResourceIds(auth.getName());
        return allowed == null || allowed.contains(menu.getId());
    }
}

package com.zhul.erp.framework.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.system.entity.AccountDO;
import com.zhul.erp.modules.system.entity.RoleResourceDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.AccountMapper;
import com.zhul.erp.modules.system.repository.RoleResourceMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.tenant.entity.TenantDO;
import com.zhul.erp.modules.tenant.entity.TenantPackageDO;
import com.zhul.erp.modules.tenant.repository.TenantMapper;
import com.zhul.erp.modules.tenant.repository.TenantPackageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 计算一个账号最终能访问的 resource id 集合，供 {@link PermissionChecker}（按钮/接口级拦截）
 * 和菜单接口（侧边栏展示）共用——服务端拦截和前端展示必须用同一份口径，否则会出现
 * "前端隐藏了按钮，后端接口其实还能调"或反过来的不一致。
 *
 * <p>判定规则（tenant_id=0 是平台级共享池，没有"所属套餐"这个概念，只有真实租户
 * tenant_id&gt;0 才受套餐 menu_ids 限制）：
 * <ul>
 *   <li>tenant_id=0 且 admin_flag=1：平台超级管理员，不受限（返回 null）</li>
 *   <li>tenant_id=0 且 admin_flag=0：平台级但非管理员账号，只受角色 role_resource 限制</li>
 *   <li>tenant_id&gt;0 且 admin_flag=1：租户管理员，只受套餐 menu_ids 限制，不受角色限制</li>
 *   <li>tenant_id&gt;0 且 admin_flag=0：租户内普通员工，受套餐 menu_ids 与角色 role_resource 的交集限制</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class EffectivePermissionResolver {

    private final AccountMapper accountMapper;
    private final UserBasicMapper userBasicMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final TenantMapper tenantMapper;
    private final TenantPackageMapper packageMapper;
    private final ObjectMapper objectMapper;

    /** 返回 null 表示不受限；返回具体集合（可能为空集合）表示只能访问这些 resource id */
    public Set<Integer> resolveAllowedResourceIds(String username) {
        AccountDO account = accountMapper.selectOne(
                new LambdaQueryWrapper<AccountDO>().eq(AccountDO::getUsername, username).last("LIMIT 1"));
        if (account == null) {
            return Collections.emptySet();
        }

        boolean isAdmin = account.getAdminFlag() != null && account.getAdminFlag() == 1;
        boolean isPlatformScoped = account.getTenantId() == null || account.getTenantId() == 0;

        if (isPlatformScoped) {
            return isAdmin ? null : resolveByRole(username);
        }

        Set<Integer> packageIds = resolvePackageResourceIds(account.getTenantId());
        if (isAdmin) {
            return packageIds;
        }
        packageIds.retainAll(resolveByRole(username));
        return packageIds;
    }

    private Set<Integer> resolveByRole(String username) {
        UserBasicDO user = userBasicMapper.selectOne(
                new LambdaQueryWrapper<UserBasicDO>().eq(UserBasicDO::getUsername, username).last("LIMIT 1"));
        if (user == null || !StringUtils.hasText(user.getRoleCode())) {
            return new HashSet<>();
        }
        return roleResourceMapper.selectList(
                new LambdaQueryWrapper<RoleResourceDO>().eq(RoleResourceDO::getRoleCode, user.getRoleCode())
        ).stream().map(RoleResourceDO::getResourceId).collect(Collectors.toCollection(HashSet::new));
    }

    private Set<Integer> resolvePackageResourceIds(Integer tenantId) {
        TenantDO tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getPackageId() == null) {
            return new HashSet<>();
        }
        TenantPackageDO pkg = packageMapper.selectById(tenant.getPackageId());
        if (pkg == null) {
            return new HashSet<>();
        }
        return parseMenuIds(pkg.getMenuIds());
    }

    private Set<Integer> parseMenuIds(String menuIdsJson) {
        if (!StringUtils.hasText(menuIdsJson)) {
            return new HashSet<>();
        }
        try {
            List<Integer> ids = objectMapper.readValue(menuIdsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Integer.class));
            return new HashSet<>(ids);
        } catch (JsonProcessingException e) {
            return new HashSet<>();
        }
    }
}

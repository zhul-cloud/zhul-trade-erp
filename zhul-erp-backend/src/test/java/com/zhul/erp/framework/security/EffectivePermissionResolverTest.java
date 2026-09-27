package com.zhul.erp.framework.security;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EffectivePermissionResolverTest {

    @Mock
    private AccountMapper accountMapper;
    @Mock
    private UserBasicMapper userBasicMapper;
    @Mock
    private RoleResourceMapper roleResourceMapper;
    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private TenantPackageMapper packageMapper;

    private EffectivePermissionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new EffectivePermissionResolver(
                accountMapper, userBasicMapper, roleResourceMapper, tenantMapper, packageMapper, new ObjectMapper());
    }

    private static AccountDO account(Integer tenantId, int adminFlag) {
        AccountDO a = new AccountDO();
        a.setTenantId(tenantId);
        a.setAdminFlag(adminFlag);
        return a;
    }

    @Test
    void unknownAccountGetsEmptySet() {
        lenient().when(accountMapper.selectOne(any())).thenReturn(null);
        assertEquals(Set.of(), resolver.resolveAllowedResourceIds("nobody"));
    }

    @Test
    void platformAdminIsUnrestricted() {
        when(accountMapper.selectOne(any())).thenReturn(account(0, 1));
        assertNull(resolver.resolveAllowedResourceIds("platform"));
    }

    @Test
    void platformScopedNonAdminIsRestrictedByRoleOnly() {
        when(accountMapper.selectOne(any())).thenReturn(account(0, 0));
        UserBasicDO user = new UserBasicDO();
        user.setRoleCode("IT_PRODUCT");
        when(userBasicMapper.selectOne(any())).thenReturn(user);
        RoleResourceDO rr = new RoleResourceDO();
        rr.setResourceId(110101);
        when(roleResourceMapper.selectList(any())).thenReturn(List.of(rr));

        assertEquals(Set.of(110101), resolver.resolveAllowedResourceIds("it_product_user"));
    }

    @Test
    void platformScopedNonAdminWithoutRoleGetsEmptySet() {
        when(accountMapper.selectOne(any())).thenReturn(account(0, 0));
        when(userBasicMapper.selectOne(any())).thenReturn(null);

        assertEquals(Set.of(), resolver.resolveAllowedResourceIds("no_role_user"));
    }

    @Test
    void tenantAdminIsRestrictedByPackageOnly() throws Exception {
        when(accountMapper.selectOne(any())).thenReturn(account(1006, 1));
        TenantDO tenant = new TenantDO();
        tenant.setPackageId(4);
        when(tenantMapper.selectById(1006)).thenReturn(tenant);
        TenantPackageDO pkg = new TenantPackageDO();
        pkg.setMenuIds("[100001,100004,100031]");
        when(packageMapper.selectById(4)).thenReturn(pkg);

        assertEquals(Set.of(100001, 100004, 100031), resolver.resolveAllowedResourceIds("tenant_admin"));
    }

    @Test
    void tenantAdminWithMissingTenantGetsEmptySet() {
        when(accountMapper.selectOne(any())).thenReturn(account(1006, 1));
        when(tenantMapper.selectById(1006)).thenReturn(null);

        assertEquals(Set.of(), resolver.resolveAllowedResourceIds("tenant_admin"));
    }

    @Test
    void tenantAdminWithMissingPackageGetsEmptySet() {
        when(accountMapper.selectOne(any())).thenReturn(account(1006, 1));
        TenantDO tenant = new TenantDO();
        tenant.setPackageId(4);
        when(tenantMapper.selectById(1006)).thenReturn(tenant);
        when(packageMapper.selectById(4)).thenReturn(null);

        assertEquals(Set.of(), resolver.resolveAllowedResourceIds("tenant_admin"));
    }

    @Test
    void tenantStaffIsRestrictedByPackageAndRoleIntersection() {
        when(accountMapper.selectOne(any())).thenReturn(account(1006, 0));
        TenantDO tenant = new TenantDO();
        tenant.setPackageId(4);
        when(tenantMapper.selectById(1006)).thenReturn(tenant);
        TenantPackageDO pkg = new TenantPackageDO();
        pkg.setMenuIds("[100001,100004,100031,100032]");
        when(packageMapper.selectById(4)).thenReturn(pkg);

        UserBasicDO user = new UserBasicDO();
        user.setRoleCode("ROLE_STAFF");
        when(userBasicMapper.selectOne(any())).thenReturn(user);
        RoleResourceDO rr1 = new RoleResourceDO();
        rr1.setResourceId(100031);
        RoleResourceDO rr2 = new RoleResourceDO();
        // 角色给了 100005（询盘管理），但套餐没有——交集里不应该出现
        rr2.setResourceId(100005);
        when(roleResourceMapper.selectList(any())).thenReturn(List.of(rr1, rr2));

        assertEquals(Set.of(100031), resolver.resolveAllowedResourceIds("tenant_staff"));
    }

    @Test
    void tenantStaffWithoutRoleGetsEmptySetEvenIfPackageAllowsMenus() {
        when(accountMapper.selectOne(any())).thenReturn(account(1006, 0));
        TenantDO tenant = new TenantDO();
        tenant.setPackageId(4);
        when(tenantMapper.selectById(1006)).thenReturn(tenant);
        TenantPackageDO pkg = new TenantPackageDO();
        pkg.setMenuIds("[100001,100004]");
        when(packageMapper.selectById(4)).thenReturn(pkg);
        when(userBasicMapper.selectOne(any())).thenReturn(null);

        assertTrue(resolver.resolveAllowedResourceIds("tenant_staff").isEmpty());
    }
}

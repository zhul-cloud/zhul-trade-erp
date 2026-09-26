package com.zhul.erp.framework.security;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** 对应 specs/access/data-scope/spec.md「按角色数据权限范围计算可见负责人」 */
@ExtendWith(MockitoExtension.class)
class DataScopeResolverTest {

    @Mock private AccountMapper accountMapper;
    @Mock private UserBasicMapper userBasicMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private RoleOrgMapper roleOrgMapper;
    @Mock private DepartmentMapper departmentMapper;
    @Mock private UserDepartmentMapper userDepartmentMapper;

    private DataScopeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DataScopeResolver(accountMapper, userBasicMapper, roleMapper, roleOrgMapper,
                departmentMapper, userDepartmentMapper);
    }

    @Test
    void adminAccount_seesAll() {
        when(accountMapper.selectOne(any())).thenReturn(account(1));
        when(userBasicMapper.selectOne(any())).thenReturn(user(10, "ROLE_STAFF", 0));

        DataScope scope = resolver.resolve("admin");

        assertThat(scope.isAll()).isTrue();
        assertThat(scope.selfId()).isEqualTo(10L);
    }

    @Test
    void noUserProfile_seesNothing() {
        when(accountMapper.selectOne(any())).thenReturn(account(0));
        when(userBasicMapper.selectOne(any())).thenReturn(null);

        DataScope scope = resolver.resolve("ghost");

        assertThat(scope.type()).isEqualTo(DataScope.Type.NONE);
        assertThat(scope.canSee(10L)).isFalse();
    }

    @Test
    void roleScopeAll_seesAll() {
        stubUser(10, "ROLE_MGR", 0);
        when(roleMapper.selectOne(any())).thenReturn(role(1));

        assertThat(resolver.resolve("mgr").isAll()).isTrue();
    }

    @Test
    void roleScopeSelf_seesOnlyOwn() {
        stubUser(10, "ROLE_STAFF", 0);
        when(roleMapper.selectOne(any())).thenReturn(role(3));

        DataScope scope = resolver.resolve("zhangwei");

        assertThat(scope.type()).isEqualTo(DataScope.Type.SELF);
        assertThat(scope.ownerIds()).containsExactly(10L);
    }

    @Test
    void roleScopeNone_treatedAsSelf() {
        stubUser(10, "ROLE_X", 0);
        when(roleMapper.selectOne(any())).thenReturn(role(0));

        assertThat(resolver.resolve("zhangwei").ownerIds()).containsExactly(10L);
    }

    @Test
    void noRole_treatedAsSelf() {
        stubUser(10, "", 0);

        DataScope scope = resolver.resolve("zhangwei");

        assertThat(scope.type()).isEqualTo(DataScope.Type.SELF);
        assertThat(scope.ownerIds()).containsExactly(10L);
    }

    @Test
    void roleScopeCustom_includesSubDepartmentsSecondaryDepartmentsAndSelf() {
        // 销售中心(1) ← 华东销售部(2) ← 华东一组(3)；华南销售部(4) 不在配置内
        stubUser(99, "ROLE_MGR", 1);
        when(roleMapper.selectOne(any())).thenReturn(role(2));
        when(roleOrgMapper.selectList(any())).thenReturn(List.of(roleOrg("DP1")));
        when(departmentMapper.selectList(any())).thenReturn(List.of(
                dept(1, 0, "DP1"), dept(2, 1, "DP2"), dept(3, 2, "DP3"), dept(4, 0, "DP4")));
        // 主部门命中：张伟(10) 在华东一组，李娜(11) 在华东销售部
        when(userBasicMapper.selectList(any())).thenReturn(List.of(user(10, "ROLE_STAFF", 3), user(11, "ROLE_STAFF", 2)));
        // 附属部门命中：赵敏(12) 主部门在华南，附属在华东销售部
        when(userDepartmentMapper.selectList(any())).thenReturn(List.of(userDept(12, "DP2")));

        DataScope scope = resolver.resolve("wangqiang");

        assertThat(scope.type()).isEqualTo(DataScope.Type.CUSTOM);
        assertThat(scope.ownerIds()).containsExactlyInAnyOrder(10L, 11L, 12L, 99L);
        assertThat(scope.canSee(13L)).isFalse();
    }

    @Test
    void roleScopeCustom_withoutConfiguredDepartments_seesOnlyOwn() {
        stubUser(99, "ROLE_MGR", 1);
        when(roleMapper.selectOne(any())).thenReturn(role(2));
        when(roleOrgMapper.selectList(any())).thenReturn(List.of());

        assertThat(resolver.resolve("wangqiang").ownerIds()).containsExactly(99L);
    }

    private void stubUser(int id, String roleCode, int deptId) {
        lenient().when(accountMapper.selectOne(any())).thenReturn(account(0));
        when(userBasicMapper.selectOne(any())).thenReturn(user(id, roleCode, deptId));
    }

    private static AccountDO account(int adminFlag) {
        AccountDO a = new AccountDO();
        a.setAdminFlag(adminFlag);
        return a;
    }

    private static UserBasicDO user(int id, String roleCode, int deptId) {
        UserBasicDO u = new UserBasicDO();
        u.setId(id);
        u.setTenantId(1);
        u.setRoleCode(roleCode);
        u.setDeptId(deptId);
        return u;
    }

    private static RoleDO role(int scope) {
        RoleDO r = new RoleDO();
        r.setPermissionScope(scope);
        return r;
    }

    private static RoleOrgDO roleOrg(String code) {
        RoleOrgDO ro = new RoleOrgDO();
        ro.setOrgCode(code);
        return ro;
    }

    private static DepartmentDO dept(int id, int pid, String code) {
        DepartmentDO d = new DepartmentDO();
        d.setId(id);
        d.setPid(pid);
        d.setCode(code);
        return d;
    }

    private static UserDepartmentDO userDept(int userId, String code) {
        UserDepartmentDO ud = new UserDepartmentDO();
        ud.setUserId(userId);
        ud.setDeptCode(code);
        return ud;
    }
}

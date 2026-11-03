package com.zhul.erp.modules.system;

import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.dto.SaveRoleRequest;
import com.zhul.erp.modules.system.service.RoleService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 角色数据权限（audit UI-012、UI-013）：只改状态等字段不动数据范围和自定义部门；不传范围新建为仅本人 */
class RoleScopeContractTest extends IntegrationTestBase {

    private static final int TENANT_ID = 99000081;
    private static final String CODE_PREFIX = "ITSC";

    @Autowired private RoleService roleService;

    @BeforeEach
    void data() {
        cleanup();
        jdbc.update("insert into department (tenant_id, code, name) values (?, 'ITSCOPE_D1', '审查一部'), (?, 'ITSCOPE_D2', '审查二部')",
                TENANT_ID, TENANT_ID);
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from role_org where role_code like ?", CODE_PREFIX + "%");
        jdbc.update("delete from role where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from department where tenant_id = ?", TENANT_ID);
    }

    private int role(String code, int scope) {
        jdbc.update("insert into role (tenant_id, code, name, permission_scope, status) values (?, ?, ?, ?, 1)",
                TENANT_ID, code, code, scope);
        return jdbc.queryForObject("select id from role where code = ?", Integer.class, code);
    }

    private int scope(int id) {
        return jdbc.queryForObject("select permission_scope from role where id = ?", Integer.class, id);
    }

    private List<String> depts(String code) {
        return jdbc.queryForList("select org_code from role_org where role_code = ? order by org_code", String.class, code);
    }

    private int deptId(String code) {
        return jdbc.queryForObject("select id from department where code = ?", Integer.class, code);
    }

    private static SaveRoleRequest statusOnly(String name, int status) {
        SaveRoleRequest req = new SaveRoleRequest();
        req.setName(name);
        req.setStatus(status);
        return req;
    }

    @Test
    void toggleStatusKeepsScopeAndCustomDepts() {
        for (int s : new int[]{1, 2, 3}) {
            String code = CODE_PREFIX + s;
            int id = role(code, s);
            if (s == 2) {
                jdbc.update("insert into role_org (role_code, org_code) values (?, 'ITSCOPE_D1'), (?, 'ITSCOPE_D2')", code, code);
            }
            for (int round = 0; round < 2; round++) {
                roleService.updateRole(id, statusOnly(code, 0));
                roleService.updateRole(id, statusOnly(code, 1));
            }
            assertEquals(s, scope(id), "启停不改数据范围：" + s);
            assertEquals(s == 2 ? List.of("ITSCOPE_D1", "ITSCOPE_D2") : List.of(), depts(code), "启停不动自定义部门：" + s);
        }
    }

    @Test
    void explicitScopeChangeStillRebuildsDepts() {
        String code = CODE_PREFIX + "E";
        int id = role(code, 3);
        SaveRoleRequest toCustom = statusOnly(code, 1);
        toCustom.setPermissionScope(2);
        toCustom.setDeptIds(List.of(deptId("ITSCOPE_D2")));
        roleService.updateRole(id, toCustom);
        assertEquals(2, scope(id));
        assertEquals(List.of("ITSCOPE_D2"), depts(code));

        SaveRoleRequest toSelf = statusOnly(code, 1);
        toSelf.setPermissionScope(3);
        roleService.updateRole(id, toSelf);
        assertEquals(3, scope(id));
        assertEquals(List.of(), depts(code), "改为仅本人后清掉自定义部门");
    }

    @Test
    void createWithoutScopeIsSelf() {
        SaveRoleRequest req = new SaveRoleRequest();
        req.setName(CODE_PREFIX + "NEW");
        roleService.createRole(req);
        assertEquals(3, jdbc.queryForObject("select permission_scope from role where tenant_id = ? and name = ?",
                Integer.class, TENANT_ID, CODE_PREFIX + "NEW"));
        assertEquals(1, jdbc.queryForObject("select status from role where tenant_id = ? and name = ?",
                Integer.class, TENANT_ID, CODE_PREFIX + "NEW"));
    }
}

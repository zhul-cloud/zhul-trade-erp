package com.zhul.erp.framework.tenant;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.finance.service.CustomerRefundService;
import com.zhul.erp.modules.masterdata.service.SupplierService;
import com.zhul.erp.modules.system.dto.CreateUserRequest;
import com.zhul.erp.modules.system.dto.SaveDeptRequest;
import com.zhul.erp.modules.system.dto.SaveDictItemRequest;
import com.zhul.erp.modules.system.dto.SaveRoleRequest;
import com.zhul.erp.modules.system.dto.UpdateUserRequest;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.ConfigService;
import com.zhul.erp.modules.system.service.DeptService;
import com.zhul.erp.modules.system.service.DictItemService;
import com.zhul.erp.modules.system.service.MenuService;
import com.zhul.erp.modules.system.service.RoleService;
import com.zhul.erp.modules.system.service.UserService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租户隔离（audit SEC-011 分类租户拦截器；SEC-002、SEC-003、SEC-009、SEC-010、BUS-002）：
 * 租户 A 对租户 B 的对象一律拒绝且 B 的数据不变；平台内置角色、配置对 A 只读；A 自己的操作照常成功。
 */
class TenantIsolationContractTest extends IntegrationTestBase {

    private static final int A = 99000091;
    private static final int B = 99000092;
    private static final int USER_A = 99000091;
    private static final int USER_B = 99000092;
    private static final String PLATFORM_ROLE = "ITTIPLAT";
    private static final String ROLE_A = "ITTIA";
    private static final String ROLE_B = "ITTIB";

    @Autowired private UserService userService;
    @Autowired private RoleService roleService;
    @Autowired private MenuService menuService;
    @Autowired private DeptService deptService;
    @Autowired private ConfigService configService;
    @Autowired private DictItemService dictItemService;
    @Autowired private SupplierService supplierService;
    @Autowired private CustomerRefundService refundService;
    @Autowired private UserBasicMapper userBasicMapper;

    @BeforeEach
    void data() {
        cleanup();
        for (int[] u : new int[][]{{USER_A, A}, {USER_B, B}}) {
            jdbc.update("insert into user_basic (id, tenant_id, name, username, status) values (?, ?, ?, ?, 1)",
                    u[0], u[1], "隔离" + u[1], "it_iso_" + u[1]);
            jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag, status) values (?, ?, ?, ?, 0, 1)",
                    u[0], u[1], u[0], "it_iso_" + u[1]);
            jdbc.update("insert into account_local_auth (account_id, username, password, salt) values (?, ?, 'x', '')",
                    u[0], "it_iso_" + u[1]);
        }
        jdbc.update("insert into role (tenant_id, code, name, permission_scope, status) values (0, ?, '隔离平台角色', 3, 1), "
                + "(?, ?, '隔离A角色', 3, 1), (?, ?, '隔离B角色', 2, 1)", PLATFORM_ROLE, A, ROLE_A, B, ROLE_B);
        jdbc.update("insert into role_org (role_code, org_code) values (?, 'ITTI_DB')", ROLE_B);
        jdbc.update("insert into department (tenant_id, code, name, pid, level, status) values (?, 'ITTI_DA', '隔离A部', 0, 1, 1), "
                + "(?, 'ITTI_DB', '隔离B部', 0, 1, 1)", A, B);
        jdbc.update("insert into sys_config (tenant_id, config_key, config_name, config_value, is_builtin) values "
                + "(0, 'it.iso.platform', '隔离平台配置', 'p', 0), (?, 'it.iso.b', '隔离B配置', 'b', 0)", B);
        jdbc.update("insert into dict_type (tenant_id, dict_type, dict_name) values (0, 'it_iso_p', '隔离平台字典'), (?, 'it_iso_b', '隔离B字典')", B);
        jdbc.update("insert into dict_item (tenant_id, dict_type_id, item_code, item_name) select tenant_id, id, 'X', '旧' "
                + "from dict_type where dict_type in ('it_iso_p', 'it_iso_b')");
        jdbc.update("insert into supplier (tenant_id, supplier_code, name) values (?, 'ITISOSUP', '隔离B供应商')", B);
        jdbc.update("insert into sales_order (tenant_id, so_no, owner_id, status) values (?, 'ITISO-SO-A', ?, 1)", A, USER_B);
        jdbc.update("insert into fin_customer_refund (tenant_id, so_id, amount, amount_cny, currency_code, exchange_rate, refund_date, status) "
                + "select ?, id, 10, 70, 'USD', 7, curdate(), 1 from sales_order where so_no = 'ITISO-SO-A'", A);
        TenantContext.setTenantId(A);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from fin_customer_refund where so_id in (select id from sales_order where so_no like 'ITISO-%')");
        jdbc.update("delete from sales_order where so_no like 'ITISO-%'");
        jdbc.update("delete from supplier where supplier_code = 'ITISOSUP'");
        jdbc.update("delete from dict_item where dict_type_id in (select id from dict_type where dict_type like 'it_iso_%')");
        jdbc.update("delete from dict_type where dict_type like 'it_iso_%'");
        jdbc.update("delete from sys_config where config_key like 'it.iso.%'");
        jdbc.update("delete from department where code like 'ITTI_%'");
        jdbc.update("delete from role_org where role_code like 'ITTI%'");
        jdbc.update("delete from role_resource where role_code like 'ITTI%'");
        jdbc.update("delete from role where code like 'ITTI%'");
        jdbc.update("delete from account_local_auth where account_id in (?, ?)", USER_A, USER_B);
        jdbc.update("delete from account where id in (?, ?) or username = 'it_iso_dup'", USER_A, USER_B);
        jdbc.update("delete from user_basic where id in (?, ?) or username = 'it_iso_dup'", USER_A, USER_B);
    }

    private void rejected(String message, Executable action) {
        assertEquals(message, assertThrows(BizException.class, action).getMessage());
    }

    private Object col(String sql, Object... args) {
        return jdbc.queryForObject(sql, Object.class, args);
    }

    private int id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void userManagementCannotTouchOtherTenant() {
        rejected("用户不存在", () -> userService.updateUserStatus(USER_B, 0));
        UpdateUserRequest edit = new UpdateUserRequest();
        edit.setName("改名");
        rejected("用户不存在", () -> userService.updateUser(USER_B, edit));
        rejected("用户不存在", () -> userService.deleteUser(USER_B));
        rejected("用户不存在", () -> userService.resetPassword(USER_B, "New@123456"));
        assertEquals(1, ((Number) col("select status from account where id = ?", USER_B)).intValue());
        assertEquals("隔离" + B, col("select name from user_basic where id = ?", USER_B));
        assertEquals("x", col("select password from account_local_auth where account_id = ?", USER_B));

        // 引用其他租户的部门被拒绝；用户名全局唯一，与其他租户重名也不行
        UpdateUserRequest moveDept = new UpdateUserRequest();
        moveDept.setDeptId(id("select id from department where code = 'ITTI_DB'"));
        rejected("部门不存在", () -> userService.updateUser(USER_A, moveDept));
        CreateUserRequest dup = new CreateUserRequest();
        dup.setName("重名");
        dup.setUsername("it_iso_" + B);
        dup.setPassword("Pass@123456");
        dup.setStatus(1);
        rejected("用户名已存在", () -> userService.createUser(dup));

        // 本租户的操作照常成功
        userService.updateUser(USER_A, edit);
        assertEquals("改名", col("select name from user_basic where id = ?", USER_A));
    }

    @Test
    void rolesAndDeptsOfOtherTenantRejected_platformRoleReadOnly() {
        int roleB = id("select id from role where code = ?", ROLE_B);
        int platformRole = id("select id from role where code = ?", PLATFORM_ROLE);
        SaveRoleRequest rename = new SaveRoleRequest();
        rename.setName("改名");
        rejected("角色不存在", () -> roleService.updateRole(roleB, rename));
        rejected("平台内置角色不能修改", () -> roleService.updateRole(platformRole, rename));
        rejected("平台内置角色不能修改", () -> roleService.deleteRole(platformRole));
        rejected("角色不存在", () -> menuService.assignRoleMenus(ROLE_B, List.of()));
        rejected("平台内置角色不能修改", () -> menuService.assignRoleMenus(PLATFORM_ROLE, List.of()));
        assertEquals(1, id("select count(*) from role_org where role_code = ?", ROLE_B), "B 角色的自定义部门不受影响");
        assertEquals("隔离平台角色", col("select name from role where code = ?", PLATFORM_ROLE));

        int deptB = id("select id from department where code = 'ITTI_DB'");
        SaveDeptRequest dept = new SaveDeptRequest();
        dept.setName("改名");
        rejected("部门不存在", () -> deptService.updateDept(deptB, dept));
        rejected("部门不存在", () -> deptService.updateStatus(deptB, 0));
        SaveDeptRequest underB = new SaveDeptRequest();
        underB.setName("挂到B下");
        underB.setPid(deptB);
        rejected("上级部门不存在", () -> deptService.updateDept(id("select id from department where code = 'ITTI_DA'"), underB));
        assertEquals("隔离B部", col("select name from department where id = ?", deptB));

        // 本租户角色照常修改；平台角色可见（可引用）
        roleService.updateRole(id("select id from role where code = ?", ROLE_A), rename);
        assertEquals("改名", col("select name from role where code = ?", ROLE_A));
        assertTrue(roleService.getRoleDeptIds(PLATFORM_ROLE).isEmpty());
    }

    @Test
    void configAndDictOfOtherTenantRejected() {
        int configB = id("select id from sys_config where config_key = 'it.iso.b'");
        rejected("配置不存在", () -> configService.updateValue(configB, "改"));
        rejected("配置不存在", () -> configService.delete(configB));
        rejected("平台配置不能删除", () -> configService.delete(id("select id from sys_config where config_key = 'it.iso.platform'")));
        assertEquals("b", col("select config_value from sys_config where id = ?", configB));
        assertEquals(1, id("select count(*) from sys_config where config_key = 'it.iso.platform' and deleted_at is null"));

        int itemB = id("select i.id from dict_item i join dict_type t on t.id = i.dict_type_id where t.dict_type = 'it_iso_b'");
        SaveDictItemRequest item = new SaveDictItemRequest();
        item.setItemCode("X");
        item.setItemName("新");
        rejected("字典项不存在", () -> dictItemService.update(itemB, item));
        assertEquals("旧", col("select item_name from dict_item where id = ?", itemB));

        // PRD：租户可以维护平台的全局字典项
        int itemP = id("select i.id from dict_item i join dict_type t on t.id = i.dict_type_id where t.dict_type = 'it_iso_p'");
        item.setDictTypeId(id("select id from dict_type where dict_type = 'it_iso_p'"));
        dictItemService.update(itemP, item);
        assertEquals("新", col("select item_name from dict_item where id = ?", itemP));
    }

    @Test
    void supplierOfOtherTenantRejected() {
        long supplierB = id("select id from supplier where supplier_code = 'ITISOSUP'");
        assertNull(supplierService.getById(supplierB), "详情读不到其他租户的供应商");
        assertThrows(BizException.class, () -> supplierService.updateStatus(supplierB, 0));
        assertThrows(BizException.class, () -> supplierService.delete(supplierB));
        assertEquals(1, ((Number) col("select status from supplier where id = ?", supplierB)).intValue());
        assertEquals(0, id("select count(*) from supplier where id = ? and deleted_at is not null", supplierB));
    }

    @Test
    void refundOfOrderNeedsTenantAndSalesDataScope() {
        long so = id("select id from sales_order where so_no = 'ITISO-SO-A'");
        // 只有销售订单菜单（没有退款管理菜单）、数据范围仅本人：别人负责的订单不能看退款
        loginWithResources("it_iso_sales", id("select id from resource where path = '/sales/orders' and type = 2 limit 1"));
        testUserTenantAs(A);
        rejected("销售订单不存在", () -> refundService.ofOrder(so));
        // 其他租户看不到这张退款
        TenantContext.setTenantId(B);
        loginAsAdmin("it_iso_admin");
        assertTrue(refundService.ofOrder(so).isEmpty());
    }

    @Test
    void everyEnforcedTableExistsWithTenantColumn() {
        // 表名写错时拦截器会悄悄不处理那张表：清单里的每张表都必须真实存在并有 tenant_id
        for (String table : TenantTables.enforced().keySet()) {
            assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = database() "
                    + "and table_name = ? and column_name = 'tenant_id'", Integer.class, table), table);
        }
    }

    @Test
    void everyTenantTableIsClassified() {
        // 新建带 tenant_id 的表必须归类：纳入拦截器，或写明理由排除
        List<String> tables = jdbc.queryForList("select table_name from information_schema.columns where table_schema = database() "
                + "and column_name = 'tenant_id' and table_name not like 'it\\_%' and table_name not like 'bak\\_%'", String.class);
        List<String> missing = tables.stream()
                .filter(t -> !TenantTables.enforced().containsKey(t) && !TenantTables.excluded().containsKey(t)).toList();
        assertTrue(missing.isEmpty(), "未归类的带 tenant_id 的表：" + missing);
    }

    @Test
    void missingTenantContextFailsClosed() {
        TenantContext.clear();
        assertThrows(Exception.class, () -> userBasicMapper.selectById(USER_A));
        assertNotNull(TenantScope.platform(() -> userBasicMapper.selectById(USER_B)), "平台范围内可按 ID 读任意租户");
    }

    /** loginWithResources 建的测试用户放到租户 A（数据范围按 A 的用户判断） */
    private void testUserTenantAs(int tenant) {
        jdbc.update("update user_basic set tenant_id = ? where username = 'it_iso_sales'", tenant);
        jdbc.update("update account set tenant_id = ? where username = 'it_iso_sales'", tenant);
        TenantContext.setTenantId(tenant);
    }
}

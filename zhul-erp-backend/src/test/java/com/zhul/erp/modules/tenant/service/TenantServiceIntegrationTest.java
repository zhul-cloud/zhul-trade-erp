package com.zhul.erp.modules.tenant.service;

import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.tenant.dto.RenewTenantRequest;
import com.zhul.erp.modules.tenant.dto.ResetPasswordResultVO;
import com.zhul.erp.modules.tenant.dto.SaveTenantRequest;
import com.zhul.erp.modules.tenant.dto.TenantCreateResultVO;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 覆盖需要真实 Spring 上下文 + 真实库的场景：创建/编辑租户时联动的管理员账号
 * （user_basic/account/account_local_auth）需要走 LambdaUpdateWrapper.set(...)，
 * MyBatis-Plus 的 lambda 列缓存要靠 Spring 启动时真的扫描过这些实体对应的 Mapper
 * 才会建立，纯 Mockito 单测（见 TenantServiceImplTest）构造这类 wrapper 会直接报
 * "can not find lambda cache"，跟业务逻辑对不对无关，只能在这里用真实上下文验证。
 */
class TenantServiceIntegrationTest extends IntegrationTestBase {

    private static final int TEST_PACKAGE_ID = 90001;

    @Autowired
    private TenantService tenantService;

    @BeforeEach
    void setUpFixture() {
        TenantContext.setTenantId(0);
        jdbc.update("delete from tenant_package where id = ?", TEST_PACKAGE_ID);
        jdbc.update("insert into tenant_package (id, name, menu_ids, remark, status) values (?, ?, ?, ?, ?)",
                TEST_PACKAGE_ID, "集成测试套餐", "[]", "", 1);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from account_local_auth where account_id in "
                + "(select id from account where tenant_id in (select id from tenant where package_id = ?))", TEST_PACKAGE_ID);
        jdbc.update("delete from account where tenant_id in (select id from tenant where package_id = ?)", TEST_PACKAGE_ID);
        jdbc.update("delete from user_basic where tenant_id in (select id from tenant where package_id = ?)", TEST_PACKAGE_ID);
        jdbc.update("delete from tenant where package_id = ?", TEST_PACKAGE_ID);
        jdbc.update("delete from tenant_package where id = ?", TEST_PACKAGE_ID);
        TenantContext.clear();
    }

    private SaveTenantRequest request(String namePrefix, String email, String phone) {
        SaveTenantRequest req = new SaveTenantRequest();
        req.setName(namePrefix + "_" + System.nanoTime());
        req.setPackageId(TEST_PACKAGE_ID);
        req.setContactName("集成测试联系人");
        req.setContactPhone(phone);
        req.setContactEmail(email);
        req.setExpireDate(LocalDate.now().plusYears(1));
        return req;
    }

    @Test
    void updateEmailChangeSyncsAdminAccountLoginCredentials() {
        TenantCreateResultVO created = tenantService.create(
                request("邮箱同步测试", "before-" + System.nanoTime() + "@example.com", "13800001111"));
        Integer tenantId = created.getTenant().getId();
        String newEmail = "after-" + System.nanoTime() + "@example.com";

        SaveTenantRequest updateReq = request("邮箱同步测试", newEmail, "13800001111");
        updateReq.setName(created.getTenant().getName()); // 名称不变，只改邮箱
        tenantService.update(tenantId, updateReq);

        Integer accountId = jdbc.queryForObject(
                "select id from account where tenant_id = ? and username = ?", Integer.class, tenantId, newEmail);
        assertNotEquals(null, accountId, "账号的登录用户名/邮箱应该同步成新邮箱");

        String userBasicEmail = jdbc.queryForObject(
                "select email from user_basic where tenant_id = ?", String.class, tenantId);
        assertEquals(newEmail, userBasicEmail);
    }

    @Test
    void disableThenEnableCascadesAccountAndUserStatus() {
        TenantCreateResultVO created = tenantService.create(
                request("级联状态测试", "cascade-" + System.nanoTime() + "@example.com", "13800002222"));
        Integer tenantId = created.getTenant().getId();

        tenantService.updateStatus(tenantId, 0);
        assertEquals(0, jdbc.queryForObject("select status from account where tenant_id = ?", Integer.class, tenantId));
        assertEquals(0, jdbc.queryForObject("select status from user_basic where tenant_id = ?", Integer.class, tenantId));

        tenantService.updateStatus(tenantId, 1);
        assertEquals(1, jdbc.queryForObject("select status from account where tenant_id = ?", Integer.class, tenantId));
        assertEquals(1, jdbc.queryForObject("select status from user_basic where tenant_id = ?", Integer.class, tenantId));
    }

    @Test
    void resetPasswordActuallyChangesStoredHashAndReturnsDifferentPasswordEachTime() {
        TenantCreateResultVO created = tenantService.create(
                request("重置密码测试", "resetpwd-" + System.nanoTime() + "@example.com", "13800003333"));
        Integer tenantId = created.getTenant().getId();
        Integer accountId = jdbc.queryForObject("select id from account where tenant_id = ?", Integer.class, tenantId);
        String originalHash = jdbc.queryForObject(
                "select password from account_local_auth where account_id = ?", String.class, accountId);

        ResetPasswordResultVO result1 = tenantService.resetPassword(tenantId);
        String hashAfterFirstReset = jdbc.queryForObject(
                "select password from account_local_auth where account_id = ?", String.class, accountId);
        ResetPasswordResultVO result2 = tenantService.resetPassword(tenantId);

        assertNotEquals(originalHash, hashAfterFirstReset, "重置后哈希应该变化");
        assertNotEquals(result1.getTempPassword(), result2.getTempPassword(), "两次重置的明文临时密码不应相同");
        assertEquals(created.getAdminEmail(), result1.getAdminEmail());
    }

    @Test
    void renewByDurationExtendsFromCurrentExpireTimeAgainstRealDb() {
        TenantCreateResultVO created = tenantService.create(
                request("续期测试", "renew-" + System.nanoTime() + "@example.com", "13800004444"));
        Integer tenantId = created.getTenant().getId();

        RenewTenantRequest req = new RenewTenantRequest();
        req.setMode("DURATION");
        req.setMonths(3);
        var renewed = tenantService.renew(tenantId, req);

        assertEquals(created.getTenant().getExpireTime().plusMonths(3), renewed.getExpireTime());
    }
}

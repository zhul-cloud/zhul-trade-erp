package com.zhul.erp.modules.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.entity.AccountDO;
import com.zhul.erp.modules.system.entity.AccountLocalAuthDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.AccountLocalAuthMapper;
import com.zhul.erp.modules.system.repository.AccountMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.tenant.dto.PackageOptionVO;
import com.zhul.erp.modules.tenant.dto.RenewTenantRequest;
import com.zhul.erp.modules.tenant.dto.ResetPasswordResultVO;
import com.zhul.erp.modules.tenant.dto.SaveTenantRequest;
import com.zhul.erp.modules.tenant.dto.TenantCreateResultVO;
import com.zhul.erp.modules.tenant.dto.TenantQuery;
import com.zhul.erp.modules.tenant.dto.TenantVO;
import com.zhul.erp.modules.tenant.entity.TenantDO;
import com.zhul.erp.modules.tenant.entity.TenantPackageDO;
import com.zhul.erp.modules.tenant.repository.TenantMapper;
import com.zhul.erp.modules.tenant.repository.TenantPackageMapper;
import com.zhul.erp.modules.tenant.service.impl.TenantServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private TenantPackageMapper packageMapper;
    @Mock
    private UserBasicMapper userBasicMapper;
    @Mock
    private AccountMapper accountMapper;
    @Mock
    private AccountLocalAuthMapper accountLocalAuthMapper;

    private TenantServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0);
        service = new TenantServiceImpl(tenantMapper, packageMapper, userBasicMapper, accountMapper, accountLocalAuthMapper);
        // insert 时模拟自增主键回填，跟真实 MyBatis-Plus 行为一致；lenient 是因为不是每个用例都会
        // 走到创建这一步（校验类用例提交就被拦下了），严格模式下会被判定成没用到的多余 stub
        lenient().doAnswer(invocation -> {
            TenantDO arg = invocation.getArgument(0);
            arg.setId(2000);
            return 1;
        }).when(tenantMapper).insert(any(TenantDO.class));
        lenient().doAnswer(invocation -> {
            UserBasicDO arg = invocation.getArgument(0);
            arg.setId(3000);
            return 1;
        }).when(userBasicMapper).insert(any(UserBasicDO.class));
        lenient().doAnswer(invocation -> {
            AccountDO arg = invocation.getArgument(0);
            arg.setId(4000);
            return 1;
        }).when(accountMapper).insert(any(AccountDO.class));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static SaveTenantRequest request() {
        SaveTenantRequest req = new SaveTenantRequest();
        req.setName("示例科技");
        req.setPackageId(1);
        req.setContactName("张三");
        req.setContactPhone("13800000001");
        req.setContactEmail("zhangsan@example.com");
        req.setExpireDate(LocalDate.now().plusYears(1));
        req.setRemark("备注");
        return req;
    }

    private static TenantPackageDO enabledPackage() {
        TenantPackageDO pkg = new TenantPackageDO();
        pkg.setId(1);
        pkg.setName("标准版");
        pkg.setStatus(1);
        return pkg;
    }

    private static TenantDO tenant(Integer id, String name) {
        TenantDO t = new TenantDO();
        t.setId(id);
        t.setCode("TN" + id);
        t.setName(name);
        t.setPackageId(1);
        t.setContactName("张三");
        t.setContactPhone("13800000001");
        t.setExpireTime(LocalDateTime.now().plusYears(1));
        t.setStatus(1);
        t.setRemark("");
        return t;
    }

    // ---------- 权限边界：只有平台账号（tenantId=0）能访问 ----------

    @Test
    void nonPlatformTenantIsRejected() {
        TenantContext.setTenantId(1000);
        BizException e = assertThrows(BizException.class, () -> service.page(new TenantQuery()));
        assertEquals("仅平台账号可访问租户管理", e.getMessage());
    }

    @Test
    void nullTenantIsRejected() {
        TenantContext.clear();
        assertThrows(BizException.class, () -> service.page(new TenantQuery()));
    }

    // ---------- 创建 ----------

    @Test
    void createSucceedsAndCreatesAdminAccountWithGeneratedCode() {
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        when(accountMapper.selectCount(any())).thenReturn(0L);
        when(packageMapper.selectById(1)).thenReturn(enabledPackage());

        TenantCreateResultVO result = service.create(request());

        ArgumentCaptor<TenantDO> tenantCaptor = ArgumentCaptor.forClass(TenantDO.class);
        verify(tenantMapper).insert(tenantCaptor.capture());
        assertEquals("示例科技", tenantCaptor.getValue().getName());

        // code 依赖自增主键回填，通过 updateById 补一次
        ArgumentCaptor<TenantDO> codeCaptor = ArgumentCaptor.forClass(TenantDO.class);
        verify(tenantMapper).updateById(codeCaptor.capture());
        assertEquals("TN2000", codeCaptor.getValue().getCode());
        assertEquals("TN2000", result.getTenant().getCode());

        ArgumentCaptor<UserBasicDO> userCaptor = ArgumentCaptor.forClass(UserBasicDO.class);
        verify(userBasicMapper).insert(userCaptor.capture());
        assertEquals(2000, userCaptor.getValue().getTenantId());
        assertEquals("ROLE_ADMIN", userCaptor.getValue().getRoleCode());
        assertEquals("zhangsan@example.com", userCaptor.getValue().getUsername());

        ArgumentCaptor<AccountDO> accountCaptor = ArgumentCaptor.forClass(AccountDO.class);
        verify(accountMapper).insert(accountCaptor.capture());
        assertEquals(3000, accountCaptor.getValue().getUserId());
        assertEquals(1, accountCaptor.getValue().getAdminFlag());
        assertEquals(1, accountCaptor.getValue().getStatus());

        verify(accountLocalAuthMapper).insert(any(AccountLocalAuthDO.class));

        assertEquals("zhangsan@example.com", result.getAdminEmail());
        assertNotNull(result.getTempPassword());
        assertTempPasswordIsValid(result.getTempPassword());
    }

    @Test
    void tempPasswordHasAtLeastOneUpperLowerDigit() {
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        when(accountMapper.selectCount(any())).thenReturn(0L);
        when(packageMapper.selectById(1)).thenReturn(enabledPackage());
        for (int i = 0; i < 20; i++) {
            TenantCreateResultVO result = service.create(request());
            assertTempPasswordIsValid(result.getTempPassword());
        }
    }

    private static void assertTempPasswordIsValid(String pwd) {
        assertEquals(12, pwd.length());
        assertTrue(Pattern.compile("[A-Z]").matcher(pwd).find(), "缺大写字母: " + pwd);
        assertTrue(Pattern.compile("[a-z]").matcher(pwd).find(), "缺小写字母: " + pwd);
        assertTrue(Pattern.compile("[0-9]").matcher(pwd).find(), "缺数字: " + pwd);
        assertTrue(Pattern.matches("[A-Za-z0-9]+", pwd), "含特殊字符: " + pwd);
    }

    @Test
    void createRejectsDuplicateName() {
        when(tenantMapper.selectCount(any())).thenReturn(1L);

        BizException e = assertThrows(BizException.class, () -> service.create(request()));
        assertEquals("该租户名称已存在", e.getMessage());
        verify(tenantMapper, never()).insert(any());
    }

    @Test
    void createRejectsDuplicatePhone() {
        when(tenantMapper.selectCount(any()))
                .thenReturn(0L) // name free
                .thenReturn(1L); // phone taken

        BizException e = assertThrows(BizException.class, () -> service.create(request()));
        assertEquals("该手机号已被其他账号使用", e.getMessage());
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        when(accountMapper.selectCount(any())).thenReturn(1L);

        BizException e = assertThrows(BizException.class, () -> service.create(request()));
        assertEquals("该邮箱已被其他账号使用", e.getMessage());
        verify(tenantMapper, never()).insert(any());
    }

    @Test
    void createRejectsDisabledPackage() {
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        TenantPackageDO disabled = enabledPackage();
        disabled.setStatus(0);
        when(packageMapper.selectById(1)).thenReturn(disabled);

        BizException e = assertThrows(BizException.class, () -> service.create(request()));
        assertEquals("所选套餐不可用，请重新选择", e.getMessage());
    }

    @Test
    void createRejectsNonExistentPackage() {
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        when(packageMapper.selectById(1)).thenReturn(null);

        assertThrows(BizException.class, () -> service.create(request()));
    }

    @Test
    void createRejectsTodayAsExpireDate() {
        // 边界值：今天本身也不算合法，必须严格晚于今天
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        when(packageMapper.selectById(1)).thenReturn(enabledPackage());
        SaveTenantRequest req = request();
        req.setExpireDate(LocalDate.now());

        BizException e = assertThrows(BizException.class, () -> service.create(req));
        assertEquals("到期时间不可早于当前时间", e.getMessage());
    }

    @Test
    void createRejectsPastExpireDate() {
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        when(packageMapper.selectById(1)).thenReturn(enabledPackage());
        SaveTenantRequest req = request();
        req.setExpireDate(LocalDate.now().minusDays(1));

        assertThrows(BizException.class, () -> service.create(req));
    }

    // ---------- 编辑 ----------

    @Test
    void updateNotFoundThrows() {
        when(tenantMapper.selectById(1)).thenReturn(null);
        assertThrows(BizException.class, () -> service.update(1, request()));
    }

    @Test
    void updateKeepingUnchangedDisabledPackageIsAllowed() {
        TenantDO current = tenant(1, "示例科技");
        current.setPackageId(9);
        when(tenantMapper.selectById(1)).thenReturn(current, current);
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        when(accountMapper.selectCount(any())).thenReturn(0L);
        TenantPackageDO disabledButUnchanged = new TenantPackageDO();
        disabledButUnchanged.setId(9);
        disabledButUnchanged.setName("旧套餐（已停用）");
        disabledButUnchanged.setStatus(0);
        when(packageMapper.selectById(9)).thenReturn(disabledButUnchanged);
        when(userBasicMapper.selectOne(any())).thenReturn(null);

        SaveTenantRequest req = request();
        req.setPackageId(9); // 跟当前一致，即使已停用也允许保存

        TenantVO vo = service.update(1, req);
        assertEquals("旧套餐（已停用）", vo.getPackageName());
    }

    @Test
    void updateToADifferentDisabledPackageIsRejected() {
        TenantDO current = tenant(1, "示例科技");
        current.setPackageId(1);
        when(tenantMapper.selectById(1)).thenReturn(current);
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        when(accountMapper.selectCount(any())).thenReturn(0L);
        TenantPackageDO disabled = enabledPackage();
        disabled.setId(9);
        disabled.setStatus(0);
        when(packageMapper.selectById(9)).thenReturn(disabled);
        when(userBasicMapper.selectOne(any())).thenReturn(null);

        SaveTenantRequest req = request();
        req.setPackageId(9);

        BizException e = assertThrows(BizException.class, () -> service.update(1, req));
        assertEquals("所选套餐不可用，请重新选择", e.getMessage());
    }

    // updateEmailChangeSyncsAdminAccount、disable/enable 级联、重置密码这三类场景要真的执行
    // LambdaUpdateWrapper.set(UserBasicDO::xxx) / set(AccountDO::xxx) 这些字段访问——MyBatis-Plus
    // 的 lambda 列缓存要靠 Spring 启动时真的扫描一次这些实体对应的 Mapper 才会建立，纯 Mockito
    // 单测没有 Spring 上下文，构造这些 wrapper 本身就会抛 "can not find lambda cache"，
    // 跟业务逻辑对不对无关，是纯单测的基础设施限制。这几个场景挪到
    // TenantServiceIntegrationTest（真实 DB + Spring 上下文）里覆盖。

    @Test
    void updateStatusNotFoundThrows() {
        when(tenantMapper.selectById(1)).thenReturn(null);
        assertThrows(BizException.class, () -> service.updateStatus(1, 0));
    }

    // ---------- 续期 ----------

    @Test
    void renewByDurationExtendsFromCurrentExpireTime() {
        TenantDO current = tenant(1, "示例科技");
        LocalDateTime baseExpire = LocalDateTime.of(2027, 3, 15, 23, 59, 59);
        current.setExpireTime(baseExpire);
        when(tenantMapper.selectById(1)).thenReturn(current, current);
        when(packageMapper.selectById(1)).thenReturn(enabledPackage());
        when(userBasicMapper.selectOne(any())).thenReturn(null);

        RenewTenantRequest req = new RenewTenantRequest();
        req.setMode("DURATION");
        req.setMonths(12);
        service.renew(1, req);

        ArgumentCaptor<TenantDO> captor = ArgumentCaptor.forClass(TenantDO.class);
        verify(tenantMapper).updateById(captor.capture());
        assertEquals(baseExpire.plusMonths(12), captor.getValue().getExpireTime());
    }

    @Test
    void renewByDurationRejectsMonthsNotInAllowedSet() {
        when(tenantMapper.selectById(1)).thenReturn(tenant(1, "示例科技"));
        RenewTenantRequest req = new RenewTenantRequest();
        req.setMode("DURATION");
        req.setMonths(2);

        BizException e = assertThrows(BizException.class, () -> service.renew(1, req));
        assertEquals("续期时长不合法，只能是 1/3/6/12 个月", e.getMessage());
    }

    @Test
    void renewByDateRejectsDateBeforeCurrentExpireTime() {
        TenantDO current = tenant(1, "示例科技");
        current.setExpireTime(LocalDateTime.of(2027, 3, 15, 23, 59, 59));
        when(tenantMapper.selectById(1)).thenReturn(current);

        RenewTenantRequest req = new RenewTenantRequest();
        req.setMode("DATE");
        req.setExpireDate(LocalDate.of(2027, 3, 14));

        BizException e = assertThrows(BizException.class, () -> service.renew(1, req));
        assertEquals("指定日期不可早于当前到期时间", e.getMessage());
    }

    @Test
    void renewByDateAcceptsSameDayAsCurrentExpireTime() {
        // 边界值：跟当前到期日期同一天不算"早于"，应该允许
        TenantDO current = tenant(1, "示例科技");
        current.setExpireTime(LocalDateTime.of(2027, 3, 15, 23, 59, 59));
        when(tenantMapper.selectById(1)).thenReturn(current, current);
        when(packageMapper.selectById(1)).thenReturn(enabledPackage());
        when(userBasicMapper.selectOne(any())).thenReturn(null);

        RenewTenantRequest req = new RenewTenantRequest();
        req.setMode("DATE");
        req.setExpireDate(LocalDate.of(2027, 3, 15));

        service.renew(1, req);
        verify(tenantMapper).updateById(any(TenantDO.class));
    }

    @Test
    void renewRejectsInvalidMode() {
        when(tenantMapper.selectById(1)).thenReturn(tenant(1, "示例科技"));
        RenewTenantRequest req = new RenewTenantRequest();
        req.setMode("WHATEVER");

        assertThrows(BizException.class, () -> service.renew(1, req));
    }

    @Test
    void renewNotFoundThrows() {
        when(tenantMapper.selectById(99)).thenReturn(null);
        RenewTenantRequest req = new RenewTenantRequest();
        req.setMode("DURATION");
        req.setMonths(1);

        assertThrows(BizException.class, () -> service.renew(99, req));
    }

    // ---------- 重置密码 ----------
    // 生成两次密码不同、真的写入新哈希这些场景见 TenantServiceIntegrationTest
    // （原因同上：resetPassword 要查 UserBasicDO/AccountDO 的 lambda wrapper）

    @Test
    void resetPasswordThrowsWhenAdminAccountMissing() {
        when(tenantMapper.selectById(1)).thenReturn(tenant(1, "示例科技"));
        when(userBasicMapper.selectOne(any())).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.resetPassword(1));
        assertEquals("该租户的管理员账号不存在", e.getMessage());
    }

    // ---------- 套餐下拉选项 ----------

    @Test
    void packageOptionsOnlyReturnsEnabledOnes() {
        when(packageMapper.selectList(any())).thenReturn(List.of(enabledPackage()));

        List<PackageOptionVO> options = service.packageOptions();
        assertEquals(1, options.size());
        assertEquals("标准版", options.get(0).getName());
    }

    // ---------- 列表分页 ----------

    @Test
    void pageReturnsMappedResults() {
        Page<TenantDO> page = new Page<>(1, 20);
        page.setRecords(List.of(tenant(1, "示例科技")));
        page.setTotal(1);
        when(tenantMapper.selectPage(any(), any())).thenReturn(page);
        when(packageMapper.selectBatchIds(anyCollection())).thenReturn(List.of(enabledPackage()));
        when(userBasicMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        PageResult<TenantVO> result = service.page(new TenantQuery());
        assertEquals(1L, result.getTotal());
        assertEquals("示例科技", result.getRecords().get(0).getName());
        assertEquals("标准版", result.getRecords().get(0).getPackageName());
    }
}

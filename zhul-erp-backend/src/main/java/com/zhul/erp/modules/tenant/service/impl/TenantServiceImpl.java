package com.zhul.erp.modules.tenant.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.zhul.erp.modules.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 租户管理：平台超级管理员专用模块，读、写都要求平台账号（tenantId=0），
 * 见 {@link #requirePlatform()}——跟商品主数据"读所有人开放、只写限制"不同，
 * 因为租户列表本身就是其它租户不该看到的数据。
 */
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    /** 内置角色编码：租户管理员，创建租户时同步创建的账号固定用这个角色（见 role 表种子数据） */
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final String PWD_UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String PWD_LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String PWD_DIGIT = "23456789";
    private static final List<Integer> RENEW_MONTHS_OPTIONS = List.of(1, 3, 6, 12);
    private static final SecureRandom RANDOM = new SecureRandom();
    // datetime 列不带小数秒；用 LocalTime.MAX（23:59:59.999999999）存库会被四舍五入进位到
    // 次日 00:00:00，"到期日 2027-09-22" 就变成显示成 2027-09-23 了，所以要用整秒的 23:59:59
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private final TenantMapper tenantMapper;
    private final TenantPackageMapper packageMapper;
    private final UserBasicMapper userBasicMapper;
    private final AccountMapper accountMapper;
    private final AccountLocalAuthMapper accountLocalAuthMapper;

    @Override
    public PageResult<TenantVO> page(TenantQuery query) {
        requirePlatform();
        LambdaQueryWrapper<TenantDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getName())) {
            wrapper.like(TenantDO::getName, query.getName());
        }
        if (query.getStatus() != null) {
            wrapper.eq(TenantDO::getStatus, query.getStatus());
        }
        if (query.getExpireDateFrom() != null) {
            wrapper.ge(TenantDO::getExpireTime, query.getExpireDateFrom().atStartOfDay());
        }
        if (query.getExpireDateTo() != null) {
            wrapper.le(TenantDO::getExpireTime, query.getExpireDateTo().atTime(END_OF_DAY));
        }
        // PRD 默认假设 7：列表默认按创建时间降序，最新创建排最前
        wrapper.orderByDesc(TenantDO::getCreateTime);

        Page<TenantDO> pageParam = new Page<>(query.getPage(), query.getPageSize());
        Page<TenantDO> pageResult = tenantMapper.selectPage(pageParam, wrapper);
        return PageResult.of(pageResult.getTotal(), toVoList(pageResult.getRecords()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantCreateResultVO create(SaveTenantRequest req) {
        requirePlatform();
        assertNameFree(req.getName(), null);
        assertPhoneFree(req.getContactPhone(), null);
        assertEmailFree(req.getContactEmail(), null);
        TenantPackageDO pkg = requireEnabledPackage(req.getPackageId());
        requireFutureDate(req.getExpireDate());

        TenantDO tenant = new TenantDO();
        tenant.setCode("");
        tenant.setName(req.getName());
        tenant.setPackageId(pkg.getId());
        tenant.setContactName(req.getContactName());
        tenant.setContactPhone(req.getContactPhone());
        tenant.setExpireTime(req.getExpireDate().atTime(END_OF_DAY));
        tenant.setStatus(1);
        tenant.setRemark(req.getRemark() == null ? "" : req.getRemark());
        tenantMapper.insert(tenant);

        // 编码依赖自增主键生成，先插入再回填，跟菜单管理的 code 生成方式一致
        TenantDO codeChange = new TenantDO();
        codeChange.setId(tenant.getId());
        codeChange.setCode("TN" + tenant.getId());
        tenantMapper.updateById(codeChange);
        tenant.setCode(codeChange.getCode());

        String tempPassword = generateTempPassword();
        createAdminAccount(tenant.getId(), req.getContactName(), req.getContactPhone(), req.getContactEmail(), tempPassword);

        TenantCreateResultVO result = new TenantCreateResultVO();
        result.setTenant(toVO(tenant, pkg.getName(), req.getContactEmail()));
        result.setAdminEmail(req.getContactEmail());
        result.setTempPassword(tempPassword);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantVO update(Integer id, SaveTenantRequest req) {
        requirePlatform();
        TenantDO current = getOrThrow(id);
        assertNameFree(req.getName(), id);
        assertPhoneFree(req.getContactPhone(), id);

        UserBasicDO adminUser = findAdminUser(id);
        AccountDO adminAccount = adminUser == null ? null : findAccountByUserId(adminUser.getId());
        String currentEmail = adminAccount == null ? "" : adminAccount.getEmail();
        if (!req.getContactEmail().equalsIgnoreCase(currentEmail)) {
            assertEmailFree(req.getContactEmail(), id);
        }

        // 套餐已被禁用时，保持原套餐不算错误；只有"改成"一个已禁用的套餐才拦截（PRD 默认假设 3）
        TenantPackageDO pkg;
        if (req.getPackageId().equals(current.getPackageId())) {
            pkg = packageMapper.selectById(current.getPackageId());
        } else {
            pkg = requireEnabledPackage(req.getPackageId());
        }
        requireFutureDate(req.getExpireDate());

        TenantDO change = new TenantDO();
        change.setId(id);
        change.setName(req.getName());
        change.setPackageId(req.getPackageId());
        change.setContactName(req.getContactName());
        change.setContactPhone(req.getContactPhone());
        change.setExpireTime(req.getExpireDate().atTime(END_OF_DAY));
        change.setRemark(req.getRemark() == null ? "" : req.getRemark());
        tenantMapper.updateById(change);

        if (adminUser != null && adminAccount != null) {
            LambdaUpdateWrapper<UserBasicDO> userWrapper = new LambdaUpdateWrapper<UserBasicDO>()
                    .eq(UserBasicDO::getId, adminUser.getId())
                    .set(UserBasicDO::getName, req.getContactName())
                    .set(UserBasicDO::getPhone, req.getContactPhone())
                    .set(UserBasicDO::getEmail, req.getContactEmail())
                    .set(UserBasicDO::getUsername, req.getContactEmail());
            userBasicMapper.update(null, userWrapper);

            LambdaUpdateWrapper<AccountDO> accountWrapper = new LambdaUpdateWrapper<AccountDO>()
                    .eq(AccountDO::getId, adminAccount.getId())
                    .set(AccountDO::getPhone, req.getContactPhone())
                    .set(AccountDO::getEmail, req.getContactEmail())
                    .set(AccountDO::getUsername, req.getContactEmail());
            accountMapper.update(null, accountWrapper);

            accountLocalAuthMapper.update(null, new LambdaUpdateWrapper<AccountLocalAuthDO>()
                    .eq(AccountLocalAuthDO::getAccountId, adminAccount.getId())
                    .set(AccountLocalAuthDO::getUsername, req.getContactEmail()));
        }

        TenantDO updated = getOrThrow(id);
        return toVO(updated, pkg == null ? "" : pkg.getName(), req.getContactEmail());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Integer id, Integer status) {
        requirePlatform();
        getOrThrow(id);
        TenantDO change = new TenantDO();
        change.setId(id);
        change.setStatus(status);
        tenantMapper.updateById(change);

        // 登录只看 account.status（见 AuthServiceImpl），级联同步才能真正拦住/放开该租户下的用户登录；
        // user_basic.status 一起同步只是为了跟用户管理页面展示的状态保持一致
        accountMapper.update(null, new LambdaUpdateWrapper<AccountDO>()
                .eq(AccountDO::getTenantId, id).set(AccountDO::getStatus, status));
        userBasicMapper.update(null, new LambdaUpdateWrapper<UserBasicDO>()
                .eq(UserBasicDO::getTenantId, id).set(UserBasicDO::getStatus, status));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantVO renew(Integer id, RenewTenantRequest req) {
        requirePlatform();
        TenantDO tenant = getOrThrow(id);
        LocalDateTime newExpireTime;
        if ("DURATION".equalsIgnoreCase(req.getMode())) {
            if (req.getMonths() == null || !RENEW_MONTHS_OPTIONS.contains(req.getMonths())) {
                throw new BizException("续期时长不合法，只能是 1/3/6/12 个月");
            }
            newExpireTime = tenant.getExpireTime().plusMonths(req.getMonths());
        } else if ("DATE".equalsIgnoreCase(req.getMode())) {
            if (req.getExpireDate() == null) {
                throw new BizException("请选择到期日期");
            }
            LocalDateTime candidate = req.getExpireDate().atTime(END_OF_DAY);
            if (candidate.isBefore(tenant.getExpireTime())) {
                throw new BizException("指定日期不可早于当前到期时间");
            }
            newExpireTime = candidate;
        } else {
            throw new BizException("续期方式不合法");
        }

        TenantDO change = new TenantDO();
        change.setId(id);
        change.setExpireTime(newExpireTime);
        tenantMapper.updateById(change);

        TenantDO updated = getOrThrow(id);
        TenantPackageDO pkg = packageMapper.selectById(updated.getPackageId());
        return toVO(updated, pkg == null ? "" : pkg.getName(), resolveAdminEmail(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResetPasswordResultVO resetPassword(Integer id) {
        requirePlatform();
        getOrThrow(id);
        UserBasicDO adminUser = findAdminUser(id);
        if (adminUser == null) {
            throw new BizException("该租户的管理员账号不存在");
        }
        AccountDO account = findAccountByUserId(adminUser.getId());
        if (account == null) {
            throw new BizException("该租户的管理员账号不存在");
        }

        String tempPassword = generateTempPassword();
        accountLocalAuthMapper.update(null, new LambdaUpdateWrapper<AccountLocalAuthDO>()
                .eq(AccountLocalAuthDO::getAccountId, account.getId())
                .set(AccountLocalAuthDO::getPassword, BCrypt.hashpw(tempPassword, BCrypt.gensalt()))
                .set(AccountLocalAuthDO::getFailCount, 0)
                .set(AccountLocalAuthDO::getUnlockAt, null));

        ResetPasswordResultVO result = new ResetPasswordResultVO();
        result.setAdminEmail(account.getEmail());
        result.setTempPassword(tempPassword);
        return result;
    }

    @Override
    public List<PackageOptionVO> packageOptions() {
        requirePlatform();
        List<TenantPackageDO> packages = packageMapper.selectList(new LambdaQueryWrapper<TenantPackageDO>()
                .eq(TenantPackageDO::getStatus, 1)
                .isNull(TenantPackageDO::getDeletedAt)
                .orderByAsc(TenantPackageDO::getId));
        List<PackageOptionVO> options = new ArrayList<>(packages.size());
        for (TenantPackageDO pkg : packages) {
            PackageOptionVO vo = new PackageOptionVO();
            vo.setId(pkg.getId());
            vo.setName(pkg.getName());
            options.add(vo);
        }
        return options;
    }

    // ---------- 内部实现 ----------

    private void requirePlatform() {
        Integer tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId != 0) {
            throw new BizException("仅平台账号可访问租户管理");
        }
    }

    private TenantDO getOrThrow(Integer id) {
        TenantDO tenant = id == null ? null : tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BizException("租户不存在");
        }
        return tenant;
    }

    private void assertNameFree(String name, Integer excludeId) {
        LambdaQueryWrapper<TenantDO> wrapper = new LambdaQueryWrapper<TenantDO>().eq(TenantDO::getName, name);
        if (excludeId != null) {
            wrapper.ne(TenantDO::getId, excludeId);
        }
        if (tenantMapper.selectCount(wrapper) > 0) {
            throw new BizException("该租户名称已存在");
        }
    }

    private void assertPhoneFree(String phone, Integer excludeId) {
        LambdaQueryWrapper<TenantDO> wrapper = new LambdaQueryWrapper<TenantDO>().eq(TenantDO::getContactPhone, phone);
        if (excludeId != null) {
            wrapper.ne(TenantDO::getId, excludeId);
        }
        if (tenantMapper.selectCount(wrapper) > 0) {
            throw new BizException("该手机号已被其他账号使用");
        }
    }

    /** 邮箱即登录账号（account.username），全平台唯一；excludeTenantId 排除该租户自己当前的管理员账号 */
    private void assertEmailFree(String email, Integer excludeTenantId) {
        LambdaQueryWrapper<AccountDO> wrapper = new LambdaQueryWrapper<AccountDO>().eq(AccountDO::getUsername, email);
        if (excludeTenantId != null) {
            wrapper.ne(AccountDO::getTenantId, excludeTenantId);
        }
        if (accountMapper.selectCount(wrapper) > 0) {
            throw new BizException("该邮箱已被其他账号使用");
        }
    }

    private TenantPackageDO requireEnabledPackage(Integer packageId) {
        TenantPackageDO pkg = packageMapper.selectById(packageId);
        if (pkg == null || pkg.getDeletedAt() != null || pkg.getStatus() == null || pkg.getStatus() != 1) {
            throw new BizException("所选套餐不可用，请重新选择");
        }
        return pkg;
    }

    private void requireFutureDate(LocalDate expireDate) {
        if (!expireDate.isAfter(LocalDate.now())) {
            throw new BizException("到期时间不可早于当前时间");
        }
    }

    private UserBasicDO findAdminUser(Integer tenantId) {
        return userBasicMapper.selectOne(new LambdaQueryWrapper<UserBasicDO>()
                .eq(UserBasicDO::getTenantId, tenantId)
                .eq(UserBasicDO::getRoleCode, ROLE_ADMIN)
                .last("LIMIT 1"));
    }

    private AccountDO findAccountByUserId(Integer userId) {
        return accountMapper.selectOne(new LambdaQueryWrapper<AccountDO>().eq(AccountDO::getUserId, userId));
    }

    private String resolveAdminEmail(Integer tenantId) {
        UserBasicDO adminUser = findAdminUser(tenantId);
        if (adminUser == null) {
            return "";
        }
        AccountDO account = findAccountByUserId(adminUser.getId());
        return account == null ? "" : account.getEmail();
    }

    /** 创建租户管理员账号：user_basic + account + account_local_auth 三张表，角色固定 ROLE_ADMIN */
    private void createAdminAccount(Integer tenantId, String contactName, String phone, String email, String tempPassword) {
        UserBasicDO user = new UserBasicDO();
        user.setTenantId(tenantId);
        user.setName(contactName);
        user.setType(1);
        user.setUsername(email);
        user.setPhone(phone);
        user.setEmail(email);
        user.setRoleCode(ROLE_ADMIN);
        user.setStatus(1);
        userBasicMapper.insert(user);

        AccountDO account = new AccountDO();
        account.setTenantId(tenantId);
        account.setUserId(user.getId());
        account.setUsername(email);
        account.setPhone(phone);
        account.setEmail(email);
        // 租户管理员在自己租户范围内拥有全部权限（ROLE_ADMIN 的既定语义），跟种子数据的 admin 账号一致
        account.setAdminFlag(1);
        account.setStatus(1);
        accountMapper.insert(account);

        AccountLocalAuthDO localAuth = new AccountLocalAuthDO();
        localAuth.setAccountId(account.getId());
        localAuth.setUsername(email);
        localAuth.setPassword(BCrypt.hashpw(tempPassword, BCrypt.gensalt()));
        localAuth.setSalt("");
        accountLocalAuthMapper.insert(localAuth);
    }

    /** 12 位随机密码：大写、小写、数字各至少 1 位，不含容易看混的字符（0/O/1/l/I）和特殊字符 */
    private static String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        sb.append(PWD_UPPER.charAt(RANDOM.nextInt(PWD_UPPER.length())));
        sb.append(PWD_LOWER.charAt(RANDOM.nextInt(PWD_LOWER.length())));
        sb.append(PWD_DIGIT.charAt(RANDOM.nextInt(PWD_DIGIT.length())));
        String all = PWD_UPPER + PWD_LOWER + PWD_DIGIT;
        for (int i = sb.length(); i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(all.charAt(RANDOM.nextInt(all.length())));
        }
        // 打乱顺序，避免固定位置总是"大写+小写+数字+随机..."这种可预测模式
        List<Character> chars = new ArrayList<>();
        for (int i = 0; i < sb.length(); i++) {
            chars.add(sb.charAt(i));
        }
        java.util.Collections.shuffle(chars, RANDOM);
        StringBuilder result = new StringBuilder(TEMP_PASSWORD_LENGTH);
        chars.forEach(result::append);
        return result.toString();
    }

    private List<TenantVO> toVoList(List<TenantDO> tenants) {
        if (tenants.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Integer> packageIds = tenants.stream().map(TenantDO::getPackageId).collect(Collectors.toSet());
        Map<Integer, String> packageNames = new HashMap<>();
        if (!packageIds.isEmpty()) {
            List<TenantPackageDO> packages = packageMapper.selectBatchIds(packageIds);
            for (TenantPackageDO pkg : packages) {
                packageNames.put(pkg.getId(), pkg.getName());
            }
        }

        List<Integer> tenantIds = tenants.stream().map(TenantDO::getId).toList();
        Map<Integer, UserBasicDO> adminByTenant = new HashMap<>();
        if (!tenantIds.isEmpty()) {
            List<UserBasicDO> admins = userBasicMapper.selectList(new LambdaQueryWrapper<UserBasicDO>()
                    .in(UserBasicDO::getTenantId, tenantIds)
                    .eq(UserBasicDO::getRoleCode, ROLE_ADMIN));
            for (UserBasicDO admin : admins) {
                adminByTenant.put(admin.getTenantId(), admin);
            }
        }
        Map<Integer, String> emailByTenant = new HashMap<>();
        if (!adminByTenant.isEmpty()) {
            List<Integer> userIds = adminByTenant.values().stream().map(UserBasicDO::getId).toList();
            List<AccountDO> accounts = accountMapper.selectList(new LambdaQueryWrapper<AccountDO>().in(AccountDO::getUserId, userIds));
            Map<Integer, String> emailByUserId = new HashMap<>();
            for (AccountDO account : accounts) {
                emailByUserId.put(account.getUserId(), account.getEmail());
            }
            adminByTenant.forEach((tenantId, admin) -> emailByTenant.put(tenantId, emailByUserId.get(admin.getId())));
        }

        List<TenantVO> result = new ArrayList<>(tenants.size());
        for (TenantDO tenant : tenants) {
            result.add(toVO(tenant, packageNames.get(tenant.getPackageId()), emailByTenant.get(tenant.getId())));
        }
        return result;
    }

    private static TenantVO toVO(TenantDO tenant, String packageName, String contactEmail) {
        TenantVO vo = new TenantVO();
        vo.setId(tenant.getId());
        vo.setCode(tenant.getCode());
        vo.setName(tenant.getName());
        vo.setPackageId(tenant.getPackageId());
        vo.setPackageName(packageName == null ? "" : packageName);
        vo.setContactName(tenant.getContactName());
        vo.setContactPhone(tenant.getContactPhone());
        vo.setContactEmail(contactEmail == null ? "" : contactEmail);
        vo.setStatus(tenant.getStatus());
        vo.setExpireTime(tenant.getExpireTime());
        vo.setRemark(tenant.getRemark());
        vo.setCreateBy(tenant.getCreateBy());
        vo.setCreateTime(tenant.getCreateTime());
        vo.setUpdateBy(tenant.getUpdateBy());
        vo.setUpdateTime(tenant.getUpdateTime());
        return vo;
    }
}

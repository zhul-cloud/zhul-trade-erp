package com.zhul.erp.modules.system.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.dto.CreateUserRequest;
import com.zhul.erp.modules.system.dto.UpdateUserRequest;
import com.zhul.erp.modules.system.dto.UserPageQuery;
import com.zhul.erp.modules.system.dto.UserStatsVO;
import com.zhul.erp.modules.system.dto.UserVO;
import com.zhul.erp.modules.system.entity.AccountDO;
import com.zhul.erp.modules.system.entity.AccountLocalAuthDO;
import com.zhul.erp.modules.system.entity.DepartmentDO;
import com.zhul.erp.modules.system.entity.PositionDO;
import com.zhul.erp.modules.system.entity.RoleDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.AccountLocalAuthMapper;
import com.zhul.erp.modules.system.repository.AccountMapper;
import com.zhul.erp.modules.system.repository.DepartmentMapper;
import com.zhul.erp.modules.system.repository.PositionMapper;
import com.zhul.erp.modules.system.repository.RoleMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserBasicMapper userBasicMapper;
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final RoleMapper roleMapper;
    private final AccountMapper accountMapper;
    private final AccountLocalAuthMapper accountLocalAuthMapper;

    @Override
    public PageResult<UserVO> listUsers(UserPageQuery query) {
        LambdaQueryWrapper<UserBasicDO> wrapper = new LambdaQueryWrapper<>();

        Integer tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            wrapper.eq(UserBasicDO::getTenantId, tenantId);
        }
        if (StringUtils.hasText(query.getName())) {
            wrapper.like(UserBasicDO::getName, query.getName());
        }
        if (StringUtils.hasText(query.getPhone())) {
            wrapper.eq(UserBasicDO::getPhone, query.getPhone());
        }
        if (query.getDeptId() != null) {
            wrapper.eq(UserBasicDO::getDeptId, query.getDeptId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(UserBasicDO::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(UserBasicDO::getUpdateTime);

        Page<UserBasicDO> pageParam = new Page<>(query.getPage(), query.getPageSize());
        Page<UserBasicDO> pageResult = userBasicMapper.selectPage(pageParam, wrapper);

        List<UserVO> voList = new ArrayList<>(pageResult.getRecords().size());
        for (UserBasicDO user : pageResult.getRecords()) {
            UserVO vo = new UserVO();
            vo.setId(user.getId());
            vo.setName(user.getName());
            vo.setUsername(user.getUsername());
            vo.setPhone(user.getPhone());
            vo.setEmail(user.getEmail());
            vo.setNickname(user.getNickname());
            vo.setAvatarUrl(user.getAvatarUrl());
            vo.setDeptId(user.getDeptId());
            vo.setPositionId(user.getPositionId());
            vo.setRoleCode(user.getRoleCode());
            vo.setStatus(user.getStatus());
            vo.setCreateBy(user.getCreateBy());
            vo.setCreateTime(user.getCreateTime());
            vo.setUpdateBy(user.getUpdateBy());
            vo.setUpdateTime(user.getUpdateTime());

            if (user.getDeptId() != null) {
                DepartmentDO dept = departmentMapper.selectById(user.getDeptId());
                if (dept != null) {
                    vo.setDeptName(dept.getName());
                }
            }
            if (user.getPositionId() != null) {
                PositionDO position = positionMapper.selectById(user.getPositionId());
                if (position != null) {
                    vo.setPositionName(position.getName());
                }
            }
            if (StringUtils.hasText(user.getRoleCode())) {
                RoleDO role = roleMapper.selectOne(
                        new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getCode, user.getRoleCode()));
                if (role != null) {
                    vo.setRoleName(role.getName());
                }
            }
            voList.add(vo);
        }

        return PageResult.of(pageResult.getTotal(), voList);
    }

    @Override
    public UserStatsVO getUserStats() {
        Integer tenantId = TenantContext.getTenantId();

        LambdaQueryWrapper<UserBasicDO> base = new LambdaQueryWrapper<>();
        if (tenantId != null) {
            base.eq(UserBasicDO::getTenantId, tenantId);
        }

        LambdaQueryWrapper<UserBasicDO> enabledWrapper = base.clone().eq(UserBasicDO::getStatus, 1);
        LambdaQueryWrapper<UserBasicDO> disabledWrapper = base.clone().eq(UserBasicDO::getStatus, 0);
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        LambdaQueryWrapper<UserBasicDO> newWrapper = base.clone().ge(UserBasicDO::getCreateTime, monthStart);

        UserStatsVO stats = new UserStatsVO();
        stats.setTotal(Math.toIntExact(userBasicMapper.selectCount(base)));
        stats.setEnabled(Math.toIntExact(userBasicMapper.selectCount(enabledWrapper)));
        stats.setDisabled(Math.toIntExact(userBasicMapper.selectCount(disabledWrapper)));
        stats.setNewThisMonth(Math.toIntExact(userBasicMapper.selectCount(newWrapper)));
        return stats;
    }

    @Override
    public void updateUserStatus(Integer userId, Integer status) {
        LambdaUpdateWrapper<UserBasicDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserBasicDO::getId, userId)
               .set(UserBasicDO::getStatus, status);
        userBasicMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createUser(CreateUserRequest request) {
        Long count = accountMapper.selectCount(
                new LambdaQueryWrapper<AccountDO>().eq(AccountDO::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BizException("用户名已存在");
        }

        Integer tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 0;

        UserBasicDO user = new UserBasicDO();
        user.setTenantId(tenantId);
        user.setName(request.getName());
        user.setUsername(request.getUsername());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setNickname(request.getNickname());
        user.setDeptId(request.getDeptId());
        user.setPositionId(request.getPositionId());
        user.setRoleCode(request.getRoleCode());
        user.setStatus(request.getStatus());
        userBasicMapper.insert(user);

        AccountDO account = new AccountDO();
        account.setTenantId(tenantId);
        account.setUserId(user.getId());
        account.setUsername(request.getUsername());
        account.setPhone(request.getPhone());
        account.setEmail(request.getEmail());
        account.setAdminFlag(0);
        account.setStatus(request.getStatus());
        accountMapper.insert(account);

        AccountLocalAuthDO localAuth = new AccountLocalAuthDO();
        localAuth.setAccountId(account.getId());
        localAuth.setUsername(request.getUsername());
        localAuth.setPassword(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
        localAuth.setSalt("");
        accountLocalAuthMapper.insert(localAuth);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Integer userId, UpdateUserRequest request) {
        LambdaUpdateWrapper<UserBasicDO> wrapper = new LambdaUpdateWrapper<UserBasicDO>()
                .eq(UserBasicDO::getId, userId);
        if (StringUtils.hasText(request.getName())) {
            wrapper.set(UserBasicDO::getName, request.getName());
        }
        if (StringUtils.hasText(request.getPhone())) {
            wrapper.set(UserBasicDO::getPhone, request.getPhone());
        }
        if (StringUtils.hasText(request.getEmail())) {
            wrapper.set(UserBasicDO::getEmail, request.getEmail());
        }
        if (StringUtils.hasText(request.getNickname())) {
            wrapper.set(UserBasicDO::getNickname, request.getNickname());
        }
        if (request.getDeptId() != null) {
            wrapper.set(UserBasicDO::getDeptId, request.getDeptId());
        }
        if (request.getPositionId() != null) {
            wrapper.set(UserBasicDO::getPositionId, request.getPositionId());
        }
        if (StringUtils.hasText(request.getRoleCode())) {
            wrapper.set(UserBasicDO::getRoleCode, request.getRoleCode());
        }
        if (request.getStatus() != null) {
            wrapper.set(UserBasicDO::getStatus, request.getStatus());
        }
        userBasicMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Integer userId) {
        LambdaUpdateWrapper<UserBasicDO> userWrapper = new LambdaUpdateWrapper<UserBasicDO>()
                .eq(UserBasicDO::getId, userId)
                .set(UserBasicDO::getStatus, 0);
        userBasicMapper.update(null, userWrapper);

        LambdaUpdateWrapper<AccountDO> accountWrapper = new LambdaUpdateWrapper<AccountDO>()
                .eq(AccountDO::getUserId, userId)
                .set(AccountDO::getStatus, 0);
        accountMapper.update(null, accountWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Integer userId, String newPassword) {
        AccountDO account = accountMapper.selectOne(
                new LambdaQueryWrapper<AccountDO>().eq(AccountDO::getUserId, userId));
        if (account == null) {
            throw new BizException("用户不存在");
        }
        LambdaUpdateWrapper<AccountLocalAuthDO> wrapper = new LambdaUpdateWrapper<AccountLocalAuthDO>()
                .eq(AccountLocalAuthDO::getAccountId, account.getId())
                .set(AccountLocalAuthDO::getPassword, BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        accountLocalAuthMapper.update(null, wrapper);
    }
}

package com.zhul.erp.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.dto.RoleDeleteCheckVO;
import com.zhul.erp.modules.system.dto.RoleStatsVO;
import com.zhul.erp.modules.system.dto.RoleVO;
import com.zhul.erp.modules.system.dto.SaveRoleRequest;
import com.zhul.erp.modules.system.entity.DepartmentDO;
import com.zhul.erp.modules.system.entity.RoleDO;
import com.zhul.erp.modules.system.entity.RoleOrgDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.DepartmentMapper;
import com.zhul.erp.modules.system.repository.RoleMapper;
import com.zhul.erp.modules.system.repository.RoleOrgMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private static final int MAX_SAMPLE_USER_NAMES = 4;

    private final RoleMapper roleMapper;
    private final UserBasicMapper userBasicMapper;
    private final RoleOrgMapper roleOrgMapper;
    private final DepartmentMapper departmentMapper;

    @Override
    public PageResult<RoleVO> listRoles(Integer page, Integer pageSize, String name, String code, Integer status) {
        LambdaQueryWrapper<RoleDO> wrapper = buildWrapper(name, code, status);
        wrapper.orderByDesc(RoleDO::getUpdateTime);

        Page<RoleDO> pageParam = new Page<>(page, pageSize);
        Page<RoleDO> pageResult = roleMapper.selectPage(pageParam, wrapper);

        List<RoleVO> voList = toVoList(pageResult.getRecords());
        return PageResult.of(pageResult.getTotal(), voList);
    }

    @Override
    public List<RoleVO> allRoles() {
        // 只给下拉选择用：已禁用的角色（如下线的内置角色）不应该再被选中
        LambdaQueryWrapper<RoleDO> wrapper = buildWrapper(null, null, 1);
        List<RoleDO> roles = roleMapper.selectList(wrapper);
        return toVoList(roles);
    }

    @Override
    public RoleStatsVO getStats() {
        Integer tenantId = TenantContext.getTenantId();

        long total = roleMapper.selectCount(tenantWrapper(tenantId));
        long enabledCount = roleMapper.selectCount(tenantWrapper(tenantId).eq(RoleDO::getStatus, 1));
        long builtInCount = roleMapper.selectCount(tenantWrapper(tenantId).eq(RoleDO::getIsBuiltIn, 1));
        long customCount = total - builtInCount;

        List<UserBasicDO> users = userBasicMapper.selectList(
                new LambdaQueryWrapper<UserBasicDO>().ne(UserBasicDO::getDeptId, 0));
        long deptCoverage = users.stream().map(UserBasicDO::getDeptId).distinct().count();

        RoleStatsVO vo = new RoleStatsVO();
        vo.setTotal(total);
        vo.setEnabledCount(enabledCount);
        vo.setBuiltInCount(builtInCount);
        vo.setCustomCount(customCount);
        vo.setDeptCoverage(deptCoverage);
        vo.setEnabledRate(total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(enabledCount)
                        .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP));
        return vo;
    }

    private LambdaQueryWrapper<RoleDO> tenantWrapper(Integer tenantId) {
        LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) {
            // 内置角色 tenant_id=0 为平台级共享，所有租户可见
            wrapper.and(w -> w.eq(RoleDO::getTenantId, tenantId).or().eq(RoleDO::getTenantId, 0));
        }
        return wrapper;
    }

    private LambdaQueryWrapper<RoleDO> buildWrapper(String name, String code, Integer status) {
        LambdaQueryWrapper<RoleDO> wrapper = tenantWrapper(TenantContext.getTenantId());
        if (StringUtils.hasText(name)) {
            wrapper.like(RoleDO::getName, name);
        }
        if (StringUtils.hasText(code)) {
            wrapper.like(RoleDO::getCode, code);
        }
        if (status != null) {
            wrapper.eq(RoleDO::getStatus, status);
        }
        return wrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createRole(SaveRoleRequest request) {
        Integer tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 0;

        RoleDO role = new RoleDO();
        role.setTenantId(tenantId);
        role.setCode("R" + (System.currentTimeMillis() % 100000000));
        role.setName(request.getName());
        role.setPermissionScope(request.getPermissionScope() != null ? request.getPermissionScope() : 0);
        role.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        role.setIsBuiltIn(0);
        role.setRemark(request.getRemark());
        roleMapper.insert(role);

        assignRoleDepts(role.getCode(), role.getPermissionScope(), request.getDeptIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Integer roleId, SaveRoleRequest request) {
        RoleDO role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException("角色不存在");
        }

        LambdaUpdateWrapper<RoleDO> wrapper = new LambdaUpdateWrapper<RoleDO>()
                .eq(RoleDO::getId, roleId);
        if (StringUtils.hasText(request.getName())) {
            wrapper.set(RoleDO::getName, request.getName());
        }
        if (request.getPermissionScope() != null) {
            wrapper.set(RoleDO::getPermissionScope, request.getPermissionScope());
        }
        if (request.getStatus() != null) {
            wrapper.set(RoleDO::getStatus, request.getStatus());
        }
        if (request.getRemark() != null) {
            wrapper.set(RoleDO::getRemark, request.getRemark());
        }
        roleMapper.update(new RoleDO(), wrapper);

        Integer effectiveScope = request.getPermissionScope() != null
                ? request.getPermissionScope() : role.getPermissionScope();
        assignRoleDepts(role.getCode(), effectiveScope, request.getDeptIds());
    }

    private void assignRoleDepts(String roleCode, Integer permissionScope, List<Integer> deptIds) {
        roleOrgMapper.delete(new LambdaQueryWrapper<RoleOrgDO>().eq(RoleOrgDO::getRoleCode, roleCode));
        if (permissionScope == null || permissionScope != 2 || deptIds == null || deptIds.isEmpty()) {
            return;
        }
        for (Integer deptId : deptIds) {
            DepartmentDO dept = departmentMapper.selectById(deptId);
            if (dept == null) continue;
            RoleOrgDO ro = new RoleOrgDO();
            ro.setRoleCode(roleCode);
            ro.setOrgCode(dept.getCode());
            roleOrgMapper.insert(ro);
        }
    }

    @Override
    public RoleDeleteCheckVO checkDeletable(Integer roleId) {
        RoleDO role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException("角色不存在");
        }

        RoleDeleteCheckVO vo = new RoleDeleteCheckVO();
        if (role.getIsBuiltIn() != null && role.getIsBuiltIn() == 1) {
            vo.setBlocked(true);
            vo.setBuiltIn(true);
            vo.setUserCount(0);
            vo.setSampleUserNames(Collections.emptyList());
            return vo;
        }

        List<UserBasicDO> users = userBasicMapper.selectList(
                new LambdaQueryWrapper<UserBasicDO>().eq(UserBasicDO::getRoleCode, role.getCode()));
        vo.setBuiltIn(false);
        vo.setUserCount(users.size());
        vo.setBlocked(!users.isEmpty());
        vo.setSampleUserNames(users.stream()
                .map(UserBasicDO::getName)
                .limit(MAX_SAMPLE_USER_NAMES)
                .collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Integer roleId) {
        RoleDO role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        if (role.getIsBuiltIn() != null && role.getIsBuiltIn() == 1) {
            throw new BizException("内置角色不可删除");
        }
        Long usedCount = userBasicMapper.selectCount(
                new LambdaQueryWrapper<UserBasicDO>().eq(UserBasicDO::getRoleCode, role.getCode()));
        if (usedCount > 0) {
            throw new BizException("该角色已有用户使用，无法删除");
        }
        roleOrgMapper.delete(new LambdaQueryWrapper<RoleOrgDO>().eq(RoleOrgDO::getRoleCode, role.getCode()));
        roleMapper.deleteById(roleId);
    }

    @Override
    public List<Integer> getRoleDeptIds(String roleCode) {
        List<String> orgCodes = roleOrgMapper.selectList(
                        new LambdaQueryWrapper<RoleOrgDO>().eq(RoleOrgDO::getRoleCode, roleCode))
                .stream().map(RoleOrgDO::getOrgCode).collect(Collectors.toList());
        if (orgCodes.isEmpty()) {
            return new ArrayList<>();
        }
        return departmentMapper.selectList(
                        new LambdaQueryWrapper<DepartmentDO>().in(DepartmentDO::getCode, orgCodes))
                .stream().map(DepartmentDO::getId).collect(Collectors.toList());
    }

    private List<RoleVO> toVoList(List<RoleDO> roles) {
        if (roles.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> codes = roles.stream().map(RoleDO::getCode).collect(Collectors.toList());
        Map<String, Long> userCountByRoleCode = userBasicMapper.selectList(
                        new LambdaQueryWrapper<UserBasicDO>().in(UserBasicDO::getRoleCode, codes))
                .stream()
                .collect(Collectors.groupingBy(UserBasicDO::getRoleCode, Collectors.counting()));

        List<RoleVO> voList = new ArrayList<>(roles.size());
        for (RoleDO role : roles) {
            RoleVO vo = new RoleVO();
            vo.setId(role.getId());
            vo.setCode(role.getCode());
            vo.setName(role.getName());
            vo.setPermissionScope(role.getPermissionScope());
            vo.setStatus(role.getStatus());
            vo.setIsBuiltIn(role.getIsBuiltIn());
            vo.setUserCount(userCountByRoleCode.getOrDefault(role.getCode(), 0L).intValue());
            vo.setRemark(role.getRemark());
            vo.setCreateBy(role.getCreateBy());
            vo.setCreateTime(role.getCreateTime());
            vo.setUpdateBy(role.getUpdateBy());
            vo.setUpdateTime(role.getUpdateTime());
            voList.add(vo);
        }
        return voList;
    }
}

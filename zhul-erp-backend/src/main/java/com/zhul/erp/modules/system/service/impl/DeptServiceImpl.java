package com.zhul.erp.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.dto.DeptDeleteCheckVO;
import com.zhul.erp.modules.system.dto.DeptVO;
import com.zhul.erp.modules.system.dto.SaveDeptRequest;
import com.zhul.erp.modules.system.entity.DepartmentDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.DepartmentMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.DeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {

    private static final int MAX_DEPTH = 10;
    private static final int MAX_SAMPLE_USER_NAMES = 4;

    private final DepartmentMapper departmentMapper;
    private final UserBasicMapper userBasicMapper;

    @Override
    public List<DeptVO> listAll() {
        List<DepartmentDO> list = departmentMapper.selectList(activeWrapper().orderByAsc(DepartmentDO::getSort));
        return toVoList(list);
    }

    @Override
    public List<DeptVO> getDeptTree() {
        List<DepartmentDO> list = departmentMapper.selectList(activeWrapper().orderByAsc(DepartmentDO::getSort));
        List<DeptVO> voList = toVoList(list);
        return buildTree(voList, 0);
    }

    private List<DeptVO> buildTree(List<DeptVO> all, int pid) {
        return all.stream()
                .filter(d -> d.getPid() == pid)
                .peek(d -> {
                    List<DeptVO> children = buildTree(all, d.getId());
                    if (!children.isEmpty()) {
                        d.setChildren(children);
                    }
                })
                .collect(Collectors.toList());
    }

    private LambdaQueryWrapper<DepartmentDO> activeWrapper() {
        LambdaQueryWrapper<DepartmentDO> wrapper = new LambdaQueryWrapper<DepartmentDO>()
                .isNull(DepartmentDO::getDeletedAt);
        Integer tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            wrapper.eq(DepartmentDO::getTenantId, tenantId);
        }
        return wrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createDept(SaveDeptRequest req) {
        Integer pid = req.getPid() != null ? req.getPid() : 0;
        DepartmentDO parent = null;
        if (pid != 0) {
            parent = departmentMapper.selectById(pid);
            if (parent == null || parent.getDeletedAt() != null) {
                throw new BizException("上级部门不存在");
            }
            if (parent.getStatus() == 0) {
                throw new BizException("上级部门已禁用，不能新增子部门");
            }
        }
        int level = parent != null ? parent.getLevel() + 1 : 1;
        if (level > MAX_DEPTH) {
            throw new BizException("已达最大层级（" + MAX_DEPTH + "级）限制");
        }
        checkNameDuplicate(pid, req.getName(), null);

        DepartmentDO dept = new DepartmentDO();
        dept.setTenantId(TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 0);
        dept.setPid(pid);
        dept.setName(req.getName());
        dept.setAllName(req.getAllName());
        dept.setLeaderId(req.getLeaderId());
        dept.setPhone(req.getPhone());
        dept.setRemark(req.getRemark());
        dept.setLevel(level);
        dept.setSort(req.getSort());
        dept.setStatus(req.getStatus());
        departmentMapper.insert(dept);
        dept.setCode("DP" + dept.getId());
        departmentMapper.updateById(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDept(Integer id, SaveDeptRequest req) {
        DepartmentDO dept = departmentMapper.selectById(id);
        if (dept == null || dept.getDeletedAt() != null) {
            throw new BizException("部门不存在");
        }
        Integer newPid = req.getPid() != null ? req.getPid() : 0;
        int level = dept.getLevel();
        if (!newPid.equals(dept.getPid())) {
            if (newPid.equals(id)) {
                throw new BizException("上级部门不能设置为自身");
            }
            DepartmentDO newParent = null;
            if (newPid != 0) {
                newParent = departmentMapper.selectById(newPid);
                if (newParent == null || newParent.getDeletedAt() != null) {
                    throw new BizException("上级部门不存在");
                }
                if (isDescendant(newPid, id)) {
                    throw new BizException("不能将子孙节点设置为上级部门");
                }
            }
            level = newParent != null ? newParent.getLevel() + 1 : 1;
            if (level > MAX_DEPTH) {
                throw new BizException("已达最大层级（" + MAX_DEPTH + "级）限制");
            }
        }
        checkNameDuplicate(newPid, req.getName(), id);

        dept.setPid(newPid);
        dept.setName(req.getName());
        dept.setAllName(req.getAllName());
        dept.setLeaderId(req.getLeaderId());
        dept.setPhone(req.getPhone());
        dept.setRemark(req.getRemark());
        dept.setLevel(level);
        dept.setSort(req.getSort());
        dept.setStatus(req.getStatus());
        departmentMapper.updateById(dept);
    }

    /** 判断 candidateId 是否是 ofId 的子孙节点（含自身以外的整条子树） */
    private boolean isDescendant(Integer candidateId, Integer ofId) {
        List<DepartmentDO> all = departmentMapper.selectList(activeWrapper());
        List<Integer> descendantIds = new ArrayList<>();
        collectDescendants(all, ofId, descendantIds);
        return descendantIds.contains(candidateId);
    }

    private void collectDescendants(List<DepartmentDO> all, Integer parentId, List<Integer> result) {
        for (DepartmentDO d : all) {
            if (d.getPid().equals(parentId)) {
                result.add(d.getId());
                collectDescendants(all, d.getId(), result);
            }
        }
    }

    private void checkNameDuplicate(Integer pid, String name, Integer excludeId) {
        LambdaQueryWrapper<DepartmentDO> wrapper = activeWrapper()
                .eq(DepartmentDO::getPid, pid)
                .eq(DepartmentDO::getName, name);
        if (excludeId != null) {
            wrapper.ne(DepartmentDO::getId, excludeId);
        }
        if (departmentMapper.selectCount(wrapper) > 0) {
            throw new BizException("同级部门名称已存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Integer id, Integer status) {
        DepartmentDO dept = departmentMapper.selectById(id);
        if (dept == null || dept.getDeletedAt() != null) {
            throw new BizException("部门不存在");
        }
        dept.setStatus(status);
        departmentMapper.updateById(dept);
    }

    @Override
    public DeptDeleteCheckVO checkDeletable(Integer id) {
        DeptDeleteCheckVO vo = new DeptDeleteCheckVO();
        long childCount = departmentMapper.selectCount(activeWrapper().eq(DepartmentDO::getPid, id));
        List<UserBasicDO> users = userBasicMapper.selectList(
                new LambdaQueryWrapper<UserBasicDO>().eq(UserBasicDO::getDeptId, id));
        vo.setChildCount((int) childCount);
        vo.setUserCount(users.size());
        vo.setBlocked(childCount > 0 || !users.isEmpty());
        vo.setSampleUserNames(users.stream()
                .map(UserBasicDO::getName)
                .limit(MAX_SAMPLE_USER_NAMES)
                .collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDept(Integer id) {
        DepartmentDO dept = departmentMapper.selectById(id);
        if (dept == null || dept.getDeletedAt() != null) {
            throw new BizException("部门不存在");
        }
        long childCount = departmentMapper.selectCount(activeWrapper().eq(DepartmentDO::getPid, id));
        if (childCount > 0) {
            throw new BizException("该部门下存在子部门，请先删除或迁移子部门后重试");
        }
        long userCount = userBasicMapper.selectCount(
                new LambdaQueryWrapper<UserBasicDO>().eq(UserBasicDO::getDeptId, id));
        if (userCount > 0) {
            throw new BizException("该部门已分配 " + userCount + " 名用户，请先移除关联用户后重试");
        }
        dept.setDeletedAt(LocalDateTime.now());
        departmentMapper.updateById(dept);
    }

    private List<DeptVO> toVoList(List<DepartmentDO> list) {
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> leaderIds = list.stream()
                .map(DepartmentDO::getLeaderId)
                .filter(lid -> lid != null && lid != 0)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, String> leaderNames = leaderIds.isEmpty() ? Collections.emptyMap()
                : userBasicMapper.selectBatchIds(leaderIds).stream()
                        .collect(Collectors.toMap(UserBasicDO::getId, UserBasicDO::getName));

        List<DeptVO> result = new ArrayList<>(list.size());
        for (DepartmentDO dept : list) {
            DeptVO vo = new DeptVO();
            vo.setId(dept.getId());
            vo.setPid(dept.getPid());
            vo.setCode(dept.getCode());
            vo.setName(dept.getName());
            vo.setAllName(dept.getAllName());
            vo.setLeaderId(dept.getLeaderId());
            vo.setLeaderName(leaderNames.get(dept.getLeaderId()));
            vo.setPhone(dept.getPhone());
            vo.setLevel(dept.getLevel());
            vo.setSort(dept.getSort());
            vo.setStatus(dept.getStatus());
            vo.setRemark(dept.getRemark());
            vo.setCreateTime(dept.getCreateTime());
            vo.setUpdateTime(dept.getUpdateTime());
            result.add(vo);
        }
        return result;
    }
}

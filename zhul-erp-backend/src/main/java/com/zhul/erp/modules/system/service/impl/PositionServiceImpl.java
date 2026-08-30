package com.zhul.erp.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.dto.PositionDeleteCheckVO;
import com.zhul.erp.modules.system.dto.PositionVO;
import com.zhul.erp.modules.system.dto.SavePositionRequest;
import com.zhul.erp.modules.system.entity.PositionDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.PositionMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    private static final int MAX_SAMPLE_USER_NAMES = 4;

    private final PositionMapper positionMapper;
    private final UserBasicMapper userBasicMapper;

    @Override
    public PageResult<PositionVO> listAll(int page, int pageSize, String name, Integer status) {
        LambdaQueryWrapper<PositionDO> wrapper = activeWrapper();
        if (StringUtils.hasText(name)) {
            wrapper.like(PositionDO::getName, name);
        }
        if (status != null) {
            wrapper.eq(PositionDO::getStatus, status);
        }
        wrapper.orderByAsc(PositionDO::getSort).orderByAsc(PositionDO::getCreateTime);

        Page<PositionDO> pageParam = new Page<>(page, pageSize);
        Page<PositionDO> result = positionMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getTotal(), toVoList(result.getRecords()));
    }

    @Override
    public List<PositionVO> allPositions() {
        List<PositionDO> list = positionMapper.selectList(
                activeWrapper().orderByAsc(PositionDO::getSort).orderByAsc(PositionDO::getCreateTime));
        return toVoList(list);
    }

    private LambdaQueryWrapper<PositionDO> activeWrapper() {
        LambdaQueryWrapper<PositionDO> wrapper = new LambdaQueryWrapper<PositionDO>()
                .isNull(PositionDO::getDeletedAt);
        Integer tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            wrapper.eq(PositionDO::getTenantId, tenantId);
        }
        return wrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(SavePositionRequest req) {
        checkCodeDuplicate(req.getCode(), null);
        checkNameDuplicate(req.getName(), null);

        PositionDO position = new PositionDO();
        position.setTenantId(TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 0);
        position.setCode(req.getCode());
        position.setName(req.getName());
        position.setSort(req.getSort());
        position.setStatus(req.getStatus());
        position.setRemark(req.getRemark());
        positionMapper.insert(position);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Integer id, SavePositionRequest req) {
        PositionDO position = positionMapper.selectById(id);
        if (position == null || position.getDeletedAt() != null) {
            throw new BizException("岗位不存在");
        }
        checkNameDuplicate(req.getName(), id);

        // 岗位编码创建后不可修改
        position.setName(req.getName());
        position.setSort(req.getSort());
        position.setStatus(req.getStatus());
        position.setRemark(req.getRemark());
        positionMapper.updateById(position);
    }

    private void checkCodeDuplicate(String code, Integer excludeId) {
        LambdaQueryWrapper<PositionDO> wrapper = activeWrapper().eq(PositionDO::getCode, code);
        if (excludeId != null) {
            wrapper.ne(PositionDO::getId, excludeId);
        }
        if (positionMapper.selectCount(wrapper) > 0) {
            throw new BizException("岗位编码已存在");
        }
    }

    private void checkNameDuplicate(String name, Integer excludeId) {
        LambdaQueryWrapper<PositionDO> wrapper = activeWrapper().eq(PositionDO::getName, name);
        if (excludeId != null) {
            wrapper.ne(PositionDO::getId, excludeId);
        }
        if (positionMapper.selectCount(wrapper) > 0) {
            throw new BizException("岗位名称已存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Integer id, Integer status) {
        PositionDO position = positionMapper.selectById(id);
        if (position == null || position.getDeletedAt() != null) {
            throw new BizException("岗位不存在");
        }
        position.setStatus(status);
        positionMapper.updateById(position);
    }

    @Override
    public PositionDeleteCheckVO checkDeletable(Integer id) {
        List<UserBasicDO> users = userBasicMapper.selectList(
                new LambdaQueryWrapper<UserBasicDO>()
                        .eq(UserBasicDO::getPositionId, id));
        PositionDeleteCheckVO vo = new PositionDeleteCheckVO();
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
    public void delete(Integer id) {
        PositionDO position = positionMapper.selectById(id);
        if (position == null || position.getDeletedAt() != null) {
            throw new BizException("岗位不存在");
        }
        long userCount = userBasicMapper.selectCount(
                new LambdaQueryWrapper<UserBasicDO>()
                        .eq(UserBasicDO::getPositionId, id));
        if (userCount > 0) {
            throw new BizException("该岗位已分配 " + userCount + " 名用户，请先解除关联后再删除");
        }
        position.setDeletedAt(LocalDateTime.now());
        positionMapper.updateById(position);
    }

    private List<PositionVO> toVoList(List<PositionDO> list) {
        List<PositionVO> result = new ArrayList<>(list.size());
        for (PositionDO position : list) {
            PositionVO vo = new PositionVO();
            vo.setId(position.getId());
            vo.setCode(position.getCode());
            vo.setName(position.getName());
            vo.setSort(position.getSort());
            vo.setStatus(position.getStatus());
            vo.setRemark(position.getRemark());
            vo.setCreateTime(position.getCreateTime());
            vo.setUpdateTime(position.getUpdateTime());
            result.add(vo);
        }
        return result;
    }
}

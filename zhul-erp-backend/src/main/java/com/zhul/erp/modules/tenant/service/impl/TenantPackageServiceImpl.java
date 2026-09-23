package com.zhul.erp.modules.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.tenant.dto.PackageDeleteCheckVO;
import com.zhul.erp.modules.tenant.dto.SaveTenantPackageRequest;
import com.zhul.erp.modules.tenant.dto.TenantPackageQuery;
import com.zhul.erp.modules.tenant.dto.TenantPackageVO;
import com.zhul.erp.modules.tenant.entity.TenantDO;
import com.zhul.erp.modules.tenant.entity.TenantPackageDO;
import com.zhul.erp.modules.tenant.repository.PackageTenantCount;
import com.zhul.erp.modules.tenant.repository.TenantMapper;
import com.zhul.erp.modules.tenant.repository.TenantPackageMapper;
import com.zhul.erp.modules.tenant.service.TenantPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户套餐管理：平台超级管理员专用模块，读写都要求平台账号（tenantId=0），
 * 见 {@link #requirePlatform()}，跟 {@link TenantServiceImpl} 同理。
 */
@Service
@RequiredArgsConstructor
public class TenantPackageServiceImpl implements TenantPackageService {

    private final TenantPackageMapper packageMapper;
    private final TenantMapper tenantMapper;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<TenantPackageVO> page(TenantPackageQuery query) {
        requirePlatform();
        LambdaQueryWrapper<TenantPackageDO> wrapper = new LambdaQueryWrapper<TenantPackageDO>()
                .isNull(TenantPackageDO::getDeletedAt);
        if (StringUtils.hasText(query.getName())) {
            wrapper.like(TenantPackageDO::getName, query.getName());
        }
        if (query.getStatus() != null) {
            wrapper.eq(TenantPackageDO::getStatus, query.getStatus());
        }
        // 与租户列表一致：按创建时间降序，最新创建排最前
        wrapper.orderByDesc(TenantPackageDO::getCreateTime);

        Page<TenantPackageDO> pageParam = new Page<>(query.getPage(), query.getPageSize());
        Page<TenantPackageDO> pageResult = packageMapper.selectPage(pageParam, wrapper);
        return PageResult.of(pageResult.getTotal(), toVoList(pageResult.getRecords()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantPackageVO create(SaveTenantPackageRequest req) {
        requirePlatform();
        assertNameFree(req.getName(), null);

        TenantPackageDO pkg = new TenantPackageDO();
        pkg.setName(req.getName());
        pkg.setRemark(req.getDescription() == null ? "" : req.getDescription());
        pkg.setMenuIds(writeMenuIds(req.getMenuIds()));
        pkg.setStatus(1);
        packageMapper.insert(pkg);
        return toVO(pkg, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantPackageVO update(Integer id, SaveTenantPackageRequest req) {
        requirePlatform();
        getOrThrow(id);
        assertNameFree(req.getName(), id);

        TenantPackageDO change = new TenantPackageDO();
        change.setId(id);
        change.setName(req.getName());
        change.setRemark(req.getDescription() == null ? "" : req.getDescription());
        change.setMenuIds(writeMenuIds(req.getMenuIds()));
        packageMapper.updateById(change);
        return toVO(getOrThrow(id), tenantCountOf(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Integer id, Integer status) {
        requirePlatform();
        getOrThrow(id);
        TenantPackageDO change = new TenantPackageDO();
        change.setId(id);
        change.setStatus(status);
        packageMapper.updateById(change);
    }

    @Override
    public PackageDeleteCheckVO checkDeletable(Integer id) {
        requirePlatform();
        getOrThrow(id);
        int tenantCount = tenantCountOf(id);
        PackageDeleteCheckVO vo = new PackageDeleteCheckVO();
        vo.setBlocked(tenantCount > 0);
        vo.setTenantCount(tenantCount);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        requirePlatform();
        getOrThrow(id);
        if (tenantCountOf(id) > 0) {
            throw new BizException("该套餐正在被租户使用，无法删除");
        }
        TenantPackageDO change = new TenantPackageDO();
        change.setId(id);
        change.setDeletedAt(LocalDateTime.now());
        packageMapper.updateById(change);
    }

    // ---------- 内部实现 ----------

    private void requirePlatform() {
        Integer tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId != 0) {
            throw new BizException("仅平台账号可访问套餐管理");
        }
    }

    private TenantPackageDO getOrThrow(Integer id) {
        TenantPackageDO pkg = id == null ? null : packageMapper.selectById(id);
        if (pkg == null || pkg.getDeletedAt() != null) {
            throw new BizException("套餐不存在");
        }
        return pkg;
    }

    private void assertNameFree(String name, Integer excludeId) {
        LambdaQueryWrapper<TenantPackageDO> wrapper = new LambdaQueryWrapper<TenantPackageDO>()
                .eq(TenantPackageDO::getName, name)
                .isNull(TenantPackageDO::getDeletedAt);
        if (excludeId != null) {
            wrapper.ne(TenantPackageDO::getId, excludeId);
        }
        if (packageMapper.selectCount(wrapper) > 0) {
            throw new BizException("该套餐名称已存在");
        }
    }

    private int tenantCountOf(Integer packageId) {
        return tenantMapper.selectCount(new LambdaQueryWrapper<TenantDO>()
                .eq(TenantDO::getPackageId, packageId)).intValue();
    }

    private String writeMenuIds(List<Integer> menuIds) {
        try {
            return objectMapper.writeValueAsString(menuIds);
        } catch (JsonProcessingException e) {
            BizException be = new BizException("菜单数据格式错误");
            be.initCause(e);
            throw be;
        }
    }

    private List<Integer> readMenuIds(String menuIds) {
        if (!StringUtils.hasText(menuIds)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(menuIds,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Integer.class));
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private List<TenantPackageVO> toVoList(List<TenantPackageDO> packages) {
        if (packages.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = packages.stream().map(TenantPackageDO::getId).toList();
        Map<Integer, Integer> counts = new HashMap<>(ids.size() * 2);
        for (PackageTenantCount row : tenantMapper.countByPackageIds(ids)) {
            counts.put(row.getId(), row.getCnt());
        }
        List<TenantPackageVO> result = new ArrayList<>(packages.size());
        for (TenantPackageDO pkg : packages) {
            result.add(toVO(pkg, counts.getOrDefault(pkg.getId(), 0)));
        }
        return result;
    }

    private TenantPackageVO toVO(TenantPackageDO pkg, int tenantCount) {
        TenantPackageVO vo = new TenantPackageVO();
        vo.setId(pkg.getId());
        vo.setName(pkg.getName());
        vo.setDescription(pkg.getRemark());
        List<Integer> menuIds = readMenuIds(pkg.getMenuIds());
        vo.setMenuIds(menuIds);
        vo.setMenuCount(menuIds.size());
        vo.setTenantCount(tenantCount);
        vo.setStatus(pkg.getStatus());
        vo.setCreateBy(pkg.getCreateBy());
        vo.setCreateTime(pkg.getCreateTime());
        vo.setUpdateBy(pkg.getUpdateBy());
        vo.setUpdateTime(pkg.getUpdateTime());
        return vo;
    }
}

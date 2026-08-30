package com.zhul.erp.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.dto.DictTypeDeleteCheckVO;
import com.zhul.erp.modules.system.dto.DictTypeVO;
import com.zhul.erp.modules.system.dto.SaveDictTypeRequest;
import com.zhul.erp.modules.system.entity.DictItemDO;
import com.zhul.erp.modules.system.entity.DictTypeDO;
import com.zhul.erp.modules.system.repository.DictItemMapper;
import com.zhul.erp.modules.system.repository.DictTypeMapper;
import com.zhul.erp.modules.system.service.DictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DictTypeServiceImpl implements DictTypeService {

    private final DictTypeMapper dictTypeMapper;
    private final DictItemMapper dictItemMapper;

    @Override
    public List<DictTypeVO> listAll(String name) {
        LambdaQueryWrapper<DictTypeDO> wrapper = activeWrapper();
        if (StringUtils.hasText(name)) {
            wrapper.like(DictTypeDO::getDictName, name);
        }
        wrapper.orderByDesc(DictTypeDO::getIsBuiltin).orderByAsc(DictTypeDO::getCreateTime);
        return toVoList(dictTypeMapper.selectList(wrapper));
    }

    private LambdaQueryWrapper<DictTypeDO> activeWrapper() {
        LambdaQueryWrapper<DictTypeDO> wrapper = new LambdaQueryWrapper<DictTypeDO>()
                .isNull(DictTypeDO::getDeletedAt);
        Integer tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            // 内置字典 tenant_id=0 为平台级共享，所有租户可见
            wrapper.and(w -> w.eq(DictTypeDO::getTenantId, tenantId).or().eq(DictTypeDO::getTenantId, 0));
        }
        return wrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(SaveDictTypeRequest req) {
        checkCodeDuplicate(req.getDictType(), null);

        DictTypeDO dictType = new DictTypeDO();
        dictType.setTenantId(TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 0);
        dictType.setDictType(req.getDictType());
        dictType.setDictName(req.getDictName());
        dictType.setIsBuiltin(0);
        dictType.setStatus(req.getStatus());
        dictType.setRemark(req.getRemark());
        dictTypeMapper.insert(dictType);
    }

    private void checkCodeDuplicate(String dictType, Integer excludeId) {
        LambdaQueryWrapper<DictTypeDO> wrapper = activeWrapper().eq(DictTypeDO::getDictType, dictType);
        if (excludeId != null) {
            wrapper.ne(DictTypeDO::getId, excludeId);
        }
        if (dictTypeMapper.selectCount(wrapper) > 0) {
            throw new BizException("字典类型编码已存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Integer id, SaveDictTypeRequest req) {
        DictTypeDO dictType = dictTypeMapper.selectById(id);
        if (dictType == null || dictType.getDeletedAt() != null) {
            throw new BizException("字典类型不存在");
        }
        // 字典类型编码创建后不可修改
        dictType.setDictName(req.getDictName());
        dictType.setStatus(req.getStatus());
        dictType.setRemark(req.getRemark());
        dictTypeMapper.updateById(dictType);
    }

    @Override
    public DictTypeDeleteCheckVO checkDeletable(Integer id) {
        DictTypeDO dictType = dictTypeMapper.selectById(id);
        if (dictType == null || dictType.getDeletedAt() != null) {
            throw new BizException("字典类型不存在");
        }
        DictTypeDeleteCheckVO vo = new DictTypeDeleteCheckVO();
        if (dictType.getIsBuiltin() != null && dictType.getIsBuiltin() == 1) {
            vo.setBuiltin(true);
            vo.setBlocked(true);
            vo.setItemCount(0);
            return vo;
        }
        long itemCount = dictItemMapper.selectCount(
                new LambdaQueryWrapper<DictItemDO>()
                        .eq(DictItemDO::getDictTypeId, id)
                        .isNull(DictItemDO::getDeletedAt));
        vo.setBuiltin(false);
        vo.setItemCount((int) itemCount);
        vo.setBlocked(itemCount > 0);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        DictTypeDO dictType = dictTypeMapper.selectById(id);
        if (dictType == null || dictType.getDeletedAt() != null) {
            throw new BizException("字典类型不存在");
        }
        if (dictType.getIsBuiltin() != null && dictType.getIsBuiltin() == 1) {
            throw new BizException("系统内置字典不可删除");
        }
        long itemCount = dictItemMapper.selectCount(
                new LambdaQueryWrapper<DictItemDO>()
                        .eq(DictItemDO::getDictTypeId, id)
                        .isNull(DictItemDO::getDeletedAt));
        if (itemCount > 0) {
            throw new BizException("请先删除该字典类型下的所有字典项");
        }
        dictType.setDeletedAt(LocalDateTime.now());
        dictTypeMapper.updateById(dictType);
    }

    private List<DictTypeVO> toVoList(List<DictTypeDO> list) {
        List<DictTypeVO> result = new ArrayList<>(list.size());
        for (DictTypeDO d : list) {
            DictTypeVO vo = new DictTypeVO();
            vo.setId(d.getId());
            vo.setDictType(d.getDictType());
            vo.setDictName(d.getDictName());
            vo.setIsBuiltin(d.getIsBuiltin());
            vo.setStatus(d.getStatus());
            vo.setRemark(d.getRemark());
            vo.setCreateTime(d.getCreateTime());
            vo.setUpdateTime(d.getUpdateTime());
            result.add(vo);
        }
        return result;
    }
}

package com.zhul.erp.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.dto.DictItemVO;
import com.zhul.erp.modules.system.dto.SaveDictItemRequest;
import com.zhul.erp.modules.system.entity.DictItemDO;
import com.zhul.erp.modules.system.entity.DictTypeDO;
import com.zhul.erp.modules.system.repository.DictItemMapper;
import com.zhul.erp.modules.system.repository.DictTypeMapper;
import com.zhul.erp.modules.system.service.DictItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DictItemServiceImpl implements DictItemService {

    private final DictItemMapper dictItemMapper;
    private final DictTypeMapper dictTypeMapper;

    @Override
    public List<DictItemVO> listByDictTypeId(Integer dictTypeId) {
        List<DictItemDO> list = dictItemMapper.selectList(
                new LambdaQueryWrapper<DictItemDO>()
                        .eq(DictItemDO::getDictTypeId, dictTypeId)
                        .isNull(DictItemDO::getDeletedAt)
                        .orderByAsc(DictItemDO::getSortOrder)
                        .orderByAsc(DictItemDO::getCreateTime));
        return toVoList(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(SaveDictItemRequest req) {
        DictTypeDO dictType = dictTypeMapper.selectById(req.getDictTypeId());
        if (dictType == null || dictType.getDeletedAt() != null) {
            throw new BizException("所属字典类型不存在");
        }
        checkCodeDuplicate(req.getDictTypeId(), req.getItemCode(), null);

        DictItemDO item = new DictItemDO();
        item.setTenantId(TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 0);
        item.setDictTypeId(req.getDictTypeId());
        item.setDictType(dictType.getDictType());
        item.setItemCode(req.getItemCode());
        item.setItemName(req.getItemName());
        item.setItemValue(req.getItemValue());
        item.setCssClass(req.getCssClass());
        item.setListClass("");
        item.setSortOrder(req.getSortOrder());
        item.setIsDefault(req.getIsDefault());
        item.setStatus(req.getStatus());
        item.setRemark(req.getRemark());
        dictItemMapper.insert(item);

        if (req.getIsDefault() != null && req.getIsDefault() == 1) {
            clearOtherDefaults(req.getDictTypeId(), item.getId());
        }
    }

    private void checkCodeDuplicate(Integer dictTypeId, String itemCode, Integer excludeId) {
        LambdaQueryWrapper<DictItemDO> wrapper = new LambdaQueryWrapper<DictItemDO>()
                .isNull(DictItemDO::getDeletedAt)
                .eq(DictItemDO::getDictTypeId, dictTypeId)
                .eq(DictItemDO::getItemCode, itemCode);
        if (excludeId != null) {
            wrapper.ne(DictItemDO::getId, excludeId);
        }
        if (dictItemMapper.selectCount(wrapper) > 0) {
            throw new BizException("字典项编码在当前字典类型下已存在");
        }
    }

    private void clearOtherDefaults(Integer dictTypeId, Integer keepId) {
        dictItemMapper.update(new DictItemDO(), new LambdaUpdateWrapper<DictItemDO>()
                .eq(DictItemDO::getDictTypeId, dictTypeId)
                .ne(DictItemDO::getId, keepId)
                .set(DictItemDO::getIsDefault, 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Integer id, SaveDictItemRequest req) {
        DictItemDO item = dictItemMapper.selectById(id);
        if (item == null || item.getDeletedAt() != null) {
            throw new BizException("字典项不存在");
        }
        // 字典项编码创建后不可修改
        item.setItemName(req.getItemName());
        item.setItemValue(req.getItemValue());
        item.setCssClass(req.getCssClass());
        item.setSortOrder(req.getSortOrder());
        item.setIsDefault(req.getIsDefault());
        item.setStatus(req.getStatus());
        item.setRemark(req.getRemark());
        dictItemMapper.updateById(item);

        if (req.getIsDefault() != null && req.getIsDefault() == 1) {
            clearOtherDefaults(item.getDictTypeId(), id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        DictItemDO item = dictItemMapper.selectById(id);
        if (item == null || item.getDeletedAt() != null) {
            throw new BizException("字典项不存在");
        }
        item.setDeletedAt(LocalDateTime.now());
        dictItemMapper.updateById(item);
    }

    private List<DictItemVO> toVoList(List<DictItemDO> list) {
        List<DictItemVO> result = new ArrayList<>(list.size());
        for (DictItemDO item : list) {
            DictItemVO vo = new DictItemVO();
            vo.setId(item.getId());
            vo.setDictTypeId(item.getDictTypeId());
            vo.setDictType(item.getDictType());
            vo.setItemCode(item.getItemCode());
            vo.setItemName(item.getItemName());
            vo.setItemValue(item.getItemValue());
            vo.setCssClass(item.getCssClass());
            vo.setSortOrder(item.getSortOrder());
            vo.setIsDefault(item.getIsDefault());
            vo.setStatus(item.getStatus());
            vo.setRemark(item.getRemark());
            vo.setCreateTime(item.getCreateTime());
            vo.setUpdateTime(item.getUpdateTime());
            result.add(vo);
        }
        return result;
    }
}

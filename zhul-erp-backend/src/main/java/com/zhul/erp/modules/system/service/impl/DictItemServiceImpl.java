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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        item.setItemNameEn(req.getItemNameEn() == null ? "" : req.getItemNameEn().trim());
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
        item.setItemNameEn(req.getItemNameEn() == null ? "" : req.getItemNameEn().trim());
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

    @Override
    public List<DictItemVO> listByDictType(String dictType) {
        return toVoList(selectByDictType(dictType, false));
    }

    @Override
    public Map<Integer, String> intLabels(String dictType) {
        List<DictItemDO> list = selectByDictType(dictType, false);
        Map<Integer, String> labels = new HashMap<>(list.size() * 2);
        for (DictItemDO item : list) {
            Integer code = parseInt(item.getItemValue());
            if (code != null) {
                labels.putIfAbsent(code, item.getItemName());
            }
        }
        return labels;
    }

    @Override
    public Map<Integer, String> intEnLabels(String dictType) {
        List<DictItemDO> list = selectByDictType(dictType, false);
        Map<Integer, String> labels = new HashMap<>(list.size() * 2);
        for (DictItemDO item : list) {
            Integer code = parseInt(item.getItemValue());
            if (code != null && item.getItemNameEn() != null && !item.getItemNameEn().isBlank()) {
                labels.putIfAbsent(code, item.getItemNameEn());
            }
        }
        return labels;
    }

    @Override
    public void requireEnabledValue(String dictType, Integer value, String message) {
        if (value == null) {
            throw new BizException(message);
        }
        for (DictItemDO item : selectByDictType(dictType, true)) {
            if (value.equals(parseInt(item.getItemValue()))) {
                return;
            }
        }
        throw new BizException(message);
    }

    private List<DictItemDO> selectByDictType(String dictType, boolean enabledOnly) {
        Integer tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<DictItemDO> w = new LambdaQueryWrapper<DictItemDO>()
                .eq(DictItemDO::getDictType, dictType)
                .eq(enabledOnly, DictItemDO::getStatus, 1)
                .isNull(DictItemDO::getDeletedAt)
                .orderByAsc(DictItemDO::getSortOrder)
                .orderByAsc(DictItemDO::getId);
        if (tenantId != null) {
            // 内置字典 tenant_id=0 为平台级共享，所有租户可见
            w.and(x -> x.eq(DictItemDO::getTenantId, tenantId).or().eq(DictItemDO::getTenantId, 0));
        }
        return dictItemMapper.selectList(w);
    }

    private static Integer parseInt(String value) {
        if (value == null || !value.trim().matches("-?\\d{1,9}")) {
            return null;
        }
        return Integer.valueOf(value.trim());
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
            vo.setItemNameEn(item.getItemNameEn());
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

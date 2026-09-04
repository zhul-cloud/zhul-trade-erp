package com.zhul.erp.modules.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.masterdata.dto.CreateSupplierFromChannelRequest;
import com.zhul.erp.modules.masterdata.dto.SaveSupplierRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierCreateResultVO;
import com.zhul.erp.modules.masterdata.dto.SupplierVO;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.masterdata.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private static final int SEARCH_LIMIT = 20;

    private final SupplierMapper supplierMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierCreateResultVO create(SaveSupplierRequest req) {
        int tenantId = currentTenantId();
        SupplierCreateResultVO result = new SupplierCreateResultVO();

        SupplierDO existing = findActiveByName(tenantId, req.getName());
        if (existing != null && !req.isForce()) {
            result.setDuplicate(true);
            result.setExistingSupplier(toVo(existing));
            return result;
        }

        SupplierDO supplier = new SupplierDO();
        supplier.setTenantId(tenantId);
        supplier.setName(req.getName());
        supplier.setCountry(req.getCountry());
        supplier.setContactName(req.getContactName());
        supplier.setContactPhone(req.getContactPhone());
        supplier.setContactEmail(req.getContactEmail());
        supplier.setMainBrands(req.getMainBrands());
        supplier.setStatus(1);
        supplierMapper.insert(supplier);

        result.setDuplicate(false);
        result.setCreatedSupplier(toVo(supplier));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierCreateResultVO createFromChannel(CreateSupplierFromChannelRequest req) {
        SaveSupplierRequest saveReq = new SaveSupplierRequest();
        saveReq.setName(req.getChannelName());
        saveReq.setForce(req.isForce());
        return create(saveReq);
    }

    @Override
    public List<SupplierVO> search(String keyword) {
        LambdaQueryWrapper<SupplierDO> wrapper = new LambdaQueryWrapper<SupplierDO>()
                .eq(SupplierDO::getTenantId, currentTenantId())
                .isNull(SupplierDO::getDeletedAt)
                .eq(SupplierDO::getStatus, 1);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SupplierDO::getName, keyword);
        }
        wrapper.last("LIMIT " + SEARCH_LIMIT);
        List<SupplierDO> list = supplierMapper.selectList(wrapper);
        List<SupplierVO> result = new ArrayList<>(list.size());
        for (SupplierDO supplier : list) {
            result.add(toVo(supplier));
        }
        return result;
    }

    @Override
    public SupplierVO getById(Long id) {
        SupplierDO supplier = supplierMapper.selectById(id);
        if (supplier == null || supplier.getDeletedAt() != null) {
            return null;
        }
        return toVo(supplier);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SupplierDO supplier = supplierMapper.selectById(id);
        if (supplier == null || supplier.getDeletedAt() != null) {
            throw new BizException("供应商不存在");
        }
        supplier.setDeletedAt(LocalDateTime.now());
        supplierMapper.updateById(supplier);
    }

    private SupplierDO findActiveByName(int tenantId, String name) {
        return supplierMapper.selectOne(new LambdaQueryWrapper<SupplierDO>()
                .eq(SupplierDO::getTenantId, tenantId)
                .eq(SupplierDO::getName, name)
                .isNull(SupplierDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    private int currentTenantId() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : 0;
    }

    private SupplierVO toVo(SupplierDO supplier) {
        SupplierVO vo = new SupplierVO();
        vo.setId(supplier.getId());
        vo.setName(supplier.getName());
        vo.setCountry(supplier.getCountry());
        vo.setContactName(supplier.getContactName());
        vo.setContactPhone(supplier.getContactPhone());
        vo.setContactEmail(supplier.getContactEmail());
        vo.setMainBrands(supplier.getMainBrands());
        vo.setStatus(supplier.getStatus());
        vo.setCreateTime(supplier.getCreateTime());
        return vo;
    }
}

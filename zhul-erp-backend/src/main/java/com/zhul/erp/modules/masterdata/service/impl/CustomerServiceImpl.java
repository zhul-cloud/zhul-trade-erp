package com.zhul.erp.modules.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.masterdata.dto.CustomerCreateResultVO;
import com.zhul.erp.modules.masterdata.dto.CustomerVO;
import com.zhul.erp.modules.masterdata.dto.SaveCustomerRequest;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private static final int SEARCH_LIMIT = 20;

    private final CustomerMapper customerMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerCreateResultVO create(SaveCustomerRequest req) {
        int tenantId = currentTenantId();
        CustomerCreateResultVO result = new CustomerCreateResultVO();

        CustomerDO existing = findActiveByName(tenantId, req.getName());
        if (existing != null && !req.isForce()) {
            result.setDuplicate(true);
            result.setExistingCustomer(toVo(existing));
            return result;
        }

        CustomerDO customer = new CustomerDO();
        customer.setTenantId(tenantId);
        customer.setName(req.getName());
        customer.setCountry(req.getCountry());
        customer.setContactName(req.getContactName());
        customer.setContactPhone(req.getContactPhone());
        customer.setContactEmail(req.getContactEmail());
        customer.setStatus(1);
        customerMapper.insert(customer);

        result.setDuplicate(false);
        result.setCreatedCustomer(toVo(customer));
        return result;
    }

    @Override
    public List<CustomerVO> search(String keyword) {
        LambdaQueryWrapper<CustomerDO> wrapper = new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getTenantId, currentTenantId())
                .isNull(CustomerDO::getDeletedAt)
                .eq(CustomerDO::getStatus, 1);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(CustomerDO::getName, keyword);
        }
        wrapper.last("LIMIT " + SEARCH_LIMIT);
        List<CustomerDO> list = customerMapper.selectList(wrapper);
        List<CustomerVO> result = new ArrayList<>(list.size());
        for (CustomerDO customer : list) {
            result.add(toVo(customer));
        }
        return result;
    }

    @Override
    public CustomerVO getById(Long id) {
        CustomerDO customer = customerMapper.selectById(id);
        if (customer == null || customer.getDeletedAt() != null) {
            return null;
        }
        return toVo(customer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CustomerDO customer = customerMapper.selectById(id);
        if (customer == null || customer.getDeletedAt() != null) {
            throw new BizException("客户不存在");
        }
        customer.setDeletedAt(LocalDateTime.now());
        customerMapper.updateById(customer);
    }

    private CustomerDO findActiveByName(int tenantId, String name) {
        return customerMapper.selectOne(new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getTenantId, tenantId)
                .eq(CustomerDO::getName, name)
                .isNull(CustomerDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    private int currentTenantId() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : 0;
    }

    private CustomerVO toVo(CustomerDO customer) {
        CustomerVO vo = new CustomerVO();
        vo.setId(customer.getId());
        vo.setName(customer.getName());
        vo.setCountry(customer.getCountry());
        vo.setContactName(customer.getContactName());
        vo.setContactPhone(customer.getContactPhone());
        vo.setContactEmail(customer.getContactEmail());
        vo.setStatus(customer.getStatus());
        vo.setCreateTime(customer.getCreateTime());
        return vo;
    }
}

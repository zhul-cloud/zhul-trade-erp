package com.zhul.erp.modules.masterdata.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.masterdata.dto.CustomerCreateResultVO;
import com.zhul.erp.modules.masterdata.dto.SaveCustomerRequest;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.service.impl.CustomerServiceImpl;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对应 specs/master-data/customer/spec.md：
 * - 创建客户记录（仅名称必填）
 * - 同租户内客户名称重复提示
 * - 客户记录仅支持软删除
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerMapper customerMapper;

    private CustomerServiceImpl customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerServiceImpl(customerMapper);
        TenantContext.setTenantId(1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_withOnlyNameProvided_succeeds() {
        when(customerMapper.selectOne(any())).thenReturn(null);

        SaveCustomerRequest req = new SaveCustomerRequest();
        req.setName("Global Trade Partners");

        CustomerCreateResultVO result = customerService.create(req);

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.getCreatedCustomer()).isNotNull();
        assertThat(result.getCreatedCustomer().getName()).isEqualTo("Global Trade Partners");
        assertThat(result.getCreatedCustomer().getContactPhone()).isNull();
        verify(customerMapper, times(1)).insert(any(CustomerDO.class));
    }

    @Test
    void create_withBlankName_failsBeanValidation() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        SaveCustomerRequest req = new SaveCustomerRequest();
        req.setName("");

        Set<ConstraintViolation<SaveCustomerRequest>> violations = validator.validate(req);

        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).isEqualTo("客户名称不能为空");
    }

    @Test
    void create_withDuplicateNameInSameTenant_returnsDuplicateWithoutInserting() {
        CustomerDO existing = new CustomerDO();
        existing.setId(100L);
        existing.setTenantId(1);
        existing.setName("Nordic Industrial AB");
        when(customerMapper.selectOne(any())).thenReturn(existing);

        SaveCustomerRequest req = new SaveCustomerRequest();
        req.setName("Nordic Industrial AB");

        CustomerCreateResultVO result = customerService.create(req);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getExistingCustomer().getId()).isEqualTo(100L);
        assertThat(result.getCreatedCustomer()).isNull();
        verify(customerMapper, never()).insert(any());
    }

    @Test
    void create_withDuplicateNameAndForceTrue_insertsAnyway() {
        CustomerDO existing = new CustomerDO();
        existing.setId(100L);
        existing.setName("Nordic Industrial AB");
        when(customerMapper.selectOne(any())).thenReturn(existing);

        SaveCustomerRequest req = new SaveCustomerRequest();
        req.setName("Nordic Industrial AB");
        req.setForce(true);

        CustomerCreateResultVO result = customerService.create(req);

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.getCreatedCustomer()).isNotNull();
        verify(customerMapper, times(1)).insert(any(CustomerDO.class));
    }

    @Test
    void delete_marksDeletedAtInsteadOfPhysicalDelete() {
        CustomerDO customer = new CustomerDO();
        customer.setId(1L);
        when(customerMapper.selectById(1L)).thenReturn(customer);

        customerService.delete(1L);

        ArgumentCaptor<CustomerDO> captor = ArgumentCaptor.forClass(CustomerDO.class);
        verify(customerMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
        verify(customerMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void delete_whenCustomerNotFound_throwsBizException() {
        when(customerMapper.selectById(999L)).thenReturn(null);

        assertThrows(BizException.class, () -> customerService.delete(999L));
    }

    @Test
    void getById_whenFound_returnsVo() {
        CustomerDO customer = new CustomerDO();
        customer.setId(1L);
        customer.setName("Nordic Industrial AB");
        when(customerMapper.selectById(1L)).thenReturn(customer);

        assertThat(customerService.getById(1L).getName()).isEqualTo("Nordic Industrial AB");
    }

    @Test
    void getById_whenNotFoundOrDeleted_returnsNull() {
        when(customerMapper.selectById(999L)).thenReturn(null);
        assertThat(customerService.getById(999L)).isNull();

        CustomerDO deleted = new CustomerDO();
        deleted.setId(2L);
        deleted.setDeletedAt(java.time.LocalDateTime.now());
        when(customerMapper.selectById(2L)).thenReturn(deleted);
        assertThat(customerService.getById(2L)).isNull();
    }
}

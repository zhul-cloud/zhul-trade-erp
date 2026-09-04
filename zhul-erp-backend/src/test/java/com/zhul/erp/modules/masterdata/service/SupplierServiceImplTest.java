package com.zhul.erp.modules.masterdata.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.masterdata.dto.CreateSupplierFromChannelRequest;
import com.zhul.erp.modules.masterdata.dto.SaveSupplierRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierCreateResultVO;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.masterdata.service.impl.SupplierServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对应 specs/master-data/supplier/spec.md：
 * - 创建供应商记录（仅名称必填）
 * - 从电商询价渠道创建供应商（预填名称）
 * - 同租户内供应商名称重复提示
 * - 供应商记录仅支持软删除
 */
@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTest {

    @Mock
    private SupplierMapper supplierMapper;

    private SupplierServiceImpl supplierService;

    @BeforeEach
    void setUp() {
        supplierService = new SupplierServiceImpl(supplierMapper);
        TenantContext.setTenantId(1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_withOnlyNameProvided_succeeds() {
        when(supplierMapper.selectOne(any())).thenReturn(null);

        SaveSupplierRequest req = new SaveSupplierRequest();
        req.setName("上海科菱自动化设备有限公司");

        SupplierCreateResultVO result = supplierService.create(req);

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.getCreatedSupplier().getName()).isEqualTo("上海科菱自动化设备有限公司");
        assertThat(result.getCreatedSupplier().getMainBrands()).isNull();
        verify(supplierMapper, times(1)).insert(any(SupplierDO.class));
    }

    @Test
    void create_withDuplicateNameInSameTenant_returnsDuplicateWithoutInserting() {
        SupplierDO existing = new SupplierDO();
        existing.setId(200L);
        existing.setName("深圳智控电气有限公司");
        when(supplierMapper.selectOne(any())).thenReturn(existing);

        SaveSupplierRequest req = new SaveSupplierRequest();
        req.setName("深圳智控电气有限公司");

        SupplierCreateResultVO result = supplierService.create(req);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getExistingSupplier().getId()).isEqualTo(200L);
        verify(supplierMapper, never()).insert(any());
    }

    @Test
    void createFromChannel_prefillsSupplierNameFromChannelName() {
        when(supplierMapper.selectOne(any())).thenReturn(null);

        CreateSupplierFromChannelRequest req = new CreateSupplierFromChannelRequest();
        req.setChannelName("东莞市XX五金机电经营部");

        SupplierCreateResultVO result = supplierService.createFromChannel(req);

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.getCreatedSupplier().getName()).isEqualTo("东莞市XX五金机电经营部");
        assertThat(result.getCreatedSupplier().getContactName()).isNull();
    }

    @Test
    void createFromChannel_withNameAlreadyExisting_returnsDuplicateForCallerToDecide() {
        SupplierDO existing = new SupplierDO();
        existing.setId(300L);
        existing.setName("东莞市XX五金机电经营部");
        when(supplierMapper.selectOne(any())).thenReturn(existing);

        CreateSupplierFromChannelRequest req = new CreateSupplierFromChannelRequest();
        req.setChannelName("东莞市XX五金机电经营部");

        SupplierCreateResultVO result = supplierService.createFromChannel(req);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getExistingSupplier().getId()).isEqualTo(300L);
        verify(supplierMapper, never()).insert(any());
    }

    @Test
    void delete_marksDeletedAtInsteadOfPhysicalDelete() {
        SupplierDO supplier = new SupplierDO();
        supplier.setId(1L);
        when(supplierMapper.selectById(1L)).thenReturn(supplier);

        supplierService.delete(1L);

        ArgumentCaptor<SupplierDO> captor = ArgumentCaptor.forClass(SupplierDO.class);
        verify(supplierMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
        verify(supplierMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void delete_whenSupplierNotFound_throwsBizException() {
        when(supplierMapper.selectById(999L)).thenReturn(null);

        assertThrows(BizException.class, () -> supplierService.delete(999L));
    }

    @Test
    void getById_whenFound_returnsVo() {
        SupplierDO supplier = new SupplierDO();
        supplier.setId(1L);
        supplier.setName("上海科菱自动化设备有限公司");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);

        assertThat(supplierService.getById(1L).getName()).isEqualTo("上海科菱自动化设备有限公司");
    }

    @Test
    void getById_whenNotFoundOrDeleted_returnsNull() {
        when(supplierMapper.selectById(999L)).thenReturn(null);
        assertThat(supplierService.getById(999L)).isNull();

        SupplierDO deleted = new SupplierDO();
        deleted.setId(2L);
        deleted.setDeletedAt(java.time.LocalDateTime.now());
        when(supplierMapper.selectById(2L)).thenReturn(deleted);
        assertThat(supplierService.getById(2L)).isNull();
    }
}

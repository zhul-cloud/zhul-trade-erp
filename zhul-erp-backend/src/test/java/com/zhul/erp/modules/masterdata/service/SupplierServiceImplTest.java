package com.zhul.erp.modules.masterdata.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.masterdata.constants.SupplierConstants;
import com.zhul.erp.modules.masterdata.dto.CreateSupplierFromChannelRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierBatchDeleteResultVO;
import com.zhul.erp.modules.masterdata.dto.SupplierFormVO;
import com.zhul.erp.modules.masterdata.dto.SaveSupplierRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierCreateResultVO;
import com.zhul.erp.modules.masterdata.dto.SupplierPageQuery;
import com.zhul.erp.modules.masterdata.dto.SupplierProductScopeRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierVO;
import com.zhul.erp.modules.masterdata.dto.UpdateSupplierRequest;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.masterdata.service.impl.SupplierProductScopeSync;
import com.zhul.erp.modules.masterdata.service.impl.SupplierServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
 * - 分页查询供应商列表 / 更新供应商记录 / 启用禁用供应商记录
 * 以及 enrich-supplier-basic-info：编码唯一与自动生成、信用代码唯一、银行账号脱敏、批量删除、导出
 */
@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTest {

    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private SupplierProductScopeSync scopeSync;

    private SupplierServiceImpl supplierService;

    @BeforeEach
    void setUp() {
        supplierService = new SupplierServiceImpl(supplierMapper, scopeSync);
        TenantContext.setTenantId(1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_withOnlyNameProvided_succeeds() {
        when(supplierMapper.selectOne(any())).thenReturn(null);
        stubInsertAssignsId(12L);

        SaveSupplierRequest req = new SaveSupplierRequest();
        req.setName("上海科菱自动化设备有限公司");

        SupplierCreateResultVO result = supplierService.create(req);

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.getCreatedSupplier().getName()).isEqualTo("上海科菱自动化设备有限公司");
        verify(scopeSync, never()).replace(any(), any(), any());
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
        stubInsertAssignsId(13L);

        CreateSupplierFromChannelRequest req = new CreateSupplierFromChannelRequest();
        req.setChannelName("东莞市XX五金机电经营部");

        SupplierCreateResultVO result = supplierService.createFromChannel(req);

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.getCreatedSupplier().getName()).isEqualTo("东莞市XX五金机电经营部");
        assertThat(result.getCreatedSupplier().getContactName()).isEmpty();
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

    @Test
    void page_withoutFilters_returnsAllWithTotal() {
        SupplierDO supplier = new SupplierDO();
        supplier.setId(1L);
        supplier.setName("上海科菱自动化设备有限公司");
        Page<SupplierDO> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(supplier));
        mockPage.setTotal(1);
        when(supplierMapper.selectPage(any(), any())).thenReturn(mockPage);

        PageResult<SupplierVO> result = supplierService.page(new SupplierPageQuery());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getName()).isEqualTo("上海科菱自动化设备有限公司");
    }

    @Test
    void update_withProductScopes_normalizesThenReplaces() {
        SupplierDO supplier = new SupplierDO();
        supplier.setId(1L);
        supplier.setTenantId(1);
        supplier.setName("上海科菱自动化设备有限公司");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);
        when(supplierMapper.selectOne(any())).thenReturn(null);
        List<SupplierProductScopeSync.Scope> normalized =
                List.of(new SupplierProductScopeSync.Scope(9L, "", java.util.Set.of()));
        when(scopeSync.normalize(any())).thenReturn(normalized);

        UpdateSupplierRequest req = new UpdateSupplierRequest();
        req.setName("上海科菱自动化设备有限公司");
        SupplierProductScopeRequest scope = new SupplierProductScopeRequest();
        scope.setBrandName("Siemens");
        req.setProductScopes(List.of(scope));

        supplierService.update(1L, req);

        verify(supplierMapper, times(1)).updateById(any(SupplierDO.class));
        verify(scopeSync).replace(1, 1L, normalized);
    }

    @Test
    void update_withoutProductScopes_leavesThemUntouched() {
        SupplierDO supplier = new SupplierDO();
        supplier.setId(1L);
        supplier.setTenantId(1);
        supplier.setName("A");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);
        when(supplierMapper.selectOne(any())).thenReturn(null);
        UpdateSupplierRequest req = new UpdateSupplierRequest();
        req.setName("A");

        supplierService.update(1L, req);

        verify(scopeSync, never()).normalize(any());
        verify(scopeSync, never()).replace(any(), any(), any());
    }

    @Test
    void update_renamingToAnotherExistingSupplierName_throwsWithoutSaving() {
        SupplierDO supplier = new SupplierDO();
        supplier.setId(1L);
        supplier.setTenantId(1);
        supplier.setName("Old Name");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);

        SupplierDO other = new SupplierDO();
        other.setId(2L);
        other.setName("Taken Name");
        when(supplierMapper.selectOne(any())).thenReturn(other);

        UpdateSupplierRequest req = new UpdateSupplierRequest();
        req.setName("Taken Name");

        assertThrows(BizException.class, () -> supplierService.update(1L, req));
        verify(supplierMapper, never()).updateById(any());
    }

    @Test
    void updateStatus_disablesSupplier() {
        SupplierDO supplier = new SupplierDO();
        supplier.setId(1L);
        supplier.setStatus(1);
        when(supplierMapper.selectById(1L)).thenReturn(supplier);

        supplierService.updateStatus(1L, 0);

        ArgumentCaptor<SupplierDO> captor = ArgumentCaptor.forClass(SupplierDO.class);
        verify(supplierMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
    }

    @Test
    void updateStatus_whenSupplierNotFound_throwsBizException() {
        when(supplierMapper.selectById(999L)).thenReturn(null);
        assertThrows(BizException.class, () -> supplierService.updateStatus(999L, 0));
    }

    // ---------- enrich-supplier-basic-info ----------

    @Test
    void create_withManualCode_savesUppercasedCodeWithoutGenerating() {
        when(supplierMapper.selectOne(any())).thenReturn(null);
        stubInsertAssignsId(20L);

        SaveSupplierRequest req = fullRequest();
        req.setSupplierCode("sup88");

        SupplierCreateResultVO result = supplierService.create(req);

        assertThat(result.getCreatedSupplier().getSupplierCode()).isEqualTo("SUP88");
        assertThat(result.getCreatedSupplier().getSupplierType()).isEqualTo(1);
        verify(supplierMapper, never()).updateById(any());
    }

    @Test
    void create_withDuplicateManualCode_throwsCodeDuplicate() {
        when(supplierMapper.selectOne(any())).thenReturn(supplier(9L, "已有供应商"));

        SaveSupplierRequest req = fullRequest();
        req.setSupplierCode("SUP00001");

        BizException e = assertThrows(BizException.class, () -> supplierService.create(req));
        assertThat(e.getErrorCode()).isEqualTo(SupplierConstants.ERROR_CODE_DUPLICATE);
        assertThat(e.getMessage()).isEqualTo("供应商编码已存在，请更换");
        verify(supplierMapper, never()).insert(any(SupplierDO.class));
    }

    @Test
    void create_withoutCode_generatesPaddedCodeFromId() {
        when(supplierMapper.selectOne(any())).thenReturn(null);
        stubInsertAssignsId(12L);

        SaveSupplierRequest req = new SaveSupplierRequest();
        req.setName("询盘内联创建的供应商");

        SupplierCreateResultVO result = supplierService.create(req);

        assertThat(result.getCreatedSupplier().getSupplierCode()).isEqualTo("SUP00012");
        assertThat(result.getCreatedSupplier().getSupplierType()).isEqualTo(SupplierConstants.UNSET);
        assertThat(result.getCreatedSupplier().getStatus()).isEqualTo(1);
        verify(supplierMapper, times(1)).updateById(any(SupplierDO.class));
    }

    @Test
    void create_withoutCode_whenGeneratedCodeTaken_appendsSuffix() {
        // 依次是：名称判重（无）、SUP00012 已被手工编码占用、SUP000121 可用
        when(supplierMapper.selectOne(any())).thenReturn(null, supplier(5L, "手工编码的供应商"), null);
        stubInsertAssignsId(12L);

        SaveSupplierRequest req = new SaveSupplierRequest();
        req.setName("询盘内联创建的供应商");

        SupplierCreateResultVO result = supplierService.create(req);

        assertThat(result.getCreatedSupplier().getSupplierCode()).isEqualTo("SUP000121");
    }

    @Test
    void create_withCreditCodeUsedByAnother_throwsWithHolderName() {
        when(supplierMapper.selectOne(any())).thenReturn(null, supplier(3L, "上海电子科技有限公司"));

        SaveSupplierRequest req = fullRequest();
        req.setSupplierCode("SUP90");
        req.setCreditCode("91310115ma1g832x01");

        BizException e = assertThrows(BizException.class, () -> supplierService.create(req));
        assertThat(e.getErrorCode()).isEqualTo(SupplierConstants.ERROR_CREDIT_CODE_DUPLICATE);
        assertThat(e.getMessage()).isEqualTo("该统一社会信用代码已被供应商「上海电子科技有限公司」使用，请核实后再提交");
        verify(supplierMapper, never()).insert(any(SupplierDO.class));
    }

    @Test
    void create_withBlankCreditCode_skipsUniquenessCheck() {
        // 手工编码判重、名称判重各查一次，信用代码为空不查
        when(supplierMapper.selectOne(any())).thenReturn(null);
        stubInsertAssignsId(21L);

        SaveSupplierRequest req = fullRequest();
        req.setSupplierCode("SUP91");
        req.setCreditCode("");

        supplierService.create(req);

        verify(supplierMapper, times(2)).selectOne(any());
    }

    @Test
    void create_uppercasesCreditCodeAndMasksBankAccountInResult() {
        when(supplierMapper.selectOne(any())).thenReturn(null);
        stubInsertAssignsId(22L);

        SaveSupplierRequest req = fullRequest();
        req.setSupplierCode("SUP92");
        req.setCreditCode("91310115ma1g832x01");
        req.setBankAccount("6222021234560008888");

        SupplierVO created = supplierService.create(req).getCreatedSupplier();

        assertThat(created.getCreditCode()).isEqualTo("91310115MA1G832X01");
        assertThat(created.getBankAccount()).isEqualTo("6222 **** **** 8888");
    }

    @Test
    void update_savesAllFieldsAndKeepsCode() {
        SupplierDO existing = supplier(1L, "上海电子科技有限公司");
        existing.setSupplierCode("SUP00001");
        existing.setCountry("China");
        when(supplierMapper.selectById(1L)).thenReturn(existing);
        when(supplierMapper.selectOne(any())).thenReturn(null);

        UpdateSupplierRequest req = new UpdateSupplierRequest();
        req.setName("上海电子科技有限公司");
        req.setSupplierType(2);
        req.setStatus(0);
        req.setRegisteredCapital(new BigDecimal("5000.00"));
        req.setRegion("上海市/上海市/浦东新区");
        req.setBankAccount("6222021234560008888");

        supplierService.update(1L, req);

        ArgumentCaptor<SupplierDO> captor = ArgumentCaptor.forClass(SupplierDO.class);
        verify(supplierMapper).updateById(captor.capture());
        SupplierDO saved = captor.getValue();
        assertThat(saved.getSupplierCode()).isEqualTo("SUP00001");
        assertThat(saved.getSupplierType()).isEqualTo(2);
        assertThat(saved.getStatus()).isEqualTo(0);
        assertThat(saved.getRegisteredCapital()).isEqualByComparingTo("5000.00");
        assertThat(saved.getRegion()).isEqualTo("上海市/上海市/浦东新区");
        assertThat(saved.getBankAccount()).isEqualTo("6222021234560008888");
        // 管理页不传 country，更新时保持原值
        assertThat(saved.getCountry()).isEqualTo("China");
    }

    @Test
    void update_withCreditCodeUsedByAnother_throwsWithoutSaving() {
        when(supplierMapper.selectById(1L)).thenReturn(supplier(1L, "A 公司"));
        // 名称判重（无）、信用代码被 B 公司占用
        when(supplierMapper.selectOne(any())).thenReturn(null, supplier(2L, "B 公司"));

        UpdateSupplierRequest req = new UpdateSupplierRequest();
        req.setName("A 公司");
        req.setSupplierType(1);
        req.setStatus(1);
        req.setCreditCode("91310115MA1G832X01");

        BizException e = assertThrows(BizException.class, () -> supplierService.update(1L, req));
        assertThat(e.getMessage()).contains("B 公司");
        verify(supplierMapper, never()).updateById(any());
    }

    @Test
    void update_keepingOwnCreditCode_saves() {
        SupplierDO self = supplier(1L, "A 公司");
        self.setCreditCode("91310115MA1G832X01");
        when(supplierMapper.selectById(1L)).thenReturn(self);
        // 查询条件排除了自身，所以两次查询都没有命中
        when(supplierMapper.selectOne(any())).thenReturn(null);

        UpdateSupplierRequest req = new UpdateSupplierRequest();
        req.setName("A 公司");
        req.setSupplierType(1);
        req.setStatus(1);
        req.setCreditCode("91310115MA1G832X01");

        supplierService.update(1L, req);

        verify(supplierMapper).updateById(any(SupplierDO.class));
    }

    @Test
    void getById_masksBankAccount_getFormById_returnsPlainText() {
        SupplierDO s = supplier(1L, "A 公司");
        s.setBankAccount("6222021234560008888");
        when(supplierMapper.selectById(1L)).thenReturn(s);

        assertThat(supplierService.getById(1L).getBankAccount()).isEqualTo("6222 **** **** 8888");
        SupplierFormVO form = supplierService.getFormById(1L);
        assertThat(form.getBankAccount()).isEqualTo("6222021234560008888");
    }

    @Test
    void getFormById_whenDeleted_throws() {
        SupplierDO s = supplier(1L, "A 公司");
        s.setDeletedAt(LocalDateTime.now());
        when(supplierMapper.selectById(1L)).thenReturn(s);

        assertThrows(BizException.class, () -> supplierService.getFormById(1L));
    }

    @Test
    void page_withCombinedFilters_returnsFilteredRecords() {
        SupplierDO s = supplier(1L, "上海电子科技有限公司");
        s.setSupplierCode("SUP00001");
        s.setSupplierType(1);
        Page<SupplierDO> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(s));
        mockPage.setTotal(1);
        when(supplierMapper.selectPage(any(), any())).thenReturn(mockPage);

        SupplierPageQuery query = new SupplierPageQuery();
        query.setSupplierCode("SUP0000");
        query.setSupplierType(1);
        query.setStatus(1);
        query.setCreditCode("91310115ma1g832x01");

        PageResult<SupplierVO> result = supplierService.page(query);

        assertThat(result.getRecords()).extracting(SupplierVO::getSupplierCode).containsExactly("SUP00001");
    }

    @Test
    void batchDelete_softDeletesFoundAndCountsMissingAsSkipped() {
        when(supplierMapper.selectList(any())).thenReturn(List.of(supplier(1L, "A"), supplier(2L, "B")));

        SupplierBatchDeleteResultVO result = supplierService.batchDelete(List.of(1L, 2L, 3L, 3L));

        assertThat(result.getDeleted()).isEqualTo(2);
        assertThat(result.getSkipped()).isEqualTo(1);
        ArgumentCaptor<SupplierDO> captor = ArgumentCaptor.forClass(SupplierDO.class);
        verify(supplierMapper, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues()).allMatch(s -> s.getDeletedAt() != null);
        verify(supplierMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void batchDelete_withEmptyIds_throws() {
        assertThrows(BizException.class, () -> supplierService.batchDelete(List.of()));
    }

    @Test
    void export_writesHeaderAndMaskedRows() throws IOException {
        SupplierDO s = supplier(1L, "上海电子科技有限公司");
        s.setSupplierCode("SUP00001");
        s.setSupplierType(1);
        s.setStatus(1);
        s.setRegisteredCapital(new BigDecimal("5000.00"));
        s.setBankAccount("6222021234560008888");
        when(supplierMapper.selectCount(any())).thenReturn(1L);
        when(supplierMapper.selectList(any())).thenReturn(List.of(s));

        try (Workbook workbook = supplierService.export(new SupplierPageQuery())) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("供应商编码");
            assertThat(sheet.getRow(0).getCell(15).getStringCellValue()).isEqualTo("银行账号");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("SUP00001");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("生产商");
            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEqualTo("5000.00");
            assertThat(sheet.getRow(1).getCell(15).getStringCellValue()).isEqualTo("6222 **** **** 8888");
        }
    }

    @Test
    void export_overLimit_throws() {
        when(supplierMapper.selectCount(any())).thenReturn((long) SupplierConstants.EXPORT_MAX_ROWS + 1);

        assertThrows(BizException.class, () -> supplierService.export(new SupplierPageQuery()));
        verify(supplierMapper, never()).selectList(any());
    }

    private void stubInsertAssignsId(Long id) {
        doAnswer(invocation -> {
            invocation.<SupplierDO>getArgument(0).setId(id);
            return 1;
        }).when(supplierMapper).insert(any(SupplierDO.class));
    }

    private static SaveSupplierRequest fullRequest() {
        SaveSupplierRequest req = new SaveSupplierRequest();
        req.setName("上海电子科技有限公司");
        req.setSupplierType(1);
        req.setStatus(1);
        req.setForce(true);
        return req;
    }

    private static SupplierDO supplier(Long id, String name) {
        SupplierDO s = new SupplierDO();
        s.setId(id);
        s.setTenantId(1);
        s.setName(name);
        return s;
    }
}

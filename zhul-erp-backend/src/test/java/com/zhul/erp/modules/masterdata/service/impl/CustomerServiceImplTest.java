package com.zhul.erp.modules.masterdata.service.impl;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.masterdata.constants.CustomerConstants;
import com.zhul.erp.modules.masterdata.dto.AssignableOwnersVO;
import com.zhul.erp.modules.masterdata.dto.CustomerBatchDeleteResultVO;
import com.zhul.erp.modules.masterdata.dto.CustomerPageQuery;
import com.zhul.erp.modules.masterdata.dto.CustomerPartyRequest;
import com.zhul.erp.modules.masterdata.dto.CustomerTransferRequest;
import com.zhul.erp.modules.masterdata.dto.CustomerVO;
import com.zhul.erp.modules.masterdata.dto.SaveCustomerRequest;
import com.zhul.erp.modules.masterdata.dto.UpdateCustomerRequest;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.entity.CustomerPartyDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.repository.CustomerPartyMapper;
import com.zhul.erp.modules.product.support.CountryCatalog;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.DepartmentMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.support.MybatisPlusTestSupport;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对应 specs/master-data/customer/spec.md、specs/master-data/customer-party/spec.md。
 * 数据权限、查重条件等依赖真实 SQL 的部分另见 CustomerApiContractTest。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerServiceImplTest {

    private static final long SELF = 10L;
    private static final long OTHER = 20L;

    @Mock private CustomerMapper customerMapper;
    @Mock private CustomerPartyMapper partyMapper;
    @Mock private DataScopeResolver dataScopeResolver;
    @Mock private CountryCatalog countryCatalog;
    @Mock private UserBasicMapper userBasicMapper;
    @Mock private DepartmentMapper departmentMapper;
    @Mock private LogService logService;

    private CustomerServiceImpl service;
    private final List<CustomerPartyDO> insertedParties = new ArrayList<>();
    private final List<CustomerPartyDO> updatedParties = new ArrayList<>();

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestSupport.initTableInfo(CustomerDO.class);
    }

    @BeforeEach
    void setUp() {
        service = new CustomerServiceImpl(customerMapper, new CustomerPartySync(partyMapper), dataScopeResolver,
                countryCatalog, userBasicMapper, departmentMapper, logService);
        TenantContext.setTenantId(1);
        when(dataScopeResolver.current()).thenReturn(new DataScope(DataScope.Type.SELF, SELF, Set.of(SELF)));
        when(countryCatalog.canonicalName(anyString())).thenAnswer(inv -> {
            String c = inv.getArgument(0);
            return c.equalsIgnoreCase("germany") ? "Germany" : c.equalsIgnoreCase("austria") ? "Austria" : null;
        });
        when(userBasicMapper.selectBatchIds(any())).thenReturn(List.of(user(SELF, "张伟"), user(OTHER, "李娜")));
        doAnswer(inv -> {
            CustomerPartyDO p = inv.getArgument(0);
            p.setId(1000L + insertedParties.size());
            insertedParties.add(p);
            return 1;
        }).when(partyMapper).insert(any(CustomerPartyDO.class));
        doAnswer(inv -> {
            updatedParties.add(inv.getArgument(0));
            return 1;
        }).when(partyMapper).updateById(any(CustomerPartyDO.class));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---------------------------------------------------------------- 新增

    @Test
    void create_withoutCode_generatesCodeAndDefaultsOwnerToSelf() {
        stubInsertCustomerId(12L);

        CustomerVO vo = service.create(quick("Pacific Controls Pty Ltd"));

        assertThat(vo.getCustomerCode()).isEqualTo("CUS00012");
        assertThat(vo.getOwnerId()).isEqualTo(SELF);
        assertThat(vo.getOwnerName()).isEqualTo("张伟");
        assertThat(vo.getCustomerRole()).isEqualTo(CustomerConstants.UNSET);
        assertThat(vo.getCountry()).isEqualTo("Germany");
        ArgumentCaptor<CustomerDO> captor = ArgumentCaptor.forClass(CustomerDO.class);
        verify(customerMapper).insert(captor.capture());
        assertThat(captor.getValue().getCurrency()).isEqualTo("USD");
        assertThat(captor.getValue().getNameKey()).isEqualTo("pacificcontrols");
    }

    @Test
    void create_withDuplicateManualCode_throwsCodeDuplicate() {
        when(customerMapper.selectOne(any())).thenReturn(customer(5L, "Other", OTHER));
        SaveCustomerRequest req = quick("ABC Automation GmbH");
        req.setCustomerCode("cus00001");

        BizException e = assertThrows(BizException.class, () -> service.create(req));

        assertThat(e.getErrorCode()).isEqualTo(CustomerConstants.ERROR_CODE_DUPLICATE);
        verify(customerMapper, never()).insert(any(CustomerDO.class));
    }

    @Test
    void create_duplicateOfOthersCustomer_blocksAndOnlyRevealsOwnerName() {
        // 编码为空，第一次 selectOne 就是查重
        when(customerMapper.selectOne(any())).thenReturn(customer(5L, "ABC Automation GmbH", OTHER));

        BizException e = assertThrows(BizException.class, () -> service.create(quick("abc automation")));

        assertThat(e.getErrorCode()).isEqualTo(CustomerConstants.ERROR_DUPLICATE);
        assertThat(e.getMessage()).isEqualTo("该客户已存在，负责业务员：李娜");
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) e.getDetail();
        assertThat(detail).containsEntry("existingId", 5L).containsEntry("selectable", false);
        verify(customerMapper, never()).insert(any(CustomerDO.class));
    }

    @Test
    void create_duplicateOfOwnCustomer_isSelectable() {
        when(customerMapper.selectOne(any())).thenReturn(customer(6L, "ABC Automation GmbH", SELF));

        BizException e = assertThrows(BizException.class, () -> service.create(quick("ABC Automation")));

        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) e.getDetail();
        assertThat(detail).containsEntry("selectable", true);
    }

    @Test
    void create_assigningOwnerOutsideScope_isRejected() {
        when(userBasicMapper.selectById(20)).thenReturn(user(OTHER, "李娜"));
        SaveCustomerRequest req = quick("ABC Automation GmbH");
        req.setOwnerId(OTHER);

        BizException e = assertThrows(BizException.class, () -> service.create(req));

        assertThat(e.getMessage()).isEqualTo("只能指定你数据范围内的在职业务员");
    }

    @Test
    void create_withFirstPartyNotFlagged_makesItDefault() {
        stubInsertCustomerId(12L);
        SaveCustomerRequest req = quick("ABC Automation GmbH");
        req.setParties(List.of(party(null, 1, "ABC Automation GmbH", false)));

        service.create(req);

        assertThat(insertedParties).hasSize(1);
        assertThat(insertedParties.get(0).getIsDefault()).isEqualTo(1);
        assertThat(insertedParties.get(0).getCustomerId()).isEqualTo(12L);
    }

    // ---------------------------------------------------------------- 校验

    @Test
    void validation_rejectsChineseInEnglishName() {
        BizException e = assertThrows(BizException.class, () -> service.create(quick("德国ABC自动化")));
        assertThat(e.getMessage()).isEqualTo("单据字段请使用英文");
    }

    @Test
    void validation_allowsLatinAccentsInAddress() {
        stubInsertCustomerId(12L);
        SaveCustomerRequest req = quick("ABC Automation GmbH");
        req.setAddress("Industriestraße 12, München");

        service.create(req);

        verify(customerMapper).insert(any(CustomerDO.class));
    }

    @Test
    void validation_rejectsCountryOutsideCatalog() {
        SaveCustomerRequest req = quick("ABC");
        req.setCountry("Atlantis");
        BizException e = assertThrows(BizException.class, () -> service.create(req));
        assertThat(e.getMessage()).isEqualTo("请选择系统清单中的国家/地区");
    }

    @Test
    void validation_incotermRequiresPlace() {
        SaveCustomerRequest req = quick("ABC");
        req.setIncoterm("FOB");
        BizException e = assertThrows(BizException.class, () -> service.create(req));
        assertThat(e.getMessage()).isEqualTo("请填写术语地点，如 FOB 的装运港");
    }

    @Test
    void validation_openAccountRequiresPaymentDays() {
        SaveCustomerRequest req = quick("ABC");
        req.setPaymentMethod(8);
        BizException e = assertThrows(BizException.class, () -> service.create(req));
        assertThat(e.getMessage()).isEqualTo("请填写账期（天）");
    }

    @Test
    void validation_depositPaymentRequiresRatio() {
        SaveCustomerRequest req = quick("ABC");
        req.setPaymentMethod(2);
        BizException e = assertThrows(BizException.class, () -> service.create(req));
        assertThat(e.getMessage()).isEqualTo("请填写定金比例");
    }

    @Test
    void validation_creditLimitRequiresCurrency() {
        SaveCustomerRequest req = quick("ABC");
        req.setCreditLimit(new BigDecimal("200000"));
        BizException e = assertThrows(BizException.class, () -> service.create(req));
        assertThat(e.getMessage()).isEqualTo("信用额度需要同时填写金额和币种");
    }

    @Test
    void validation_invalidTimezoneRejected() {
        SaveCustomerRequest req = quick("ABC");
        req.setTimezone("Mars/Base");
        assertThrows(BizException.class, () -> service.create(req));
    }

    // ---------------------------------------------------------------- 更新

    @Test
    void update_switchingToFullPrepaymentClearsDepositAndKeepsCodeAndOwner() {
        CustomerDO existing = customer(1L, "ABC Automation GmbH", SELF);
        existing.setCustomerCode("CUS00001");
        existing.setDepositRatio(30);
        existing.setPaymentMethod(2);
        when(customerMapper.selectById(1L)).thenReturn(existing);
        UpdateCustomerRequest req = update("ABC Automation GmbH");
        req.setPaymentMethod(1);
        req.setDepositRatio(30);

        service.update(1L, req);

        ArgumentCaptor<CustomerDO> captor = ArgumentCaptor.forClass(CustomerDO.class);
        verify(customerMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDepositRatio()).isNull();
        assertThat(captor.getValue().getPaymentMethod()).isEqualTo(1);
        assertThat(captor.getValue().getCustomerCode()).isEqualTo("CUS00001");
        assertThat(captor.getValue().getOwnerId()).isEqualTo(SELF);
    }

    @Test
    void update_customerOutsideScope_isRejectedAsNotFound() {
        when(customerMapper.selectById(1L)).thenReturn(customer(1L, "ABC", OTHER));

        BizException e = assertThrows(BizException.class, () -> service.update(1L, update("ABC")));

        assertThat(e.getMessage()).isEqualTo("客户不存在或无权查看");
        verify(customerMapper, never()).updateById(any());
    }

    @Test
    void parties_switchDefault() {
        when(customerMapper.selectById(1L)).thenReturn(customer(1L, "ABC", SELF));
        when(partyMapper.selectList(any())).thenReturn(List.of(existingParty(1L, 1, true), existingParty(2L, 1, false)));
        UpdateCustomerRequest req = update("ABC");
        req.setParties(List.of(party(1L, 1, "A GmbH", false), party(2L, 1, "B GmbH", true)));

        service.update(1L, req);

        assertThat(defaultOf(1L)).isZero();
        assertThat(defaultOf(2L)).isEqualTo(1);
    }

    @Test
    void parties_deletingDefaultPromotesEarliestRemaining() {
        when(customerMapper.selectById(1L)).thenReturn(customer(1L, "ABC", SELF));
        when(partyMapper.selectList(any())).thenReturn(List.of(
                existingParty(1L, 1, true), existingParty(2L, 1, false), existingParty(3L, 1, false)));
        UpdateCustomerRequest req = update("ABC");
        req.setParties(List.of(party(3L, 1, "C GmbH", false), party(2L, 1, "B GmbH", false)));

        service.update(1L, req);

        assertThat(defaultOf(2L)).isEqualTo(1);
        assertThat(defaultOf(3L)).isZero();
        CustomerPartyDO deleted = updatedParties.stream().filter(p -> p.getId() == 1L).findFirst().orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    @Test
    void parties_eachTypeHasOwnDefaultAndNotifyDropsPort() {
        stubInsertCustomerId(12L);
        SaveCustomerRequest req = quick("ABC");
        CustomerPartyRequest notify = party(null, 2, "Forwarder GmbH", false);
        notify.setDestinationPort("Hamburg");
        req.setParties(List.of(party(null, 1, "ABC GmbH", false), notify));

        service.create(req);

        assertThat(insertedParties).allMatch(p -> p.getIsDefault() == 1);
        assertThat(insertedParties.get(1).getDestinationPort()).isEmpty();
    }

    @Test
    void parties_chineseCompanyNameRejected() {
        SaveCustomerRequest req = quick("ABC");
        req.setParties(List.of(party(null, 1, "汉堡物流", false)));
        assertThrows(BizException.class, () -> service.create(req));
    }

    // ---------------------------------------------------------------- 查询

    @Test
    void getDetail_outsideScope_isNotFound() {
        when(customerMapper.selectById(1L)).thenReturn(customer(1L, "ABC", OTHER));
        BizException e = assertThrows(BizException.class, () -> service.getDetail(1L));
        assertThat(e.getMessage()).isEqualTo("客户不存在或无权查看");
    }

    @Test
    void getRef_isNotScopedButOnlyReturnsReferenceFields() {
        CustomerDO c = customer(1L, "ABC", OTHER);
        c.setCustomerCode("CUS00001");
        when(customerMapper.selectById(1L)).thenReturn(c);

        assertThat(service.getRef(1L).getName()).isEqualTo("ABC");
        assertThat(service.getRef(1L).getCustomerCode()).isEqualTo("CUS00001");
    }

    @Test
    void getById_internalLookupIsNotScoped() {
        when(customerMapper.selectById(1L)).thenReturn(customer(1L, "ABC", OTHER));
        assertThat(service.getById(1L)).isNotNull();
    }

    // ---------------------------------------------------------------- 删除 / 启停

    @Test
    void delete_referencedCustomerIsRejected() {
        when(customerMapper.selectById(1L)).thenReturn(customer(1L, "ABC", SELF));
        when(customerMapper.selectReferencedCustomerIds(any())).thenReturn(List.of(1L));

        BizException e = assertThrows(BizException.class, () -> service.delete(1L));

        assertThat(e.getMessage()).isEqualTo("该客户已有询盘或单据记录，不能删除，可改为禁用");
        verify(customerMapper, never()).updateById(any());
    }

    @Test
    void batchDelete_mixedReferencedAndOutOfScope() {
        when(customerMapper.selectList(any())).thenReturn(List.of(
                customer(1L, "A", SELF), customer(2L, "B", SELF), customer(3L, "C", OTHER)));
        when(customerMapper.selectReferencedCustomerIds(any())).thenReturn(List.of(2L));

        CustomerBatchDeleteResultVO r = service.batchDelete(List.of(1L, 2L, 3L));

        assertThat(r.getDeleted()).isEqualTo(1);
        assertThat(r.getReferenced()).isEqualTo(1);
        assertThat(r.getMissing()).isEqualTo(1);
        ArgumentCaptor<CustomerDO> captor = ArgumentCaptor.forClass(CustomerDO.class);
        verify(customerMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void updateStatus_outsideScope_isRejected() {
        when(customerMapper.selectById(1L)).thenReturn(customer(1L, "A", OTHER));
        assertThrows(BizException.class, () -> service.updateStatus(1L, 0));
    }

    // ---------------------------------------------------------------- 转移

    @Test
    void transfer_batchUpdatesOwnerAndLogsEach() {
        when(dataScopeResolver.current()).thenReturn(new DataScope(DataScope.Type.CUSTOM, 99L, Set.of(99L, SELF, OTHER, 30L)));
        when(customerMapper.selectList(any())).thenReturn(List.of(customer(1L, "A", SELF), customer(2L, "B", OTHER)));
        when(userBasicMapper.selectById(30)).thenReturn(user(30L, "赵敏"));
        CustomerTransferRequest req = new CustomerTransferRequest();
        req.setIds(List.of(1L, 2L));
        req.setOwnerId(30L);
        req.setReason("岗位调整");

        service.transfer(req);

        ArgumentCaptor<CustomerDO> captor = ArgumentCaptor.forClass(CustomerDO.class);
        verify(customerMapper, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues()).allMatch(c -> c.getOwnerId() == 30L);
        verify(logService, times(2)).recordOperateLog(any(), any(), any(), any());
    }

    @Test
    void transfer_toOwnerOutsideScope_isRejected() {
        when(customerMapper.selectList(any())).thenReturn(List.of(customer(1L, "A", SELF)));
        when(userBasicMapper.selectById(30)).thenReturn(user(30L, "赵敏"));
        CustomerTransferRequest req = new CustomerTransferRequest();
        req.setIds(List.of(1L));
        req.setOwnerId(30L);

        assertThrows(BizException.class, () -> service.transfer(req));
        verify(customerMapper, never()).updateById(any());
    }

    @Test
    void assignableOwners_selfScopeReturnsScopeType() {
        when(userBasicMapper.selectList(any())).thenReturn(List.of(user(SELF, "张伟")));

        AssignableOwnersVO vo = service.assignableOwners();

        assertThat(vo.getScope()).isEqualTo("SELF");
        assertThat(vo.getOwners()).extracting(AssignableOwnersVO.OwnerOptionVO::getName).containsExactly("张伟");
    }

    // ---------------------------------------------------------------- 导出 / 补算

    @Test
    void export_writesHeaderAndRows() throws IOException {
        CustomerDO c = customer(1L, "ABC Automation GmbH", SELF);
        c.setCustomerCode("CUS00001");
        c.setCustomerRole(3);
        c.setCreditLimit(new BigDecimal("200000.00"));
        c.setCreditCurrency("EUR");
        when(customerMapper.selectCount(any())).thenReturn(1L);
        when(customerMapper.selectList(any())).thenReturn(List.of(c));

        try (Workbook wb = service.export(new CustomerPageQuery())) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("客户编码");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("经销商");
            assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).isEqualTo("张伟");
            assertThat(sheet.getRow(1).getCell(28).getStringCellValue()).isEqualTo("200000.00");
        }
    }

    @Test
    void export_overLimit_throws() {
        when(customerMapper.selectCount(any())).thenReturn((long) CustomerConstants.EXPORT_MAX_ROWS + 1);
        assertThrows(BizException.class, () -> service.export(new CustomerPageQuery()));
    }

    @Test
    void backfillNameKeys_fillsEmptyKeys() {
        when(customerMapper.selectList(any())).thenReturn(List.of(customer(1L, "ABC Automation GmbH", SELF)));

        assertThat(service.backfillNameKeys()).isEqualTo(1);
        verify(customerMapper).update(any(), any());
    }

    // ---------------------------------------------------------------- helpers

    private void stubInsertCustomerId(Long id) {
        doAnswer(inv -> {
            inv.<CustomerDO>getArgument(0).setId(id);
            return 1;
        }).when(customerMapper).insert(any(CustomerDO.class));
    }

    private int defaultOf(Long partyId) {
        return updatedParties.stream().filter(p -> p.getId().equals(partyId)).reduce((a, b) -> b)
                .orElseThrow().getIsDefault();
    }

    private static SaveCustomerRequest quick(String name) {
        SaveCustomerRequest req = new SaveCustomerRequest();
        req.setName(name);
        req.setCountry("germany");
        return req;
    }

    private static UpdateCustomerRequest update(String name) {
        UpdateCustomerRequest req = new UpdateCustomerRequest();
        req.setName(name);
        req.setCountry("Germany");
        req.setCustomerRole(3);
        req.setStatus(1);
        return req;
    }

    private static CustomerPartyRequest party(Long id, int type, String company, boolean isDefault) {
        CustomerPartyRequest p = new CustomerPartyRequest();
        p.setId(id);
        p.setPartyType(type);
        p.setCompanyName(company);
        p.setCountry("Germany");
        p.setAddress("Hafenstrasse 88");
        p.setDefaultParty(isDefault);
        return p;
    }

    private static CustomerPartyDO existingParty(Long id, int type, boolean isDefault) {
        CustomerPartyDO p = new CustomerPartyDO();
        p.setId(id);
        p.setPartyType(type);
        p.setIsDefault(isDefault ? 1 : 0);
        return p;
    }

    private static CustomerDO customer(Long id, String name, long ownerId) {
        CustomerDO c = new CustomerDO();
        c.setId(id);
        c.setTenantId(1);
        c.setName(name);
        c.setCountry("Germany");
        c.setOwnerId(ownerId);
        c.setStatus(1);
        c.setCreateTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        return c;
    }

    private static UserBasicDO user(long id, String name) {
        UserBasicDO u = new UserBasicDO();
        u.setId((int) id);
        u.setTenantId(1);
        u.setName(name);
        u.setStatus(1);
        return u;
    }
}

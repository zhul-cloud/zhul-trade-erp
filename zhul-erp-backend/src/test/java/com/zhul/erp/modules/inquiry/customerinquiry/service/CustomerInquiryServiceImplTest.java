package com.zhul.erp.modules.inquiry.customerinquiry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.dto.AiTaskVO;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.inquiry.constants.ConfidenceLevel;
import com.zhul.erp.modules.inquiry.customerinquiry.constants.CustomerInquirySource;
import com.zhul.erp.modules.inquiry.customerinquiry.constants.CustomerInquiryStatus;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AdvanceCustomerInquiryStatusRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.ConfirmSplitGroupRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.ConfirmSplitItemRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.ConfirmSplitRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.InquiryPreviewVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.SubmitCustomerInquiryRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.customerinquiry.service.impl.CustomerInquiryServiceImpl;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderVO;
import com.zhul.erp.modules.inquiry.inquiryorder.service.InquiryOrderService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryCodeGenerator;
import com.zhul.erp.modules.masterdata.dto.CustomerVO;
import com.zhul.erp.modules.masterdata.service.CustomerService;
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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对应 specs/inquiry/customer-inquiry/spec.md 全部场景：
 * 提交不自动解析、手动发起解析、AI结果异步接收、拆单预览确认前不落地、
 * 确认拆单生成询盘单（含空分组跳过）、状态人工流转。
 */
@ExtendWith(MockitoExtension.class)
class CustomerInquiryServiceImplTest {

    @Mock
    private CustomerInquiryMapper customerInquiryMapper;
    @Mock
    private InquiryCodeGenerator codeGenerator;
    @Mock
    private AiTaskService aiTaskService;
    @Mock
    private InquiryOrderService inquiryOrderService;
    @Mock
    private CustomerService customerService;
    @Mock
    private CurrentUserResolver currentUserResolver;

    private CustomerInquiryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomerInquiryServiceImpl(customerInquiryMapper, codeGenerator, aiTaskService,
                inquiryOrderService, customerService, currentUserResolver, new ObjectMapper());
        TenantContext.setTenantId(1);
    }

    private void stubCodeGenerator() {
        when(codeGenerator.nextCustomerInquiryCode(anyInt()))
                .thenReturn(new com.zhul.erp.modules.inquiry.support.GeneratedInquiryCode("IQ20260901001", false));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void page_withNoFilters_returnsMappedRecords() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<CustomerInquiryDO> mpPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        mpPage.setRecords(List.of(inquiry));
        mpPage.setTotal(1L);
        when(customerInquiryMapper.selectPage(any(), any())).thenReturn(mpPage);

        var result = service.page(new com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryPageQuery());

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    void page_withCustomerNameNotMatchingAnyCustomer_returnsEmptyWithoutQueryingMapper() {
        when(customerService.search("不存在的客户")).thenReturn(List.of());

        var query = new com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryPageQuery();
        query.setCustomerName("不存在的客户");

        var result = service.page(query);

        assertThat(result.getTotal()).isEqualTo(0L);
        assertThat(result.getRecords()).isEmpty();
        verify(customerInquiryMapper, never()).selectPage(any(), any());
    }

    @Test
    void page_withStatusAndDateFilters_appliesAllPredicates() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<CustomerInquiryDO> mpPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        mpPage.setRecords(List.of());
        mpPage.setTotal(0L);
        CustomerVO customerVo = new CustomerVO();
        customerVo.setId(10L);
        when(customerService.search("E2E")).thenReturn(List.of(customerVo));
        when(customerInquiryMapper.selectPage(any(), any())).thenReturn(mpPage);

        var query = new com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryPageQuery();
        query.setInquiryCode("IQ2026");
        query.setCustomerName("E2E");
        query.setStatusList(List.of(CustomerInquiryStatus.PENDING_CONFIRM));
        query.setInquiryDateFrom(java.time.LocalDate.of(2026, 1, 1));
        query.setInquiryDateTo(java.time.LocalDate.of(2026, 12, 31));
        query.setOwnerId(1000000000L);

        var result = service.page(query);

        assertThat(result.getTotal()).isEqualTo(0L);
    }

    @Test
    void submit_createsPendingParseStatus_withoutCreatingAiTask() {
        stubCodeGenerator();
        when(customerService.getById(10L)).thenReturn(new CustomerVO());

        SubmitCustomerInquiryRequest req = new SubmitCustomerInquiryRequest();
        req.setCustomerId(10L);
        req.setSource(CustomerInquirySource.TEXT);
        req.setRawContent("Siemens PLC x20");

        CustomerInquiryVO result = service.submit(req);

        assertThat(result.getStatus()).isEqualTo(CustomerInquiryStatus.PENDING_PARSE);
        assertThat(result.getAiTaskId()).isNull();
        verify(aiTaskService, never()).createAndSubmit(anyString(), anyString(), any());
        verify(customerInquiryMapper, times(1)).insert(any(CustomerInquiryDO.class));
    }

    @Test
    void submit_withNonExistentCustomer_throwsBizException() {
        when(customerService.getById(999L)).thenReturn(null);

        SubmitCustomerInquiryRequest req = new SubmitCustomerInquiryRequest();
        req.setCustomerId(999L);
        req.setSource(CustomerInquirySource.TEXT);

        assertThrows(BizException.class, () -> service.submit(req));
        verify(customerInquiryMapper, never()).insert(any());
    }

    @Test
    void submit_withoutCustomerId_failsBeanValidation() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        SubmitCustomerInquiryRequest req = new SubmitCustomerInquiryRequest();
        req.setSource(CustomerInquirySource.TEXT);

        Set<ConstraintViolation<SubmitCustomerInquiryRequest>> violations = validator.validate(req);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void startAiParse_fromPendingParse_createsTaskAndTransitionsToParsing() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        when(customerInquiryMapper.selectById(1L)).thenReturn(inquiry);
        AiTaskVO task = new AiTaskVO();
        task.setId(500L);
        when(aiTaskService.createAndSubmit(eq("inquiry-parse-and-split"), anyString(), any())).thenReturn(task);

        CustomerInquiryVO result = service.startAiParse(1L);

        assertThat(result.getStatus()).isEqualTo(CustomerInquiryStatus.PARSING);
        assertThat(result.getAiTaskId()).isEqualTo(500L);
    }

    @Test
    void startAiParse_fromNonPendingParseStatus_throwsBizException() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        inquiry.setStatus(CustomerInquiryStatus.PARSING);
        when(customerInquiryMapper.selectById(1L)).thenReturn(inquiry);

        assertThrows(BizException.class, () -> service.startAiParse(1L));
        verify(aiTaskService, never()).createAndSubmit(anyString(), anyString(), any());
    }

    @Test
    void retryParse_fromParseFailed_createsNewTaskAndTransitionsToParsing() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        inquiry.setStatus(CustomerInquiryStatus.PARSE_FAILED);
        inquiry.setAiTaskId(500L);
        inquiry.setRemark("java.net.ConnectException");
        when(customerInquiryMapper.selectById(1L)).thenReturn(inquiry);
        AiTaskVO task = new AiTaskVO();
        task.setId(600L);
        when(aiTaskService.createAndSubmit(eq("inquiry-parse-and-split"), anyString(), any())).thenReturn(task);

        CustomerInquiryVO result = service.retryParse(1L);

        assertThat(result.getStatus()).isEqualTo(CustomerInquiryStatus.PARSING);
        assertThat(result.getAiTaskId()).isEqualTo(600L);
        ArgumentCaptor<CustomerInquiryDO> captor = ArgumentCaptor.forClass(CustomerInquiryDO.class);
        verify(customerInquiryMapper).updateById(captor.capture());
        assertThat(captor.getValue().getRemark()).isEmpty();
    }

    @Test
    void retryParse_fromNonParseFailedStatus_throwsBizException() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        inquiry.setStatus(CustomerInquiryStatus.PENDING_PARSE);
        when(customerInquiryMapper.selectById(1L)).thenReturn(inquiry);

        assertThrows(BizException.class, () -> service.retryParse(1L));
        verify(aiTaskService, never()).createAndSubmit(anyString(), anyString(), any());
    }

    @Test
    void applyParseSuccess_computesStatsAndTransitionsToPendingConfirm() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        inquiry.setStatus(CustomerInquiryStatus.PARSING);
        inquiry.setAiTaskId(500L);
        when(customerInquiryMapper.selectOne(any())).thenReturn(inquiry);

        String output = "{\"groups\":[{\"brand\":\"Siemens\",\"category\":\"PLC\",\"items\":["
                + "{\"originalModel\":\"A\",\"confirmedModel\":\"A\",\"confidence\":1},"
                + "{\"originalModel\":\"B\",\"confirmedModel\":\"B\",\"confidence\":4}"
                + "]}]}";

        service.applyParseSuccess(500L, output);

        ArgumentCaptor<CustomerInquiryDO> captor = ArgumentCaptor.forClass(CustomerInquiryDO.class);
        verify(customerInquiryMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CustomerInquiryStatus.PENDING_CONFIRM);
        assertThat(captor.getValue().getTotalItemCount()).isEqualTo(2);
        assertThat(captor.getValue().getPendingVerifyCount()).isEqualTo(1);
    }

    @Test
    void applyParseFailure_setsFailedStatusAndErrorMessage() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        inquiry.setStatus(CustomerInquiryStatus.PARSING);
        inquiry.setAiTaskId(500L);
        when(customerInquiryMapper.selectOne(any())).thenReturn(inquiry);

        service.applyParseFailure(500L, "联网搜索超时");

        ArgumentCaptor<CustomerInquiryDO> captor = ArgumentCaptor.forClass(CustomerInquiryDO.class);
        verify(customerInquiryMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CustomerInquiryStatus.PARSE_FAILED);
        assertThat(captor.getValue().getRemark()).isEqualTo("联网搜索超时");
    }

    @Test
    void applyParseSuccess_whenNoMatchingInquiry_doesNothingSilently() {
        when(customerInquiryMapper.selectOne(any())).thenReturn(null);

        service.applyParseSuccess(999L, "{\"groups\":[]}");

        verify(customerInquiryMapper, never()).updateById(any());
    }

    @Test
    void getPreview_computesSummaryStatsFromAiTaskOutput() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        inquiry.setStatus(CustomerInquiryStatus.PENDING_CONFIRM);
        inquiry.setAiTaskId(500L);
        when(customerInquiryMapper.selectById(1L)).thenReturn(inquiry);

        AiTaskVO task = new AiTaskVO();
        task.setOutput("{\"groups\":[{\"brand\":\"Siemens\",\"category\":\"PLC\",\"items\":["
                + "{\"originalModel\":\"A\",\"confidence\":1},"
                + "{\"originalModel\":\"B\",\"confidence\":2},"
                + "{\"originalModel\":\"C\",\"confidence\":3},"
                + "{\"originalModel\":\"D\",\"confidence\":4}"
                + "]}]}");
        when(aiTaskService.getById(500L)).thenReturn(task);

        InquiryPreviewVO preview = service.getPreview(1L);

        assertThat(preview.getTotalItemCount()).isEqualTo(4);
        assertThat(preview.getConfirmedCount()).isEqualTo(1);
        assertThat(preview.getCorrectedCount()).isEqualTo(1);
        assertThat(preview.getPendingVerifyCount()).isEqualTo(1);
        assertThat(preview.getUnrecognizedCount()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmSplit_createsOrdersForNonEmptyGroupsOnly() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        inquiry.setStatus(CustomerInquiryStatus.PENDING_CONFIRM);
        inquiry.setAiTaskId(500L);
        when(customerInquiryMapper.selectById(1L)).thenReturn(inquiry);

        AiTaskVO task = new AiTaskVO();
        task.setOutput("{\"groups\":["
                + "{\"brand\":\"Siemens\",\"category\":\"PLC\",\"inquiryTemplate\":\"t1\",\"items\":[{\"originalModel\":\"A\",\"confirmedModel\":\"A\",\"confidence\":1}]},"
                + "{\"brand\":\"Schneider\",\"category\":\"变频器\",\"items\":[{\"originalModel\":\"B\",\"confirmedModel\":\"B\",\"confidence\":1}]}"
                + "]}");
        when(aiTaskService.getById(500L)).thenReturn(task);
        when(inquiryOrderService.createFromConfirmedGroups(eq(1L), eq("IQ20260901001"), eq(500L), any()))
                .thenReturn(List.of(new InquiryOrderVO()));

        ConfirmSplitRequest req = new ConfirmSplitRequest();
        ConfirmSplitGroupRequest group0 = new ConfirmSplitGroupRequest();
        group0.setGroupIndex(0);
        ConfirmSplitItemRequest item = new ConfirmSplitItemRequest();
        item.setOriginalModel("A");
        item.setConfirmedModel("A");
        item.setConfidence(ConfidenceLevel.CONFIRMED);
        group0.setItems(List.of(item));

        ConfirmSplitGroupRequest group1Empty = new ConfirmSplitGroupRequest();
        group1Empty.setGroupIndex(1);
        group1Empty.setItems(List.of()); // 全部移除的分组

        req.setGroups(List.of(group0, group1Empty));

        service.confirmSplit(1L, req);

        ArgumentCaptor<List<com.zhul.erp.modules.inquiry.support.ConfirmedGroupDTO>> groupsCaptor = ArgumentCaptor.forClass(List.class);
        verify(inquiryOrderService, times(1)).createFromConfirmedGroups(eq(1L), eq("IQ20260901001"), eq(500L), groupsCaptor.capture());
        assertThat(groupsCaptor.getValue()).hasSize(1); // 空分组被跳过，未传给 inquiryOrderService
        assertThat(groupsCaptor.getValue().get(0).getBrand()).isEqualTo("Siemens");

        ArgumentCaptor<CustomerInquiryDO> captor = ArgumentCaptor.forClass(CustomerInquiryDO.class);
        verify(customerInquiryMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CustomerInquiryStatus.CONFIRMED);
        assertThat(captor.getValue().getTotalOrderCount()).isEqualTo(1);
    }

    @Test
    void confirmSplit_fromNonPendingConfirmStatus_throwsBizException() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        inquiry.setStatus(CustomerInquiryStatus.PENDING_PARSE);
        when(customerInquiryMapper.selectById(1L)).thenReturn(inquiry);

        assertThrows(BizException.class, () -> service.confirmSplit(1L, new ConfirmSplitRequest()));
    }

    @Test
    void advanceStatus_legalSequentialTransition_succeeds() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        inquiry.setStatus(CustomerInquiryStatus.CONFIRMED);
        when(customerInquiryMapper.selectById(1L)).thenReturn(inquiry);

        AdvanceCustomerInquiryStatusRequest req = new AdvanceCustomerInquiryStatusRequest();
        req.setTargetStatus(CustomerInquiryStatus.PENDING_QUOTE);

        service.advanceStatus(1L, req);

        ArgumentCaptor<CustomerInquiryDO> captor = ArgumentCaptor.forClass(CustomerInquiryDO.class);
        verify(customerInquiryMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CustomerInquiryStatus.PENDING_QUOTE);
    }

    @Test
    void advanceStatus_illegalSkipTransition_throwsBizException() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        inquiry.setStatus(CustomerInquiryStatus.CONFIRMED);
        when(customerInquiryMapper.selectById(1L)).thenReturn(inquiry);

        AdvanceCustomerInquiryStatusRequest req = new AdvanceCustomerInquiryStatusRequest();
        req.setTargetStatus(CustomerInquiryStatus.QUOTED); // 跳过了 待报价、报价中

        assertThrows(BizException.class, () -> service.advanceStatus(1L, req));
    }

    @Test
    void advanceStatus_cancelFromActiveState_succeeds() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        inquiry.setStatus(CustomerInquiryStatus.QUOTING);
        when(customerInquiryMapper.selectById(1L)).thenReturn(inquiry);

        AdvanceCustomerInquiryStatusRequest req = new AdvanceCustomerInquiryStatusRequest();
        req.setTargetStatus(CustomerInquiryStatus.CANCELLED);

        service.advanceStatus(1L, req);

        ArgumentCaptor<CustomerInquiryDO> captor = ArgumentCaptor.forClass(CustomerInquiryDO.class);
        verify(customerInquiryMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CustomerInquiryStatus.CANCELLED);
    }

    @Test
    void cancel_fromDealStatus_throwsBizException() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        inquiry.setStatus(CustomerInquiryStatus.DEAL);
        when(customerInquiryMapper.selectById(1L)).thenReturn(inquiry);

        assertThrows(BizException.class, () -> service.cancel(1L));
    }

    @Test
    void cancel_fromActiveStatus_succeeds() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        inquiry.setStatus(CustomerInquiryStatus.PENDING_QUOTE);
        when(customerInquiryMapper.selectById(1L)).thenReturn(inquiry);

        service.cancel(1L);

        ArgumentCaptor<CustomerInquiryDO> captor = ArgumentCaptor.forClass(CustomerInquiryDO.class);
        verify(customerInquiryMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CustomerInquiryStatus.CANCELLED);
    }

    @Test
    void advanceStatus_cancelWhenAlreadyCancelled_throwsBizException() {
        CustomerInquiryDO inquiry = pendingParseInquiry();
        inquiry.setStatus(CustomerInquiryStatus.CANCELLED);
        when(customerInquiryMapper.selectById(1L)).thenReturn(inquiry);

        AdvanceCustomerInquiryStatusRequest req = new AdvanceCustomerInquiryStatusRequest();
        req.setTargetStatus(CustomerInquiryStatus.CANCELLED);

        assertThrows(BizException.class, () -> service.advanceStatus(1L, req));
    }

    private CustomerInquiryDO pendingParseInquiry() {
        CustomerInquiryDO inquiry = new CustomerInquiryDO();
        inquiry.setId(1L);
        inquiry.setTenantId(1);
        inquiry.setInquiryCode("IQ20260901001");
        inquiry.setCustomerId(10L);
        inquiry.setStatus(CustomerInquiryStatus.PENDING_PARSE);
        return inquiry;
    }
}

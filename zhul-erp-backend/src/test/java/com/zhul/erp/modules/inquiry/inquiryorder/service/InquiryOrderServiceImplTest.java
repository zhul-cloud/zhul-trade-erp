package com.zhul.erp.modules.inquiry.inquiryorder.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.constants.ChannelPlatform;
import com.zhul.erp.modules.inquiry.constants.ConfidenceLevel;
import com.zhul.erp.modules.inquiry.constants.InquirySourceType;
import com.zhul.erp.modules.inquiry.inquiryorder.constants.InquiryOrderStatus;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.AddInquiryOrderSupplierRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.AdvanceInquiryOrderStatusRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.AssignPurchaserRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.ConvertToSupplierRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.CreateInquiryOrderManualRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderSupplierVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.ManualItemRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.QuoteComparisonVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.QuoteVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.RecordQuoteRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.entity.InquiryOrderDO;
import com.zhul.erp.modules.inquiry.inquiryorder.entity.InquiryOrderItemDO;
import com.zhul.erp.modules.inquiry.inquiryorder.entity.InquiryOrderItemQuoteDO;
import com.zhul.erp.modules.inquiry.inquiryorder.entity.InquiryOrderSupplierDO;
import com.zhul.erp.modules.inquiry.inquiryorder.repository.InquiryOrderItemMapper;
import com.zhul.erp.modules.inquiry.inquiryorder.repository.InquiryOrderItemQuoteMapper;
import com.zhul.erp.modules.inquiry.inquiryorder.repository.InquiryOrderMapper;
import com.zhul.erp.modules.inquiry.inquiryorder.repository.InquiryOrderSupplierMapper;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.inquiryorder.service.impl.InquiryOrderServiceImpl;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.GeneratedInquiryCode;
import com.zhul.erp.modules.inquiry.support.InquiryCodeGenerator;
import com.zhul.erp.modules.masterdata.dto.SupplierCreateResultVO;
import com.zhul.erp.modules.masterdata.dto.SupplierVO;
import com.zhul.erp.modules.masterdata.service.SupplierService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对应 specs/inquiry/inquiry-order/spec.md 全部场景：手动创建、分配/重新分配、
 * 报价来源类型校验、转为正式供应商、报价录入（HALF_UP精度）、报价对比取最低价、
 * 状态人工流转。
 */
@ExtendWith(MockitoExtension.class)
class InquiryOrderServiceImplTest {

    @Mock
    private InquiryOrderMapper inquiryOrderMapper;
    @Mock
    private InquiryOrderItemMapper inquiryOrderItemMapper;
    @Mock
    private InquiryOrderSupplierMapper inquiryOrderSupplierMapper;
    @Mock
    private InquiryOrderItemQuoteMapper inquiryOrderItemQuoteMapper;
    @Mock
    private InquiryCodeGenerator codeGenerator;
    @Mock
    private CurrentUserResolver currentUserResolver;
    @Mock
    private SupplierService supplierService;
    @Mock
    private CustomerInquiryMapper customerInquiryMapper;

    private InquiryOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InquiryOrderServiceImpl(inquiryOrderMapper, inquiryOrderItemMapper, inquiryOrderSupplierMapper,
                inquiryOrderItemQuoteMapper, codeGenerator, currentUserResolver, supplierService, customerInquiryMapper);
        TenantContext.setTenantId(1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createManual_withoutCustomer_usesStandaloneCodeAndConfirmedConfidence() {
        when(codeGenerator.nextStandaloneInquiryOrderCode(1))
                .thenReturn(new GeneratedInquiryCode("IQ20260901005A", false));

        CreateInquiryOrderManualRequest req = new CreateInquiryOrderManualRequest();
        req.setBrand("Omron");
        req.setCategory("传感器");
        ManualItemRequest item = new ManualItemRequest();
        item.setModel("E3F-DS30C4");
        item.setQuantity(15);
        item.setUnit("个");
        req.setItems(List.of(item));

        InquiryOrderVO result = service.createManual(req);

        assertThat(result.getInquiryCode()).isEqualTo("IQ20260901005A");
        assertThat(result.getStatus()).isEqualTo(InquiryOrderStatus.PENDING_ASSIGN);
        assertThat(result.getAiTaskId()).isNull();
        assertThat(result.getCustomerId()).isNull();
        assertThat(result.getCustomerInquiryId()).isNull();

        ArgumentCaptor<InquiryOrderItemDO> itemCaptor = ArgumentCaptor.forClass(InquiryOrderItemDO.class);
        verify(inquiryOrderItemMapper, times(1)).insert(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getConfidence()).isEqualTo(ConfidenceLevel.CONFIRMED);
        assertThat(itemCaptor.getValue().getOriginalModel()).isEqualTo("E3F-DS30C4");
        assertThat(itemCaptor.getValue().getConfirmedModel()).isEqualTo("E3F-DS30C4");
    }

    @Test
    void createManual_withCustomerSelected_setsCustomerIdAndStillUsesStandaloneCode() {
        // 选了客户不等于有父级客户询盘——两个外键独立，编号规则恒为"无父级"独立编号，
        // 不借用"有父级"的字母后缀规则，见 design.md 决策12（否决了挂靠 customer_inquiry 的方案）。
        when(codeGenerator.nextStandaloneInquiryOrderCode(1))
                .thenReturn(new GeneratedInquiryCode("IQ20260901006A", false));

        CreateInquiryOrderManualRequest req = new CreateInquiryOrderManualRequest();
        req.setCustomerId(10L);
        req.setBrand("Omron");
        req.setCategory("传感器");
        ManualItemRequest item = new ManualItemRequest();
        item.setModel("E3F-DS30C4");
        req.setItems(List.of(item));

        InquiryOrderVO result = service.createManual(req);

        assertThat(result.getCustomerId()).isEqualTo(10L);
        assertThat(result.getCustomerInquiryId()).isNull();
        verify(codeGenerator, times(1)).nextStandaloneInquiryOrderCode(1);
        verify(codeGenerator, org.mockito.Mockito.never()).nextInquiryOrderCodeForParent(anyInt(), any(), any());
    }

    @Test
    void createManual_withCustomerInquiryId_linksExistingInquiryAndUsesParentCode() {
        // 入口B（P03"解析失败"态兜底手动创建）：关联一条已经真实存在的 customer_inquiry，
        // 不是 design.md 决策12否决的"自动新建空壳记录"方案；customerId 传入也会被忽略。
        com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO parent =
                new com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO();
        parent.setId(99L);
        parent.setInquiryCode("IQ20260901007");
        when(customerInquiryMapper.selectById(99L)).thenReturn(parent);
        when(codeGenerator.nextInquiryOrderCodeForParent(1, 99L, "IQ20260901007"))
                .thenReturn(new GeneratedInquiryCode("IQ20260901007A", false));

        CreateInquiryOrderManualRequest req = new CreateInquiryOrderManualRequest();
        req.setCustomerInquiryId(99L);
        req.setCustomerId(10L); // 应被忽略：customerInquiryId 优先，两者互斥
        req.setBrand("Siemens");
        req.setCategory("PLC");
        ManualItemRequest item = new ManualItemRequest();
        item.setModel("6ES7214-1AG40-0XB0");
        req.setItems(List.of(item));

        InquiryOrderVO result = service.createManual(req);

        assertThat(result.getInquiryCode()).isEqualTo("IQ20260901007A");
        assertThat(result.getCustomerInquiryId()).isEqualTo(99L);
        assertThat(result.getCustomerId()).isNull();
        verify(codeGenerator, org.mockito.Mockito.never()).nextStandaloneInquiryOrderCode(anyInt());
    }

    @Test
    void createManual_withUnknownCustomerInquiryId_throws() {
        when(customerInquiryMapper.selectById(404L)).thenReturn(null);

        CreateInquiryOrderManualRequest req = new CreateInquiryOrderManualRequest();
        req.setCustomerInquiryId(404L);
        req.setBrand("Siemens");
        req.setCategory("PLC");
        ManualItemRequest item = new ManualItemRequest();
        item.setModel("6ES7214-1AG40-0XB0");
        req.setItems(List.of(item));

        assertThrows(BizException.class, () -> service.createManual(req));
    }

    @Test
    void assign_firstTime_transitionsPendingAssignToAssigned() {
        InquiryOrderDO order = orderWithStatus(InquiryOrderStatus.PENDING_ASSIGN);
        when(inquiryOrderMapper.selectById(1L)).thenReturn(order);

        AssignPurchaserRequest req = new AssignPurchaserRequest();
        req.setAssigneeId(2000L);
        service.assign(1L, req);

        ArgumentCaptor<InquiryOrderDO> captor = ArgumentCaptor.forClass(InquiryOrderDO.class);
        verify(inquiryOrderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InquiryOrderStatus.ASSIGNED);
        assertThat(captor.getValue().getAssigneeId()).isEqualTo(2000L);
    }

    @Test
    void assign_reassign_overwritesAssigneeWithoutTouchingLaterStatus() {
        InquiryOrderDO order = orderWithStatus(InquiryOrderStatus.SENT_TO_SUPPLIER);
        order.setAssigneeId(2000L);
        when(inquiryOrderMapper.selectById(1L)).thenReturn(order);

        AssignPurchaserRequest req = new AssignPurchaserRequest();
        req.setAssigneeId(3000L);
        service.assign(1L, req);

        ArgumentCaptor<InquiryOrderDO> captor = ArgumentCaptor.forClass(InquiryOrderDO.class);
        verify(inquiryOrderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getAssigneeId()).isEqualTo(3000L);
        assertThat(captor.getValue().getStatus()).isEqualTo(InquiryOrderStatus.SENT_TO_SUPPLIER);
    }

    @Test
    void addSupplier_formalSupplierWithoutSupplierId_throwsBizException() {
        AddInquiryOrderSupplierRequest req = new AddInquiryOrderSupplierRequest();
        req.setSourceType(InquirySourceType.FORMAL_SUPPLIER);

        assertThrows(BizException.class, () -> service.addSupplier(1L, req));
        verify(inquiryOrderSupplierMapper, org.mockito.Mockito.never()).insert(any());
    }

    @Test
    void addSupplier_ecommerceChannelWithoutChannelName_throwsBizException() {
        AddInquiryOrderSupplierRequest req = new AddInquiryOrderSupplierRequest();
        req.setSourceType(InquirySourceType.ECOMMERCE_CHANNEL);
        req.setChannelPlatform(ChannelPlatform.ALIBABA_1688);

        assertThrows(BizException.class, () -> service.addSupplier(1L, req));
    }

    @Test
    void addSupplier_ecommerceChannelWithValidFields_succeeds() {
        AddInquiryOrderSupplierRequest req = new AddInquiryOrderSupplierRequest();
        req.setSourceType(InquirySourceType.ECOMMERCE_CHANNEL);
        req.setChannelPlatform(ChannelPlatform.ALIBABA_1688);
        req.setChannelName("东莞市XX五金机电经营部");

        InquiryOrderSupplierVO result = service.addSupplier(1L, req);

        assertThat(result.getDisplayName()).isEqualTo("1688·东莞市XX五金机电经营部");
        verify(inquiryOrderSupplierMapper, times(1)).insert(any(InquiryOrderSupplierDO.class));
    }

    @Test
    void convertToFormalSupplier_onEcommerceChannel_updatesSourceTypeAndKeepsChannelFields() {
        InquiryOrderSupplierDO assoc = new InquiryOrderSupplierDO();
        assoc.setId(1L);
        assoc.setSourceType(InquirySourceType.ECOMMERCE_CHANNEL);
        assoc.setChannelPlatform(ChannelPlatform.ALIBABA_1688);
        assoc.setChannelName("东莞市XX五金机电经营部");
        when(inquiryOrderSupplierMapper.selectById(1L)).thenReturn(assoc);

        SupplierCreateResultVO createResult = new SupplierCreateResultVO();
        createResult.setDuplicate(false);
        SupplierVO created = new SupplierVO();
        created.setId(999L);
        created.setName("东莞市XX五金机电经营部");
        createResult.setCreatedSupplier(created);
        when(supplierService.createFromChannel(any())).thenReturn(createResult);
        when(supplierService.getById(999L)).thenReturn(created);

        InquiryOrderSupplierVO result = service.convertToFormalSupplier(1L, new ConvertToSupplierRequest());

        assertThat(result.getSourceType()).isEqualTo(InquirySourceType.FORMAL_SUPPLIER);
        assertThat(result.getSupplierId()).isEqualTo(999L);
        assertThat(assoc.getChannelName()).isEqualTo("东莞市XX五金机电经营部"); // 渠道信息保留不清空
    }

    @Test
    void convertToFormalSupplier_onFormalSupplierSource_throwsBizException() {
        InquiryOrderSupplierDO assoc = new InquiryOrderSupplierDO();
        assoc.setId(1L);
        assoc.setSourceType(InquirySourceType.FORMAL_SUPPLIER);
        when(inquiryOrderSupplierMapper.selectById(1L)).thenReturn(assoc);

        assertThrows(BizException.class, () -> service.convertToFormalSupplier(1L, new ConvertToSupplierRequest()));
    }

    @Test
    void recordQuote_withValidRateAndOriginal_calculatesCnyWithHalfUp() {
        when(inquiryOrderItemQuoteMapper.selectOne(any())).thenReturn(null);

        RecordQuoteRequest req = new RecordQuoteRequest();
        req.setInquiryOrderItemId(10L);
        req.setInquiryOrderSupplierId(20L);
        req.setQuotePriceOriginal(new BigDecimal("168.00"));
        req.setCurrencyCode("USD");
        req.setExchangeRate(new BigDecimal("7.1686")); // 168 * 7.1686 = 1204.3248 -> HALF_UP -> 1204.32
        req.setQuoteStatus(2);

        QuoteVO result = service.recordQuote(1L, req);

        assertThat(result.getQuotePriceCny()).isEqualByComparingTo("1204.32");
    }

    @Test
    void recordQuote_withMissingExchangeRate_leavesQuotePriceCnyNull() {
        when(inquiryOrderItemQuoteMapper.selectOne(any())).thenReturn(null);

        RecordQuoteRequest req = new RecordQuoteRequest();
        req.setInquiryOrderItemId(10L);
        req.setInquiryOrderSupplierId(20L);
        req.setQuotePriceOriginal(new BigDecimal("100.00"));
        req.setCurrencyCode("USD");
        req.setExchangeRate(null);
        req.setQuoteStatus(1); // 待报价

        QuoteVO result = service.recordQuote(1L, req);

        assertThat(result.getQuotePriceCny()).isNull();
    }

    @Test
    void recordQuote_withZeroPrice_calculatesZeroNotNull() {
        when(inquiryOrderItemQuoteMapper.selectOne(any())).thenReturn(null);

        RecordQuoteRequest req = new RecordQuoteRequest();
        req.setInquiryOrderItemId(10L);
        req.setInquiryOrderSupplierId(20L);
        req.setQuotePriceOriginal(BigDecimal.ZERO);
        req.setCurrencyCode("CNY");
        req.setExchangeRate(BigDecimal.ONE);
        req.setQuoteStatus(2);

        QuoteVO result = service.recordQuote(1L, req);

        assertThat(result.getQuotePriceCny()).isEqualByComparingTo("0.00");
    }

    @Test
    void recordQuote_withLargeAmount_roundsHalfUpCorrectly() {
        when(inquiryOrderItemQuoteMapper.selectOne(any())).thenReturn(null);

        RecordQuoteRequest req = new RecordQuoteRequest();
        req.setInquiryOrderItemId(10L);
        req.setInquiryOrderSupplierId(20L);
        req.setQuotePriceOriginal(new BigDecimal("999999.995")); // 三位小数触发四舍五入
        req.setCurrencyCode("CNY");
        req.setExchangeRate(BigDecimal.ONE);
        req.setQuoteStatus(2);

        QuoteVO result = service.recordQuote(1L, req);

        assertThat(result.getQuotePriceCny()).isEqualByComparingTo("1000000.00");
    }

    @Test
    void recordQuote_whenExistingQuote_updatesInsteadOfInserting() {
        InquiryOrderItemQuoteDO existing = new InquiryOrderItemQuoteDO();
        existing.setId(5L);
        when(inquiryOrderItemQuoteMapper.selectOne(any())).thenReturn(existing);

        RecordQuoteRequest req = new RecordQuoteRequest();
        req.setInquiryOrderItemId(10L);
        req.setInquiryOrderSupplierId(20L);
        req.setQuotePriceOriginal(new BigDecimal("50.00"));
        req.setCurrencyCode("CNY");
        req.setExchangeRate(BigDecimal.ONE);
        req.setQuoteStatus(2);

        service.recordQuote(1L, req);

        verify(inquiryOrderItemQuoteMapper, times(1)).updateById(any());
        verify(inquiryOrderItemQuoteMapper, org.mockito.Mockito.never()).insert(any());
    }

    @Test
    void getQuoteComparison_marksLowestPriceAndUnquotedCells() {
        InquiryOrderItemDO item = new InquiryOrderItemDO();
        item.setId(100L);
        item.setConfirmedModel("6ES7214-1AG40-0XB0");
        when(inquiryOrderItemMapper.selectList(any())).thenReturn(List.of(item));

        InquiryOrderSupplierDO supplierA = new InquiryOrderSupplierDO();
        supplierA.setId(1L);
        supplierA.setSourceType(InquirySourceType.FORMAL_SUPPLIER);
        supplierA.setSupplierId(11L);
        InquiryOrderSupplierDO supplierB = new InquiryOrderSupplierDO();
        supplierB.setId(2L);
        supplierB.setSourceType(InquirySourceType.FORMAL_SUPPLIER);
        supplierB.setSupplierId(12L);
        when(inquiryOrderSupplierMapper.selectList(any())).thenReturn(List.of(supplierA, supplierB));
        SupplierVO supplierVoA = new SupplierVO();
        supplierVoA.setId(11L);
        supplierVoA.setName("上海科菱");
        SupplierVO supplierVoB = new SupplierVO();
        supplierVoB.setId(12L);
        supplierVoB.setName("深圳智控");
        when(supplierService.getById(11L)).thenReturn(supplierVoA);
        when(supplierService.getById(12L)).thenReturn(supplierVoB);

        InquiryOrderItemQuoteDO quoteA = new InquiryOrderItemQuoteDO();
        quoteA.setInquiryOrderItemId(100L);
        quoteA.setInquiryOrderSupplierId(1L);
        quoteA.setQuotePriceCny(new BigDecimal("1280.00"));
        InquiryOrderItemQuoteDO quoteB = new InquiryOrderItemQuoteDO();
        quoteB.setInquiryOrderItemId(100L);
        quoteB.setInquiryOrderSupplierId(2L);
        quoteB.setQuotePriceCny(new BigDecimal("1204.32"));
        when(inquiryOrderItemQuoteMapper.selectList(any())).thenReturn(List.of(quoteA, quoteB));

        QuoteComparisonVO result = service.getQuoteComparison(1L);

        assertThat(result.getRows()).hasSize(1);
        var row = result.getRows().get(0);
        assertThat(row.getCellsBySupplierId().get(1L).isLowestPrice()).isFalse();
        assertThat(row.getCellsBySupplierId().get(2L).isLowestPrice()).isTrue();
        assertThat(row.getCellsBySupplierId().get(1L).isQuoted()).isTrue();
    }

    @Test
    void getQuoteComparison_returnsEmptyColumnsOnly_whenNoItems() {
        when(inquiryOrderItemMapper.selectList(any())).thenReturn(List.of());
        InquiryOrderSupplierDO supplierA = new InquiryOrderSupplierDO();
        supplierA.setId(1L);
        supplierA.setSourceType(InquirySourceType.FORMAL_SUPPLIER);
        supplierA.setSupplierId(11L);
        when(inquiryOrderSupplierMapper.selectList(any())).thenReturn(List.of(supplierA));
        SupplierVO supplierVoA = new SupplierVO();
        supplierVoA.setId(11L);
        supplierVoA.setName("上海科菱");
        when(supplierService.getById(11L)).thenReturn(supplierVoA);

        QuoteComparisonVO result = service.getQuoteComparison(1L);

        assertThat(result.getRows()).isEmpty();
        assertThat(result.getColumns()).hasSize(1);
    }

    @Test
    void getQuoteComparison_marksUnquotedCell_whenSupplierHasNoQuote() {
        InquiryOrderItemDO item = new InquiryOrderItemDO();
        item.setId(100L);
        item.setConfirmedModel("6ES7214-1AG40-0XB0");
        when(inquiryOrderItemMapper.selectList(any())).thenReturn(List.of(item));

        InquiryOrderSupplierDO supplierA = new InquiryOrderSupplierDO();
        supplierA.setId(1L);
        supplierA.setSourceType(InquirySourceType.FORMAL_SUPPLIER);
        supplierA.setSupplierId(11L);
        when(inquiryOrderSupplierMapper.selectList(any())).thenReturn(List.of(supplierA));
        SupplierVO supplierVoA = new SupplierVO();
        supplierVoA.setId(11L);
        supplierVoA.setName("上海科菱");
        when(supplierService.getById(11L)).thenReturn(supplierVoA);

        when(inquiryOrderItemQuoteMapper.selectList(any())).thenReturn(List.of());

        QuoteComparisonVO result = service.getQuoteComparison(1L);

        var row = result.getRows().get(0);
        assertThat(row.getCellsBySupplierId().get(1L).isQuoted()).isFalse();
        assertThat(row.getCellsBySupplierId().get(1L).isLowestPrice()).isFalse();
    }

    @Test
    void advanceStatus_legalSequentialTransition_succeeds() {
        InquiryOrderDO order = orderWithStatus(InquiryOrderStatus.ASSIGNED);
        when(inquiryOrderMapper.selectById(1L)).thenReturn(order);

        AdvanceInquiryOrderStatusRequest req = new AdvanceInquiryOrderStatusRequest();
        req.setTargetStatus(InquiryOrderStatus.SENT_TO_SUPPLIER);
        service.advanceStatus(1L, req);

        ArgumentCaptor<InquiryOrderDO> captor = ArgumentCaptor.forClass(InquiryOrderDO.class);
        verify(inquiryOrderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InquiryOrderStatus.SENT_TO_SUPPLIER);
    }

    @Test
    void advanceStatus_illegalSkipTransition_throwsBizException() {
        InquiryOrderDO order = orderWithStatus(InquiryOrderStatus.ASSIGNED);
        when(inquiryOrderMapper.selectById(1L)).thenReturn(order);

        AdvanceInquiryOrderStatusRequest req = new AdvanceInquiryOrderStatusRequest();
        req.setTargetStatus(InquiryOrderStatus.QUOTED_TO_CUSTOMER); // 跳过已发供应商、已收报价

        assertThrows(BizException.class, () -> service.advanceStatus(1L, req));
    }

    @Test
    void advanceStatus_doesNotAutoAggregateFromSupplierSubStatuses() {
        // 3个供应商中只有1个"已回复"，询盘单状态不应受此影响自动变化——
        // 本方法本身就不读取 inquiry_order_supplier，此处验证 advanceStatus 仍严格按
        // 人工传入的 targetStatus 执行，不做任何隐式聚合判断。
        InquiryOrderDO order = orderWithStatus(InquiryOrderStatus.SENT_TO_SUPPLIER);
        when(inquiryOrderMapper.selectById(1L)).thenReturn(order);

        AdvanceInquiryOrderStatusRequest req = new AdvanceInquiryOrderStatusRequest();
        req.setTargetStatus(InquiryOrderStatus.QUOTE_RECEIVED);
        service.advanceStatus(1L, req);

        verify(inquiryOrderSupplierMapper, org.mockito.Mockito.never()).selectList(any());
        verify(inquiryOrderMapper, times(1)).updateById(any());
    }

    @Test
    void advanceStatus_cancelFromActiveState_succeeds() {
        InquiryOrderDO order = orderWithStatus(InquiryOrderStatus.SENT_TO_SUPPLIER);
        when(inquiryOrderMapper.selectById(1L)).thenReturn(order);

        AdvanceInquiryOrderStatusRequest req = new AdvanceInquiryOrderStatusRequest();
        req.setTargetStatus(InquiryOrderStatus.CANCELLED);
        service.advanceStatus(1L, req);

        ArgumentCaptor<InquiryOrderDO> captor = ArgumentCaptor.forClass(InquiryOrderDO.class);
        verify(inquiryOrderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InquiryOrderStatus.CANCELLED);
    }

    @Test
    void advanceStatus_cancelFromPendingAssign_throwsBizException() {
        InquiryOrderDO order = orderWithStatus(InquiryOrderStatus.PENDING_ASSIGN);
        when(inquiryOrderMapper.selectById(1L)).thenReturn(order);

        AdvanceInquiryOrderStatusRequest req = new AdvanceInquiryOrderStatusRequest();
        req.setTargetStatus(InquiryOrderStatus.CANCELLED);

        assertThrows(BizException.class, () -> service.advanceStatus(1L, req));
    }

    private InquiryOrderDO orderWithStatus(int status) {
        InquiryOrderDO order = new InquiryOrderDO();
        order.setId(1L);
        order.setTenantId(1);
        order.setInquiryCode("IQ20260901003A");
        order.setStatus(status);
        return order;
    }
}

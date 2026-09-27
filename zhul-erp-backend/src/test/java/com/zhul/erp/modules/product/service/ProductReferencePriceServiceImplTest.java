package com.zhul.erp.modules.product.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.ReferencePriceVO;
import com.zhul.erp.modules.product.dto.SaveReferencePriceRequest;
import com.zhul.erp.modules.product.entity.ProductReferencePriceDO;
import com.zhul.erp.modules.product.repository.ProductReferencePriceMapper;
import com.zhul.erp.modules.product.service.impl.ProductReferencePriceServiceImpl;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.ProductFinder;
import com.zhul.erp.support.MybatisPlusTestSupport;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 参考价折算与舍入：财务计算，覆盖零值、负值、超大金额、多币种（tasks 5.19）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductReferencePriceServiceImplTest {

    @Mock
    private ProductReferencePriceMapper priceMapper;
    @Mock
    private ProductFinder productFinder;

    private ProductReferencePriceServiceImpl service;

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.initTableInfo(ProductReferencePriceDO.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0);
        service = new ProductReferencePriceServiceImpl(priceMapper, productFinder, new PlatformScopeGuard());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static SaveReferencePriceRequest request(String amount, String currency, String rate) {
        SaveReferencePriceRequest req = new SaveReferencePriceRequest();
        req.setPriceOriginal(amount == null ? null : new BigDecimal(amount));
        req.setCurrencyCode(currency);
        req.setExchangeRate(rate == null ? null : new BigDecimal(rate));
        return req;
    }

    private ProductReferencePriceDO saved() {
        ArgumentCaptor<ProductReferencePriceDO> captor = ArgumentCaptor.forClass(ProductReferencePriceDO.class);
        verify(priceMapper).insert(captor.capture());
        return captor.getValue();
    }

    private void assertRejected(SaveReferencePriceRequest req, String errorCode) {
        BizException e = assertThrows(BizException.class, () -> service.save(1L, req));
        assertEquals(errorCode, e.getErrorCode(), e.getMessage());
        verify(priceMapper, never()).insert(any(ProductReferencePriceDO.class));
    }

    @Test
    void usdIsConvertedWithSixDecimalRateAndHalfUpRounding() {
        service.save(1L, request("100.00", "USD", "7.123456"));

        ProductReferencePriceDO row = saved();
        assertEquals(new BigDecimal("100.00"), row.getPriceOriginal());
        assertEquals(new BigDecimal("7.123456"), row.getExchangeRate());
        assertEquals(new BigDecimal("712.35"), row.getPriceCny());   // 712.3456 → 712.35
        assertEquals("USD", row.getCurrencyCode());
        assertEquals(0, row.getTenantId());
    }

    @Test
    void halfUpRoundsExactlyHalfAwayFromZero() {
        // 0.005 × 1 = 0.005 → 0.01（HALF_UP），而不是银行家舍入的 0.00
        service.save(1L, request("0.005", "USD", "1"));

        ProductReferencePriceDO row = saved();
        assertEquals(new BigDecimal("0.01"), row.getPriceOriginal());
        assertEquals(new BigDecimal("0.01"), row.getPriceCny());
    }

    @Test
    void conversionUsesTheRoundedRateNotTheRawInput() {
        // 汇率先舍入到 6 位：7.1234565 → 7.123457；100 × 7.123457 = 712.3457 → 712.35
        service.save(1L, request("100", "USD", "7.1234565"));

        ProductReferencePriceDO row = saved();
        assertEquals(new BigDecimal("7.123457"), row.getExchangeRate());
        assertEquals(new BigDecimal("712.35"), row.getPriceCny());
    }

    @Test
    void midpointAtSecondDecimalRoundsUp() {
        // 1.25 × 1.1 = 1.375 → 1.38
        service.save(1L, request("1.25", "EUR", "1.1"));

        assertEquals(new BigDecimal("1.38"), saved().getPriceCny());
    }

    @Test
    void baseCurrencyHasRateOneAndIgnoresSuppliedRate() {
        service.save(1L, request("500.00", "CNY", "9.99"));

        ProductReferencePriceDO row = saved();
        assertEquals(new BigDecimal("1.000000"), row.getExchangeRate());
        assertEquals(new BigDecimal("500.00"), row.getPriceCny());
    }

    @Test
    void missingRateLeavesBaseAmountEmptyNotZero() {
        service.save(1L, request("100.00", "USD", null));

        ProductReferencePriceDO row = saved();
        assertNull(row.getExchangeRate());
        assertNull(row.getPriceCny());
        assertEquals(new BigDecimal("100.00"), row.getPriceOriginal());
    }

    @Test
    void zeroAndNegativeAmountsAreRejected() {
        assertRejected(request("0", "USD", "7"), ProductErrorCodes.PRICE_INVALID);
        assertRejected(request("0.00", "USD", "7"), ProductErrorCodes.PRICE_INVALID);
        assertRejected(request("-10", "USD", "7"), ProductErrorCodes.PRICE_INVALID);
        assertRejected(request(null, "USD", "7"), ProductErrorCodes.PRICE_INVALID);
        // 舍入后变成 0 也算无效
        assertRejected(request("0.004", "USD", "7"), ProductErrorCodes.PRICE_INVALID);
    }

    @Test
    void zeroAndNegativeRatesAreRejected() {
        assertRejected(request("10", "USD", "0"), ProductErrorCodes.PRICE_INVALID);
        assertRejected(request("10", "USD", "-7"), ProductErrorCodes.PRICE_INVALID);
        assertRejected(request("10", "USD", "0.0000004"), ProductErrorCodes.PRICE_INVALID);
    }

    @Test
    void missingOrBlankCurrencyIsRejected() {
        assertRejected(request("10", null, "7"), ProductErrorCodes.CURRENCY_REQUIRED);
        assertRejected(request("10", "   ", "7"), ProductErrorCodes.CURRENCY_REQUIRED);
    }

    @Test
    void invalidCurrencyCodesAreRejected() {
        assertRejected(request("10", "usd", "7"), ProductErrorCodes.PRICE_INVALID);
        assertRejected(request("10", "US", "7"), ProductErrorCodes.PRICE_INVALID);
        assertRejected(request("10", "DOLLAR", "7"), ProductErrorCodes.PRICE_INVALID);
        assertRejected(request("10", "XXY", "7"), ProductErrorCodes.PRICE_INVALID);
    }

    @Test
    void amountsBeyondColumnCapacityAreRejected() {
        assertRejected(request("10000000000000000", "CNY", null), ProductErrorCodes.PRICE_INVALID);
        // 原币金额没超，但乘汇率后超出 decimal(18,2)
        assertRejected(request("9000000000000000", "USD", "7"), ProductErrorCodes.PRICE_INVALID);
        assertRejected(request("10", "USD", "1000000000000"), ProductErrorCodes.PRICE_INVALID);
    }

    @Test
    void largestValidAmountIsAccepted() {
        service.save(1L, request("9999999999999999.99", "CNY", null));

        assertEquals(new BigDecimal("9999999999999999.99"), saved().getPriceCny());
    }

    @Test
    void savingAgainAfterClearRevivesTheSameRow() {
        ProductReferencePriceDO deleted = new ProductReferencePriceDO();
        deleted.setId(9L);
        deleted.setDeletedAt(java.time.LocalDateTime.now());
        when(priceMapper.selectOne(any())).thenReturn(deleted, deleted, (ProductReferencePriceDO) null);

        service.save(1L, request("100", "USD", "7"));

        verify(priceMapper).update(any(ProductReferencePriceDO.class), any());
        verify(priceMapper, never()).insert(any(ProductReferencePriceDO.class));
    }

    @Test
    void sourceAndDateAreStored() {
        SaveReferencePriceRequest req = request("100", "USD", "7");
        req.setPriceSource("eBay 参考价");
        req.setPriceDate(LocalDate.of(2026, 9, 1));

        service.save(1L, req);

        ProductReferencePriceDO row = saved();
        assertEquals("eBay 参考价", row.getPriceSource());
        assertEquals(LocalDate.of(2026, 9, 1), row.getPriceDate());
    }

    @Test
    void clearIsSoftDeleteAndNoOpWhenNothingToClear() {
        service.clear(1L);
        verify(priceMapper, never()).updateById(any(ProductReferencePriceDO.class));

        ProductReferencePriceDO row = new ProductReferencePriceDO();
        row.setId(9L);
        when(priceMapper.selectOne(any())).thenReturn(row);
        service.clear(1L);

        ArgumentCaptor<ProductReferencePriceDO> captor = ArgumentCaptor.forClass(ProductReferencePriceDO.class);
        verify(priceMapper).updateById(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    void readWithoutRowReturnsEmptyObject() {
        ReferencePriceVO vo = service.get(1L);

        assertNull(vo.getId());
        assertNull(vo.getPriceOriginal());
        assertNull(vo.getPriceCny());
        assertEquals(1L, vo.getProductId());
    }

    @Test
    void tenantAccountCannotWrite() {
        TenantContext.setTenantId(1001);

        BizException e = assertThrows(BizException.class, () -> service.save(1L, request("10", "USD", "7")));
        assertEquals(ProductErrorCodes.PLATFORM_ADMIN_REQUIRED, e.getErrorCode());
        assertThrows(BizException.class, () -> service.clear(1L));
        verifyNoInteractions(priceMapper);
    }
}

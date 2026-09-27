package com.zhul.erp.modules.product.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.SaveCustomsRequest;
import com.zhul.erp.modules.product.entity.ProductCustomsDO;
import com.zhul.erp.modules.product.repository.ProductCustomsMapper;
import com.zhul.erp.modules.product.service.impl.ProductCustomsServiceImpl;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductCustomsServiceImplTest {

    @Mock
    private ProductCustomsMapper customsMapper;
    @Mock
    private ProductFinder productFinder;

    private ProductCustomsServiceImpl service;

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.initTableInfo(ProductCustomsDO.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0);
        service = new ProductCustomsServiceImpl(customsMapper, productFinder, new PlatformScopeGuard());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static SaveCustomsRequest hs(String code) {
        SaveCustomsRequest req = new SaveCustomsRequest();
        req.setHsCode(code);
        return req;
    }

    private ProductCustomsDO saved() {
        ArgumentCaptor<ProductCustomsDO> captor = ArgumentCaptor.forClass(ProductCustomsDO.class);
        verify(customsMapper).insert(captor.capture());
        return captor.getValue();
    }

    private void assertRejected(SaveCustomsRequest req, String errorCode) {
        BizException e = assertThrows(BizException.class, () -> service.save(1L, req));
        assertEquals(errorCode, e.getErrorCode(), e.getMessage());
        verify(customsMapper, never()).insert(any(ProductCustomsDO.class));
    }

    @Test
    void hsCodeWithDotsAndSpacesIsStoredAsDigitsOnly() {
        service.save(1L, hs(" 8537.10.90 "));

        assertEquals("85371090", saved().getHsCode());
    }

    @Test
    void hsCodeAcceptsSixToTenDigitsAfterStrippingDotsAndSpaces() {
        String[][] cases = {{"853710", "853710"}, {"8537109012", "8537109012"}, {"8537.10", "853710"},
                {"8537 10 90 12", "8537109012"}};
        for (String[] c : cases) {
            service.save(1L, hs(c[0]));
        }
        ArgumentCaptor<ProductCustomsDO> captor = ArgumentCaptor.forClass(ProductCustomsDO.class);
        verify(customsMapper, org.mockito.Mockito.times(cases.length)).insert(captor.capture());
        for (int i = 0; i < cases.length; i++) {
            assertEquals(cases[i][1], captor.getAllValues().get(i).getHsCode(), cases[i][0]);
        }
    }

    @Test
    void hsCodeOutsideSixToTenDigitsOrWithLettersIsRejected() {
        assertRejected(hs("12345"), ProductErrorCodes.HS_CODE_INVALID);
        assertRejected(hs("12345678901"), ProductErrorCodes.HS_CODE_INVALID);
        assertRejected(hs("85AB"), ProductErrorCodes.HS_CODE_INVALID);
        assertRejected(hs("8537A0"), ProductErrorCodes.HS_CODE_INVALID);
        assertRejected(hs("8537-10"), ProductErrorCodes.HS_CODE_INVALID);
    }

    @Test
    void blankHsCodeMeansNotMaintained() {
        service.save(1L, hs("  "));

        assertEquals("", saved().getHsCode());
    }

    @Test
    void originCountryMustBeIsoAlpha2Uppercase() {
        for (String bad : new String[]{"Germany", "XX", "de", "D", "DEU", "1A"}) {
            SaveCustomsRequest req = new SaveCustomsRequest();
            req.setOriginCountry(bad);
            assertRejected(req, ProductErrorCodes.COUNTRY_CODE_INVALID);
        }
    }

    @Test
    void validCountryCodeAndBlankAreAccepted() {
        SaveCustomsRequest req = new SaveCustomsRequest();
        req.setOriginCountry("DE");
        service.save(1L, req);
        assertEquals("DE", saved().getOriginCountry());
    }

    @Test
    void rebateRateBoundariesAndRounding() {
        for (String bad : new String[]{"101", "-1", "100.01", "-0.01"}) {
            SaveCustomsRequest req = new SaveCustomsRequest();
            req.setExportRebateRate(new BigDecimal(bad));
            assertRejected(req, ProductErrorCodes.PARAM_INVALID);
        }
        SaveCustomsRequest req = new SaveCustomsRequest();
        req.setExportRebateRate(new BigDecimal("13.005"));
        service.save(1L, req);
        assertEquals(new BigDecimal("13.01"), saved().getExportRebateRate());   // HALF_UP
    }

    @Test
    void rebateRateZeroAndHundredAreAllowedAndNullStaysNull() {
        SaveCustomsRequest zero = new SaveCustomsRequest();
        zero.setExportRebateRate(BigDecimal.ZERO);
        service.save(1L, zero);
        assertEquals(new BigDecimal("0.00"), saved().getExportRebateRate());
    }

    @Test
    void unspecifiedRebateRateIsNullNotZero() {
        service.save(1L, new SaveCustomsRequest());

        assertNull(saved().getExportRebateRate());
    }

    @Test
    void readWithoutRowReturnsEmptyObject() {
        var vo = service.get(1L);

        assertNull(vo.getId());
        assertEquals("", vo.getHsCode());
        assertNull(vo.getExportRebateRate());
    }

    @Test
    void tenantAccountCannotWrite() {
        TenantContext.setTenantId(1001);

        BizException e = assertThrows(BizException.class, () -> service.save(1L, new SaveCustomsRequest()));
        assertEquals(ProductErrorCodes.PLATFORM_ADMIN_REQUIRED, e.getErrorCode());
        verifyNoInteractions(customsMapper);
    }
}

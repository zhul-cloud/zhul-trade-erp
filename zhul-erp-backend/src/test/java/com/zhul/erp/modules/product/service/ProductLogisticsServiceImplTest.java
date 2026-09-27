package com.zhul.erp.modules.product.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.SaveLogisticsRequest;
import com.zhul.erp.modules.product.entity.ProductLogisticsDO;
import com.zhul.erp.modules.product.repository.ProductLogisticsMapper;
import com.zhul.erp.modules.product.service.impl.ProductLogisticsServiceImpl;
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
class ProductLogisticsServiceImplTest {

    @Mock
    private ProductLogisticsMapper logisticsMapper;
    @Mock
    private ProductFinder productFinder;

    private ProductLogisticsServiceImpl service;

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.initTableInfo(ProductLogisticsDO.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0);
        service = new ProductLogisticsServiceImpl(logisticsMapper, productFinder, new PlatformScopeGuard());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private void assertRejected(SaveLogisticsRequest req) {
        BizException e = assertThrows(BizException.class, () -> service.save(1L, req));
        assertEquals(ProductErrorCodes.LOGISTICS_INVALID, e.getErrorCode(), e.getMessage());
        verify(logisticsMapper, never()).insert(any(ProductLogisticsDO.class));
    }

    private ProductLogisticsDO saved() {
        ArgumentCaptor<ProductLogisticsDO> captor = ArgumentCaptor.forClass(ProductLogisticsDO.class);
        verify(logisticsMapper).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    void savesWeightsAndDimensionsLeavingOthersNull() {
        SaveLogisticsRequest req = new SaveLogisticsRequest();
        req.setNetWeightKg(d("0.31"));
        req.setGrossWeightKg(d("0.45"));
        req.setLengthMm(d("90"));
        req.setWidthMm(d("100"));
        req.setHeightMm(d("75"));

        service.save(1L, req);

        ProductLogisticsDO row = saved();
        assertEquals(d("0.310"), row.getNetWeightKg());
        assertEquals(d("0.450"), row.getGrossWeightKg());
        assertEquals(d("90.0"), row.getLengthMm());
        assertNull(row.getPackageLengthMm());
        assertNull(row.getPackageQuantity());
        assertEquals(0, row.getIsDangerous());
        assertEquals(0, row.getTenantId());
    }

    @Test
    void grossWeightBelowNetWeightIsRejected() {
        SaveLogisticsRequest req = new SaveLogisticsRequest();
        req.setNetWeightKg(d("0.5"));
        req.setGrossWeightKg(d("0.3"));
        assertRejected(req);
    }

    @Test
    void grossEqualToNetIsAllowed() {
        SaveLogisticsRequest req = new SaveLogisticsRequest();
        req.setNetWeightKg(d("0.5"));
        req.setGrossWeightKg(d("0.5"));

        service.save(1L, req);

        assertEquals(d("0.500"), saved().getGrossWeightKg());
    }

    @Test
    void grossOnlyOrNetOnlyIsNotCompared() {
        SaveLogisticsRequest req = new SaveLogisticsRequest();
        req.setGrossWeightKg(d("0.1"));

        service.save(1L, req);

        assertEquals(d("0.100"), saved().getGrossWeightKg());
    }

    @Test
    void zeroAndNegativeValuesAreRejectedForEveryNumericField() {
        for (String bad : new String[]{"0", "-1", "0.0000"}) {
            for (int field = 0; field < 8; field++) {
                SaveLogisticsRequest req = new SaveLogisticsRequest();
                BigDecimal v = d(bad);
                switch (field) {
                    case 0 -> req.setNetWeightKg(v);
                    case 1 -> req.setGrossWeightKg(v);
                    case 2 -> req.setLengthMm(v);
                    case 3 -> req.setWidthMm(v);
                    case 4 -> req.setHeightMm(v);
                    case 5 -> req.setPackageLengthMm(v);
                    case 6 -> req.setPackageWidthMm(v);
                    default -> req.setPackageHeightMm(v);
                }
                assertRejected(req);
            }
        }
        SaveLogisticsRequest quantity = new SaveLogisticsRequest();
        quantity.setPackageQuantity(0);
        assertRejected(quantity);
        quantity.setPackageQuantity(-3);
        assertRejected(quantity);
    }

    @Test
    void smallestValidWeightIsOneThousandthAndDimensionsRoundHalfUp() {
        SaveLogisticsRequest ok = new SaveLogisticsRequest();
        ok.setNetWeightKg(d("0.001"));
        ok.setLengthMm(d("0.05"));   // 0.05 → 0.1（HALF_UP）

        service.save(1L, ok);

        ProductLogisticsDO row = saved();
        assertEquals(d("0.001"), row.getNetWeightKg());
        assertEquals(d("0.1"), row.getLengthMm());
    }

    @Test
    void valuesThatRoundToZeroAreRejected() {
        // 0.0004 舍入到 3 位是 0.000，等同于 0，必须拒绝
        SaveLogisticsRequest tooSmall = new SaveLogisticsRequest();
        tooSmall.setNetWeightKg(d("0.0004"));
        assertRejected(tooSmall);
        SaveLogisticsRequest tooSmallDimension = new SaveLogisticsRequest();
        tooSmallDimension.setHeightMm(d("0.04"));
        assertRejected(tooSmallDimension);
    }

    @Test
    void valuesBeyondColumnCapacityAreRejected() {
        SaveLogisticsRequest weight = new SaveLogisticsRequest();
        weight.setNetWeightKg(d("10000000"));
        assertRejected(weight);
        SaveLogisticsRequest dimension = new SaveLogisticsRequest();
        dimension.setLengthMm(d("100000000"));
        assertRejected(dimension);
    }

    @Test
    void invalidDangerousFlagIsRejected() {
        SaveLogisticsRequest req = new SaveLogisticsRequest();
        req.setIsDangerous(2);
        BizException e = assertThrows(BizException.class, () -> service.save(1L, req));
        assertEquals(ProductErrorCodes.PARAM_INVALID, e.getErrorCode());
    }

    @Test
    void readWithoutRowReturnsNullNumbersNotZero() {
        var vo = service.get(1L);

        assertNull(vo.getId());
        assertNull(vo.getNetWeightKg());
        assertNull(vo.getLengthMm());
        assertNull(vo.getPackageQuantity());
    }

    @Test
    void tenantAccountCannotWrite() {
        TenantContext.setTenantId(1001);

        BizException e = assertThrows(BizException.class, () -> service.save(1L, new SaveLogisticsRequest()));
        assertEquals(ProductErrorCodes.PLATFORM_ADMIN_REQUIRED, e.getErrorCode());
        verifyNoInteractions(logisticsMapper);
    }
}

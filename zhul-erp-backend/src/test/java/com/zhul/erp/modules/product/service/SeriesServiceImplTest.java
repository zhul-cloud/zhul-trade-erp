package com.zhul.erp.modules.product.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.CreateSeriesRequest;
import com.zhul.erp.modules.product.dto.UpdateSeriesRequest;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductSeriesDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.repository.ProductSeriesMapper;
import com.zhul.erp.modules.product.service.impl.SeriesServiceImpl;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeriesServiceImplTest {

    @Mock
    private ProductSeriesMapper seriesMapper;
    @Mock
    private ProductBrandMapper brandMapper;
    @Mock
    private ProductMapper productMapper;

    private SeriesServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0);
        service = new SeriesServiceImpl(seriesMapper, brandMapper, productMapper, new PlatformScopeGuard());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static CreateSeriesRequest create(Long brandId, String name) {
        CreateSeriesRequest req = new CreateSeriesRequest();
        req.setBrandId(brandId);
        req.setSeriesName(name);
        return req;
    }

    private static ProductBrandDO brand(Long id, String name) {
        ProductBrandDO b = new ProductBrandDO();
        b.setId(id);
        b.setBrandName(name);
        return b;
    }

    private static ProductSeriesDO series(Long id, Long brandId, String name) {
        ProductSeriesDO s = new ProductSeriesDO();
        s.setId(id);
        s.setBrandId(brandId);
        s.setSeriesName(name);
        s.setStatus(1);
        return s;
    }

    private static BizException assertBiz(org.junit.jupiter.api.function.Executable e, String errorCode) {
        BizException ex = assertThrows(BizException.class, e);
        assertEquals(errorCode, ex.getErrorCode());
        return ex;
    }

    @Test
    void createSeriesUnderExistingBrand() {
        when(brandMapper.selectOne(any())).thenReturn(brand(1L, "Siemens"));

        var vo = service.create(create(1L, "S7-1200"));

        ArgumentCaptor<ProductSeriesDO> captor = ArgumentCaptor.forClass(ProductSeriesDO.class);
        verify(seriesMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getBrandId());
        assertEquals("S7-1200", captor.getValue().getSeriesName());
        assertEquals(0, captor.getValue().getTenantId());
        assertEquals(1, captor.getValue().getStatus());
        assertEquals("Siemens", vo.getBrandName());
    }

    @Test
    void createSeriesForMissingBrandIsRejected() {
        when(brandMapper.selectOne(any())).thenReturn(null);

        assertBiz(() -> service.create(create(99L, "X")), ProductErrorCodes.BRAND_NOT_FOUND);
        assertBiz(() -> service.create(create(null, "X")), ProductErrorCodes.BRAND_NOT_FOUND);
        verify(seriesMapper, never()).insert(any(ProductSeriesDO.class));
    }

    @Test
    void duplicateNameUnderSameBrandIsRejected() {
        when(brandMapper.selectOne(any())).thenReturn(brand(1L, "Siemens"));
        when(seriesMapper.selectOne(any())).thenReturn(series(9L, 1L, "S7-1200"));

        assertBiz(() -> service.create(create(1L, "s7-1200")), ProductErrorCodes.SERIES_DUPLICATE);
        verify(seriesMapper, never()).insert(any(ProductSeriesDO.class));
    }

    @Test
    void sameNameUnderDifferentBrandIsAllowed() {
        // 唯一性检查按品牌过滤，另一个品牌下没有同名系列，所以查不到
        when(brandMapper.selectOne(any())).thenReturn(brand(2L, "ABB"));
        when(seriesMapper.selectOne(any())).thenReturn(null);

        service.create(create(2L, "Basic"));

        verify(seriesMapper).insert(any(ProductSeriesDO.class));
    }

    @Test
    void blankNameIsRejected() {
        assertBiz(() -> service.create(create(1L, "  ")), ProductErrorCodes.PARAM_INVALID);
        assertBiz(() -> service.create(create(1L, "s".repeat(65))), ProductErrorCodes.PARAM_INVALID);
    }

    @Test
    void updateKeepsBrandAndChangesNameAndDescription() {
        ProductSeriesDO current = series(5L, 1L, "S7-1200");
        when(seriesMapper.selectOne(any())).thenReturn(current, null, current);
        when(brandMapper.selectById(1L)).thenReturn(brand(1L, "Siemens"));
        UpdateSeriesRequest req = new UpdateSeriesRequest();
        req.setSeriesName("S7-1200 Basic");
        req.setDescription("small PLC");

        service.update(5L, req);

        ArgumentCaptor<ProductSeriesDO> captor = ArgumentCaptor.forClass(ProductSeriesDO.class);
        verify(seriesMapper).updateById(captor.capture());
        assertEquals("S7-1200 Basic", captor.getValue().getSeriesName());
        assertEquals("small PLC", captor.getValue().getDescription());
        assertEquals(null, captor.getValue().getBrandId());
    }

    @Test
    void deleteSeriesWithProductsIsRejected() {
        when(seriesMapper.selectOne(any())).thenReturn(series(5L, 1L, "S7-1200"));
        when(productMapper.selectCount(any())).thenReturn(3L);

        BizException e = assertBiz(() -> service.delete(5L), ProductErrorCodes.SERIES_IN_USE);

        assertTrue(e.getMessage().contains("3"));
        verify(seriesMapper, never()).updateById(any(ProductSeriesDO.class));
    }

    @Test
    void deleteSeriesWithoutProductsSoftDeletes() {
        when(seriesMapper.selectOne(any())).thenReturn(series(5L, 1L, "S7-1200"));
        when(productMapper.selectCount(any())).thenReturn(0L);

        service.delete(5L);

        ArgumentCaptor<ProductSeriesDO> captor = ArgumentCaptor.forClass(ProductSeriesDO.class);
        verify(seriesMapper).updateById(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    void disableSeries() {
        when(seriesMapper.selectOne(any())).thenReturn(series(5L, 1L, "S7-1200"));

        service.updateStatus(5L, 0);

        ArgumentCaptor<ProductSeriesDO> captor = ArgumentCaptor.forClass(ProductSeriesDO.class);
        verify(seriesMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
    }

    @Test
    void tenantAccountCannotWrite() {
        TenantContext.setTenantId(1001);
        UpdateSeriesRequest update = new UpdateSeriesRequest();
        update.setSeriesName("X");

        assertBiz(() -> service.create(create(1L, "X")), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.update(1L, update), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.updateStatus(1L, 0), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.delete(1L), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        verifyNoInteractions(seriesMapper, brandMapper, productMapper);
    }
}

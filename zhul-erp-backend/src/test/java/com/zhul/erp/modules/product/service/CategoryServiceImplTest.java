package com.zhul.erp.modules.product.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.CategoryOptionVO;
import com.zhul.erp.modules.product.dto.SaveCategoryRequest;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.service.impl.CategoryServiceImpl;
import com.zhul.erp.modules.product.support.OptionsCache;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

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
class CategoryServiceImplTest {

    @Mock
    private ProductCategoryMapper categoryMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private OptionsCache optionsCache;

    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0);
        service = new CategoryServiceImpl(categoryMapper, productMapper, new PlatformScopeGuard(), optionsCache);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static SaveCategoryRequest request(String code, String name) {
        SaveCategoryRequest req = new SaveCategoryRequest();
        req.setCategoryCode(code);
        req.setCategoryName(name);
        return req;
    }

    private static ProductCategoryDO category(Long id, String code, String name) {
        ProductCategoryDO c = new ProductCategoryDO();
        c.setId(id);
        c.setTenantId(0);
        c.setCategoryCode(code);
        c.setCategoryName(name);
        c.setSortOrder(0);
        c.setStatus(1);
        return c;
    }

    private static BizException assertBiz(org.junit.jupiter.api.function.Executable e, String errorCode) {
        BizException ex = assertThrows(BizException.class, e);
        assertEquals(errorCode, ex.getErrorCode());
        return ex;
    }

    @Test
    void createSavesEnabledCategoryForPlatform() {
        service.create(request("controllers", "PLC & Controllers"));

        ArgumentCaptor<ProductCategoryDO> captor = ArgumentCaptor.forClass(ProductCategoryDO.class);
        verify(categoryMapper).insert(captor.capture());
        assertEquals("controllers", captor.getValue().getCategoryCode());
        assertEquals("PLC & Controllers", captor.getValue().getCategoryName());
        assertEquals(0, captor.getValue().getTenantId());
        assertEquals(1, captor.getValue().getStatus());
        verify(optionsCache).evictAfterCommit(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS);
    }

    @Test
    void duplicateCodeIsRejected() {
        when(categoryMapper.selectOne(any())).thenReturn(category(2L, "servo", "Servo"));

        assertBiz(() -> service.create(request("servo", "Servo 2")), ProductErrorCodes.CATEGORY_DUPLICATE);
        verify(categoryMapper, never()).insert(any(ProductCategoryDO.class));
    }

    @Test
    void invalidCodeFormatsAreRejected() {
        for (String bad : new String[]{"PLC Controllers", "Servo", "1abc", "_abc", "a-b", "a b", "a".repeat(33)}) {
            assertBiz(() -> service.create(request(bad, "N")), ProductErrorCodes.PARAM_INVALID);
        }
        verify(categoryMapper, never()).insert(any(ProductCategoryDO.class));
    }

    @Test
    void validCodeBoundariesAreAccepted() {
        service.create(request("a", "N"));
        service.create(request("a".repeat(32), "N"));
        service.create(request("servo_2", "N"));
        verify(categoryMapper, org.mockito.Mockito.times(3)).insert(any(ProductCategoryDO.class));
    }

    @Test
    void blankNameIsRejected() {
        assertBiz(() -> service.create(request("servo", " ")), ProductErrorCodes.PARAM_INVALID);
        assertBiz(() -> service.create(request("servo", "n".repeat(65))), ProductErrorCodes.PARAM_INVALID);
    }

    @Test
    void codeOfCategoryWithProductsCannotBeChanged() {
        when(categoryMapper.selectOne(any())).thenReturn(category(3L, "drives", "Drives"));
        when(productMapper.selectCount(any())).thenReturn(4L);

        BizException e = assertBiz(() -> service.update(3L, request("inverters", "Drives")),
                ProductErrorCodes.CATEGORY_CODE_IMMUTABLE);

        assertEquals(4L, ((Map<?, ?>) e.getDetail()).get("usageCount"));
        verify(categoryMapper, never()).updateById(any(ProductCategoryDO.class));
    }

    @Test
    void nameOfCategoryWithProductsCanBeChanged() {
        ProductCategoryDO current = category(3L, "drives", "Drives");
        when(categoryMapper.selectOne(any())).thenReturn(current, current);
        when(productMapper.selectCount(any())).thenReturn(4L);

        service.update(3L, request("drives", "Drives & Inverters"));

        ArgumentCaptor<ProductCategoryDO> captor = ArgumentCaptor.forClass(ProductCategoryDO.class);
        verify(categoryMapper).updateById(captor.capture());
        assertEquals("drives", captor.getValue().getCategoryCode());
        assertEquals("Drives & Inverters", captor.getValue().getCategoryName());
    }

    @Test
    void codeOfCategoryWithoutProductsCanBeChangedIfFree() {
        ProductCategoryDO current = category(3L, "drives", "Drives");
        when(categoryMapper.selectOne(any())).thenReturn(current, null, current);
        when(productMapper.selectCount(any())).thenReturn(0L);

        service.update(3L, request("inverters", "Drives"));

        verify(categoryMapper).updateById(any(ProductCategoryDO.class));
    }

    @Test
    void deleteCategoryWithProductsIsRejected() {
        when(categoryMapper.selectOne(any())).thenReturn(category(4L, "sensors", "Sensors"));
        when(productMapper.selectCount(any())).thenReturn(21L);

        BizException e = assertBiz(() -> service.delete(4L), ProductErrorCodes.CATEGORY_IN_USE);

        assertTrue(e.getMessage().contains("21"));
        verify(categoryMapper, never()).updateById(any(ProductCategoryDO.class));
    }

    @Test
    void deleteCategoryWithoutProductsSoftDeletes() {
        when(categoryMapper.selectOne(any())).thenReturn(category(4L, "spares", "Spares"));
        when(productMapper.selectCount(any())).thenReturn(0L);

        service.delete(4L);

        ArgumentCaptor<ProductCategoryDO> captor = ArgumentCaptor.forClass(ProductCategoryDO.class);
        verify(categoryMapper).updateById(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    void disableCategoryUpdatesStatusAndEvictsCache() {
        when(categoryMapper.selectOne(any())).thenReturn(category(5L, "spares", "Spares"));

        service.updateStatus(5L, 0);

        ArgumentCaptor<ProductCategoryDO> captor = ArgumentCaptor.forClass(ProductCategoryDO.class);
        verify(categoryMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
        verify(optionsCache).evictAfterCommit(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS);
    }

    @Test
    void tenantAccountCannotWriteButCanRead() {
        TenantContext.setTenantId(1001);
        assertBiz(() -> service.create(request("servo", "Servo")), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.update(1L, request("servo", "Servo")), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.updateStatus(1L, 0), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.delete(1L), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        verifyNoInteractions(categoryMapper, productMapper);

        when(optionsCache.get(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS, CategoryOptionVO.class)).thenReturn(List.of());
        assertEquals(0, service.options().size());
    }
}

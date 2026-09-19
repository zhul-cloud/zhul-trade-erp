package com.zhul.erp.modules.product.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.ProductVO;
import com.zhul.erp.modules.product.dto.SaveProductRequest;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductSeriesDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.repository.ProductRelationshipMapper;
import com.zhul.erp.modules.product.repository.ProductSeriesMapper;
import com.zhul.erp.modules.product.service.impl.ProductServiceImpl;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.ProductFinder;
import com.zhul.erp.modules.product.support.ProductNames;
import com.zhul.erp.modules.product.support.ProductUsageChecker;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Stream;

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
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductBrandMapper brandMapper;
    @Mock
    private ProductCategoryMapper categoryMapper;
    @Mock
    private ProductSeriesMapper seriesMapper;
    @Mock
    private ProductRelationshipMapper relationshipMapper;
    @Mock
    private ProductFinder productFinder;
    @Mock
    private ProductNames productNames;
    @Mock
    private ProductCompletenessService completenessService;
    @Mock
    private ObjectProvider<ProductUsageChecker> usageCheckers;

    private ProductServiceImpl service;
    private long usage = 0;

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.initTableInfo(ProductDO.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0);
        ProductUsageChecker checker = productId -> usage;
        when(usageCheckers.orderedStream()).thenAnswer(inv -> Stream.of(checker));
        when(productNames.load(any())).thenReturn(new ProductNames.Lookup(Map.of(), Map.of(), Map.of()));
        service = new ProductServiceImpl(productMapper, brandMapper, categoryMapper, seriesMapper,
                relationshipMapper, productFinder, productNames, completenessService, new PlatformScopeGuard(),
                usageCheckers);
        when(brandMapper.selectOne(any())).thenReturn(brand(1L, 1));
        when(categoryMapper.selectOne(any())).thenReturn(category(2L, 1));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ProductBrandDO brand(Long id, int status) {
        ProductBrandDO b = new ProductBrandDO();
        b.setId(id);
        b.setStatus(status);
        b.setBrandName("Siemens");
        return b;
    }

    private static ProductCategoryDO category(Long id, int status) {
        ProductCategoryDO c = new ProductCategoryDO();
        c.setId(id);
        c.setStatus(status);
        return c;
    }

    private static SaveProductRequest request(String mpn) {
        SaveProductRequest req = new SaveProductRequest();
        req.setBrandId(1L);
        req.setCategoryId(2L);
        req.setMpnRaw(mpn);
        return req;
    }

    private static ProductDO product(Long id, String raw, String normalized) {
        ProductDO p = new ProductDO();
        p.setId(id);
        p.setBrandId(1L);
        p.setCategoryId(2L);
        p.setMpnRaw(raw);
        p.setMpnNormalized(normalized);
        p.setMpnDisplay(raw);
        p.setLifecycleStatus(6);
        p.setLifecycleSource("");
        p.setStatus(1);
        return p;
    }

    private static BizException assertBiz(org.junit.jupiter.api.function.Executable e, String errorCode) {
        BizException ex = assertThrows(BizException.class, e);
        assertEquals(errorCode, ex.getErrorCode());
        return ex;
    }

    // ---------- 创建 ----------

    @Test
    void createStoresRawTrimmedAndDefaultsDisplayLifecycleAndStatus() {
        ProductVO vo = service.create(request("  6ES7 214-1BD23-0XB0 "));

        ArgumentCaptor<ProductDO> captor = ArgumentCaptor.forClass(ProductDO.class);
        verify(productMapper).insert(captor.capture());
        ProductDO saved = captor.getValue();
        assertEquals("6ES7 214-1BD23-0XB0", saved.getMpnRaw());
        assertEquals("6es72141bd230xb0", saved.getMpnNormalized());
        assertEquals("6ES7 214-1BD23-0XB0", saved.getMpnDisplay());
        assertEquals(6, saved.getLifecycleStatus());
        assertEquals(1, saved.getStatus());
        assertEquals(0, saved.getTenantId());
        assertEquals("6ES7 214-1BD23-0XB0", vo.getMpnDisplay());
    }

    @Test
    void createKeepsExplicitDisplayModel() {
        SaveProductRequest req = request("6ES7214");
        req.setMpnDisplay("6ES7 214");

        service.create(req);

        ArgumentCaptor<ProductDO> captor = ArgumentCaptor.forClass(ProductDO.class);
        verify(productMapper).insert(captor.capture());
        assertEquals("6ES7 214", captor.getValue().getMpnDisplay());
        assertEquals("6ES7214", captor.getValue().getMpnRaw());
    }

    @Test
    void mpnWithoutLettersOrDigitsIsRejected() {
        assertBiz(() -> service.create(request("---")), ProductErrorCodes.PRODUCT_MPN_INVALID);
        assertBiz(() -> service.create(request("   ")), ProductErrorCodes.PARAM_INVALID);
        assertBiz(() -> service.create(request("a".repeat(129))), ProductErrorCodes.PARAM_INVALID);
        verify(productMapper, never()).insert(any(ProductDO.class));
    }

    @Test
    void missingBrandOrCategoryIsRejected() {
        when(brandMapper.selectOne(any())).thenReturn(null);
        assertBiz(() -> service.create(request("A1")), ProductErrorCodes.BRAND_NOT_FOUND);

        when(brandMapper.selectOne(any())).thenReturn(brand(1L, 1));
        when(categoryMapper.selectOne(any())).thenReturn(null);
        assertBiz(() -> service.create(request("A1")), ProductErrorCodes.CATEGORY_NOT_FOUND);
    }

    @Test
    void disabledBrandCannotBeUsedForNewProduct() {
        when(brandMapper.selectOne(any())).thenReturn(brand(1L, 0));

        assertBiz(() -> service.create(request("A1")), ProductErrorCodes.PARAM_INVALID);
    }

    @Test
    void seriesOfAnotherBrandIsRejected() {
        ProductSeriesDO series = new ProductSeriesDO();
        series.setId(9L);
        series.setBrandId(77L);
        when(seriesMapper.selectOne(any())).thenReturn(series);
        SaveProductRequest req = request("A1");
        req.setSeriesId(9L);

        assertBiz(() -> service.create(req), ProductErrorCodes.PRODUCT_SERIES_MISMATCH);
        verify(productMapper, never()).insert(any(ProductDO.class));
    }

    @Test
    void seriesOfSameBrandIsAccepted() {
        ProductSeriesDO series = new ProductSeriesDO();
        series.setId(9L);
        series.setBrandId(1L);
        when(seriesMapper.selectOne(any())).thenReturn(series);
        SaveProductRequest req = request("A1");
        req.setSeriesId(9L);

        service.create(req);

        ArgumentCaptor<ProductDO> captor = ArgumentCaptor.forClass(ProductDO.class);
        verify(productMapper).insert(captor.capture());
        assertEquals(9L, captor.getValue().getSeriesId());
    }

    // ---------- 归一化去重 ----------

    @Test
    void duplicateReturnsExistingIdAndDisplay() {
        ProductDO existing = product(12L, "6ES7214-1BD23-0XB0", "6es72141bd230xb0");
        when(productMapper.selectOne(any())).thenReturn(existing);

        BizException e = assertBiz(() -> service.create(request("6ES7 214-1BD23-0XB0")),
                ProductErrorCodes.PRODUCT_DUPLICATE);

        Map<?, ?> detail = (Map<?, ?>) e.getDetail();
        assertEquals(12L, detail.get("existingId"));
        assertEquals("6ES7214-1BD23-0XB0", detail.get("mpnDisplay"));
        assertEquals(false, detail.get("deleted"));
        verify(productMapper, never()).insert(any(ProductDO.class));
    }

    @Test
    void duplicateOfSoftDeletedProductIsFlaggedDeleted() {
        ProductDO existing = product(12L, "6ES7214", "6es7214");
        existing.setDeletedAt(LocalDateTime.now());
        when(productMapper.selectOne(any())).thenReturn(existing);

        BizException e = assertBiz(() -> service.create(request("6ES7214")), ProductErrorCodes.PRODUCT_DUPLICATE);

        assertEquals(true, ((Map<?, ?>) e.getDetail()).get("deleted"));
    }

    @Test
    void uniqueKeyViolationFromConcurrentCreateBecomesDuplicate() {
        ProductDO winner = product(30L, "ABC-100", "abc100");
        // 第一次（预检查）没查到，插入时撞唯一键，再加锁读到对方刚提交的那一行
        when(productMapper.selectOne(any())).thenReturn(null, winner);
        when(productMapper.insert(any(ProductDO.class))).thenThrow(new DuplicateKeyException("uk"));

        BizException e = assertBiz(() -> service.create(request("abc100")), ProductErrorCodes.PRODUCT_DUPLICATE);

        assertEquals(30L, ((Map<?, ?>) e.getDetail()).get("existingId"));
    }

    // ---------- 生命周期 ----------

    @Test
    void discontinuedWithoutSourceIsRejected() {
        SaveProductRequest req = request("A1");
        req.setLifecycleStatus(4);
        assertBiz(() -> service.create(req), ProductErrorCodes.PRODUCT_LIFECYCLE_SOURCE_REQUIRED);

        req.setLifecycleStatus(5);
        req.setLifecycleSource("   ");
        assertBiz(() -> service.create(req), ProductErrorCodes.PRODUCT_LIFECYCLE_SOURCE_REQUIRED);
        verify(productMapper, never()).insert(any(ProductDO.class));
    }

    @Test
    void discontinuedWithSourceIsAccepted() {
        SaveProductRequest req = request("A1");
        req.setLifecycleStatus(4);
        req.setLifecycleSource("Siemens EOL notice 2024");

        service.create(req);

        ArgumentCaptor<ProductDO> captor = ArgumentCaptor.forClass(ProductDO.class);
        verify(productMapper).insert(captor.capture());
        assertEquals(4, captor.getValue().getLifecycleStatus());
    }

    @Test
    void invalidLifecycleValueIsRejected() {
        SaveProductRequest req = request("A1");
        req.setLifecycleStatus(9);
        assertBiz(() -> service.create(req), ProductErrorCodes.PARAM_INVALID);
    }

    @Test
    void obsoleteWithReplacementRelationshipSavesWithWarning() {
        ProductDO current = product(5L, "A1", "a1");
        ProductDO saved = product(5L, "A1", "a1");
        saved.setLifecycleStatus(5);
        when(productFinder.lockActive(5L)).thenReturn(current);
        when(productFinder.active(5L)).thenReturn(saved);
        when(relationshipMapper.selectCount(any())).thenReturn(1L);
        SaveProductRequest req = request("A1");
        req.setLifecycleStatus(5);
        req.setLifecycleSource("EOL list");

        ProductVO vo = service.update(5L, req);

        assertEquals(1, vo.getWarnings().size());
        verify(productMapper).update(any(ProductDO.class), any());
    }

    @Test
    void obsoleteWithoutReplacementHasNoWarning() {
        ProductDO current = product(5L, "A1", "a1");
        ProductDO saved = product(5L, "A1", "a1");
        saved.setLifecycleStatus(5);
        when(productFinder.lockActive(5L)).thenReturn(current);
        when(productFinder.active(5L)).thenReturn(saved);
        when(relationshipMapper.selectCount(any())).thenReturn(0L);
        SaveProductRequest req = request("A1");
        req.setLifecycleStatus(5);
        req.setLifecycleSource("EOL list");

        assertTrue(service.update(5L, req).getWarnings().isEmpty());
    }

    // ---------- 被引用后的限制 ----------

    @Test
    void referencedProductCannotChangeMpnOrBrand() {
        usage = 2;
        ProductDO current = product(5L, "A1", "a1");
        when(productFinder.lockActive(5L)).thenReturn(current);

        BizException e = assertBiz(() -> service.update(5L, request("A2")), ProductErrorCodes.PRODUCT_MPN_IMMUTABLE);
        assertEquals(2L, ((Map<?, ?>) e.getDetail()).get("usageCount"));

        ProductBrandDO otherBrand = brand(3L, 1);
        when(brandMapper.selectOne(any())).thenReturn(otherBrand);
        SaveProductRequest brandChange = request("A1");
        brandChange.setBrandId(3L);
        assertBiz(() -> service.update(5L, brandChange), ProductErrorCodes.PRODUCT_MPN_IMMUTABLE);
        verify(productMapper, never()).update(any(ProductDO.class), any());
    }

    @Test
    void referencedProductCanStillChangeName() {
        usage = 2;
        ProductDO current = product(5L, "A1", "a1");
        when(productFinder.lockActive(5L)).thenReturn(current);
        when(productFinder.active(5L)).thenReturn(current);
        SaveProductRequest req = request("A1");
        req.setProductName("SITOP Power Supply");

        ProductVO vo = service.update(5L, req);

        ArgumentCaptor<ProductDO> captor = ArgumentCaptor.forClass(ProductDO.class);
        verify(productMapper).update(captor.capture(), any());
        assertEquals("SITOP Power Supply", captor.getValue().getProductName());
        assertEquals(2L, vo.getUsageCount());
    }

    @Test
    void unreferencedProductMpnChangeChecksForDuplicates() {
        ProductDO current = product(5L, "A1", "a1");
        when(productFinder.lockActive(5L)).thenReturn(current);
        when(productMapper.selectOne(any())).thenReturn(product(6L, "A2", "a2"));

        assertBiz(() -> service.update(5L, request("A-2")), ProductErrorCodes.PRODUCT_DUPLICATE);
        verify(productMapper, never()).update(any(ProductDO.class), any());
    }

    @Test
    void identityFieldsAreNotPartOfUpdateWhenUnchangedAndSeriesCanBeCleared() {
        ProductDO current = product(5L, "A1", "a1");
        current.setSeriesId(9L);
        when(productFinder.lockActive(5L)).thenReturn(current);
        when(productFinder.active(5L)).thenReturn(current);

        service.update(5L, request("A1"));

        ArgumentCaptor<ProductDO> captor = ArgumentCaptor.forClass(ProductDO.class);
        verify(productMapper).update(captor.capture(), any());
        assertEquals(null, captor.getValue().getSeriesId());
        assertEquals(null, captor.getValue().getId());
        assertEquals(null, captor.getValue().getCreateTime());
    }

    // ---------- 删除与停用 ----------

    @Test
    void deleteReferencedProductIsRejectedWithUsageCount() {
        usage = 2;
        when(productFinder.lockActive(5L)).thenReturn(product(5L, "A1", "a1"));

        BizException e = assertBiz(() -> service.delete(5L), ProductErrorCodes.PRODUCT_IN_USE);

        assertTrue(e.getMessage().contains("2"));
        assertEquals(2L, ((Map<?, ?>) e.getDetail()).get("usageCount"));
        verify(productMapper, never()).updateById(any(ProductDO.class));
    }

    @Test
    void deleteUnreferencedProductSoftDeletes() {
        when(productFinder.lockActive(5L)).thenReturn(product(5L, "A1", "a1"));

        service.delete(5L);

        ArgumentCaptor<ProductDO> captor = ArgumentCaptor.forClass(ProductDO.class);
        verify(productMapper).updateById(captor.capture());
        assertEquals(5L, captor.getValue().getId());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    void disablingReferencedProductIsAllowed() {
        usage = 2;
        when(productFinder.active(5L)).thenReturn(product(5L, "A1", "a1"));

        service.updateStatus(5L, 0);

        ArgumentCaptor<ProductDO> captor = ArgumentCaptor.forClass(ProductDO.class);
        verify(productMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
    }

    @Test
    void invalidStatusIsRejected() {
        assertBiz(() -> service.updateStatus(5L, 3), ProductErrorCodes.PARAM_INVALID);
    }

    // ---------- 恢复 ----------

    @Test
    void restoreOfNonDeletedOrMissingProductIsRejected() {
        when(productMapper.selectOne(any())).thenReturn(null);

        assertBiz(() -> service.restore(5L), ProductErrorCodes.PRODUCT_NOT_FOUND);
        assertBiz(() -> service.restore(null), ProductErrorCodes.PRODUCT_NOT_FOUND);
    }

    @Test
    void restoreRequiresBrandAndCategoryToStillExist() {
        ProductDO deleted = product(5L, "A1", "a1");
        deleted.setDeletedAt(LocalDateTime.now());
        when(productMapper.selectOne(any())).thenReturn(deleted);
        when(brandMapper.selectCount(any())).thenReturn(0L);
        assertBiz(() -> service.restore(5L), ProductErrorCodes.BRAND_NOT_FOUND);

        when(brandMapper.selectCount(any())).thenReturn(1L);
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        assertBiz(() -> service.restore(5L), ProductErrorCodes.CATEGORY_NOT_FOUND);
    }

    @Test
    void restoreClearsDeletedAt() {
        ProductDO deleted = product(5L, "A1", "a1");
        deleted.setDeletedAt(LocalDateTime.now());
        when(productMapper.selectOne(any())).thenReturn(deleted);
        when(brandMapper.selectCount(any())).thenReturn(1L);
        when(categoryMapper.selectCount(any())).thenReturn(1L);
        when(productFinder.active(5L)).thenReturn(product(5L, "A1", "a1"));

        ProductVO vo = service.restore(5L);

        verify(productMapper).update(any(ProductDO.class), any());
        assertEquals(false, vo.getDeleted());
    }

    // ---------- 平台账号 ----------

    @Test
    void tenantAccountCannotWrite() {
        TenantContext.setTenantId(1001);

        assertBiz(() -> service.create(request("A1")), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.update(5L, request("A1")), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.updateStatus(5L, 0), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.delete(5L), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.restore(5L), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        verifyNoInteractions(productMapper, productFinder);
    }

    @Test
    void uniqueKeyViolationOnUpdateBecomesDuplicate() {
        ProductDO current = product(5L, "A1", "a1");
        ProductDO winner = product(6L, "A2", "a2");
        when(productFinder.lockActive(5L)).thenReturn(current);
        when(productMapper.selectOne(any())).thenReturn(null, winner);
        when(productMapper.update(any(ProductDO.class), any())).thenThrow(new DuplicateKeyException("uk"));

        BizException e = assertBiz(() -> service.update(5L, request("A2")), ProductErrorCodes.PRODUCT_DUPLICATE);

        assertEquals(6L, ((Map<?, ?>) e.getDetail()).get("existingId"));
    }

    @Test
    void duplicateWhoseRowCannotBeFoundStillReportsDuplicateWithoutId() {
        when(productMapper.selectOne(any())).thenReturn(null, (ProductDO) null);
        when(productMapper.insert(any(ProductDO.class))).thenThrow(new DuplicateKeyException("uk"));

        BizException e = assertBiz(() -> service.create(request("A1")), ProductErrorCodes.PRODUCT_DUPLICATE);

        Map<?, ?> detail = (Map<?, ?>) e.getDetail();
        assertEquals(false, detail.get("deleted"));
        assertEquals(null, detail.get("existingId"));
    }

    @Test
    void missingSeriesIsRejected() {
        when(seriesMapper.selectOne(any())).thenReturn(null);
        SaveProductRequest req = request("A1");
        req.setSeriesId(9L);

        assertBiz(() -> service.create(req), ProductErrorCodes.SERIES_NOT_FOUND);
    }

    @Test
    void disabledCategoryCannotBeUsedForNewProduct() {
        when(categoryMapper.selectOne(any())).thenReturn(category(2L, 0));

        assertBiz(() -> service.create(request("A1")), ProductErrorCodes.PARAM_INVALID);
    }

    @Test
    void missingBrandIdOrCategoryIdIsRejected() {
        SaveProductRequest noBrand = request("A1");
        noBrand.setBrandId(null);
        assertBiz(() -> service.create(noBrand), ProductErrorCodes.BRAND_NOT_FOUND);
        SaveProductRequest noCategory = request("A1");
        noCategory.setCategoryId(null);
        assertBiz(() -> service.create(noCategory), ProductErrorCodes.CATEGORY_NOT_FOUND);
    }
}

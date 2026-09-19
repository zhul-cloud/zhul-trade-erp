package com.zhul.erp.modules.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.BrandOptionVO;
import com.zhul.erp.modules.product.dto.BrandQuery;
import com.zhul.erp.modules.product.dto.BrandVO;
import com.zhul.erp.modules.product.dto.SaveBrandRequest;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.repository.IdCount;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.service.impl.BrandServiceImpl;
import com.zhul.erp.modules.product.support.OptionsCache;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandServiceImplTest {

    @Mock
    private ProductBrandMapper brandMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private OptionsCache optionsCache;

    private BrandServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0);
        service = new BrandServiceImpl(brandMapper, productMapper, new PlatformScopeGuard(), optionsCache);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static SaveBrandRequest request(String name) {
        SaveBrandRequest req = new SaveBrandRequest();
        req.setBrandName(name);
        return req;
    }

    private static ProductBrandDO brand(Long id, String name) {
        ProductBrandDO b = new ProductBrandDO();
        b.setId(id);
        b.setTenantId(0);
        b.setBrandName(name);
        b.setIsGenuine(1);
        b.setStatus(1);
        return b;
    }

    private static BizException assertBiz(org.junit.jupiter.api.function.Executable e, String errorCode) {
        BizException ex = assertThrows(BizException.class, e);
        assertEquals(errorCode, ex.getErrorCode());
        return ex;
    }

    // ---------- 创建 ----------

    @Test
    void createDefaultsToEnabledAndGenuineWithPlatformTenant() {
        BrandVO vo = service.create(request("Siemens"));

        ArgumentCaptor<ProductBrandDO> captor = ArgumentCaptor.forClass(ProductBrandDO.class);
        verify(brandMapper).insert(captor.capture());
        ProductBrandDO saved = captor.getValue();
        assertEquals("Siemens", saved.getBrandName());
        assertEquals(0, saved.getTenantId());
        assertEquals(1, saved.getStatus());
        assertEquals(1, saved.getIsGenuine());
        assertEquals(0L, vo.getProductCount());
        verify(optionsCache).evictAfterCommit(ProductConstants.CACHE_KEY_BRAND_OPTIONS);
    }

    @Test
    void createTrimsName() {
        service.create(request("  ABB  "));

        ArgumentCaptor<ProductBrandDO> captor = ArgumentCaptor.forClass(ProductBrandDO.class);
        verify(brandMapper).insert(captor.capture());
        assertEquals("ABB", captor.getValue().getBrandName());
    }

    @Test
    void createWithExistingNameIsRejectedWithExistingId() {
        when(brandMapper.selectOne(any())).thenReturn(brand(7L, "Siemens"));

        BizException e = assertBiz(() -> service.create(request("siemens ")), ProductErrorCodes.BRAND_DUPLICATE);

        Map<?, ?> detail = (Map<?, ?>) e.getDetail();
        assertEquals(7L, detail.get("existingId"));
        assertEquals(false, detail.get("deleted"));
        verify(brandMapper, never()).insert(any(ProductBrandDO.class));
    }

    @Test
    void createWithSoftDeletedNameReportsDeleted() {
        ProductBrandDO deleted = brand(8L, "ABB");
        deleted.setDeletedAt(java.time.LocalDateTime.now());
        when(brandMapper.selectOne(any())).thenReturn(deleted);

        BizException e = assertBiz(() -> service.create(request("ABB")), ProductErrorCodes.BRAND_DUPLICATE);

        assertEquals(true, ((Map<?, ?>) e.getDetail()).get("deleted"));
    }

    @Test
    void concurrentDuplicateFromUniqueKeyBecomesBrandDuplicate() {
        when(brandMapper.insert(any(ProductBrandDO.class))).thenThrow(new DuplicateKeyException("uk"));

        assertBiz(() -> service.create(request("Siemens")), ProductErrorCodes.BRAND_DUPLICATE);
        verify(optionsCache, never()).evictAfterCommit(any());
    }

    @Test
    void blankOrMissingNameIsRejected() {
        assertBiz(() -> service.create(request("   ")), ProductErrorCodes.PARAM_INVALID);
        assertBiz(() -> service.create(request(null)), ProductErrorCodes.PARAM_INVALID);
        verify(brandMapper, never()).insert(any(ProductBrandDO.class));
    }

    @Test
    void nameLengthBoundaryIs64() {
        service.create(request("a".repeat(64)));
        assertBiz(() -> service.create(request("a".repeat(65))), ProductErrorCodes.PARAM_INVALID);
    }

    @Test
    void compatibleBrandFlagIsSaved() {
        SaveBrandRequest req = request("NoName");
        req.setIsGenuine(0);

        service.create(req);

        ArgumentCaptor<ProductBrandDO> captor = ArgumentCaptor.forClass(ProductBrandDO.class);
        verify(brandMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getIsGenuine());
    }

    @Test
    void invalidGenuineFlagAndColorAreRejected() {
        SaveBrandRequest badFlag = request("A");
        badFlag.setIsGenuine(2);
        assertBiz(() -> service.create(badFlag), ProductErrorCodes.PARAM_INVALID);

        SaveBrandRequest badColor = request("A");
        badColor.setBrandColor("red");
        assertBiz(() -> service.create(badColor), ProductErrorCodes.PARAM_INVALID);

        SaveBrandRequest goodColor = request("A");
        goodColor.setBrandColor("#009999");
        service.create(goodColor);
    }

    // ---------- 修改与启停 ----------

    @Test
    void updateChangesFieldsAndKeepsGenuineWhenOmitted() {
        ProductBrandDO current = brand(3L, "Siemens");
        current.setIsGenuine(0);
        when(brandMapper.selectOne(any())).thenReturn(current, (ProductBrandDO) null, current);
        SaveBrandRequest req = request("Siemens AG");
        req.setCountry("Germany");

        service.update(3L, req);

        ArgumentCaptor<ProductBrandDO> captor = ArgumentCaptor.forClass(ProductBrandDO.class);
        verify(brandMapper).updateById(captor.capture());
        ProductBrandDO change = captor.getValue();
        assertEquals(3L, change.getId());
        assertEquals("Siemens AG", change.getBrandName());
        assertEquals("Germany", change.getCountry());
        assertEquals(0, change.getIsGenuine());
        // 变更对象不带创建时间，才会触发 update_time / update_by 自动填充
        assertNull(change.getCreateTime());
        assertNull(change.getUpdateTime());
        verify(optionsCache).evictAfterCommit(ProductConstants.CACHE_KEY_BRAND_OPTIONS);
    }

    @Test
    void updateMissingBrandIsRejected() {
        when(brandMapper.selectOne(any())).thenReturn(null);

        assertBiz(() -> service.update(99L, request("X")), ProductErrorCodes.BRAND_NOT_FOUND);
        assertBiz(() -> service.update(null, request("X")), ProductErrorCodes.BRAND_NOT_FOUND);
    }

    @Test
    void updateToNameOfAnotherBrandIsRejected() {
        when(brandMapper.selectOne(any())).thenReturn(brand(3L, "ABB"), brand(4L, "Siemens"));

        assertBiz(() -> service.update(3L, request("Siemens")), ProductErrorCodes.BRAND_DUPLICATE);
        verify(brandMapper, never()).updateById(any(ProductBrandDO.class));
    }

    @Test
    void disablingBrandUpdatesStatusAndEvictsCache() {
        when(brandMapper.selectOne(any())).thenReturn(brand(5L, "ABB"));

        service.updateStatus(5L, 0);

        ArgumentCaptor<ProductBrandDO> captor = ArgumentCaptor.forClass(ProductBrandDO.class);
        verify(brandMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
        verify(optionsCache).evictAfterCommit(ProductConstants.CACHE_KEY_BRAND_OPTIONS);
    }

    @Test
    void invalidStatusValueIsRejected() {
        assertBiz(() -> service.updateStatus(5L, 2), ProductErrorCodes.PARAM_INVALID);
        assertBiz(() -> service.updateStatus(5L, null), ProductErrorCodes.PARAM_INVALID);
    }

    // ---------- 删除 ----------

    @Test
    void deleteBrandWithProductsIsRejectedWithUsageCount() {
        when(brandMapper.selectOne(any())).thenReturn(brand(1L, "Siemens"));
        when(productMapper.selectCount(any())).thenReturn(5L);

        BizException e = assertBiz(() -> service.delete(1L), ProductErrorCodes.BRAND_IN_USE);

        assertTrue(e.getMessage().contains("5"));
        assertEquals(5L, ((Map<?, ?>) e.getDetail()).get("usageCount"));
        verify(brandMapper, never()).updateById(any(ProductBrandDO.class));
    }

    @Test
    void deleteBrandWithoutProductsSoftDeletes() {
        when(brandMapper.selectOne(any())).thenReturn(brand(1L, "Siemens"));
        when(productMapper.selectCount(any())).thenReturn(0L);

        service.delete(1L);

        ArgumentCaptor<ProductBrandDO> captor = ArgumentCaptor.forClass(ProductBrandDO.class);
        verify(brandMapper).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertNotNull(captor.getValue().getDeletedAt());
        verify(optionsCache).evictAfterCommit(ProductConstants.CACHE_KEY_BRAND_OPTIONS);
    }

    // ---------- 平台账号 ----------

    @Test
    void tenantAccountCannotWriteEvenIfPermissionGranted() {
        TenantContext.setTenantId(1001);

        assertBiz(() -> service.create(request("X")), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.update(1L, request("X")), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.updateStatus(1L, 0), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.delete(1L), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        verifyNoInteractions(brandMapper, productMapper, optionsCache);
    }

    @Test
    void tenantAccountCanRead() {
        TenantContext.setTenantId(1001);
        when(optionsCache.get(ProductConstants.CACHE_KEY_BRAND_OPTIONS, BrandOptionVO.class)).thenReturn(List.of());

        assertEquals(0, service.options().size());
    }

    // ---------- 读取 ----------

    @Test
    void optionsHitsCacheWithoutQueryingDatabase() {
        BrandOptionVO cached = new BrandOptionVO();
        cached.setId(1L);
        when(optionsCache.get(ProductConstants.CACHE_KEY_BRAND_OPTIONS, BrandOptionVO.class)).thenReturn(List.of(cached));

        List<BrandOptionVO> options = service.options();

        assertEquals(1, options.size());
        verifyNoInteractions(brandMapper);
    }

    @Test
    void optionsMissLoadsFromDatabaseAndFillsCache() {
        when(optionsCache.get(ProductConstants.CACHE_KEY_BRAND_OPTIONS, BrandOptionVO.class)).thenReturn(null);
        when(brandMapper.selectList(any())).thenReturn(List.of(brand(1L, "ABB"), brand(2L, "Siemens")));

        List<BrandOptionVO> options = service.options();

        assertEquals(2, options.size());
        assertEquals("ABB", options.get(0).getBrandName());
        verify(optionsCache).put(eq(ProductConstants.CACHE_KEY_BRAND_OPTIONS), any());
    }

    @Test
    void pageReportsProductCountPerBrand() {
        Page<ProductBrandDO> page = new Page<>(1, 20);
        page.setRecords(List.of(brand(1L, "ABB"), brand(2L, "Siemens")));
        page.setTotal(2);
        when(brandMapper.selectPage(any(), any())).thenReturn(page);
        IdCount count = new IdCount();
        count.setId(2L);
        count.setCnt(5L);
        when(productMapper.countByBrandIds(any())).thenReturn(List.of(count));

        PageResult<BrandVO> result = service.page(new BrandQuery());

        assertEquals(2L, result.getTotal());
        assertEquals(0L, result.getRecords().get(0).getProductCount());
        assertEquals(5L, result.getRecords().get(1).getProductCount());
    }

    @Test
    void emptyPageSkipsCountQuery() {
        Page<ProductBrandDO> page = new Page<>(1, 20);
        when(brandMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<BrandVO> result = service.page(new BrandQuery());

        assertEquals(0, result.getRecords().size());
        verifyNoInteractions(productMapper);
    }
}

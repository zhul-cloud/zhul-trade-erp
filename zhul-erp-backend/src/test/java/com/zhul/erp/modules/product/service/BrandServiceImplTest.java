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
import com.zhul.erp.modules.product.entity.ProductBrandAliasDO;
import com.zhul.erp.modules.product.repository.ProductBrandAliasMapper;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.service.impl.BrandServiceImpl;
import com.zhul.erp.modules.product.support.CountryCatalog;
import com.zhul.erp.modules.product.support.OptionsCache;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    @Mock
    private ProductBrandAliasMapper aliasMapper;

    private BrandServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.setTenantId(0);
        CountryCatalog countryCatalog = new CountryCatalog(new ObjectMapper());
        countryCatalog.load();
        service = new BrandServiceImpl(brandMapper, productMapper, new PlatformScopeGuard(), optionsCache,
                countryCatalog, aliasMapper);
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

    // ---------- 简介、原产地、主题色（enrich-brand-category-info） ----------

    private ProductBrandDO createAndCapture(SaveBrandRequest req) {
        service.create(req);
        ArgumentCaptor<ProductBrandDO> captor = ArgumentCaptor.forClass(ProductBrandDO.class);
        verify(brandMapper).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    void descriptionIsSavedTrimmedAndReturned() {
        SaveBrandRequest req = request("Siemens");
        req.setDescription("  德国工业自动化厂商，产品覆盖 PLC、驱动和工业通信  ");

        BrandVO vo = service.create(req);

        ArgumentCaptor<ProductBrandDO> captor = ArgumentCaptor.forClass(ProductBrandDO.class);
        verify(brandMapper).insert(captor.capture());
        assertEquals("德国工业自动化厂商，产品覆盖 PLC、驱动和工业通信", captor.getValue().getDescription());
        assertEquals("德国工业自动化厂商，产品覆盖 PLC、驱动和工业通信", vo.getDescription());
    }

    @Test
    void missingDescriptionIsSavedAsEmpty() {
        assertEquals("", createAndCapture(request("Siemens")).getDescription());
    }

    @Test
    void descriptionLengthBoundaryIs500() {
        SaveBrandRequest ok = request("A");
        ok.setDescription("字".repeat(500));
        service.create(ok);

        SaveBrandRequest tooLong = request("B");
        tooLong.setDescription("字".repeat(501));
        assertBiz(() -> service.create(tooLong), ProductErrorCodes.PARAM_INVALID);
        verify(brandMapper, org.mockito.Mockito.times(1)).insert(any(ProductBrandDO.class));
    }

    @Test
    void updateWritesDescriptionAndCanClearIt() {
        ProductBrandDO current = brand(3L, "Siemens");
        when(brandMapper.selectOne(any())).thenReturn(current, (ProductBrandDO) null, current);
        SaveBrandRequest req = request("Siemens");
        req.setDescription("");

        service.update(3L, req);

        ArgumentCaptor<ProductBrandDO> captor = ArgumentCaptor.forClass(ProductBrandDO.class);
        verify(brandMapper).updateById(captor.capture());
        // 空串会被写入（清空简介），而不是被当成 null 跳过
        assertEquals("", captor.getValue().getDescription());
    }

    @Test
    void countryFromCatalogIsSavedAsCanonicalEnglishName() {
        SaveBrandRequest req = request("Siemens");
        req.setCountry("  germany ");
        assertEquals("Germany", createAndCapture(req).getCountry());
    }

    @Test
    void countryOutsideCatalogIsRejected() {
        SaveBrandRequest req = request("Siemens");
        req.setCountry("Deutschland1");

        assertBiz(() -> service.create(req), ProductErrorCodes.PARAM_INVALID);
        verify(brandMapper, never()).insert(any(ProductBrandDO.class));
    }

    @Test
    void chineseNameIsNotAcceptedAsStoredCountry() {
        SaveBrandRequest req = request("Siemens");
        req.setCountry("德国");

        assertBiz(() -> service.create(req), ProductErrorCodes.PARAM_INVALID);
    }

    @Test
    void missingCountryIsAllowed() {
        assertEquals("", createAndCapture(request("Siemens")).getCountry());
    }

    @Test
    void taiwanKeepsExistingSpelling() {
        SaveBrandRequest req = request("Delta");
        req.setCountry("Taiwan, China");
        assertEquals("Taiwan, China", createAndCapture(req).getCountry());
    }

    @Test
    void colorIsStoredUppercase() {
        SaveBrandRequest req = request("Mitsubishi");
        req.setBrandColor("#e60012");
        assertEquals("#E60012", createAndCapture(req).getBrandColor());
    }

    @Test
    void malformedColorsAreRejected() {
        for (String bad : new String[]{"red", "#12345", "#1234567", "009999", "#GGGGGG", "#00 999"}) {
            SaveBrandRequest req = request("A");
            req.setBrandColor(bad);
            assertBiz(() -> service.create(req), ProductErrorCodes.PARAM_INVALID);
        }
        verify(brandMapper, never()).insert(any(ProductBrandDO.class));
    }

    @Test
    void emptyColorIsAllowed() {
        SaveBrandRequest req = request("A");
        req.setBrandColor("");
        assertEquals("", createAndCapture(req).getBrandColor());
    }

    @Test
    void optionsCarryDescription() {
        ProductBrandDO abb = brand(1L, "ABB");
        abb.setDescription("瑞士电气与自动化集团");
        when(optionsCache.get(ProductConstants.CACHE_KEY_BRAND_OPTIONS, BrandOptionVO.class)).thenReturn(null);
        when(brandMapper.selectList(any())).thenReturn(List.of(abb));

        assertEquals("瑞士电气与自动化集团", service.options().get(0).getDescription());
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

    // ---------- 别名（add-supplier-brand-category） ----------

    private static ProductBrandAliasDO alias(Long brandId, String alias) {
        ProductBrandAliasDO a = new ProductBrandAliasDO();
        a.setBrandId(brandId);
        a.setAlias(alias);
        a.setAliasKey(alias.trim().toLowerCase(java.util.Locale.ROOT));
        return a;
    }

    @Test
    void updateReplacesAliasesDedupedAndIgnoresOwnName() {
        when(brandMapper.selectOne(any())).thenReturn(brand(1L, "Siemens"), (ProductBrandDO) null, null, null, brand(1L, "Siemens"));
        SaveBrandRequest req = request("Siemens");
        req.setAliases(List.of("西门子", " SIEMENS AG ", "siemens ag", "siemens"));

        service.update(1L, req);

        ArgumentCaptor<ProductBrandAliasDO> captor = ArgumentCaptor.forClass(ProductBrandAliasDO.class);
        verify(aliasMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals(List.of("西门子", "SIEMENS AG"), captor.getAllValues().stream().map(ProductBrandAliasDO::getAlias).toList());
        assertEquals("siemens ag", captor.getAllValues().get(1).getAliasKey());
    }

    @Test
    void aliasTakenByAnotherBrandIsRejected() {
        when(brandMapper.selectOne(any())).thenReturn(brand(2L, "ABB"), (ProductBrandDO) null, null);
        when(aliasMapper.selectOne(any())).thenReturn(null, alias(1L, "西门子"));
        when(brandMapper.selectById(1L)).thenReturn(brand(1L, "Siemens"));
        SaveBrandRequest req = request("ABB");
        req.setAliases(List.of("西门子"));

        BizException e = assertBiz(() -> service.update(2L, req), ProductErrorCodes.BRAND_ALIAS_CONFLICT);
        assertEquals("「西门子」已是品牌 Siemens 的别名", e.getMessage());
        verify(aliasMapper, never()).insert(any(ProductBrandAliasDO.class));
    }

    @Test
    void aliasEqualToAnotherBrandNameIsRejected() {
        when(brandMapper.selectOne(any())).thenReturn(brand(2L, "ABB"), (ProductBrandDO) null, brand(3L, "Omron"));
        SaveBrandRequest req = request("ABB");
        req.setAliases(List.of("omron"));

        assertBiz(() -> service.update(2L, req), ProductErrorCodes.BRAND_ALIAS_CONFLICT);
    }

    @Test
    void brandNameEqualToAnotherBrandsAliasIsRejected() {
        when(aliasMapper.selectOne(any())).thenReturn(alias(1L, "西门子"));
        when(brandMapper.selectById(1L)).thenReturn(brand(1L, "Siemens"));

        assertBiz(() -> service.create(request("西门子")), ProductErrorCodes.BRAND_ALIAS_CONFLICT);
        verify(brandMapper, never()).insert(any(ProductBrandDO.class));
    }

    @Test
    void deleteBrandUsedBySupplierIsRejected() {
        when(brandMapper.selectOne(any())).thenReturn(brand(1L, "ABB"));
        when(brandMapper.countSupplierScopes(1L)).thenReturn(2L);

        BizException e = assertBiz(() -> service.delete(1L), ProductErrorCodes.BRAND_IN_USE);
        assertEquals("该品牌已被供应商主营产品使用，可改为停用", e.getMessage());
    }
}

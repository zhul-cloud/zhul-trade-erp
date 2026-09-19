package com.zhul.erp.modules.product;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.controller.BrandController;
import com.zhul.erp.modules.product.dto.BrandOptionVO;
import com.zhul.erp.modules.product.dto.BrandQuery;
import com.zhul.erp.modules.product.dto.BrandVO;
import com.zhul.erp.modules.product.dto.CategoryVO;
import com.zhul.erp.modules.product.dto.CreateSeriesRequest;
import com.zhul.erp.modules.product.dto.SaveBrandRequest;
import com.zhul.erp.modules.product.dto.SaveCategoryRequest;
import com.zhul.erp.modules.product.dto.SeriesOptionVO;
import com.zhul.erp.modules.product.service.BrandService;
import com.zhul.erp.modules.product.service.CategoryService;
import com.zhul.erp.modules.product.service.SeriesService;
import com.zhul.erp.modules.product.support.OptionsCache;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 品牌 / 品类 / 系列在真实 MySQL 与 Redis 上的行为（任务 2.2、4.1–4.6）。 */
class ProductMasterDataIntegrationTest extends IntegrationTestBase {

    private static final int RES_BRAND_ADD = 110101;

    @Autowired
    private BrandService brandService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private SeriesService seriesService;
    @Autowired
    private BrandController brandController;
    @Autowired
    private OptionsCache optionsCache;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanData() {
        for (String table : List.of("product", "product_series", "product_category", "product_brand")) {
            jdbc.update("delete from " + table);
        }
        redis.delete(ProductConstants.CACHE_KEY_BRAND_OPTIONS);
        redis.delete(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS);
        TenantContext.setTenantId(0);
    }

    private static SaveBrandRequest brandRequest(String name) {
        SaveBrandRequest req = new SaveBrandRequest();
        req.setBrandName(name);
        return req;
    }

    private static SaveCategoryRequest categoryRequest(String code, String name) {
        SaveCategoryRequest req = new SaveCategoryRequest();
        req.setCategoryCode(code);
        req.setCategoryName(name);
        return req;
    }

    private long insertProduct(long brandId, Long categoryId, Long seriesId, String mpn) {
        jdbc.update("insert into product (tenant_id, brand_id, category_id, series_id, mpn_raw, mpn_normalized) "
                + "values (0, ?, ?, ?, ?, ?)", brandId, categoryId == null ? 0 : categoryId, seriesId, mpn,
                mpn.toLowerCase());
        return jdbc.queryForObject("select max(id) from product", Long.class);
    }

    // ---------- 唯一性（含大小写）与软删除占位 ----------

    @Test
    void brandNameIsUniqueIgnoringCaseAgainstRealDatabase() {
        brandService.create(brandRequest("Siemens"));

        BizException e = assertThrows(BizException.class, () -> brandService.create(brandRequest("siemens ")));
        assertEquals(ProductErrorCodes.BRAND_DUPLICATE, e.getErrorCode());
        assertEquals(false, ((Map<?, ?>) e.getDetail()).get("deleted"));
    }

    @Test
    void deletedBrandStillOccupiesItsName() {
        BrandVO brand = brandService.create(brandRequest("ABB"));
        brandService.delete(brand.getId());

        BizException e = assertThrows(BizException.class, () -> brandService.create(brandRequest("abb")));

        assertEquals(ProductErrorCodes.BRAND_DUPLICATE, e.getErrorCode());
        assertEquals(true, ((Map<?, ?>) e.getDetail()).get("deleted"));
        assertEquals(0, brandService.page(new BrandQuery()).getRecords().size());
    }

    @Test
    void seriesNameIsUniquePerBrandIgnoringCase() {
        long siemens = brandService.create(brandRequest("Siemens")).getId();
        long abb = brandService.create(brandRequest("ABB")).getId();
        seriesService.create(seriesRequest(siemens, "S7-1200"));

        BizException e = assertThrows(BizException.class,
                () -> seriesService.create(seriesRequest(siemens, "s7-1200")));
        assertEquals(ProductErrorCodes.SERIES_DUPLICATE, e.getErrorCode());

        seriesService.create(seriesRequest(abb, "s7-1200"));
        assertEquals(1, seriesService.options(siemens).size());
        assertEquals(1, seriesService.options(abb).size());
        assertEquals(2, seriesService.options(null).size());
    }

    private static CreateSeriesRequest seriesRequest(long brandId, String name) {
        CreateSeriesRequest req = new CreateSeriesRequest();
        req.setBrandId(brandId);
        req.setSeriesName(name);
        return req;
    }

    // ---------- 平台共享：所有租户读到同一份数据 ----------

    @Test
    void tenantAccountsReadTheSameDataAndRowsAreStoredWithTenantZero() {
        brandService.create(brandRequest("Siemens"));
        assertEquals(0, jdbc.queryForObject("select tenant_id from product_brand where brand_name = 'Siemens'",
                Integer.class));

        TenantContext.setTenantId(1001);
        List<String> seenBy1001 = brandService.page(new BrandQuery()).getRecords().stream()
                .map(BrandVO::getBrandName).toList();
        TenantContext.setTenantId(1002);
        List<String> seenBy1002 = brandService.page(new BrandQuery()).getRecords().stream()
                .map(BrandVO::getBrandName).toList();

        assertEquals(List.of("Siemens"), seenBy1001);
        assertEquals(seenBy1001, seenBy1002);
    }

    @Test
    void tenantAccountCannotWriteAndNothingIsPersisted() {
        TenantContext.setTenantId(1001);

        BizException e = assertThrows(BizException.class, () -> brandService.create(brandRequest("X")));

        assertEquals(ProductErrorCodes.PLATFORM_ADMIN_REQUIRED, e.getErrorCode());
        assertEquals(0, jdbc.queryForObject("select count(*) from product_brand", Integer.class));
    }

    // ---------- 权限码 + 平台账号，两道校验 ----------

    @Test
    void permissionGrantedButTenantAccountStillRejected() {
        loginWithResources("it_product_user", RES_BRAND_ADD);
        TenantContext.setTenantId(1001);

        BizException e = assertThrows(BizException.class, () -> brandController.create(brandRequest("X")));

        assertEquals(ProductErrorCodes.PLATFORM_ADMIN_REQUIRED, e.getErrorCode());
    }

    @Test
    void permissionGrantedAndPlatformAccountSucceeds() {
        loginWithResources("it_product_user", RES_BRAND_ADD);
        TenantContext.setTenantId(0);

        Result<BrandVO> result = brandController.create(brandRequest("Siemens"));

        assertEquals(0, result.getCode());
        assertNotNull(result.getData().getId());
    }

    @Test
    void platformAccountWithoutPermissionIsDenied() {
        loginWithResources("it_product_user");
        TenantContext.setTenantId(0);

        assertThrows(AccessDeniedException.class, () -> brandController.create(brandRequest("X")));
        assertThrows(AccessDeniedException.class, () -> brandController.delete(1L));
        assertEquals(0, jdbc.queryForObject("select count(*) from product_brand", Integer.class));
    }

    @Test
    void readEndpointsOnlyNeedLogin() {
        brandService.create(brandRequest("Siemens"));
        loginWithResources("it_product_user");
        TenantContext.setTenantId(1001);

        Result<PageResult<BrandVO>> page = brandController.page(new BrandQuery());
        Result<List<BrandOptionVO>> options = brandController.options();

        assertEquals(1, page.getData().getRecords().size());
        assertEquals(1, options.getData().size());
    }

    // ---------- 使用中保护 ----------

    @Test
    void brandWithProductsCannotBeDeletedThenCanAfterProductsAreGone() {
        long brandId = brandService.create(brandRequest("Siemens")).getId();
        for (int i = 0; i < 5; i++) {
            insertProduct(brandId, null, null, "P" + i);
        }

        BizException e = assertThrows(BizException.class, () -> brandService.delete(brandId));
        assertEquals(ProductErrorCodes.BRAND_IN_USE, e.getErrorCode());
        assertEquals(5L, ((Map<?, ?>) e.getDetail()).get("usageCount"));
        assertEquals(5L, brandService.page(new BrandQuery()).getRecords().get(0).getProductCount());

        jdbc.update("update product set deleted_at = now()");
        brandService.delete(brandId);
        assertNotNull(jdbc.queryForObject("select deleted_at from product_brand where id = ?", Timestamp.class, brandId));
    }

    @Test
    void categoryCodeLockedOnceProductsExistButNameStillEditable() {
        CategoryVO drives = categoryService.create(categoryRequest("drives", "Drives"));
        long brandId = brandService.create(brandRequest("ABB")).getId();
        insertProduct(brandId, drives.getId(), null, "ACS580");

        BizException e = assertThrows(BizException.class,
                () -> categoryService.update(drives.getId(), categoryRequest("inverters", "Drives")));
        assertEquals(ProductErrorCodes.CATEGORY_CODE_IMMUTABLE, e.getErrorCode());

        CategoryVO renamed = categoryService.update(drives.getId(), categoryRequest("drives", "Drives & Inverters"));
        assertEquals("drives", renamed.getCategoryCode());
        assertEquals("Drives & Inverters", renamed.getCategoryName());
    }

    @Test
    void seriesWithProductsCannotBeDeleted() {
        long brandId = brandService.create(brandRequest("Siemens")).getId();
        long seriesId = seriesService.create(seriesRequest(brandId, "S7-1200")).getId();
        for (int i = 0; i < 3; i++) {
            insertProduct(brandId, null, seriesId, "S" + i);
        }

        BizException e = assertThrows(BizException.class, () -> seriesService.delete(seriesId));

        assertEquals(ProductErrorCodes.SERIES_IN_USE, e.getErrorCode());
        assertEquals(3L, ((Map<?, ?>) e.getDetail()).get("usageCount"));
    }

    // ---------- 停用 ----------

    @Test
    void disabledBrandLeavesOptionsButStaysInList() {
        long brandId = brandService.create(brandRequest("ABB")).getId();
        assertEquals(1, brandService.options().size());

        brandService.updateStatus(brandId, 0);

        assertEquals(0, brandService.options().size());
        assertEquals(1, brandService.page(new BrandQuery()).getRecords().size());
    }

    // ---------- 审计字段 ----------

    @Test
    void updateRefreshesUpdateTimeAndOperator() {
        long brandId = brandService.create(brandRequest("ABB")).getId();
        jdbc.update("update product_brand set update_time = '2020-01-01 00:00:00', update_by = 'old' where id = ?", brandId);

        brandService.updateStatus(brandId, 0);

        Map<String, Object> row = jdbc.queryForMap("select update_time, update_by from product_brand where id = ?", brandId);
        assertTrue(((LocalDateTime) row.get("update_time")).isAfter(LocalDateTime.now().minusMinutes(1)));
        assertFalse("old".equals(row.get("update_by")));
    }

    // ---------- 选项缓存 ----------

    @Test
    void optionsAreCachedAndEvictedAfterWrite() {
        long brandId = brandService.create(brandRequest("ABB")).getId();
        assertFalse(Boolean.TRUE.equals(redis.hasKey(ProductConstants.CACHE_KEY_BRAND_OPTIONS)), "创建后缓存应已清除");

        brandService.options();
        assertTrue(Boolean.TRUE.equals(redis.hasKey(ProductConstants.CACHE_KEY_BRAND_OPTIONS)));
        Long ttl = redis.getExpire(ProductConstants.CACHE_KEY_BRAND_OPTIONS);
        assertTrue(ttl != null && ttl > 0 && ttl <= ProductConstants.OPTIONS_CACHE_TTL_SECONDS);

        brandService.updateStatus(brandId, 0);
        assertFalse(Boolean.TRUE.equals(redis.hasKey(ProductConstants.CACHE_KEY_BRAND_OPTIONS)), "修改后缓存应被清除");
        assertEquals(0, brandService.options().size());
    }

    @Test
    void categoryOptionsAreCachedAndEvictedAfterWrite() {
        long id = categoryService.create(categoryRequest("servo", "Servo")).getId();
        categoryService.options();
        assertTrue(Boolean.TRUE.equals(redis.hasKey(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS)));

        categoryService.updateStatus(id, 0);

        assertFalse(Boolean.TRUE.equals(redis.hasKey(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS)));
    }

    @Test
    void cacheIsKeptWhenTransactionRollsBack() {
        brandService.create(brandRequest("ABB"));
        brandService.options();
        assertTrue(Boolean.TRUE.equals(redis.hasKey(ProductConstants.CACHE_KEY_BRAND_OPTIONS)));

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            optionsCache.evictAfterCommit(ProductConstants.CACHE_KEY_BRAND_OPTIONS);
            throw new IllegalStateException("回滚");
        }));

        assertTrue(Boolean.TRUE.equals(redis.hasKey(ProductConstants.CACHE_KEY_BRAND_OPTIONS)), "回滚时不应清缓存");
    }

    @Test
    void optionsFromCacheEqualOptionsFromDatabase() {
        brandService.create(brandRequest("ABB"));
        List<BrandOptionVO> fromDb = brandService.options();
        List<BrandOptionVO> fromCache = brandService.options();

        assertEquals(fromDb, fromCache);
    }

    @Test
    void seriesOptionsForUnknownBrandAreEmpty() {
        assertEquals(List.<SeriesOptionVO>of(), seriesService.options(123456L));
    }
}

package com.zhul.erp.modules.product;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.controller.ProductController;
import com.zhul.erp.modules.product.dto.ProductMatchVO;
import com.zhul.erp.modules.product.dto.ProductOptionVO;
import com.zhul.erp.modules.product.dto.ProductQuery;
import com.zhul.erp.modules.product.dto.ProductVO;
import com.zhul.erp.modules.product.dto.RelationshipVO;
import com.zhul.erp.modules.product.dto.SaveBrandRequest;
import com.zhul.erp.modules.product.dto.SaveCategoryRequest;
import com.zhul.erp.modules.product.dto.SaveProductRequest;
import com.zhul.erp.modules.product.dto.SaveRelationshipRequest;
import com.zhul.erp.modules.product.dto.SaveSpecificationsRequest;
import com.zhul.erp.modules.product.dto.SpecificationVO;
import com.zhul.erp.modules.product.service.BrandService;
import com.zhul.erp.modules.product.service.CategoryService;
import com.zhul.erp.modules.product.service.ProductLookupService;
import com.zhul.erp.modules.product.service.ProductRelationshipService;
import com.zhul.erp.modules.product.service.ProductService;
import com.zhul.erp.modules.product.service.ProductSpecificationService;
import com.zhul.erp.modules.product.support.ProductUsageChecker;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 商品主体、规格、型号关系、查找在真实 MySQL 上的行为（任务 5.1–5.11、6.1、6.2、6.4）。 */
class ProductCoreIntegrationTest extends IntegrationTestBase {

    private static final int RES_PRODUCT_ADD = 110131;
    private static final AtomicLong USAGE = new AtomicLong();

    /** 测试用的引用检查器：本期没有真实的业务模块实现 */
    @TestConfiguration
    static class UsageCheckerConfig {
        @Bean
        ProductUsageChecker testUsageChecker() {
            return productId -> USAGE.get();
        }
    }

    @Autowired
    private BrandService brandService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductSpecificationService specificationService;
    @Autowired
    private ProductRelationshipService relationshipService;
    @Autowired
    private ProductLookupService lookupService;
    @Autowired
    private ProductController productController;

    private long siemens;
    private long abb;
    private long controllers;

    @BeforeEach
    void setUp() {
        for (String table : List.of("product_relationship", "product_specification", "product", "product_series",
                "product_category", "product_brand")) {
            jdbc.update("delete from " + table);
        }
        USAGE.set(0);
        TenantContext.setTenantId(0);
        siemens = brand("Siemens");
        abb = brand("ABB");
        SaveCategoryRequest category = new SaveCategoryRequest();
        category.setCategoryCode("controllers");
        category.setCategoryName("PLC & Controllers");
        controllers = categoryService.create(category).getId();
    }

    private long brand(String name) {
        SaveBrandRequest req = new SaveBrandRequest();
        req.setBrandName(name);
        return brandService.create(req).getId();
    }

    private SaveProductRequest productRequest(long brandId, String mpn) {
        SaveProductRequest req = new SaveProductRequest();
        req.setBrandId(brandId);
        req.setCategoryId(controllers);
        req.setMpnRaw(mpn);
        return req;
    }

    private long product(long brandId, String mpn) {
        return productService.create(productRequest(brandId, mpn)).getId();
    }

    private static BizException biz(org.junit.jupiter.api.function.Executable e, String errorCode) {
        BizException ex = assertThrows(BizException.class, e);
        assertEquals(errorCode, ex.getErrorCode());
        return ex;
    }

    // ---------- 创建与归一化去重 ----------

    @Test
    void createProductWithDefaults() {
        ProductVO vo = productService.create(productRequest(siemens, "6ES7 214-1BD23-0XB0"));

        assertEquals("6ES7 214-1BD23-0XB0", vo.getMpnDisplay());
        assertEquals("6es72141bd230xb0", vo.getMpnNormalized());
        assertEquals(1, vo.getStatus());
        assertEquals(6, vo.getLifecycleStatus());
        assertEquals("Siemens", vo.getBrandName());
        assertEquals("PLC & Controllers", vo.getCategoryName());
        assertEquals(0, jdbc.queryForObject("select tenant_id from product where id = ?", Integer.class, vo.getId()));
    }

    @Test
    void differentWritingsOfTheSameModelAreDuplicates() {
        long existing = product(siemens, "6ES7214-1BD23-0XB0");
        product(siemens, "SGMAH-04ADA-TF13");
        product(siemens, "6ES7214");

        for (String variant : List.of("6ES7 214-1BD23-0XB0", "6es7214-1bd23-0xb0", "6ES7214 1BD23 0XB0")) {
            BizException e = biz(() -> product(siemens, variant), ProductErrorCodes.PRODUCT_DUPLICATE);
            Map<?, ?> detail = (Map<?, ?>) e.getDetail();
            assertEquals(existing, ((Number) detail.get("existingId")).longValue());
            assertEquals("6ES7214-1BD23-0XB0", detail.get("mpnDisplay"));
        }
        biz(() -> product(siemens, "sgmah04adatf13"), ProductErrorCodes.PRODUCT_DUPLICATE);
        biz(() -> product(siemens, "６ＥＳ７２１４"), ProductErrorCodes.PRODUCT_DUPLICATE);
    }

    @Test
    void sameModelUnderDifferentBrandsCoexists() {
        long a = product(siemens, "ABC-100");
        long b = product(abb, "ABC-100");
        assertTrue(a != b);
    }

    @Test
    void modelWithoutLettersOrDigitsIsRejected() {
        biz(() -> product(siemens, "---"), ProductErrorCodes.PRODUCT_MPN_INVALID);
    }

    @Test
    void concurrentCreatesOfTheSameModelSucceedOnlyOnce() throws Exception {
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String writing = i % 2 == 0 ? "ABC-100" : "abc 100";
            results.add(pool.submit((Callable<String>) () -> {
                TenantContext.setTenantId(0);
                ready.countDown();
                go.await();
                try {
                    productService.create(productRequest(siemens, writing));
                    return "OK";
                } catch (BizException e) {
                    return e.getErrorCode();
                } finally {
                    TenantContext.clear();
                }
            }));
        }
        ready.await();
        go.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get());
        }
        pool.shutdown();

        assertEquals(1, outcomes.stream().filter("OK"::equals).count(), "只应成功一个：" + outcomes);
        assertEquals(threads - 1, outcomes.stream().filter(ProductErrorCodes.PRODUCT_DUPLICATE::equals).count(),
                "其余都应收到已存在的提示：" + outcomes);
        assertEquals(1, jdbc.queryForObject("select count(*) from product", Integer.class));
    }

    // ---------- 删除、恢复 ----------

    @Test
    void deletedModelCannotBeRecreatedButCanBeRestoredWithAllData() {
        long id = product(siemens, "6ES7214");
        SaveSpecificationsRequest specs = specs(spec("rated_voltage", "24"), spec("current", "5"));
        specificationService.replace(id, specs);
        long other = product(abb, "OTHER-1");
        SaveRelationshipRequest rel = relationship("OLD-1", 4);
        relationshipService.create(id, rel);

        productService.delete(id);

        BizException e = biz(() -> product(siemens, "6ES7 214"), ProductErrorCodes.PRODUCT_DUPLICATE);
        assertEquals(true, ((Map<?, ?>) e.getDetail()).get("deleted"));
        assertEquals(id, ((Number) ((Map<?, ?>) e.getDetail()).get("existingId")).longValue());
        biz(() -> productService.getById(id), ProductErrorCodes.PRODUCT_NOT_FOUND);

        ProductVO restored = productService.restore(id);

        assertEquals(id, restored.getId());
        assertEquals(2, specificationService.list(id).size());
        assertEquals(1, relationshipService.list(id).size());
        assertEquals(0, productService.page(new ProductQuery()).getRecords().stream()
                .filter(p -> p.getId() == other).count() - 1);
    }

    @Test
    void restoreOfActiveProductIsRejected() {
        long id = product(siemens, "6ES7214");
        biz(() -> productService.restore(id), ProductErrorCodes.PRODUCT_NOT_FOUND);
    }

    @Test
    void deleteAndImmutabilityDependOnUsage() {
        long id = product(siemens, "6ES7214");
        USAGE.set(2);

        BizException e = biz(() -> productService.delete(id), ProductErrorCodes.PRODUCT_IN_USE);
        assertEquals(2L, ((Number) ((Map<?, ?>) e.getDetail()).get("usageCount")).longValue());
        biz(() -> productService.update(id, productRequest(siemens, "6ES7215")), ProductErrorCodes.PRODUCT_MPN_IMMUTABLE);
        biz(() -> productService.update(id, productRequest(abb, "6ES7214")), ProductErrorCodes.PRODUCT_MPN_IMMUTABLE);

        SaveProductRequest rename = productRequest(siemens, "6ES7214");
        rename.setProductName("Renamed");
        assertEquals("Renamed", productService.update(id, rename).getProductName());

        productService.updateStatus(id, 0);
        assertEquals(0, productService.getById(id).getStatus());
        assertEquals(2L, productService.getById(id).getUsageCount());

        USAGE.set(0);
        productService.delete(id);
        biz(() -> productService.getById(id), ProductErrorCodes.PRODUCT_NOT_FOUND);
    }

    @Test
    void updateClearsSeriesAndRefreshesAuditFields() {
        long id = product(siemens, "6ES7214");
        var series = new com.zhul.erp.modules.product.dto.CreateSeriesRequest();
        series.setBrandId(siemens);
        series.setSeriesName("S7-1200");
        long seriesId = seriesId(series);
        SaveProductRequest withSeries = productRequest(siemens, "6ES7214");
        withSeries.setSeriesId(seriesId);
        assertEquals(seriesId, productService.update(id, withSeries).getSeriesId());
        jdbc.update("update product set update_time = '2020-01-01 00:00:00', update_by = 'old' where id = ?", id);

        ProductVO cleared = productService.update(id, productRequest(siemens, "6ES7214"));

        assertNull(cleared.getSeriesId());
        Map<String, Object> row = jdbc.queryForMap("select update_time, update_by, series_id from product where id = ?", id);
        assertNull(row.get("series_id"));
        assertFalse("old".equals(row.get("update_by")));
        assertTrue(((java.time.LocalDateTime) row.get("update_time")).isAfter(java.time.LocalDateTime.now().minusMinutes(1)));
    }

    private long seriesId(com.zhul.erp.modules.product.dto.CreateSeriesRequest req) {
        return seriesService.create(req).getId();
    }

    @Autowired
    private com.zhul.erp.modules.product.service.SeriesService seriesService;

    // ---------- 生命周期 ----------

    @Test
    void lifecycleRulesAgainstRealData() {
        long id = product(siemens, "6ES7214");
        SaveProductRequest obsolete = productRequest(siemens, "6ES7214");
        obsolete.setLifecycleStatus(5);
        biz(() -> productService.update(id, obsolete), ProductErrorCodes.PRODUCT_LIFECYCLE_SOURCE_REQUIRED);

        long successor = product(siemens, "6ES7215");
        SaveRelationshipRequest rel = relationship("6ES7215", 1);
        rel.setRelatedProductId(successor);
        relationshipService.create(id, rel);
        obsolete.setLifecycleSource("EOL notice");
        ProductVO saved = productService.update(id, obsolete);

        assertEquals(5, saved.getLifecycleStatus());
        assertEquals(1, saved.getWarnings().size());
    }

    // ---------- 规格 ----------

    private static SaveSpecificationsRequest.SpecificationItem spec(String key, String value) {
        SaveSpecificationsRequest.SpecificationItem item = new SaveSpecificationsRequest.SpecificationItem();
        item.setSpecKey(key);
        item.setSpecLabel(key);
        item.setSpecValue(value);
        return item;
    }

    private static SaveSpecificationsRequest specs(SaveSpecificationsRequest.SpecificationItem... items) {
        SaveSpecificationsRequest req = new SaveSpecificationsRequest();
        req.setItems(List.of(items));
        return req;
    }

    @Test
    void replacingSpecificationsLeavesExactlyTheSubmittedSet() {
        long id = product(siemens, "6ES7214");
        specificationService.replace(id, specs(spec("a", "1"), spec("b", "2"), spec("c", "3")));

        List<SpecificationVO> after = specificationService.replace(id, specs(spec("a", "10"), spec("c", "30")));

        assertEquals(List.of("a", "c"), after.stream().map(SpecificationVO::getSpecKey).toList());
        assertEquals("10", after.get(0).getSpecValue());
        assertEquals(2, specificationService.list(id).size());
        // 旧行是软删除，不是物理删除
        assertEquals(5, jdbc.queryForObject("select count(*) from product_specification where product_id = ?", Integer.class, id));
    }

    @Test
    void duplicateKeyRejectsWholeSubmissionAndKeepsOldSpecs() {
        long id = product(siemens, "6ES7214");
        specificationService.replace(id, specs(spec("a", "1"), spec("b", "2")));

        biz(() -> specificationService.replace(id, specs(spec("x", "1"), spec("x", "2"))), ProductErrorCodes.PARAM_INVALID);

        assertEquals(List.of("a", "b"), specificationService.list(id).stream().map(SpecificationVO::getSpecKey).toList());
    }

    @Test
    void replacingWithSameKeysRepeatedlyWorks() {
        long id = product(siemens, "6ES7214");
        for (int i = 0; i < 3; i++) {
            specificationService.replace(id, specs(spec("rated_voltage", "v" + i)));
        }
        assertEquals("v2", specificationService.list(id).get(0).getSpecValue());
    }

    @Test
    void emptySubmissionClearsAllSpecs() {
        long id = product(siemens, "6ES7214");
        specificationService.replace(id, specs(spec("a", "1")));

        assertEquals(0, specificationService.replace(id, specs()).size());
    }

    // ---------- 型号关系 ----------

    private static SaveRelationshipRequest relationship(String relatedMpn, int type) {
        SaveRelationshipRequest req = new SaveRelationshipRequest();
        req.setRelatedMpn(relatedMpn);
        req.setRelationshipType(type);
        return req;
    }

    @Test
    void relationToModelOutsideCatalogKeepsOnlyText() {
        long id = product(siemens, "6ES7214");

        RelationshipVO vo = relationshipService.create(id, relationship("6ES7212-1AB23-0XB0", 2));

        assertNull(vo.getRelatedProductId());
        assertEquals("6ES7212-1AB23-0XB0", vo.getRelatedMpn());
        assertEquals(3, vo.getConfidence());
    }

    @Test
    void cannotRelateToItselfInAnyWriting() {
        long id = product(siemens, "6ES7 214-1BD23");

        biz(() -> relationshipService.create(id, relationship("6es7214-1bd23", 4)), ProductErrorCodes.RELATIONSHIP_INVALID);
    }

    @Test
    void duplicateRelationIsRejectedButDifferentTypeIsAllowed() {
        long id = product(siemens, "6ES7214");
        relationshipService.create(id, relationship("X-100", 4));

        biz(() -> relationshipService.create(id, relationship("x 100", 4)), ProductErrorCodes.RELATIONSHIP_DUPLICATE);
        relationshipService.create(id, relationship("X-100", 5));
        assertEquals(2, relationshipService.list(id).size());
    }

    @Test
    void verifiedConfidenceNeedsVerifierAndStampsTime() {
        long id = product(siemens, "6ES7214");
        SaveRelationshipRequest rel = relationship("X-100", 1);
        rel.setConfidence(1);
        biz(() -> relationshipService.create(id, rel), ProductErrorCodes.RELATIONSHIP_INVALID);

        rel.setVerifiedBy("alice");
        RelationshipVO vo = relationshipService.create(id, rel);

        assertEquals("alice", vo.getVerifiedBy());
        assertNotNull(vo.getVerifiedAt());
    }

    @Test
    void uniqueCatalogHitIsLinkedAutomaticallyButAmbiguousOneIsNot() {
        long id = product(siemens, "6ES7214");
        long target = product(siemens, "ABC-200");

        RelationshipVO linked = relationshipService.create(id, relationship("abc 200", 4));
        assertEquals(target, linked.getRelatedProductId());
        assertEquals("Siemens", linked.getRelatedBrandName());

        product(siemens, "ABC-100");
        product(abb, "ABC-100");
        RelationshipVO ambiguous = relationshipService.create(id, relationship("ABC100", 4));
        assertNull(ambiguous.getRelatedProductId());
    }

    @Test
    void symmetricRelationCanCreateReverseInOneTransaction() {
        long a = product(siemens, "A-1");
        long b = product(abb, "B-1");
        SaveRelationshipRequest rel = relationship("B-1", 4);
        rel.setCreateReverse(true);

        relationshipService.create(a, rel);

        assertEquals(1, relationshipService.list(a).size());
        List<RelationshipVO> reverse = relationshipService.list(b);
        assertEquals(1, reverse.size());
        assertEquals(a, reverse.get(0).getRelatedProductId());
        assertEquals(4, reverse.get(0).getRelationshipType());
    }

    @Test
    void asymmetricRelationIgnoresReverseRequest() {
        long a = product(siemens, "A-1");
        long b = product(abb, "B-1");
        SaveRelationshipRequest rel = relationship("B-1", 1);
        rel.setCreateReverse(true);

        relationshipService.create(a, rel);

        assertEquals(1, relationshipService.list(a).size());
        assertEquals(0, relationshipService.list(b).size());
    }

    @Test
    void reverseFailureRollsBackTheForwardRelation() {
        long a = product(siemens, "A-1");
        long b = product(abb, "B-1");
        relationshipService.create(b, relationship("A-1", 4));
        SaveRelationshipRequest rel = relationship("B-1", 4);
        rel.setCreateReverse(true);

        biz(() -> relationshipService.create(a, rel), ProductErrorCodes.RELATIONSHIP_DUPLICATE);

        assertEquals(0, relationshipService.list(a).size(), "正向关系也不应保存");
        assertEquals(1, relationshipService.list(b).size());
    }

    @Test
    void reverseRequiresCatalogProduct() {
        long a = product(siemens, "A-1");
        SaveRelationshipRequest rel = relationship("NOT-IN-CATALOG", 4);
        rel.setCreateReverse(true);

        biz(() -> relationshipService.create(a, rel), ProductErrorCodes.RELATIONSHIP_INVALID);
        assertEquals(0, relationshipService.list(a).size());
    }

    @Test
    void updateAndDeleteRelationshipOnlyWorkOnOwnRows() {
        long a = product(siemens, "A-1");
        long b = product(abb, "B-1");
        RelationshipVO rel = relationshipService.create(a, relationship("X-100", 4));

        biz(() -> relationshipService.update(b, rel.getId(), relationship("X-100", 5)), ProductErrorCodes.CONTENT_NOT_FOUND);
        biz(() -> relationshipService.delete(b, rel.getId()), ProductErrorCodes.CONTENT_NOT_FOUND);

        SaveRelationshipRequest change = relationship("X-100", 5);
        change.setNote("changed");
        assertEquals(5, relationshipService.update(a, rel.getId(), change).getRelationshipType());
        relationshipService.delete(a, rel.getId());
        assertEquals(0, relationshipService.list(a).size());
    }

    @Test
    void updateCanClearAutoLinkAndVerification() {
        long a = product(siemens, "A-1");
        long target = product(abb, "B-1");
        SaveRelationshipRequest verified = relationship("B-1", 4);
        verified.setConfidence(1);
        verified.setVerifiedBy("alice");
        RelationshipVO rel = relationshipService.create(a, verified);
        assertEquals(target, rel.getRelatedProductId());
        assertNotNull(rel.getVerifiedAt());

        SaveRelationshipRequest downgrade = relationship("GONE-1", 4);
        downgrade.setConfidence(3);
        RelationshipVO updated = relationshipService.update(a, rel.getId(), downgrade);

        assertNull(updated.getRelatedProductId());
        assertNull(updated.getVerifiedAt());
    }

    // ---------- 查找 ----------

    @Test
    void searchIgnoresSpacesHyphensAndCase() {
        long id = product(siemens, "6ES7214-1BD23-0XB0");

        for (String keyword : List.of("6ES7 214", "6es7214", "6ES7-214", "  6ES7 214  ")) {
            List<ProductOptionVO> found = lookupService.search(keyword, null, null);
            assertEquals(1, found.size(), keyword);
            assertEquals(id, found.get(0).getId());
            assertEquals("Siemens", found.get(0).getBrandName());
            assertEquals("PLC & Controllers", found.get(0).getCategoryName());
        }
    }

    @Test
    void searchOnlyReturnsEnabledAndUndeletedProducts() {
        long enabled = product(siemens, "AB-1");
        long disabled = product(siemens, "AB-2");
        long deleted = product(siemens, "AB-3");
        productService.updateStatus(disabled, 0);
        productService.delete(deleted);

        List<Long> ids = lookupService.search("AB", null, null).stream().map(ProductOptionVO::getId).toList();

        assertEquals(List.of(enabled), ids);
    }

    @Test
    void searchLimitDefaultsTo20AndCapsAt50() {
        for (int i = 0; i < 55; i++) {
            product(siemens, String.format("LIM-%03d", i));
        }

        assertEquals(20, lookupService.search("LIM", null, null).size());
        assertEquals(20, lookupService.search("LIM", 0, null).size());
        assertEquals(20, lookupService.search("LIM", -5, null).size());
        assertEquals(50, lookupService.search("LIM", 100, null).size());
        assertEquals(7, lookupService.search("LIM", 7, null).size());
    }

    @Test
    void blankKeywordAndSymbolOnlyKeywordDoNotReturnEverything() {
        product(siemens, "AB-1");

        assertEquals(0, lookupService.search(null, null, null).size());
        assertEquals(0, lookupService.search("   ", null, null).size());
        assertEquals(0, lookupService.search("---", null, null).size(), "归一化为空时不能退化成 LIKE '%'");
    }

    @Test
    void searchMatchesProductNameAndEscapesWildcards() {
        SaveProductRequest req = productRequest(siemens, "PSU-1");
        req.setProductName("SITOP Power Supply");
        long sitop = productService.create(req).getId();
        SaveProductRequest other = productRequest(siemens, "PSU-2");
        other.setProductName("50% duty");
        productService.create(other);

        assertEquals(List.of(sitop), lookupService.search("sitop", null, null).stream().map(ProductOptionVO::getId).toList());
        assertEquals(1, lookupService.search("50%", null, null).size());
        // % 与 _ 按字面匹配：只命中名称里真的含这个字符的商品，而不是当通配符匹配全部
        assertEquals(1, lookupService.search("%", null, null).size());
        assertEquals(0, lookupService.search("_", null, null).size());
    }

    @Test
    void searchCanBeLimitedToOneBrandForSimilarModelHints() {
        product(siemens, "6ES7212-1AE40-0XB0");
        product(siemens, "6ES7222-1HH32-0XB0");
        product(abb, "6ES7-X");

        List<ProductOptionVO> inSiemens = lookupService.search("6ES7", null, siemens);
        List<ProductOptionVO> everywhere = lookupService.search("6ES7", null, null);

        assertEquals(2, inSiemens.size());
        assertTrue(inSiemens.stream().allMatch(p -> "Siemens".equals(p.getBrandName())));
        assertEquals(3, everywhere.size());
    }

    @Test
    void matchReturnsExactAndCandidates() {
        long exact = product(siemens, "6ES7214-1BD23-0XB0");
        product(abb, "6ES7214-1BD23-0XB0");
        product(abb, "6ES7214-1BD23-0XB0-EXT");

        ProductMatchVO result = lookupService.match(" siemens ", "6ES7 214-1BD23-0XB0");

        assertEquals(exact, result.getExact().getId());
        assertEquals(2, result.getCandidates().size());
        assertEquals("ABB", result.getCandidates().get(0).getBrandName());
        assertFalse(result.getCandidates().stream().anyMatch(c -> c.getId() == exact));
    }

    @Test
    void matchWithOtherBrandOnlyListsCandidate() {
        product(abb, "ABC-100");

        ProductMatchVO result = lookupService.match("Siemens", "ABC-100");

        assertNull(result.getExact());
        assertEquals(1, result.getCandidates().size());
    }

    @Test
    void matchWithUnknownBrandStillReturnsCandidates() {
        product(abb, "ABC-100");

        ProductMatchVO result = lookupService.match("NoSuchBrand", "abc100");

        assertNull(result.getExact());
        assertEquals(1, result.getCandidates().size());
    }

    @Test
    void matchWithNothingOrDisabledProductIsEmpty() {
        long only = product(siemens, "ABC-100");
        productService.updateStatus(only, 0);

        ProductMatchVO disabled = lookupService.match("Siemens", "ABC-100");
        assertNull(disabled.getExact());
        assertEquals(0, disabled.getCandidates().size());
        assertEquals(0, lookupService.match("Siemens", "ZZZ-999").getCandidates().size());
        assertEquals(0, lookupService.match("Siemens", "---").getCandidates().size());
    }

    @Test
    void matchCandidatesAreCappedAtTen() {
        for (int i = 0; i < 15; i++) {
            product(abb, String.format("CAP-%02d", i));
        }

        assertEquals(10, lookupService.match("Siemens", "CAP").getCandidates().size());
    }

    // ---------- 列表与详情 ----------

    @Test
    void pageFiltersAndIncludeDeletedIsPlatformOnly() {
        long a = product(siemens, "A-1");
        product(abb, "B-1");
        long gone = product(siemens, "A-2");
        productService.delete(gone);

        ProductQuery byBrand = new ProductQuery();
        byBrand.setBrandId(siemens);
        assertEquals(List.of(a), productService.page(byBrand).getRecords().stream().map(ProductVO::getId).toList());

        ProductQuery byKeyword = new ProductQuery();
        byKeyword.setKeyword("a 1");
        assertEquals(1, productService.page(byKeyword).getTotal());

        ProductQuery withDeleted = new ProductQuery();
        withDeleted.setIncludeDeleted(true);
        assertEquals(3, productService.page(withDeleted).getTotal());
        assertTrue(productService.page(withDeleted).getRecords().stream().anyMatch(p -> Boolean.TRUE.equals(p.getDeleted())));

        TenantContext.setTenantId(1001);
        assertEquals(2, productService.page(withDeleted).getTotal(), "租户账号传 includeDeleted 不应看到已删除商品");
    }

    // ---------- 平台共享与权限 ----------

    @Test
    void differentTenantsSeeTheSameProduct() {
        long id = product(siemens, "6ES7214");

        TenantContext.setTenantId(1001);
        ProductVO seenBy1001 = productService.getById(id);
        TenantContext.setTenantId(1002);
        ProductVO seenBy1002 = productService.getById(id);

        assertEquals(seenBy1001.getMpnDisplay(), seenBy1002.getMpnDisplay());
        assertEquals(seenBy1001.getId(), seenBy1002.getId());
        assertEquals(1, lookupService.search("6ES7", null, null).size());
    }

    @Test
    void tenantAccountWritesAreRejectedEvenWithPermission() {
        long id = product(siemens, "6ES7214");
        loginWithResources("it_product_user", RES_PRODUCT_ADD);
        TenantContext.setTenantId(1001);

        biz(() -> productController.create(productRequest(siemens, "NEW-1")), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        biz(() -> specificationService.replace(id, specs(spec("a", "1"))), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        biz(() -> relationshipService.create(id, relationship("X-1", 4)), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertEquals(1, jdbc.queryForObject("select count(*) from product", Integer.class));
    }

    @Test
    void platformAccountWithoutPermissionIsDenied() {
        loginWithResources("it_product_user");
        TenantContext.setTenantId(0);

        assertThrows(AccessDeniedException.class, () -> productController.create(productRequest(siemens, "NEW-1")));
        assertEquals(0, jdbc.queryForObject("select count(*) from product", Integer.class));
    }

    @Test
    void adminPlatformAccountCanCreate() {
        loginAsAdmin("it_admin_user");
        TenantContext.setTenantId(0);

        Result<ProductVO> result = productController.create(productRequest(siemens, "NEW-1"));

        assertEquals(0, result.getCode());
        assertNotNull(result.getData().getId());
    }

    @Test
    void readEndpointsOnlyNeedLogin() {
        product(siemens, "6ES7214");
        loginWithResources("it_product_user");
        TenantContext.setTenantId(1001);

        Result<PageResult<ProductVO>> page = productController.page(new ProductQuery());
        Result<List<ProductOptionVO>> search = productController.search("6ES7", null, null);
        Result<ProductMatchVO> match = productController.match("Siemens", "6ES7214");

        assertEquals(1, page.getData().getRecords().size());
        assertEquals(1, search.getData().size());
        assertNotNull(match.getData().getExact());
    }
}

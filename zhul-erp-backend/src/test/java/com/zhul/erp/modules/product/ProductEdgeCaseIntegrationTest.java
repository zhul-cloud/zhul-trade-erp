package com.zhul.erp.modules.product;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.ApplicationVO;
import com.zhul.erp.modules.product.dto.CreateSeriesRequest;
import com.zhul.erp.modules.product.dto.DocumentVO;
import com.zhul.erp.modules.product.dto.FaqVO;
import com.zhul.erp.modules.product.dto.MediaVO;
import com.zhul.erp.modules.product.dto.ProductQuery;
import com.zhul.erp.modules.product.dto.ProductVO;
import com.zhul.erp.modules.product.dto.RegisterMediaRequest;
import com.zhul.erp.modules.product.dto.RelationshipVO;
import com.zhul.erp.modules.product.dto.SaveApplicationRequest;
import com.zhul.erp.modules.product.dto.SaveBrandRequest;
import com.zhul.erp.modules.product.dto.SaveCategoryRequest;
import com.zhul.erp.modules.product.dto.SaveDocumentRequest;
import com.zhul.erp.modules.product.dto.SaveFaqRequest;
import com.zhul.erp.modules.product.dto.SaveProductRequest;
import com.zhul.erp.modules.product.dto.SaveRelationshipRequest;
import com.zhul.erp.modules.product.dto.SaveSpecificationsRequest;
import com.zhul.erp.modules.product.dto.UpdateMediaRequest;
import com.zhul.erp.modules.product.service.BrandService;
import com.zhul.erp.modules.product.service.CategoryService;
import com.zhul.erp.modules.product.service.ProductApplicationService;
import com.zhul.erp.modules.product.service.ProductDocumentService;
import com.zhul.erp.modules.product.service.ProductFaqService;
import com.zhul.erp.modules.product.service.ProductMediaService;
import com.zhul.erp.modules.product.service.ProductRelationshipService;
import com.zhul.erp.modules.product.service.ProductService;
import com.zhul.erp.modules.product.service.ProductSpecificationService;
import com.zhul.erp.modules.product.service.SeriesService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 输入校验与边界：非法取值、缺省值、越权 ID、空入参（补足主流程测试没覆盖到的分支）。 */
class ProductEdgeCaseIntegrationTest extends IntegrationTestBase {

    @Autowired
    private BrandService brandService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private SeriesService seriesService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductSpecificationService specificationService;
    @Autowired
    private ProductRelationshipService relationshipService;
    @Autowired
    private ProductDocumentService documentService;
    @Autowired
    private ProductApplicationService applicationService;
    @Autowired
    private ProductFaqService faqService;
    @Autowired
    private ProductMediaService mediaService;

    private long siemens;
    private long abb;
    private long controllers;
    private long product;

    @BeforeEach
    void setUp() {
        for (String table : List.of("product_reference_price", "product_customs", "product_logistics", "product_media",
                "product_faq", "product_application", "product_document", "product_relationship",
                "product_specification", "product", "product_series", "product_category", "product_brand")) {
            jdbc.update("delete from " + table);
        }
        TenantContext.setTenantId(0);
        siemens = brand("Siemens");
        abb = brand("ABB");
        controllers = category("controllers");
        product = productService.create(request(siemens, controllers, "6ES7214")).getId();
    }

    private long brand(String name) {
        SaveBrandRequest req = new SaveBrandRequest();
        req.setBrandName(name);
        return brandService.create(req).getId();
    }

    private long category(String code) {
        SaveCategoryRequest req = new SaveCategoryRequest();
        req.setCategoryCode(code);
        req.setCategoryName(code);
        return categoryService.create(req).getId();
    }

    private static SaveProductRequest request(Long brandId, Long categoryId, String mpn) {
        SaveProductRequest req = new SaveProductRequest();
        req.setBrandId(brandId);
        req.setCategoryId(categoryId);
        req.setMpnRaw(mpn);
        return req;
    }

    private static BizException biz(org.junit.jupiter.api.function.Executable e, String errorCode) {
        BizException ex = assertThrows(BizException.class, e);
        assertEquals(errorCode, ex.getErrorCode(), ex.getMessage());
        return ex;
    }

    // ---------- 商品 ----------

    @Test
    void pageFiltersByCategorySeriesLifecycleAndStatus() {
        long drives = category("drives");
        CreateSeriesRequest seriesReq = new CreateSeriesRequest();
        seriesReq.setBrandId(siemens);
        seriesReq.setSeriesName("S7-1200");
        long series = seriesService.create(seriesReq).getId();
        SaveProductRequest inSeries = request(siemens, drives, "DRV-1");
        inSeries.setSeriesId(series);
        inSeries.setLifecycleStatus(3);
        long drv = productService.create(inSeries).getId();
        productService.updateStatus(drv, 0);

        ProductQuery byCategory = new ProductQuery();
        byCategory.setCategoryId(drives);
        assertEquals(1, productService.page(byCategory).getTotal());
        ProductQuery bySeries = new ProductQuery();
        bySeries.setSeriesId(series);
        assertEquals(1, productService.page(bySeries).getTotal());
        ProductQuery byLifecycle = new ProductQuery();
        byLifecycle.setLifecycleStatus(3);
        assertEquals(1, productService.page(byLifecycle).getTotal());
        ProductQuery disabled = new ProductQuery();
        disabled.setStatus(0);
        assertEquals(drv, productService.page(disabled).getRecords().get(0).getId());
        ProductQuery enabled = new ProductQuery();
        enabled.setStatus(1);
        assertEquals(1, productService.page(enabled).getTotal());
    }

    @Test
    void symbolOnlyKeywordFallsBackToTextMatchOnly() {
        ProductQuery q = new ProductQuery();
        q.setKeyword("---");

        assertEquals(0, productService.page(q).getTotal(), "只有符号的关键词不能匹配全部商品");
    }

    @Test
    void unreferencedProductCanChangeModelAndBrand() {
        ProductVO changed = productService.update(product, request(abb, controllers, "NEW-100"));

        assertEquals("ABB", changed.getBrandName());
        assertEquals("new100", changed.getMpnNormalized());
        assertEquals("NEW-100", changed.getMpnDisplay());
    }

    @Test
    void unreferencedProductCannotChangeModelToOneThatAlreadyExists() {
        productService.create(request(siemens, controllers, "OTHER-1"));

        biz(() -> productService.update(product, request(siemens, controllers, "other 1")), ProductErrorCodes.PRODUCT_DUPLICATE);
    }

    @Test
    void disabledCategoryOrUnknownSeriesCannotBeUsedForNewProducts() {
        long spares = category("spares");
        categoryService.updateStatus(spares, 0);
        biz(() -> productService.create(request(siemens, spares, "X-1")), ProductErrorCodes.PARAM_INVALID);

        SaveProductRequest badSeries = request(siemens, controllers, "X-2");
        badSeries.setSeriesId(987654L);
        biz(() -> productService.create(badSeries), ProductErrorCodes.SERIES_NOT_FOUND);

        biz(() -> productService.create(request(null, controllers, "X-3")), ProductErrorCodes.BRAND_NOT_FOUND);
        biz(() -> productService.create(request(siemens, null, "X-4")), ProductErrorCodes.CATEGORY_NOT_FOUND);
    }

    @Test
    void existingProductKeepsWorkingWhenItsBrandLaterGetsDisabled() {
        brandService.updateStatus(siemens, 0);
        SaveProductRequest rename = request(siemens, controllers, "6ES7214");
        rename.setProductName("Renamed");

        assertEquals("Renamed", productService.update(product, rename).getProductName());
    }

    // ---------- 规格 ----------

    private static SaveSpecificationsRequest.SpecificationItem spec(String key) {
        SaveSpecificationsRequest.SpecificationItem item = new SaveSpecificationsRequest.SpecificationItem();
        item.setSpecKey(key);
        item.setSpecLabel(key);
        item.setSpecValue("v");
        return item;
    }

    @Test
    void specificationInputRules() {
        // 空请求等同于清空
        assertEquals(0, specificationService.replace(product, null).size());

        SaveSpecificationsRequest tooMany = new SaveSpecificationsRequest();
        tooMany.setItems(IntStream.range(0, 201).mapToObj(i -> spec("k" + i)).toList());
        biz(() -> specificationService.replace(product, tooMany), ProductErrorCodes.PARAM_INVALID);

        for (String bad : new String[]{"Rated Voltage", "1abc", "_x", "a-b", "A"}) {
            SaveSpecificationsRequest req = new SaveSpecificationsRequest();
            req.setItems(List.of(spec(bad)));
            biz(() -> specificationService.replace(product, req), ProductErrorCodes.PARAM_INVALID);
        }
        SaveSpecificationsRequest badVerified = new SaveSpecificationsRequest();
        var item = spec("ok");
        item.setVerified(2);
        badVerified.setItems(List.of(item));
        biz(() -> specificationService.replace(product, badVerified), ProductErrorCodes.PARAM_INVALID);
    }

    @Test
    void specificationSortOrderDefaultsToPositionAndCanBeOverridden() {
        SaveSpecificationsRequest req = new SaveSpecificationsRequest();
        var first = spec("a");
        var second = spec("b");
        second.setSortOrder(-1);
        req.setItems(List.of(first, second));

        var saved = specificationService.replace(product, req);

        assertEquals(List.of("b", "a"), saved.stream().map(s -> s.getSpecKey()).toList());
        specificationService.replace(product, new SaveSpecificationsRequest());
    }

    // ---------- 型号关系 ----------

    private static SaveRelationshipRequest relationship(String mpn, Integer type) {
        SaveRelationshipRequest req = new SaveRelationshipRequest();
        req.setRelatedMpn(mpn);
        req.setRelationshipType(type);
        return req;
    }

    @Test
    void relationshipInputRules() {
        biz(() -> relationshipService.create(product, relationship("X-1", null)), ProductErrorCodes.RELATIONSHIP_INVALID);
        biz(() -> relationshipService.create(product, relationship("X-1", 7)), ProductErrorCodes.RELATIONSHIP_INVALID);
        SaveRelationshipRequest badConfidence = relationship("X-1", 4);
        badConfidence.setConfidence(9);
        biz(() -> relationshipService.create(product, badConfidence), ProductErrorCodes.RELATIONSHIP_INVALID);
        biz(() -> relationshipService.create(product, relationship("---", 4)), ProductErrorCodes.RELATIONSHIP_INVALID);
        biz(() -> relationshipService.create(product, relationship(null, 4)), ProductErrorCodes.RELATIONSHIP_INVALID);
        SaveRelationshipRequest unknownProduct = relationship("X-1", 4);
        unknownProduct.setRelatedProductId(987654L);
        biz(() -> relationshipService.create(product, unknownProduct), ProductErrorCodes.RELATIONSHIP_INVALID);
        assertEquals(0, relationshipService.list(product).size());
    }

    @Test
    void explicitRelatedProductFillsInModelAndCannotBeSelf() {
        long other = productService.create(request(abb, controllers, "B-100")).getId();
        SaveRelationshipRequest byId = relationship(null, 4);
        byId.setRelatedProductId(other);
        byId.setSortOrder(7);

        RelationshipVO vo = relationshipService.create(product, byId);

        assertEquals("B-100", vo.getRelatedMpn());
        assertEquals(other, vo.getRelatedProductId());
        assertEquals(7, vo.getSortOrder());
        SaveRelationshipRequest self = relationship("anything", 4);
        self.setRelatedProductId(product);
        biz(() -> relationshipService.create(product, self), ProductErrorCodes.RELATIONSHIP_INVALID);
    }

    @Test
    void relationshipUpdateKeepsSortOrderWhenOmittedAndRejectsUnknownIds() {
        SaveRelationshipRequest create = relationship("X-1", 4);
        create.setSortOrder(3);
        RelationshipVO rel = relationshipService.create(product, create);

        RelationshipVO updated = relationshipService.update(product, rel.getId(), relationship("X-1", 5));

        assertEquals(3, updated.getSortOrder());
        SaveRelationshipRequest reorder = relationship("X-1", 5);
        reorder.setSortOrder(8);
        assertEquals(8, relationshipService.update(product, rel.getId(), reorder).getSortOrder());
        biz(() -> relationshipService.update(product, null, relationship("X-1", 5)), ProductErrorCodes.CONTENT_NOT_FOUND);
        biz(() -> relationshipService.delete(product, 987654L), ProductErrorCodes.CONTENT_NOT_FOUND);
    }

    // ---------- 技术资料 ----------

    private static SaveDocumentRequest document(String url) {
        SaveDocumentRequest req = new SaveDocumentRequest();
        req.setDocumentType(2);
        req.setTitle("Manual");
        req.setFileUrl(url);
        return req;
    }

    @Test
    void documentCanBeCreatedAlreadyVerifiedWithSortOrderAndLanguage() {
        SaveDocumentRequest req = document("https://example.com/m.pdf");
        req.setVerified(1);
        req.setSortOrder(4);
        req.setLanguage("zh");

        DocumentVO vo = documentService.create(product, req);

        assertEquals(1, vo.getVerified());
        assertTrue(vo.getVerifiedAt() != null);
        assertEquals(4, vo.getSortOrder());
        assertEquals("zh", vo.getLanguage());
    }

    @Test
    void documentInputRules() {
        SaveDocumentRequest badVerified = document("https://example.com/m.pdf");
        badVerified.setVerified(3);
        biz(() -> documentService.create(product, badVerified), ProductErrorCodes.PARAM_INVALID);
        biz(() -> documentService.update(product, null, document("https://example.com/m.pdf")), ProductErrorCodes.CONTENT_NOT_FOUND);
        SaveDocumentRequest keepSort = document("https://example.com/n.pdf");
        keepSort.setSortOrder(9);
        DocumentVO created = documentService.create(product, keepSort);
        assertEquals(9, documentService.update(product, created.getId(), document("https://example.com/n.pdf")).getSortOrder());
    }

    // ---------- 应用场景 ----------

    private static SaveApplicationRequest application(String title) {
        SaveApplicationRequest req = new SaveApplicationRequest();
        req.setTitle(title);
        return req;
    }

    @Test
    void applicationDeleteSortOrderAndUnknownIds() {
        SaveApplicationRequest req = application("HVAC");
        req.setSortOrder(2);
        ApplicationVO vo = applicationService.create(product, req);
        assertEquals(2, vo.getSortOrder());
        assertEquals(2, applicationService.update(product, vo.getId(), application("HVAC 2")).getSortOrder());
        assertEquals(0, applicationService.update(product, vo.getId(), application("HVAC 2")).getVerified());

        SaveApplicationRequest badVerified = application("Other");
        badVerified.setVerified(5);
        biz(() -> applicationService.create(product, badVerified), ProductErrorCodes.PARAM_INVALID);

        applicationService.delete(product, vo.getId());
        assertEquals(0, applicationService.list(product).size());
        biz(() -> applicationService.delete(product, vo.getId()), ProductErrorCodes.CONTENT_NOT_FOUND);
        biz(() -> applicationService.update(product, null, application("X")), ProductErrorCodes.CONTENT_NOT_FOUND);
    }

    // ---------- FAQ ----------

    private static SaveFaqRequest faq(String q) {
        SaveFaqRequest req = new SaveFaqRequest();
        req.setQuestion(q);
        req.setAnswer("A");
        return req;
    }

    @Test
    void faqDeleteSortOrderAndUnknownIds() {
        SaveFaqRequest req = faq("Q1?");
        req.setSortOrder(5);
        FaqVO vo = faqService.create(product, req);
        assertEquals(5, vo.getSortOrder());
        assertEquals(5, faqService.update(product, vo.getId(), faq("Q1?")).getSortOrder());
        SaveFaqRequest reorder = faq("Q1?");
        reorder.setSortOrder(1);
        assertEquals(1, faqService.update(product, vo.getId(), reorder).getSortOrder());

        faqService.delete(product, vo.getId());
        assertEquals(0, faqService.list(product).size());
        biz(() -> faqService.delete(product, vo.getId()), ProductErrorCodes.CONTENT_NOT_FOUND);
        biz(() -> faqService.update(product, null, faq("Q?")), ProductErrorCodes.CONTENT_NOT_FOUND);
        biz(() -> faqService.approve(product, 987654L), ProductErrorCodes.CONTENT_NOT_FOUND);
        // 删除后同一个问题可以再次添加
        faqService.create(product, faq("Q1?"));
    }

    // ---------- 图片与视频 ----------

    @Test
    void registerLinkWithCoverMainFlagAndSortOrder() {
        RegisterMediaRequest req = new RegisterMediaRequest();
        req.setMediaType(1);
        req.setFileUrl("https://example.com/p.jpg");
        req.setCoverUrl("https://example.com/cover.jpg");
        req.setSetMain(true);
        req.setSortOrder(3);
        req.setSource("Manufacturer Website");

        MediaVO vo = mediaService.register(product, req);

        assertEquals(1, vo.getIsMain());
        assertEquals(3, vo.getSortOrder());
        assertEquals("Manufacturer Website", vo.getSource());
        assertEquals("https://example.com/cover.jpg", vo.getCoverUrl());
    }

    @Test
    void registerVideoAsMainIsRejectedAndInvalidTypeIsRejected() {
        RegisterMediaRequest video = new RegisterMediaRequest();
        video.setMediaType(2);
        video.setFileUrl("https://example.com/v.mp4");
        video.setSetMain(true);
        biz(() -> mediaService.register(product, video), ProductErrorCodes.MEDIA_NOT_IMAGE);
        assertEquals(0, mediaService.list(product).size(), "登记失败整体回滚");

        RegisterMediaRequest noType = new RegisterMediaRequest();
        noType.setFileUrl("https://example.com/v.mp4");
        biz(() -> mediaService.register(product, noType), ProductErrorCodes.PARAM_INVALID);
        noType.setMediaType(3);
        biz(() -> mediaService.register(product, noType), ProductErrorCodes.PARAM_INVALID);
    }

    @Test
    void mediaUpdateHandlesCoverTitleSourceAndOmittedFields() {
        RegisterMediaRequest req = new RegisterMediaRequest();
        req.setMediaType(2);
        req.setFileUrl("https://example.com/v.mp4");
        req.setTitle("Demo");
        req.setSource("Vendor");
        MediaVO video = mediaService.register(product, req);

        UpdateMediaRequest keep = new UpdateMediaRequest();
        MediaVO unchanged = mediaService.update(product, video.getId(), keep);
        assertEquals("Demo", unchanged.getTitle());
        assertEquals("Vendor", unchanged.getSource());
        assertEquals(video.getSortOrder(), unchanged.getSortOrder());

        UpdateMediaRequest change = new UpdateMediaRequest();
        change.setCoverUrl("https://example.com/c.jpg");
        change.setSource("Other");
        assertEquals("https://example.com/c.jpg", mediaService.update(product, video.getId(), change).getCoverUrl());

        UpdateMediaRequest clear = new UpdateMediaRequest();
        clear.setCoverUrl("");
        assertEquals("", mediaService.update(product, video.getId(), clear).getCoverUrl());

        UpdateMediaRequest bad = new UpdateMediaRequest();
        bad.setCoverUrl("javascript:1");
        biz(() -> mediaService.update(product, video.getId(), bad), ProductErrorCodes.MEDIA_URL_INVALID);
        biz(() -> mediaService.update(product, null, keep), ProductErrorCodes.CONTENT_NOT_FOUND);
    }
}

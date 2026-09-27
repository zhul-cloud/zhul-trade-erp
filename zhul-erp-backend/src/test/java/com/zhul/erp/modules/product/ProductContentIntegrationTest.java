package com.zhul.erp.modules.product;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.controller.ProductController;
import com.zhul.erp.modules.product.dto.ApplicationVO;
import com.zhul.erp.modules.product.dto.CompletenessSummaryVO;
import com.zhul.erp.modules.product.dto.CustomsVO;
import com.zhul.erp.modules.product.dto.DocumentVO;
import com.zhul.erp.modules.product.dto.FaqVO;
import com.zhul.erp.modules.product.dto.LogisticsVO;
import com.zhul.erp.modules.product.dto.MediaVO;
import com.zhul.erp.modules.product.dto.ProductQuery;
import com.zhul.erp.modules.product.dto.ProductVO;
import com.zhul.erp.modules.product.dto.ReferencePriceVO;
import com.zhul.erp.modules.product.dto.RegisterMediaRequest;
import com.zhul.erp.modules.product.dto.SaveApplicationRequest;
import com.zhul.erp.modules.product.dto.SaveBrandRequest;
import com.zhul.erp.modules.product.dto.SaveCategoryRequest;
import com.zhul.erp.modules.product.dto.SaveCustomsRequest;
import com.zhul.erp.modules.product.dto.SaveDocumentRequest;
import com.zhul.erp.modules.product.dto.SaveFaqRequest;
import com.zhul.erp.modules.product.dto.SaveLogisticsRequest;
import com.zhul.erp.modules.product.dto.SaveProductRequest;
import com.zhul.erp.modules.product.dto.SaveReferencePriceRequest;
import com.zhul.erp.modules.product.dto.SaveSpecificationsRequest;
import com.zhul.erp.modules.product.service.BrandService;
import com.zhul.erp.modules.product.service.CategoryService;
import com.zhul.erp.modules.product.service.ProductApplicationService;
import com.zhul.erp.modules.product.service.ProductCustomsService;
import com.zhul.erp.modules.product.service.ProductDocumentService;
import com.zhul.erp.modules.product.service.ProductFaqService;
import com.zhul.erp.modules.product.service.ProductLogisticsService;
import com.zhul.erp.modules.product.service.ProductMediaService;
import com.zhul.erp.modules.product.service.ProductReferencePriceService;
import com.zhul.erp.modules.product.service.ProductService;
import com.zhul.erp.modules.product.service.ProductSpecificationService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 技术资料、应用场景、FAQ、图片视频、物流、海关、参考价、完整度与缺项筛选（任务 5.11–5.21）。 */
class ProductContentIntegrationTest extends IntegrationTestBase {

    private static final byte[] PNG = pad(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 64);
    private static final byte[] MP4 = pad(new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'}, 64);
    private static final byte[] EXE = pad(new byte[]{'M', 'Z', (byte) 0x90, 0}, 64);
    private static final String UPLOAD_LIMIT_KEY = "zhul:erp:limit:product:upload:";

    @Autowired
    private BrandService brandService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductSpecificationService specificationService;
    @Autowired
    private ProductDocumentService documentService;
    @Autowired
    private ProductApplicationService applicationService;
    @Autowired
    private ProductFaqService faqService;
    @Autowired
    private ProductMediaService mediaService;
    @Autowired
    private ProductLogisticsService logisticsService;
    @Autowired
    private ProductCustomsService customsService;
    @Autowired
    private ProductReferencePriceService priceService;
    @Autowired
    private ProductController productController;
    @Autowired
    private StringRedisTemplate redis;

    private long productId;
    private long otherProductId;

    private static byte[] pad(byte[] header, int size) {
        return Arrays.copyOf(header, size);
    }

    @BeforeEach
    void setUp() {
        for (String table : List.of("product_reference_price", "product_customs", "product_logistics", "product_media",
                "product_faq", "product_application", "product_document", "product_relationship",
                "product_specification", "product", "product_series", "product_category", "product_brand")) {
            jdbc.update("delete from " + table);
        }
        redis.delete(redis.keys(UPLOAD_LIMIT_KEY + "*"));
        TenantContext.setTenantId(0);
        SaveBrandRequest brand = new SaveBrandRequest();
        brand.setBrandName("Siemens");
        long brandId = brandService.create(brand).getId();
        SaveCategoryRequest category = new SaveCategoryRequest();
        category.setCategoryCode("controllers");
        category.setCategoryName("Controllers");
        long categoryId = categoryService.create(category).getId();
        productId = newProduct(brandId, categoryId, "6ES7214");
        otherProductId = newProduct(brandId, categoryId, "6ES7215");
    }

    private long newProduct(long brandId, long categoryId, String mpn) {
        SaveProductRequest req = new SaveProductRequest();
        req.setBrandId(brandId);
        req.setCategoryId(categoryId);
        req.setMpnRaw(mpn);
        return productService.create(req).getId();
    }

    private static BizException biz(org.junit.jupiter.api.function.Executable e, String errorCode) {
        BizException ex = assertThrows(BizException.class, e);
        assertEquals(errorCode, ex.getErrorCode(), ex.getMessage());
        return ex;
    }

    // ================= 技术资料 =================

    private static SaveDocumentRequest document(String url) {
        SaveDocumentRequest req = new SaveDocumentRequest();
        req.setDocumentType(1);
        req.setTitle("S7-1200 Datasheet");
        req.setFileUrl(url);
        return req;
    }

    @Test
    void addDocumentDefaultsLanguageAndUnverified() {
        DocumentVO vo = documentService.create(productId, document("https://example.com/s7-1200.pdf"));

        assertEquals("en", vo.getLanguage());
        assertEquals(0, vo.getVerified());
        assertNull(vo.getVerifiedAt());
    }

    @Test
    void unsafeDocumentUrlsAreRejectedAndSitePathIsAccepted() {
        for (String bad : List.of("javascript:alert(1)", "data:text/html,x", "//evil.example/x.pdf", "/\\evil.example")) {
            biz(() -> documentService.create(productId, document(bad)), ProductErrorCodes.DOCUMENT_URL_INVALID);
        }
        DocumentVO local = documentService.create(productId, document("/datasheets/6ES7212-1AE40-0XB0.pdf"));
        assertEquals("/datasheets/6ES7212-1AE40-0XB0.pdf", local.getFileUrl());
        assertEquals(1, documentService.list(productId).size());
    }

    @Test
    void duplicateDocumentUrlOnSameProductIsRejectedButAllowedOnAnotherProduct() {
        documentService.create(productId, document("https://example.com/a.pdf"));

        biz(() -> documentService.create(productId, document("https://example.com/a.pdf")), ProductErrorCodes.CONTENT_DUPLICATE);
        documentService.create(otherProductId, document("https://example.com/a.pdf"));
    }

    @Test
    void documentsBelongToOneProductOnly() {
        documentService.create(productId, document("https://example.com/a.pdf"));

        assertEquals(0, documentService.list(otherProductId).size());
    }

    @Test
    void verifyingDocumentStampsTimeAndUnverifyingClearsIt() {
        DocumentVO created = documentService.create(productId, document("https://example.com/a.pdf"));
        SaveDocumentRequest verify = document("https://example.com/a.pdf");
        verify.setVerified(1);

        DocumentVO verified = documentService.update(productId, created.getId(), verify);
        assertEquals(1, verified.getVerified());
        assertNotNull(verified.getVerifiedAt());

        DocumentVO again = documentService.update(productId, created.getId(), verify);
        assertEquals(verified.getVerifiedAt(), again.getVerifiedAt(), "再次保存不应刷新核实时间");

        SaveDocumentRequest unverify = document("https://example.com/a.pdf");
        unverify.setVerified(0);
        assertNull(documentService.update(productId, created.getId(), unverify).getVerifiedAt());
    }

    @Test
    void documentUpdateAndDeleteOnlyWorkOnOwnRows() {
        DocumentVO created = documentService.create(productId, document("https://example.com/a.pdf"));

        biz(() -> documentService.update(otherProductId, created.getId(), document("https://example.com/b.pdf")),
                ProductErrorCodes.CONTENT_NOT_FOUND);
        biz(() -> documentService.delete(otherProductId, created.getId()), ProductErrorCodes.CONTENT_NOT_FOUND);
        documentService.delete(productId, created.getId());
        assertEquals(0, documentService.list(productId).size());
        // 删除后同一地址可以再次添加
        documentService.create(productId, document("https://example.com/a.pdf"));
    }

    @Test
    void invalidDocumentFieldsAreRejected() {
        SaveDocumentRequest badType = document("https://example.com/a.pdf");
        badType.setDocumentType(9);
        biz(() -> documentService.create(productId, badType), ProductErrorCodes.PARAM_INVALID);
        SaveDocumentRequest noTitle = document("https://example.com/a.pdf");
        noTitle.setTitle(" ");
        biz(() -> documentService.create(productId, noTitle), ProductErrorCodes.PARAM_INVALID);
    }

    // ================= 应用场景 =================

    private static SaveApplicationRequest application(String title) {
        SaveApplicationRequest req = new SaveApplicationRequest();
        req.setTitle(title);
        req.setIcon("💧");
        return req;
    }

    @Test
    void addApplicationDefaultsToUnverified() {
        ApplicationVO vo = applicationService.create(productId, application("Water & pump stations"));

        assertEquals(0, vo.getVerified());
        assertEquals("💧", vo.getIcon());
    }

    @Test
    void applicationTitleRulesAreEnforced() {
        biz(() -> applicationService.create(productId, application("  ")), ProductErrorCodes.PARAM_INVALID);
        biz(() -> applicationService.create(productId, application("t".repeat(65))), ProductErrorCodes.PARAM_INVALID);
        applicationService.create(productId, application("Building automation"));

        biz(() -> applicationService.create(productId, application("building automation ")), ProductErrorCodes.CONTENT_DUPLICATE);
        applicationService.create(otherProductId, application("Building automation"));
    }

    @Test
    void applicationCanBeUpdatedWithoutClashingWithItself() {
        ApplicationVO created = applicationService.create(productId, application("HVAC"));
        SaveApplicationRequest change = application("hvac");
        change.setDescription("Heating, ventilation and air conditioning");
        change.setVerified(1);

        ApplicationVO updated = applicationService.update(productId, created.getId(), change);

        assertEquals("hvac", updated.getTitle());
        assertEquals(1, updated.getVerified());
    }

    // ================= FAQ =================

    private static SaveFaqRequest faq(String question) {
        SaveFaqRequest req = new SaveFaqRequest();
        req.setQuestion(question);
        req.setAnswer("Yes.");
        return req;
    }

    /** 造一条"AI 辅助生成待审核"的 FAQ：本期没有 AI 写入接口，直接落库模拟 */
    private long insertPendingFaq(long product, String question) {
        jdbc.update("insert into product_faq (tenant_id, product_id, question, answer, source) values (0, ?, ?, 'AI answer', 3)",
                product, question);
        return jdbc.queryForObject("select max(id) from product_faq", Long.class);
    }

    @Test
    void manualFaqHasSourceTwoAndTenantsCanReadIt() {
        FaqVO vo = faqService.create(productId, faq("Compatible with TIA Portal?"));
        assertEquals(2, vo.getSource());

        TenantContext.setTenantId(1001);
        assertEquals(1, faqService.list(productId).size());
    }

    @Test
    void pendingFaqIsHiddenFromTenantsButVisibleToPlatform() {
        faqService.create(productId, faq("Manual one?"));
        insertPendingFaq(productId, "Pending one?");

        assertEquals(2, faqService.list(productId).size());
        TenantContext.setTenantId(1001);
        List<FaqVO> seenByTenant = faqService.list(productId);
        assertEquals(1, seenByTenant.size());
        assertEquals("Manual one?", seenByTenant.get(0).getQuestion());
        assertTrue(seenByTenant.stream().noneMatch(f -> f.getSource() == 3));
    }

    @Test
    void pendingFaqIsAlsoHiddenOnTheControllerReadPath() {
        insertPendingFaq(productId, "Pending one?");
        loginWithResources("it_product_user");
        TenantContext.setTenantId(1001);

        assertEquals(0, productController.faqs(productId).getData().size());
    }

    @Test
    void approvingPendingFaqPublishesItAndRecordsReviewer() {
        long pending = insertPendingFaq(productId, "Pending one?");
        loginAsAdmin("reviewer_amy");

        FaqVO approved = faqService.approve(productId, pending);

        assertEquals(2, approved.getSource());
        assertEquals("reviewer_amy", approved.getReviewedBy());
        assertNotNull(approved.getReviewedAt());
        TenantContext.setTenantId(1001);
        assertEquals(1, faqService.list(productId).size());
    }

    @Test
    void alreadyConfirmedFaqCannotBeApprovedAgain() {
        FaqVO manual = faqService.create(productId, faq("Manual one?"));

        biz(() -> faqService.approve(productId, manual.getId()), ProductErrorCodes.FAQ_NOT_PENDING);
    }

    @Test
    void duplicateQuestionIgnoringCaseAndSpacesIsRejectedEvenAgainstPendingOnes() {
        faqService.create(productId, faq("Is this compatible with TIA Portal?"));
        biz(() -> faqService.create(productId, faq("is this compatible with tia portal? ")), ProductErrorCodes.CONTENT_DUPLICATE);

        insertPendingFaq(productId, "Does it support Modbus?");
        biz(() -> faqService.create(productId, faq("does it support modbus?")), ProductErrorCodes.CONTENT_DUPLICATE);
    }

    @Test
    void editingPendingFaqKeepsItPending() {
        long pending = insertPendingFaq(productId, "Pending one?");
        SaveFaqRequest change = faq("Pending one?");
        change.setAnswer("Corrected answer");

        FaqVO updated = faqService.update(productId, pending, change);

        assertEquals("Corrected answer", updated.getAnswer());
        assertEquals(3, updated.getSource());
    }

    @Test
    void faqFieldLimitsAreEnforced() {
        biz(() -> faqService.create(productId, faq(" ")), ProductErrorCodes.PARAM_INVALID);
        biz(() -> faqService.create(productId, faq("q".repeat(257))), ProductErrorCodes.PARAM_INVALID);
        SaveFaqRequest noAnswer = faq("Q?");
        noAnswer.setAnswer("  ");
        biz(() -> faqService.create(productId, noAnswer), ProductErrorCodes.PARAM_INVALID);
        faqService.create(productId, faq("q".repeat(256)));
    }

    // ================= 图片与视频 =================

    private MediaVO uploadPng(long product, boolean main) {
        return mediaService.upload(product, new MockMultipartFile("file", "photo.png", "image/png", PNG), 1, "front", main);
    }

    private Path storedFile(MediaVO media) {
        return Path.of("./target/test-uploads").toAbsolutePath().normalize()
                .resolve(media.getFileUrl().substring("/uploads/".length()));
    }

    @Test
    void uploadedImageIsSavedOnDiskAndRecordedAsPlatformUpload() {
        MediaVO vo = uploadPng(productId, false);

        assertEquals(1, vo.getMediaType());
        assertEquals(1, vo.getStorageType());
        assertEquals(64L, vo.getFileSize());
        assertEquals(0, vo.getIsMain());
        assertTrue(vo.getFileUrl().startsWith("/uploads/product/"));
        assertTrue(Files.exists(storedFile(vo)));
        assertFalse(vo.getFileUrl().contains("photo"));
    }

    @Test
    void rejectedUploadsSaveNothingAndLeaveMediaUnchanged() throws IOException {
        long before = countFiles();

        biz(() -> mediaService.upload(productId, new MockMultipartFile("file", "a.png", "image/png", EXE), 1, null, null),
                ProductErrorCodes.MEDIA_FILE_INVALID);
        biz(() -> mediaService.upload(productId, new MockMultipartFile("file", "a.svg", "image/svg+xml",
                "<svg/>".getBytes()), 1, null, null), ProductErrorCodes.MEDIA_FILE_INVALID);
        biz(() -> mediaService.upload(productId, new MockMultipartFile("file", "a.png", "image/png",
                pad(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 6 * 1024 * 1024)), 1, null, null),
                ProductErrorCodes.MEDIA_TOO_LARGE);

        assertEquals(0, mediaService.list(productId).size());
        assertEquals(before, countFiles());
    }

    private long countFiles() throws IOException {
        Path dir = Path.of("./target/test-uploads/product").toAbsolutePath().normalize();
        if (!Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void videoCanBeUploadedButNotSetAsMain() {
        MediaVO video = mediaService.upload(productId, new MockMultipartFile("file", "demo.mp4", "video/mp4", MP4), 2, null, null);

        assertEquals(2, video.getMediaType());
        biz(() -> mediaService.setMain(productId, video.getId()), ProductErrorCodes.MEDIA_NOT_IMAGE);
        biz(() -> mediaService.upload(productId, new MockMultipartFile("file", "demo.mp4", "video/mp4", MP4), 2, null, true),
                ProductErrorCodes.MEDIA_NOT_IMAGE);
    }

    @Test
    void externalLinkIsRecordedWithoutDownloading() {
        RegisterMediaRequest req = new RegisterMediaRequest();
        req.setMediaType(1);
        req.setFileUrl("https://example.com/p.jpg");
        req.setTitle("Front view");

        MediaVO vo = mediaService.register(productId, req);

        assertEquals(2, vo.getStorageType());
        assertEquals(0L, vo.getFileSize());
        assertEquals("Front view", vo.getTitle());
    }

    @Test
    void unsafeMediaAndCoverUrlsAreRejected() {
        for (String bad : List.of("javascript:alert(1)", "//evil.example/a.jpg")) {
            RegisterMediaRequest req = new RegisterMediaRequest();
            req.setMediaType(1);
            req.setFileUrl(bad);
            biz(() -> mediaService.register(productId, req), ProductErrorCodes.MEDIA_URL_INVALID);
        }
        RegisterMediaRequest badCover = new RegisterMediaRequest();
        badCover.setMediaType(2);
        badCover.setFileUrl("https://example.com/v.mp4");
        badCover.setCoverUrl("javascript:1");
        biz(() -> mediaService.register(productId, badCover), ProductErrorCodes.MEDIA_URL_INVALID);
        assertEquals(0, mediaService.list(productId).size());
    }

    @Test
    void settingAnotherImageAsMainReplacesTheOldMain() {
        MediaVO a = uploadPng(productId, true);
        MediaVO b = uploadPng(productId, false);
        assertEquals(1, mainCount(productId));

        mediaService.setMain(productId, b.getId());

        assertEquals(1, mainCount(productId));
        List<MediaVO> list = mediaService.list(productId);
        assertEquals(b.getId(), list.get(0).getId(), "主图排最前");
        assertEquals(1, list.get(0).getIsMain());
        assertEquals(0, list.stream().filter(m -> m.getId().equals(a.getId())).findFirst().orElseThrow().getIsMain());
    }

    private int mainCount(long product) {
        return jdbc.queryForObject("select count(*) from product_media where product_id = ? and is_main = 1 and deleted_at is null",
                Integer.class, product);
    }

    @Test
    void concurrentSetMainLeavesExactlyOneMainImage() throws Exception {
        List<Long> images = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            images.add(uploadPng(productId, false).getId());
        }
        ExecutorService pool = Executors.newFixedThreadPool(images.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (Long imageId : images) {
            futures.add(pool.submit(() -> {
                TenantContext.setTenantId(0);
                try {
                    go.await();
                    mediaService.setMain(productId, imageId);
                } finally {
                    TenantContext.clear();
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(1, mainCount(productId));
    }

    @Test
    void deletingTheMainImageClearsItsMainFlag() {
        MediaVO main = uploadPng(productId, true);

        mediaService.delete(productId, main.getId());

        assertEquals(0, mainCount(productId));
        assertEquals(0, mediaService.list(productId).size());
        assertTrue(Files.exists(storedFile(main)), "软删除不删除文件");
    }

    @Test
    void mediaCanBeRetitledAndReordered() {
        MediaVO vo = uploadPng(productId, false);
        var change = new com.zhul.erp.modules.product.dto.UpdateMediaRequest();
        change.setTitle("Side view");
        change.setSortOrder(5);

        MediaVO updated = mediaService.update(productId, vo.getId(), change);

        assertEquals("Side view", updated.getTitle());
        assertEquals(5, updated.getSortOrder());
        biz(() -> mediaService.update(otherProductId, vo.getId(), change), ProductErrorCodes.CONTENT_NOT_FOUND);
    }

    @Test
    void tenantUploadIsRejectedAndNothingIsSaved() throws IOException {
        long before = countFiles();
        TenantContext.setTenantId(1001);

        biz(() -> mediaService.upload(productId, new MockMultipartFile("file", "a.png", "image/png", PNG), 1, null, null),
                ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);

        assertEquals(before, countFiles());
        assertEquals(0, jdbc.queryForObject("select count(*) from product_media", Integer.class));
    }

    @Test
    void uploadEndpointIsRateLimitedPerAccount() {
        loginAsAdmin("upload_user");
        for (int i = 0; i < 30; i++) {
            mediaService.upload(productId, new MockMultipartFile("file", "a.png", "image/png", PNG), 1, null, null);
        }

        biz(() -> mediaService.upload(productId, new MockMultipartFile("file", "a.png", "image/png", PNG), 1, null, null),
                ProductErrorCodes.RATE_LIMITED);
        assertEquals(30, mediaService.list(productId).size());
    }

    // ================= 物流 =================

    @Test
    void logisticsIsOverwrittenAsAWholeAndCanBeCleared() {
        SaveLogisticsRequest first = new SaveLogisticsRequest();
        first.setNetWeightKg(new BigDecimal("0.31"));
        first.setGrossWeightKg(new BigDecimal("0.45"));
        first.setLengthMm(new BigDecimal("90"));
        first.setPackageType("盒装");
        LogisticsVO saved = logisticsService.save(productId, first);
        assertEquals(new BigDecimal("0.310"), saved.getNetWeightKg());
        assertNull(saved.getPackageLengthMm());

        SaveLogisticsRequest second = new SaveLogisticsRequest();
        second.setNetWeightKg(new BigDecimal("0.5"));
        LogisticsVO overwritten = logisticsService.save(productId, second);

        assertEquals(new BigDecimal("0.500"), overwritten.getNetWeightKg());
        assertNull(overwritten.getGrossWeightKg(), "整体覆盖：未提交的字段被清空为 NULL");
        assertNull(overwritten.getLengthMm());
        assertEquals("", overwritten.getPackageType());
        assertEquals(1, jdbc.queryForObject("select count(*) from product_logistics where product_id = ?", Integer.class, productId));
    }

    @Test
    void invalidLogisticsIsRejectedAndKeepsOldData() {
        SaveLogisticsRequest ok = new SaveLogisticsRequest();
        ok.setNetWeightKg(new BigDecimal("0.3"));
        logisticsService.save(productId, ok);
        SaveLogisticsRequest bad = new SaveLogisticsRequest();
        bad.setNetWeightKg(new BigDecimal("0.5"));
        bad.setGrossWeightKg(new BigDecimal("0.3"));

        biz(() -> logisticsService.save(productId, bad), ProductErrorCodes.LOGISTICS_INVALID);

        assertEquals(new BigDecimal("0.300"), logisticsService.get(productId).getNetWeightKg());
    }

    @Test
    void productWithoutLogisticsReadsAsEmptyObject() {
        LogisticsVO vo = logisticsService.get(productId);

        assertNull(vo.getId());
        assertNull(vo.getNetWeightKg());
    }

    // ================= 海关 =================

    @Test
    void customsIsSavedWithDigitsOnlyHsCodeAndOverwritten() {
        SaveCustomsRequest req = new SaveCustomsRequest();
        req.setHsCode("8537.10.90");
        req.setOriginCountry("DE");
        req.setCustomsNameCn("可编程控制器");
        req.setExportRebateRate(new BigDecimal("13"));

        CustomsVO saved = customsService.save(productId, req);

        assertEquals("85371090", saved.getHsCode());
        assertEquals("DE", saved.getOriginCountry());
        assertEquals(new BigDecimal("13.00"), saved.getExportRebateRate());

        CustomsVO overwritten = customsService.save(productId, new SaveCustomsRequest());
        assertEquals("", overwritten.getHsCode());
        assertNull(overwritten.getExportRebateRate());
    }

    @Test
    void invalidCustomsIsRejected() {
        SaveCustomsRequest badHs = new SaveCustomsRequest();
        badHs.setHsCode("12345");
        biz(() -> customsService.save(productId, badHs), ProductErrorCodes.HS_CODE_INVALID);
        SaveCustomsRequest badCountry = new SaveCustomsRequest();
        badCountry.setOriginCountry("Germany");
        biz(() -> customsService.save(productId, badCountry), ProductErrorCodes.COUNTRY_CODE_INVALID);
        SaveCustomsRequest badRate = new SaveCustomsRequest();
        badRate.setExportRebateRate(new BigDecimal("101"));
        biz(() -> customsService.save(productId, badRate), ProductErrorCodes.PARAM_INVALID);
    }

    // ================= 参考价 =================

    private static SaveReferencePriceRequest price(String amount, String currency, String rate) {
        SaveReferencePriceRequest req = new SaveReferencePriceRequest();
        req.setPriceOriginal(new BigDecimal(amount));
        req.setCurrencyCode(currency);
        req.setExchangeRate(rate == null ? null : new BigDecimal(rate));
        req.setPriceSource("eBay");
        req.setPriceDate(LocalDate.of(2026, 9, 1));
        return req;
    }

    @Test
    void referencePriceIsConvertedAndReadBackIdenticallyByEveryTenant() {
        ReferencePriceVO saved = priceService.save(productId, price("100.00", "USD", "7.123456"));

        assertEquals(new BigDecimal("712.35"), saved.getPriceCny());
        assertEquals(LocalDate.of(2026, 9, 1), saved.getPriceDate());
        TenantContext.setTenantId(1001);
        ReferencePriceVO seenByA = priceService.get(productId);
        TenantContext.setTenantId(1002);
        assertEquals(seenByA, priceService.get(productId));
    }

    @Test
    void referencePriceWithoutRateHasNoBaseAmount() {
        ReferencePriceVO saved = priceService.save(productId, price("100.00", "USD", null));

        assertNull(saved.getPriceCny());
        assertNull(saved.getExchangeRate());
        assertEquals(new BigDecimal("100.00"), saved.getPriceOriginal());
    }

    @Test
    void clearingThenSavingAgainReusesTheRowAndKeepsOnlyTheLastSave() {
        priceService.save(productId, price("100.00", "USD", "7"));
        priceService.clear(productId);
        assertNull(priceService.get(productId).getId());

        ReferencePriceVO again = priceService.save(productId, price("500.00", "CNY", null));

        assertEquals(new BigDecimal("500.00"), again.getPriceCny());
        assertEquals("CNY", again.getCurrencyCode());
        assertEquals(1, jdbc.queryForObject("select count(*) from product_reference_price where product_id = ?", Integer.class, productId));
    }

    @Test
    void invalidReferencePriceIsRejectedAndKeepsOldOne() {
        priceService.save(productId, price("100.00", "USD", "7"));

        biz(() -> priceService.save(productId, price("0", "USD", "7")), ProductErrorCodes.PRICE_INVALID);
        biz(() -> priceService.save(productId, price("-10", "USD", "7")), ProductErrorCodes.PRICE_INVALID);
        SaveReferencePriceRequest noCurrency = price("10", "USD", "7");
        noCurrency.setCurrencyCode(null);
        biz(() -> priceService.save(productId, noCurrency), ProductErrorCodes.CURRENCY_REQUIRED);

        assertEquals(new BigDecimal("100.00"), priceService.get(productId).getPriceOriginal());
    }

    // ================= 完整度与缺项筛选 =================

    private ProductVO listed(long id) {
        return productService.page(new ProductQuery()).getRecords().stream().filter(p -> p.getId() == id).findFirst().orElseThrow();
    }

    private void fillEverythingExceptMediaLogisticsCustoms(long id) {
        SaveSpecificationsRequest specs = new SaveSpecificationsRequest();
        var spec = new SaveSpecificationsRequest.SpecificationItem();
        spec.setSpecKey("voltage");
        spec.setSpecLabel("Voltage");
        spec.setSpecValue("24");
        specs.setItems(List.of(spec));
        specificationService.replace(id, specs);
        priceService.save(id, price("100.00", "USD", "7"));
        var rel = new com.zhul.erp.modules.product.dto.SaveRelationshipRequest();
        rel.setRelatedMpn("OLD-1");
        rel.setRelationshipType(4);
        productController.createRelationship(id, rel);
        documentService.create(id, document("https://example.com/" + id + ".pdf"));
        applicationService.create(id, application("HVAC"));
        faqService.create(id, faq("Q?"));
    }

    @Test
    void freshProductIsOneOfTenForPlatformAccounts() {
        var completeness = listed(productId).getCompleteness();

        assertEquals(1, completeness.getDone());
        assertEquals(10, completeness.getTotal());
        assertEquals(9, completeness.getMissing().size());
    }

    @Test
    void productWithoutMediaLogisticsCustomsIsSevenOfTen() {
        loginAsAdmin("admin_user");
        fillEverythingExceptMediaLogisticsCustoms(productId);

        var completeness = listed(productId).getCompleteness();

        assertEquals(7, completeness.getDone());
        assertEquals(List.of("media", "logistics", "customs"), completeness.getMissing());
    }

    @Test
    void completenessTracksEachModuleThroughRealData() {
        loginAsAdmin("admin_user");
        fillEverythingExceptMediaLogisticsCustoms(productId);

        // 有图片但没有主图：图片视频仍未完成
        MediaVO image = uploadPng(productId, false);
        assertTrue(listed(productId).getCompleteness().getMissing().contains("media"));
        mediaService.setMain(productId, image.getId());
        assertFalse(listed(productId).getCompleteness().getMissing().contains("media"));

        // 物流：净重或任一尺寸已填
        SaveLogisticsRequest logistics = new SaveLogisticsRequest();
        logistics.setPackageLengthMm(new BigDecimal("120"));
        logisticsService.save(productId, logistics);
        assertFalse(listed(productId).getCompleteness().getMissing().contains("logistics"));

        // 海关：HS 编码已填；只填了品名不算
        SaveCustomsRequest customs = new SaveCustomsRequest();
        customs.setCustomsNameEn("PLC");
        customsService.save(productId, customs);
        assertTrue(listed(productId).getCompleteness().getMissing().contains("customs"));
        customs.setHsCode("853710");
        customsService.save(productId, customs);

        var done = listed(productId).getCompleteness();
        assertEquals(10, done.getDone());
        assertTrue(done.getMissing().isEmpty());
        assertEquals(10, productService.getById(productId).getCompleteness().getDone());
    }

    @Test
    void onlyPendingFaqsDoNotCountForFaqModule() {
        insertPendingFaq(productId, "Pending one?");

        assertTrue(listed(productId).getCompleteness().getMissing().contains("faq"));

        loginAsAdmin("admin_user");
        faqService.approve(productId, jdbc.queryForObject("select max(id) from product_faq", Long.class));
        assertFalse(listed(productId).getCompleteness().getMissing().contains("faq"));
    }

    @Test
    void tenantAccountsNeverSeeCompleteness() {
        TenantContext.setTenantId(1001);

        assertNull(productService.page(new ProductQuery()).getRecords().get(0).getCompleteness());
        assertNull(productService.getById(productId).getCompleteness());
    }

    @Test
    void missingFilterListsOnlyMatchingProductsAndSummaryMatchesFilterCounts() {
        loginAsAdmin("admin_user");
        // productId 有主图、参考价、海关；otherProductId 什么都没有
        MediaVO image = uploadPng(productId, true);
        assertNotNull(image);
        priceService.save(productId, price("10", "CNY", null));
        SaveCustomsRequest customs = new SaveCustomsRequest();
        customs.setHsCode("853710");
        customsService.save(productId, customs);
        SaveLogisticsRequest logistics = new SaveLogisticsRequest();
        logistics.setNetWeightKg(new BigDecimal("1"));
        logisticsService.save(productId, logistics);

        CompletenessSummaryVO summary = productService.completenessSummary();

        assertEquals(2, summary.getTotal());
        assertEquals(1, summary.getMissingMedia());
        assertEquals(1, summary.getMissingLogistics());
        assertEquals(1, summary.getMissingCustoms());
        assertEquals(1, summary.getMissingPrice());
        for (String key : List.of("media", "logistics", "customs", "price")) {
            ProductQuery q = new ProductQuery();
            q.setMissing(key);
            var result = productService.page(q);
            assertEquals(1, result.getTotal(), key);
            assertEquals(otherProductId, result.getRecords().get(0).getId(), key);
        }
    }

    @Test
    void summaryAndFilterAgreeOnAMixedCatalog() {
        loginAsAdmin("admin_user");
        for (int i = 0; i < 6; i++) {
            long id = newProduct(jdbc.queryForObject("select id from product_brand limit 1", Long.class),
                    jdbc.queryForObject("select id from product_category limit 1", Long.class), "MIX-" + i);
            if (i % 2 == 0) {
                priceService.save(id, price("10", "CNY", null));
            }
            if (i % 3 == 0) {
                uploadPng(id, true);
            }
        }
        CompletenessSummaryVO summary = productService.completenessSummary();

        ProductQuery missingPrice = new ProductQuery();
        missingPrice.setMissing("price");
        ProductQuery missingMedia = new ProductQuery();
        missingMedia.setMissing("media");
        assertEquals(summary.getMissingPrice(), productService.page(missingPrice).getTotal());
        assertEquals(summary.getMissingMedia(), productService.page(missingMedia).getTotal());
    }

    @Test
    void tenantMissingFilterIsIgnoredAndInvalidValueIsRejectedForPlatform() {
        ProductQuery q = new ProductQuery();
        q.setMissing("media");
        TenantContext.setTenantId(1001);
        assertEquals(productService.page(new ProductQuery()).getTotal(), productService.page(q).getTotal());
        biz(() -> productService.completenessSummary(), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);

        TenantContext.setTenantId(0);
        q.setMissing("everything");
        biz(() -> productService.page(q), ProductErrorCodes.PARAM_INVALID);
    }

    // ================= 5.11：所有写接口的权限 =================

    @Test
    void everyWriteEndpointRejectsTenantAccountsEvenWithEditPermission() {
        long pending = insertPendingFaq(productId, "Pending one?");
        // 给足商品编辑权限（新增、编辑、删除），但账号所在租户不是平台
        loginWithResources("it_product_user", 110131, 110132, 110133);
        TenantContext.setTenantId(1001);
        long filesBefore;
        try {
            filesBefore = countFiles();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        List<org.junit.jupiter.api.function.Executable> writes = List.of(
                () -> productController.updateStatus(productId, statusRequest()),
                () -> productController.delete(productId),
                () -> productController.restore(productId),
                () -> productController.replaceSpecifications(productId, new SaveSpecificationsRequest()),
                () -> productController.createRelationship(productId, new com.zhul.erp.modules.product.dto.SaveRelationshipRequest()),
                () -> productController.createDocument(productId, document("https://example.com/a.pdf")),
                () -> productController.createApplication(productId, application("X")),
                () -> productController.createFaq(productId, faq("Q?")),
                () -> productController.approveFaq(productId, pending),
                () -> productController.registerMedia(productId, new RegisterMediaRequest()),
                () -> productController.uploadMedia(productId, new MockMultipartFile("file", "a.png", "image/png", PNG), 1, null, null),
                () -> productController.saveLogistics(productId, new SaveLogisticsRequest()),
                () -> productController.saveCustoms(productId, new SaveCustomsRequest()),
                () -> productController.saveReferencePrice(productId, price("10", "USD", "7")),
                () -> productController.clearReferencePrice(productId));
        for (int i = 0; i < writes.size(); i++) {
            BizException e = assertThrows(BizException.class, writes.get(i), "第 " + i + " 个写接口应被拒绝");
            assertEquals(ProductErrorCodes.PLATFORM_ADMIN_REQUIRED, e.getErrorCode(), "第 " + i);
        }

        try {
            assertEquals(filesBefore, countFiles(), "租户上传被拒绝时不应保存文件");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        assertEquals(3, jdbc.queryForObject("select source from product_faq where id = ?", Integer.class, pending));
    }

    private static com.zhul.erp.modules.product.dto.StatusRequest statusRequest() {
        var req = new com.zhul.erp.modules.product.dto.StatusRequest();
        req.setStatus(0);
        return req;
    }

    @Test
    void platformAccountWithoutPermissionIsDeniedOnEveryWriteEndpoint() {
        long pending = insertPendingFaq(productId, "Pending one?");
        loginWithResources("it_product_user");
        TenantContext.setTenantId(0);

        List<org.junit.jupiter.api.function.Executable> writes = List.of(
                () -> productController.approveFaq(productId, pending),
                () -> productController.uploadMedia(productId, new MockMultipartFile("file", "a.png", "image/png", PNG), 1, null, null),
                () -> productController.saveReferencePrice(productId, price("10", "USD", "7")),
                () -> productController.createDocument(productId, document("https://example.com/a.pdf")),
                () -> productController.delete(productId));
        for (var write : writes) {
            assertThrows(AccessDeniedException.class, write);
        }
        assertEquals(3, jdbc.queryForObject("select source from product_faq where id = ?", Integer.class, pending));
    }

    @Test
    void adminPlatformAccountCanUseWriteEndpoints() {
        loginAsAdmin("admin_user");
        TenantContext.setTenantId(0);

        Result<DocumentVO> doc = productController.createDocument(productId, document("https://example.com/a.pdf"));
        Result<ReferencePriceVO> priceResult = productController.saveReferencePrice(productId, price("10", "CNY", null));
        Result<MediaVO> media = productController.uploadMedia(productId,
                new MockMultipartFile("file", "a.png", "image/png", PNG), 1, "x", true);

        assertEquals(0, doc.getCode());
        assertEquals(0, priceResult.getCode());
        assertEquals(1, media.getData().getIsMain());
    }

    @Test
    void readEndpointsOnlyNeedLoginForAllContentTypes() {
        documentService.create(productId, document("https://example.com/a.pdf"));
        applicationService.create(productId, application("HVAC"));
        faqService.create(productId, faq("Q?"));
        uploadPng(productId, true);
        priceService.save(productId, price("10", "CNY", null));
        loginWithResources("it_product_user");
        TenantContext.setTenantId(1001);

        assertEquals(1, productController.documents(productId).getData().size());
        assertEquals(1, productController.applications(productId).getData().size());
        assertEquals(1, productController.faqs(productId).getData().size());
        assertEquals(1, productController.media(productId).getData().size());
        assertNotNull(productController.logistics(productId).getData());
        assertNotNull(productController.customs(productId).getData());
        assertEquals(new BigDecimal("10.00"), productController.referencePrice(productId).getData().getPriceCny());
    }
}

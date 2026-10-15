package com.zhul.erp.modules.product.content.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.modules.product.content.dto.ContentConfirmVO;
import com.zhul.erp.modules.product.content.dto.ContentFileRequest;
import com.zhul.erp.modules.product.content.dto.ContentImportRecordVO;
import com.zhul.erp.modules.product.content.dto.ContentPreviewVO;
import com.zhul.erp.modules.product.content.dto.ContentTaskQuery;
import com.zhul.erp.modules.product.content.dto.ContentTaskVO;
import com.zhul.erp.modules.product.content.dto.MarketingPlatformVO;
import com.zhul.erp.modules.product.content.dto.SavePlatformsRequest;
import com.zhul.erp.modules.product.content.dto.ProductContentVO;
import com.zhul.erp.modules.product.content.service.ContentQuickImport;
import com.zhul.erp.modules.product.content.service.ContentImportRecordService;
import com.zhul.erp.modules.product.content.service.ContentImportService;
import com.zhul.erp.modules.product.content.service.ContentPackageService;
import com.zhul.erp.modules.product.content.service.ContentTaskService;
import com.zhul.erp.modules.product.content.service.FactTaskService;
import com.zhul.erp.modules.product.content.service.MarketingSkipService;
import com.zhul.erp.modules.product.content.service.PlatformSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 内容任务按入口分两组（spec product/product-content「内容维护权限」）：
 * 资料任务（商品管理 → 资料任务）：查看、下载资料包、上传预览要菜单，确认资料包与资料已核还要「商品内容维护」；
 * 商品运营（运营管理 → 商品运营）：查看、下载营销包、上传预览、平台设置要菜单，确认营销包、改平台设置、不发布还要「营销内容维护」。
 * 商品内容与上传记录与商品详情一样对所有登录用户开放。
 */
@RestController
@RequestMapping("/api/v1/product/content-tasks")
@RequiredArgsConstructor
public class ContentTaskController {

    private static final String FACTS_MENU = "@perm.canAccessMenu('/product/fact-tasks')";
    private static final String FACTS_EDIT = FACTS_MENU + " and @perm.has('product:content:edit')";
    private static final String OPS_MENU = "@perm.canAccessMenu('/operations/products')";
    private static final String OPS_EDIT = OPS_MENU + " and @perm.has('operations:marketing:edit')";

    private final ContentTaskService taskService;
    private final ContentPackageService packageService;
    private final ContentImportService importService;
    private final FactTaskService factTaskService;
    private final PlatformSettingService platformSettings;
    private final MarketingSkipService skipService;
    private final ContentImportRecordService recordService;
    private final ContentQuickImport quickImport;

    // ---------------------------------------------------------------- 资料任务

    @PostMapping("/facts/page")
    @PreAuthorize(FACTS_MENU)
    public Result<PageResult<ContentTaskVO>> factPage(@RequestBody ContentTaskQuery query) {
        return Result.ok(taskService.page(query, ContentTaskService.View.FACTS));
    }

    @PostMapping("/facts/counts")
    @PreAuthorize(FACTS_MENU)
    public Result<Map<String, Long>> factCounts(@RequestBody ContentTaskQuery query) {
        return Result.ok(taskService.counts(query, ContentTaskService.View.FACTS));
    }

    @GetMapping("/facts/package")
    @PreAuthorize(FACTS_MENU)
    public ResponseEntity<byte[]> factPackage(@RequestParam List<Long> productIds) {
        return file(packageService.build(productIds, ContentConstants.TYPE_FACTS, null));
    }

    @PostMapping("/facts/preview")
    @PreAuthorize(FACTS_MENU)
    public Result<List<ContentPreviewVO>> factPreview(@Valid @RequestBody ContentFileRequest req) {
        return Result.ok(importService.preview(req, ContentConstants.TYPE_FACTS));
    }

    @PostMapping("/facts/confirm")
    @PreAuthorize(FACTS_EDIT)
    public Result<ContentConfirmVO> factConfirm(@Valid @RequestBody ContentFileRequest req) {
        return Result.ok(importService.confirm(req, ContentConstants.TYPE_FACTS));
    }

    /** 资料已核：写入共享商品库，与确认资料包一样需要「商品内容维护」权限，公司账号也可以操作 */
    @PostMapping("/products/{productId}/verify-facts")
    @PreAuthorize(FACTS_EDIT)
    public Result<Void> verifyFacts(@PathVariable Long productId) {
        factTaskService.verify(productId);
        return Result.ok();
    }

    // ---------------------------------------------------------------- 商品运营

    @PostMapping("/marketing/page")
    @PreAuthorize(OPS_MENU)
    public Result<PageResult<ContentTaskVO>> marketingPage(@RequestBody ContentTaskQuery query) {
        return Result.ok(taskService.page(query, ContentTaskService.View.MARKETING));
    }

    @PostMapping("/marketing/counts")
    @PreAuthorize(OPS_MENU)
    public Result<Map<String, Long>> marketingCounts(@RequestBody ContentTaskQuery query) {
        return Result.ok(taskService.counts(query, ContentTaskService.View.MARKETING));
    }

    @GetMapping("/marketing/package")
    @PreAuthorize(OPS_MENU)
    public ResponseEntity<byte[]> marketingPackage(@RequestParam List<Long> productIds,
                                                   @RequestParam(required = false) String platform) {
        return file(packageService.build(productIds, ContentConstants.TYPE_MARKETING, platform));
    }

    @PostMapping("/marketing/preview")
    @PreAuthorize(OPS_MENU)
    public Result<List<ContentPreviewVO>> marketingPreview(@Valid @RequestBody ContentFileRequest req) {
        return Result.ok(importService.preview(req, ContentConstants.TYPE_MARKETING));
    }

    @PostMapping("/marketing/confirm")
    @PreAuthorize(OPS_EDIT)
    public Result<ContentConfirmVO> marketingConfirm(@Valid @RequestBody ContentFileRequest req) {
        return Result.ok(importService.confirm(req, ContentConstants.TYPE_MARKETING));
    }

    // ---------------------------------------------------------------- 资料与营销一起上传

    @PostMapping("/combined/preview")
    @PreAuthorize(FACTS_MENU + " or " + OPS_MENU)
    public Result<List<ContentPreviewVO>> combinedPreview(@Valid @RequestBody ContentFileRequest req) {
        return Result.ok(quickImport.preview(req));
    }

    /** 写入资料与营销，并自动审核技术问答、核对资料：需要「商品内容维护」与「营销内容维护」两个权限 */
    @PostMapping("/combined/confirm")
    @PreAuthorize("(" + FACTS_EDIT + ") and (" + OPS_EDIT + ")")
    public Result<ContentConfirmVO> combinedConfirm(@Valid @RequestBody ContentFileRequest req) {
        return Result.ok(quickImport.confirm(req));
    }

    /** 本公司启用的营销平台 */
    @GetMapping("/platforms")
    @PreAuthorize(OPS_MENU)
    public Result<List<MarketingPlatformVO>> platforms() {
        return Result.ok(platformSettings.list());
    }

    @PutMapping("/platforms")
    @PreAuthorize(OPS_EDIT)
    public Result<List<MarketingPlatformVO>> savePlatforms(@Valid @RequestBody SavePlatformsRequest req) {
        return Result.ok(platformSettings.save(req.getPlatforms()));
    }

    /** 本公司某商品在某平台不发布 */
    @PutMapping("/products/{productId}/skips/{platform}")
    @PreAuthorize(OPS_EDIT)
    public Result<Void> skip(@PathVariable Long productId, @PathVariable String platform) {
        skipService.mark(productId, platform);
        return Result.ok();
    }

    @DeleteMapping("/products/{productId}/skips/{platform}")
    @PreAuthorize(OPS_EDIT)
    public Result<Void> unskip(@PathVariable Long productId, @PathVariable String platform) {
        skipService.unmark(productId, platform);
        return Result.ok();
    }

    // ---------------------------------------------------------------- 公共

    /** 商品的上传记录：本公司全部，加其他公司的资料包；type 为 facts 或 marketing 时只看该包类型 */
    @GetMapping("/products/{productId}/imports")
    public Result<List<ContentImportRecordVO>> imports(@PathVariable Long productId,
                                                       @RequestParam(required = false) String type) {
        return Result.ok(recordService.list(productId, type));
    }

    /** 上传记录详情（含原文） */
    @GetMapping("/imports/{id}")
    public Result<ContentImportRecordVO> importDetail(@PathVariable Long id) {
        return Result.ok(recordService.detail(id));
    }

    /** 商品的本公司运营内容与资料状态：与商品读取一样对所有登录用户开放 */
    @GetMapping("/products/{productId}")
    public Result<ProductContentVO> productContent(@PathVariable Long productId) {
        return Result.ok(taskService.productContent(productId));
    }

    private static ResponseEntity<byte[]> file(ContentPackageService.Package pkg) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pkg.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(pkg.contentType()))
                .body(pkg.body());
    }
}

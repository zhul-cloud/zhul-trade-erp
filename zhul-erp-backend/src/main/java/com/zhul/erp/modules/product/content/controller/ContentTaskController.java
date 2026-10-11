package com.zhul.erp.modules.product.content.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.product.content.dto.ContentConfirmVO;
import com.zhul.erp.modules.product.content.dto.ContentFileRequest;
import com.zhul.erp.modules.product.content.dto.ContentPreviewVO;
import com.zhul.erp.modules.product.content.dto.ContentTaskQuery;
import com.zhul.erp.modules.product.content.dto.ContentTaskVO;
import com.zhul.erp.modules.product.content.dto.MarketingPlatformVO;
import com.zhul.erp.modules.product.content.dto.SavePlatformsRequest;
import com.zhul.erp.modules.product.content.dto.ProductContentVO;
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
 * 内容任务：查看、下载任务包、上传预览、查看平台设置需要菜单权限；
 * 确认写入、资料已核、修改平台设置、标记或撤销不发布还需要「商品内容维护」按钮权限
 */
@RestController
@RequestMapping("/api/v1/product/content-tasks")
@RequiredArgsConstructor
public class ContentTaskController {

    private static final String MENU = "@perm.canAccessMenu('/product/content-tasks')";

    private final ContentTaskService taskService;
    private final ContentPackageService packageService;
    private final ContentImportService importService;
    private final FactTaskService factTaskService;
    private final PlatformSettingService platformSettings;
    private final MarketingSkipService skipService;

    @PostMapping("/page")
    @PreAuthorize(MENU)
    public Result<PageResult<ContentTaskVO>> page(@RequestBody ContentTaskQuery query) {
        return Result.ok(taskService.page(query));
    }

    @PostMapping("/counts")
    @PreAuthorize(MENU)
    public Result<Map<String, Long>> counts(@RequestBody ContentTaskQuery query) {
        return Result.ok(taskService.counts(query));
    }

    @GetMapping("/package")
    @PreAuthorize(MENU)
    public ResponseEntity<byte[]> download(@RequestParam List<Long> productIds, @RequestParam String type,
                                           @RequestParam(required = false) String platform) {
        ContentPackageService.Package pkg = packageService.build(productIds, type, platform);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pkg.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(pkg.contentType()))
                .body(pkg.body());
    }

    @PostMapping("/preview")
    @PreAuthorize(MENU)
    public Result<List<ContentPreviewVO>> preview(@Valid @RequestBody ContentFileRequest req) {
        return Result.ok(importService.preview(req));
    }

    @PostMapping("/confirm")
    @PreAuthorize(MENU + " and @perm.has('product:content:edit')")
    public Result<ContentConfirmVO> confirm(@Valid @RequestBody ContentFileRequest req) {
        return Result.ok(importService.confirm(req));
    }

    /** 资料已核：写入共享商品库，与确认写入一样需要「商品内容维护」权限，公司账号也可以操作 */
    @PostMapping("/products/{productId}/verify-facts")
    @PreAuthorize(MENU + " and @perm.has('product:content:edit')")
    public Result<Void> verifyFacts(@PathVariable Long productId) {
        factTaskService.verify(productId);
        return Result.ok();
    }

    /** 本公司启用的营销平台 */
    @GetMapping("/platforms")
    @PreAuthorize(MENU)
    public Result<List<MarketingPlatformVO>> platforms() {
        return Result.ok(platformSettings.list());
    }

    @PutMapping("/platforms")
    @PreAuthorize(MENU + " and @perm.has('product:content:edit')")
    public Result<List<MarketingPlatformVO>> savePlatforms(@Valid @RequestBody SavePlatformsRequest req) {
        return Result.ok(platformSettings.save(req.getPlatforms()));
    }

    /** 本公司某商品在某平台不发布 */
    @PutMapping("/products/{productId}/skips/{platform}")
    @PreAuthorize(MENU + " and @perm.has('product:content:edit')")
    public Result<Void> skip(@PathVariable Long productId, @PathVariable String platform) {
        skipService.mark(productId, platform);
        return Result.ok();
    }

    @DeleteMapping("/products/{productId}/skips/{platform}")
    @PreAuthorize(MENU + " and @perm.has('product:content:edit')")
    public Result<Void> unskip(@PathVariable Long productId, @PathVariable String platform) {
        skipService.unmark(productId, platform);
        return Result.ok();
    }

    /** 商品详情里的本公司内容：与商品读取一样对所有登录用户开放 */
    @GetMapping("/products/{productId}")
    public Result<ProductContentVO> productContent(@PathVariable Long productId) {
        return Result.ok(taskService.productContent(productId));
    }
}

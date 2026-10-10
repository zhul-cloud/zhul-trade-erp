package com.zhul.erp.modules.product.content.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.product.content.dto.ContentConfirmVO;
import com.zhul.erp.modules.product.content.dto.ContentFileRequest;
import com.zhul.erp.modules.product.content.dto.ContentPreviewVO;
import com.zhul.erp.modules.product.content.dto.ContentTaskQuery;
import com.zhul.erp.modules.product.content.dto.ContentTaskVO;
import com.zhul.erp.modules.product.content.dto.ProductContentVO;
import com.zhul.erp.modules.product.content.service.ContentImportService;
import com.zhul.erp.modules.product.content.service.ContentPackageService;
import com.zhul.erp.modules.product.content.service.ContentTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 内容任务：查看、下载任务包、上传预览需要菜单权限，确认写入还需要「商品内容维护」按钮权限 */
@RestController
@RequestMapping("/api/v1/product/content-tasks")
@RequiredArgsConstructor
public class ContentTaskController {

    private static final String MENU = "@perm.canAccessMenu('/product/content-tasks')";

    private final ContentTaskService taskService;
    private final ContentPackageService packageService;
    private final ContentImportService importService;

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
    public ResponseEntity<byte[]> download(@RequestParam List<Long> productIds) {
        ContentPackageService.Package pkg = packageService.build(productIds);
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

    /** 商品详情里的本公司内容：与商品读取一样对所有登录用户开放 */
    @GetMapping("/products/{productId}")
    public Result<ProductContentVO> productContent(@PathVariable Long productId) {
        return Result.ok(taskService.productContent(productId));
    }
}

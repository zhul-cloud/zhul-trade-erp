package com.zhul.erp.modules.purchase.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.purchase.dto.CancelPurchaseOrderRequest;
import com.zhul.erp.modules.purchase.dto.ConfirmPurchaseOrderRequest;
import com.zhul.erp.modules.purchase.dto.MoveItemsRequest;
import com.zhul.erp.modules.purchase.dto.PurchaseAttachmentFile;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderListVO;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderPageQuery;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderStatsVO;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderVO;
import com.zhul.erp.modules.purchase.dto.SavePurchaseOrderRequest;
import com.zhul.erp.modules.purchase.service.PurchaseOrderService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** 采购单：查看需要「采购单」菜单；编辑、确认下单、上传合同需要「新建采购单」，取消与删除需要「取消采购单」 */
@RestController
@RequestMapping("/api/v1/purchase/orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private static final String MENU = "@perm.canAccessMenu('/purchase/orders')";
    private static final String EDIT = MENU + " and @perm.has('purchase:order:create')";
    private static final String CANCEL = MENU + " and @perm.has('purchase:order:cancel')";

    private final PurchaseOrderService service;

    @PostMapping("/page")
    @PreAuthorize(MENU)
    public Result<PageResult<PurchaseOrderListVO>> page(@RequestBody PurchaseOrderPageQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/stats")
    @PreAuthorize(MENU)
    public Result<PurchaseOrderStatsVO> stats() {
        return Result.ok(service.stats());
    }

    @GetMapping("/{id}")
    @PreAuthorize(MENU)
    public Result<PurchaseOrderVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize(EDIT)
    public Result<PurchaseOrderVO> save(@PathVariable Long id, @Valid @RequestBody SavePurchaseOrderRequest req) {
        return Result.ok(service.save(id, req));
    }

    @DeleteMapping("/{id}/items")
    @PreAuthorize(EDIT)
    public Result<PurchaseOrderVO> removeItems(@PathVariable Long id, @RequestParam List<Long> ids) {
        return Result.ok(service.removeItems(id, ids));
    }

    @PostMapping("/{id}/move")
    @PreAuthorize(EDIT)
    public Result<Long> move(@PathVariable Long id, @Valid @RequestBody MoveItemsRequest req) {
        return Result.ok(service.moveItems(id, req));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize(EDIT)
    public Result<PurchaseOrderVO> confirm(@PathVariable Long id, @Valid @RequestBody ConfirmPurchaseOrderRequest req) {
        return Result.ok(service.confirm(id, req));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(CANCEL)
    public Result<PurchaseOrderVO> cancel(@PathVariable Long id, @Valid @RequestBody CancelPurchaseOrderRequest req) {
        return Result.ok(service.cancel(id, req));
    }

    @PostMapping("/{id}/convert-shop")
    @PreAuthorize(EDIT)
    public Result<PurchaseOrderVO> convertShop(@PathVariable Long id) {
        return Result.ok(service.convertShop(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CANCEL)
    public Result<Void> delete(@PathVariable Long id) {
        service.deleteDraft(id);
        return Result.ok();
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize(EDIT)
    public Result<PurchaseOrderVO> upload(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return Result.ok(service.uploadAttachment(id, file));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(EDIT)
    public Result<PurchaseOrderVO> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        return Result.ok(service.deleteAttachment(id, attachmentId));
    }

    /** 预览（inline=true）或下载合同；只能访问本租户、自己数据范围内采购单的附件 */
    @GetMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(MENU)
    public void download(@PathVariable Long id, @PathVariable Long attachmentId, @RequestParam(defaultValue = "false") boolean inline,
                         HttpServletResponse response) throws IOException {
        PurchaseAttachmentFile file = service.attachmentFile(id, attachmentId);
        response.setContentType(file.contentType());
        response.setContentLengthLong(Files.size(file.path()));
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, (inline ? "inline" : "attachment")
                + "; filename*=UTF-8''" + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader("X-Content-Type-Options", "nosniff");
        Files.copy(file.path(), response.getOutputStream());
    }
}

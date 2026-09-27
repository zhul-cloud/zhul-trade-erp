package com.zhul.erp.modules.masterdata.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.masterdata.dto.CreateSupplierFromChannelRequest;
import com.zhul.erp.modules.masterdata.dto.SaveSupplierRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierAttachmentFile;
import com.zhul.erp.modules.masterdata.dto.SupplierAttachmentUploadVO;
import com.zhul.erp.modules.masterdata.dto.SupplierBatchDeleteRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierBatchDeleteResultVO;
import com.zhul.erp.modules.masterdata.dto.SupplierCreateResultVO;
import com.zhul.erp.modules.masterdata.dto.SupplierFormVO;
import com.zhul.erp.modules.masterdata.dto.SupplierPageQuery;
import com.zhul.erp.modules.masterdata.dto.SupplierVO;
import com.zhul.erp.modules.masterdata.dto.UpdateSupplierRequest;
import com.zhul.erp.modules.masterdata.service.SupplierService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 供应商主数据管理：创建（含内联快速创建、以及从电商询价渠道转正式供应商，不做权限校验，
 * 避免影响询价单等已上线的内联创建流程）、关键词搜索、分页列表、详情、编辑取数、更新、启用/禁用、
 * 软删除（单条 / 批量）、导出。独立管理页见 openspec/changes/add-customer-supplier-management/、
 * openspec/changes/enrich-supplier-basic-info/。
 */
@RestController
@RequestMapping("/api/v1/masterdata/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @PostMapping
    public Result<SupplierCreateResultVO> create(@Valid @RequestBody SaveSupplierRequest req) {
        return Result.ok(supplierService.create(req));
    }

    @PostMapping("/from-channel")
    public Result<SupplierCreateResultVO> createFromChannel(@Valid @RequestBody CreateSupplierFromChannelRequest req) {
        return Result.ok(supplierService.createFromChannel(req));
    }

    @GetMapping
    public Result<List<SupplierVO>> search(@RequestParam(required = false) String keyword) {
        return Result.ok(supplierService.search(keyword));
    }

    @GetMapping("/page")
    public Result<PageResult<SupplierVO>> page(SupplierPageQuery query) {
        return Result.ok(supplierService.page(query));
    }

    /** 导出当前筛选结果（不分页），收款账号脱敏 */
    @GetMapping("/export")
    @PreAuthorize("@perm.has('partner:supplier:export')")
    public void export(SupplierPageQuery query, HttpServletResponse response) throws IOException {
        String filename = "供应商基础信息_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
        try (Workbook workbook = supplierService.export(query)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        }
    }

    /** 上传附件（新增、编辑供应商时用），文件存私有目录；返回的 fileKey 随供应商保存才生效 */
    @PostMapping("/attachments")
    @PreAuthorize("@perm.has('partner:supplier:add') or @perm.has('partner:supplier:edit')")
    public Result<SupplierAttachmentUploadVO> uploadAttachment(@RequestPart("file") MultipartFile file) {
        return Result.ok(supplierService.uploadAttachment(file));
    }

    /** 预览（inline=true）或下载附件；只能访问本租户的附件，需要能访问供应商管理菜单 */
    @GetMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("@perm.canAccessMenu('/partner/suppliers')")
    public void downloadAttachment(@PathVariable Long id, @PathVariable Long attachmentId,
                                   @RequestParam(defaultValue = "false") boolean inline,
                                   HttpServletResponse response) throws IOException {
        SupplierAttachmentFile file = supplierService.getAttachmentFile(id, attachmentId);
        response.setContentType(file.contentType());
        response.setContentLengthLong(Files.size(file.path()));
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, (inline ? "inline" : "attachment")
                + "; filename*=UTF-8''" + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader("X-Content-Type-Options", "nosniff");
        Files.copy(file.path(), response.getOutputStream());
    }

    @GetMapping("/{id}")
    public Result<SupplierVO> getById(@PathVariable Long id) {
        return Result.ok(supplierService.getById(id));
    }

    /** 编辑页取数：收款账号、手机号为明文，所以要求编辑权限 */
    @GetMapping("/{id}/form")
    @PreAuthorize("@perm.has('partner:supplier:edit')")
    public Result<SupplierFormVO> getFormById(@PathVariable Long id) {
        return Result.ok(supplierService.getFormById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('partner:supplier:edit')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateSupplierRequest req) {
        supplierService.update(id, req);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@perm.has('partner:supplier:status')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        supplierService.updateStatus(id, body.get("status"));
        return Result.ok();
    }

    @PostMapping("/batch-delete")
    @PreAuthorize("@perm.has('partner:supplier:delete')")
    public Result<SupplierBatchDeleteResultVO> batchDelete(@Valid @RequestBody SupplierBatchDeleteRequest req) {
        return Result.ok(supplierService.batchDelete(req.getIds()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('partner:supplier:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return Result.ok();
    }
}

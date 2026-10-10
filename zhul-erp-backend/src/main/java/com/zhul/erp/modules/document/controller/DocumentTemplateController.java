package com.zhul.erp.modules.document.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.document.dto.SaveTextTemplateRequest;
import com.zhul.erp.modules.document.dto.TemplateFile;
import com.zhul.erp.modules.document.dto.TemplateTypeVO;
import com.zhul.erp.modules.document.dto.TemplateVersionVO;
import com.zhul.erp.modules.document.service.DocumentTemplateService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.util.List;

/** 单据模版：能访问「单据模版」菜单即可查看、下载、预览；上传 / 设默认 / 启停需要管理权限 */
@RestController
@RequestMapping("/api/v1/system/document-templates")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/system/document-template')")
public class DocumentTemplateController {

    private static final String EDIT = "@perm.has('system:document-template:edit')";

    private final DocumentTemplateService service;

    @GetMapping
    public Result<List<TemplateTypeVO>> types() {
        return Result.ok(service.types());
    }

    @GetMapping("/{docType}/versions")
    public Result<List<TemplateVersionVO>> versions(@PathVariable int docType) {
        return Result.ok(service.versions(docType));
    }

    @PostMapping("/{docType}/versions")
    @PreAuthorize(EDIT)
    public Result<TemplateVersionVO> upload(@PathVariable int docType, @RequestPart("file") MultipartFile file,
                                            @RequestParam String note) {
        return Result.ok(service.upload(docType, file, note));
    }

    @PostMapping("/text-versions")
    @PreAuthorize(EDIT)
    public Result<TemplateVersionVO> saveText(@Valid @RequestBody SaveTextTemplateRequest req) {
        return Result.ok(service.saveText(req));
    }

    @PutMapping("/versions/{versionId}/default")
    @PreAuthorize(EDIT)
    public Result<Void> setDefault(@PathVariable Long versionId) {
        service.setDefault(versionId);
        return Result.ok();
    }

    @PutMapping("/versions/{versionId}/enabled")
    @PreAuthorize(EDIT)
    public Result<Void> setEnabled(@PathVariable Long versionId, @RequestParam boolean enabled) {
        service.setEnabled(versionId, enabled);
        return Result.ok();
    }

    @GetMapping("/versions/{versionId}/file")
    public void download(@PathVariable Long versionId, HttpServletResponse response) throws IOException {
        write(service.download(versionId), false, response);
    }

    @GetMapping("/versions/{versionId}/preview")
    public void preview(@PathVariable Long versionId, @RequestParam(defaultValue = "pdf") String format,
                        HttpServletResponse response) throws IOException {
        write(service.previewExport(versionId, format), "pdf".equalsIgnoreCase(format), response);
    }

    static void write(TemplateFile file, boolean inline, HttpServletResponse response) throws IOException {
        response.setContentType(file.contentType());
        response.setContentLength(file.content().length);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, (inline ? "inline" : "attachment")
                + "; filename*=UTF-8''" + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getOutputStream().write(file.content());
    }
}

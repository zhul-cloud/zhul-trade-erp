package com.zhul.erp.modules.logistics.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.document.dto.TemplateFile;
import com.zhul.erp.modules.logistics.dto.SaveStatementRequest;
import com.zhul.erp.modules.logistics.dto.StatementVO;
import com.zhul.erp.modules.logistics.service.StatementService;
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
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 货代月结对账 */
@RestController
@RequestMapping("/api/v1/logistics/statements")
@RequiredArgsConstructor
public class StatementController {

    private static final String MENU = "@perm.canAccessMenu('/logistics/statements')";
    private static final String EDIT = MENU + " and @perm.has('logistics:statement:edit')";

    private final StatementService service;

    @GetMapping
    @PreAuthorize(MENU)
    public Result<PageResult<StatementVO>> page(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize,
                                                @RequestParam(required = false) Long forwarderId, @RequestParam(required = false) Integer status) {
        return Result.ok(service.page(page, pageSize, forwarderId, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize(MENU)
    public Result<StatementVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @PreAuthorize(EDIT)
    public Result<StatementVO> create(@Valid @RequestBody SaveStatementRequest req) {
        return Result.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(EDIT)
    public Result<StatementVO> save(@PathVariable Long id, @Valid @RequestBody SaveStatementRequest req) {
        return Result.ok(service.save(id, req));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize(EDIT)
    public Result<StatementVO> confirm(@PathVariable Long id, @Valid @RequestBody SaveStatementRequest req) {
        return Result.ok(service.confirm(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(EDIT)
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/export")
    @PreAuthorize(MENU)
    public void export(@PathVariable Long id, HttpServletResponse response) throws IOException {
        TemplateFile file = service.export(id);
        response.setContentType(file.contentType());
        response.setContentLength(file.content().length);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getOutputStream().write(file.content());
    }
}

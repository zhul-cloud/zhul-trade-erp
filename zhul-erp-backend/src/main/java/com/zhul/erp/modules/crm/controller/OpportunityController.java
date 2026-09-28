package com.zhul.erp.modules.crm.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.crm.dto.ChangeStageRequest;
import com.zhul.erp.modules.crm.dto.CloseOpportunityRequest;
import com.zhul.erp.modules.crm.dto.OpportunityDetailVO;
import com.zhul.erp.modules.crm.dto.OpportunityPageQuery;
import com.zhul.erp.modules.crm.dto.OpportunityStageVO;
import com.zhul.erp.modules.crm.dto.OpportunityStatsVO;
import com.zhul.erp.modules.crm.dto.OpportunitySummaryVO;
import com.zhul.erp.modules.crm.dto.OpportunityUploadVO;
import com.zhul.erp.modules.crm.dto.OpportunityVO;
import com.zhul.erp.modules.crm.dto.RegisterOpportunityRequest;
import com.zhul.erp.modules.crm.dto.UpdateOpportunityRequest;
import com.zhul.erp.modules.crm.service.OpportunityService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 商机管理：登记（同时建客户）、阶段流转、列表 / 详情、附件、每日统计。
 * 查看类接口要求能访问「商机管理」菜单，写操作要求对应按钮权限；数据范围由数据权限控制。
 * 见 openspec/changes/add-opportunity-management/。
 */
@RestController
@RequestMapping("/api/v1/crm/opportunities")
@RequiredArgsConstructor
public class OpportunityController {

    private static final String VIEW = "@perm.canAccessMenu('/inquiry/opportunities')";

    private final OpportunityService opportunityService;

    @GetMapping("/stages")
    @PreAuthorize(VIEW)
    public Result<List<OpportunityStageVO>> stages() {
        return Result.ok(opportunityService.stages());
    }

    @GetMapping("/page")
    @PreAuthorize(VIEW)
    public Result<PageResult<OpportunityVO>> page(OpportunityPageQuery query) {
        return Result.ok(opportunityService.page(query));
    }

    @GetMapping("/summary")
    @PreAuthorize(VIEW)
    public Result<OpportunitySummaryVO> summary() {
        return Result.ok(opportunityService.summary());
    }

    @GetMapping("/stats")
    @PreAuthorize(VIEW)
    public Result<OpportunityStatsVO> stats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "channel") String groupBy) {
        return Result.ok(opportunityService.stats(from, to, groupBy));
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public Result<OpportunityDetailVO> detail(@PathVariable Long id) {
        return Result.ok(opportunityService.detail(id));
    }

    @PostMapping
    @PreAuthorize("@perm.has('crm:opportunity:add')")
    public Result<Map<String, Long>> register(@Valid @RequestBody RegisterOpportunityRequest req) {
        return Result.ok(Map.of("id", opportunityService.register(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('crm:opportunity:edit')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateOpportunityRequest req) {
        opportunityService.update(id, req);
        return Result.ok();
    }

    @PostMapping("/{id}/stage")
    @PreAuthorize("@perm.has('crm:opportunity:edit')")
    public Result<Void> changeStage(@PathVariable Long id, @Valid @RequestBody ChangeStageRequest req) {
        opportunityService.changeStage(id, req);
        return Result.ok();
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@perm.has('crm:opportunity:edit')")
    public Result<Void> close(@PathVariable Long id, @Valid @RequestBody CloseOpportunityRequest req) {
        opportunityService.close(id, req);
        return Result.ok();
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("@perm.has('crm:opportunity:edit')")
    public Result<Void> reopen(@PathVariable Long id) {
        opportunityService.reopen(id);
        return Result.ok();
    }

    /** 上传需求附件（登记、编辑时用）；返回的 fileKey 随商机保存才生效 */
    @PostMapping("/attachments")
    @PreAuthorize("@perm.has('crm:opportunity:add') or @perm.has('crm:opportunity:edit')")
    public Result<OpportunityUploadVO> upload(@RequestPart("file") MultipartFile file) {
        return Result.ok(opportunityService.uploadAttachment(file));
    }

    /** 预览（inline=true）或下载附件 */
    @GetMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(VIEW)
    public void download(@PathVariable Long id, @PathVariable Long attachmentId,
                         @RequestParam(defaultValue = "false") boolean inline,
                         HttpServletResponse response) throws IOException {
        OpportunityService.AttachmentFile file = opportunityService.attachmentFile(id, attachmentId);
        response.setContentType(file.contentType());
        response.setContentLengthLong(Files.size(file.path()));
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, (inline ? "inline" : "attachment")
                + "; filename*=UTF-8''" + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader("X-Content-Type-Options", "nosniff");
        Files.copy(file.path(), response.getOutputStream());
    }
}

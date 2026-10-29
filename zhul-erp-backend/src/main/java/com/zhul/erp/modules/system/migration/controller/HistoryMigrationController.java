package com.zhul.erp.modules.system.migration.controller;

import com.zhul.erp.common.result.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.system.migration.dto.MigrationBatchVO;
import com.zhul.erp.modules.system.migration.dto.MigrationBundleVO;
import com.zhul.erp.modules.system.migration.dto.RunMigrationRequest;
import com.zhul.erp.modules.system.migration.service.HistoryMigrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 飞书历史数据迁移（spec system/history-migration）：仅平台账号，权限沿用「数据初始化」 */
@RestController
@RequestMapping("/api/v1/system/history-migrations")
@RequiredArgsConstructor
public class HistoryMigrationController {

    private static final String PERM = "@perm.has('tenant:list:init')";

    private final HistoryMigrationService service;

    @PostMapping
    @PreAuthorize(PERM)
    public Result<MigrationBatchVO> run(@Valid @RequestBody RunMigrationRequest req) {
        return Result.ok(service.run(req.getTenantId(), req.getBundleId(), req.getBundleDir(), req.getAttachmentDir(), req.isDryRun()));
    }

    /** 上传迁移包（pack.js 打出的 zip） */
    @PostMapping("/bundles")
    @PreAuthorize(PERM)
    public Result<MigrationBundleVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(service.upload(file));
    }

    /** 对租户现有的迁移数据核对一次，不保存 */
    @PostMapping("/verify")
    @PreAuthorize(PERM)
    public Result<JsonNode> verify(@Valid @RequestBody RunMigrationRequest req) {
        return Result.ok(service.verify(req.getTenantId(), req.getBundleId(), req.getBundleDir()));
    }

    @GetMapping("/{batchId}")
    @PreAuthorize(PERM)
    public Result<MigrationBatchVO> get(@PathVariable Long batchId) {
        return Result.ok(service.get(batchId));
    }

    @PostMapping("/{batchId}/rollback")
    @PreAuthorize(PERM)
    public Result<MigrationBatchVO> rollback(@PathVariable Long batchId) {
        return Result.ok(service.rollback(batchId));
    }

    @GetMapping
    @PreAuthorize(PERM)
    public Result<List<MigrationBatchVO>> list(@RequestParam(required = false) Integer tenantId) {
        return Result.ok(service.list(tenantId));
    }
}

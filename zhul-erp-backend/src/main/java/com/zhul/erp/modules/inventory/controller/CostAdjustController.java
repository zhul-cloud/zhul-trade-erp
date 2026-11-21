package com.zhul.erp.modules.inventory.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.inventory.dto.CostAdjustQuery;
import com.zhul.erp.modules.inventory.dto.CostAdjustSummaryVO;
import com.zhul.erp.modules.inventory.dto.CostAdjustVO;
import com.zhul.erp.modules.inventory.service.CostAdjustService;
import com.zhul.erp.modules.system.doclog.DocAttachRequest;
import com.zhul.erp.modules.system.doclog.DocRemarkRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/** 成本调整单（spec finance/inventory-cost「成本调整页」）：「财务管理 → 成本调整」；系统生成，只能查看、改备注与附件 */
@RestController
@RequestMapping("/api/v1/finance/cost-adjustments")
@RequiredArgsConstructor
public class CostAdjustController {

    private static final String VIEW = "@perm.canAccessMenu('/finance/cost-adjustments')";

    private final CostAdjustService service;

    @PostMapping("/page")
    @PreAuthorize(VIEW)
    public Result<PageResult<CostAdjustVO>> page(@RequestBody CostAdjustQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/summary")
    @PreAuthorize(VIEW)
    public Result<CostAdjustSummaryVO> summary(@RequestParam String month) {
        return Result.ok(service.summary(month));
    }

    /** 导出：高资源操作，只导当前筛选（最多 5000 张） */
    @PostMapping("/export")
    @PreAuthorize(VIEW)
    public org.springframework.http.ResponseEntity<byte[]> export(@RequestBody CostAdjustQuery q) {
        String name = "成本调整" + (q.getMonth() == null ? "" : "-" + q.getMonth()) + ".xlsx";
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment()
                        .filename(name, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.export(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public Result<CostAdjustVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PutMapping("/{id}/remark")
    @PreAuthorize(VIEW)
    public Result<CostAdjustVO> remark(@PathVariable Long id, @Valid @RequestBody DocRemarkRequest req) {
        return Result.ok(service.remark(id, req.getRemark()));
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize(VIEW)
    public Result<CostAdjustVO> attach(@PathVariable Long id, @Valid @RequestBody DocAttachRequest req) {
        return Result.ok(service.attach(id, req.getIds()));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(VIEW)
    public Result<CostAdjustVO> removeAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        return Result.ok(service.removeAttachment(id, attachmentId));
    }
}

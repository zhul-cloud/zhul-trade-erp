package com.zhul.erp.modules.aftersales.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.aftersales.dto.ConfirmReturnRequest;
import com.zhul.erp.modules.aftersales.dto.ReasonBody;
import com.zhul.erp.modules.aftersales.dto.ReturnQuery;
import com.zhul.erp.modules.aftersales.dto.ReturnVO;
import com.zhul.erp.modules.aftersales.dto.SaveReturnRequest;
import com.zhul.erp.modules.aftersales.service.SalesReturnService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 销售退货单 TH（spec sales/sales-return）：售后单选择处理方式生成，业务员确认后交仓库验收；可见范围同售后单 */
@RestController
@RequestMapping("/api/v1/sales/returns")
@RequiredArgsConstructor
public class SalesReturnController {

    private static final String VIEW = "@perm.canAccessMenu('/sales/returns') or @perm.canAccessMenu('/sales/after-sales')";

    private final SalesReturnService service;

    @PostMapping("/page")
    @PreAuthorize(VIEW)
    public Result<PageResult<ReturnVO>> page(@RequestBody ReturnQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/counts")
    @PreAuthorize(VIEW)
    public Result<Map<String, Long>> counts() {
        return Result.ok(service.counts());
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public Result<ReturnVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @PreAuthorize(VIEW)
    public Result<ReturnVO> create(@Valid @RequestBody SaveReturnRequest req) {
        return Result.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(VIEW)
    public Result<ReturnVO> update(@PathVariable Long id, @Valid @RequestBody SaveReturnRequest req) {
        return Result.ok(service.update(id, req));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize(VIEW)
    public Result<ReturnVO> confirm(@PathVariable Long id, @Valid @RequestBody(required = false) ConfirmReturnRequest req) {
        return Result.ok(service.confirm(id, req));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(VIEW)
    public Result<ReturnVO> voidReturn(@PathVariable Long id, @Valid @RequestBody ReasonBody req) {
        return Result.ok(service.voidReturn(id, req.getReason()));
    }

    @PostMapping("/{id}/remark")
    @PreAuthorize(VIEW)
    public Result<ReturnVO> remark(@PathVariable Long id, @Valid @RequestBody DocRemarkRequest req) {
        return Result.ok(service.remark(id, req.getRemark()));
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize(VIEW)
    public Result<ReturnVO> attach(@PathVariable Long id, @Valid @RequestBody DocAttachRequest req) {
        return Result.ok(service.attach(id, req.getIds()));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(VIEW)
    public Result<ReturnVO> removeAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        return Result.ok(service.removeAttachment(id, attachmentId));
    }
}

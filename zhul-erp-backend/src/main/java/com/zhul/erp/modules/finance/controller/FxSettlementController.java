package com.zhul.erp.modules.finance.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.finance.dto.FxPendingVO;
import com.zhul.erp.modules.finance.dto.FxSettlementQuery;
import com.zhul.erp.modules.finance.dto.FxSettlementVO;
import com.zhul.erp.modules.finance.dto.SaveFxSettlementRequest;
import com.zhul.erp.modules.finance.dto.VoidPaymentRequest;
import com.zhul.erp.modules.finance.service.FxSettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 结汇单（spec finance/fx-settlement） */
@RestController
@RequestMapping("/api/v1/finance/fx-settlements")
@RequiredArgsConstructor
public class FxSettlementController {

    private static final String MENU = "@perm.canAccessMenu('/finance/fx-settlements')";
    private static final String SETTLE = MENU + " and @perm.has('finance:fx:settle')";

    private final FxSettlementService service;

    @GetMapping
    @PreAuthorize(MENU)
    public Result<PageResult<FxSettlementVO>> page(FxSettlementQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/pending")
    @PreAuthorize(MENU)
    public Result<List<FxPendingVO>> pending() {
        return Result.ok(service.pending());
    }

    @GetMapping("/{id}")
    @PreAuthorize(MENU)
    public Result<FxSettlementVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @PreAuthorize(SETTLE)
    public Result<FxSettlementVO> create(@Valid @RequestBody SaveFxSettlementRequest req) {
        return Result.ok(service.create(req));
    }

    @PostMapping("/{id}/remark")
    @PreAuthorize(SETTLE)
    public Result<FxSettlementVO> remark(@PathVariable Long id,
                                         @Valid @RequestBody com.zhul.erp.modules.system.doclog.DocRemarkRequest req) {
        return Result.ok(service.remark(id, req.getRemark()));
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize(SETTLE)
    public Result<FxSettlementVO> attach(@PathVariable Long id,
                                         @Valid @RequestBody com.zhul.erp.modules.system.doclog.DocAttachRequest req) {
        return Result.ok(service.attach(id, req.getIds()));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(SETTLE)
    public Result<FxSettlementVO> removeAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        return Result.ok(service.removeAttachment(id, attachmentId));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(SETTLE)
    public Result<FxSettlementVO> voidSettlement(@PathVariable Long id, @Valid @RequestBody VoidPaymentRequest req) {
        return Result.ok(service.voidSettlement(id, req.getReason()));
    }
}

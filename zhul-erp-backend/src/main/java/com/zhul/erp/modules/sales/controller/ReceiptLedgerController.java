package com.zhul.erp.modules.sales.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.sales.dto.ReceiptRecordPageVO;
import com.zhul.erp.modules.sales.dto.ReceiptRecordQuery;
import com.zhul.erp.modules.sales.dto.ReceiptRowVO;
import com.zhul.erp.modules.sales.dto.UnclaimReceiptRequest;
import com.zhul.erp.modules.sales.dto.UnclaimedListVO;
import com.zhul.erp.modules.sales.dto.UnclaimedReceiptRequest;
import com.zhul.erp.modules.sales.dto.VoidReceiptRequest;
import com.zhul.erp.modules.sales.service.ReceiptLedgerService;
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

/** 财务管理 → 收款管理：未认领到账与收款记录（需要「收款管理」菜单与「登记到账」权限） */
@RestController
@RequestMapping("/api/v1/finance/receipts")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/finance/receipts') and @perm.has('sales:pi:receipt-confirm')")
public class ReceiptLedgerController {

    private final ReceiptLedgerService service;

    @GetMapping("/unclaimed")
    public Result<UnclaimedListVO> unclaimed(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String currencyCode) {
        return Result.ok(service.unclaimed(keyword, currencyCode));
    }

    @PostMapping("/unclaimed")
    public Result<ReceiptRowVO> registerUnclaimed(@Valid @RequestBody UnclaimedReceiptRequest req) {
        return Result.ok(service.registerUnclaimed(req));
    }

    @PostMapping("/{id}/unclaim")
    public Result<Void> unclaim(@PathVariable Long id, @Valid @RequestBody UnclaimReceiptRequest req) {
        service.unclaim(id, req.getReason());
        return Result.ok();
    }

    @PostMapping("/{id}/void")
    public Result<Void> voidUnclaimed(@PathVariable Long id, @Valid @RequestBody VoidReceiptRequest req) {
        service.voidUnclaimed(id, req.getReason());
        return Result.ok();
    }

    @PostMapping("/records")
    public Result<ReceiptRecordPageVO> records(@RequestBody ReceiptRecordQuery q) {
        return Result.ok(service.records(q));
    }
}

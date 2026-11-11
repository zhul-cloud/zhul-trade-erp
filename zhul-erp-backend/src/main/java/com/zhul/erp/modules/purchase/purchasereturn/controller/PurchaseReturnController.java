package com.zhul.erp.modules.purchase.purchasereturn.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.aftersales.dto.ReasonBody;
import com.zhul.erp.modules.purchase.purchasereturn.dto.CompletePurchaseReturnRequest;
import com.zhul.erp.modules.purchase.purchasereturn.dto.PurchaseReturnQtyRequest;
import com.zhul.erp.modules.purchase.purchasereturn.dto.PurchaseReturnQuery;
import com.zhul.erp.modules.purchase.purchasereturn.dto.PurchaseReturnVO;
import com.zhul.erp.modules.purchase.purchasereturn.dto.ShipPurchaseReturnRequest;
import com.zhul.erp.modules.purchase.purchasereturn.service.PurchaseReturnService;
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

import java.util.List;
import java.util.Map;

/** 采购退货单（spec purchase/purchase-return）：「采购管理 → 采购退货」 */
@RestController
@RequestMapping("/api/v1/purchase/returns")
@RequiredArgsConstructor
public class PurchaseReturnController {

    private static final String VIEW = "@perm.canAccessMenu('/purchase/returns')";
    private static final String EDIT = "@perm.canAccessMenu('/purchase/returns') and @perm.has('purchase:purchase-return:edit')";

    private final PurchaseReturnService service;

    @PostMapping("/page")
    @PreAuthorize(VIEW)
    public Result<PageResult<PurchaseReturnVO>> page(@RequestBody PurchaseReturnQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/counts")
    @PreAuthorize(VIEW)
    public Result<Map<String, Long>> counts() {
        return Result.ok(service.counts());
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public Result<PurchaseReturnVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @GetMapping("/of-order/{poId}")
    @PreAuthorize("@perm.canAccessMenu('/purchase/orders') or @perm.canAccessMenu('/purchase/returns')")
    public Result<List<PurchaseReturnVO>> ofOrder(@PathVariable Long poId) {
        return Result.ok(service.ofPurchaseOrder(poId));
    }

    @PutMapping("/{id}/quantities")
    @PreAuthorize(EDIT)
    public Result<PurchaseReturnVO> quantities(@PathVariable Long id, @Valid @RequestBody PurchaseReturnQtyRequest req) {
        return Result.ok(service.updateQuantities(id, req));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize(EDIT)
    public Result<PurchaseReturnVO> ship(@PathVariable Long id, @Valid @RequestBody ShipPurchaseReturnRequest req) {
        return Result.ok(service.ship(id, req));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(EDIT)
    public Result<PurchaseReturnVO> complete(@PathVariable Long id, @Valid @RequestBody CompletePurchaseReturnRequest req) {
        return Result.ok(service.complete(id, req));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize(EDIT)
    public Result<PurchaseReturnVO> reopen(@PathVariable Long id, @Valid @RequestBody ReasonBody req) {
        return Result.ok(service.reopen(id, req.getReason()));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(EDIT)
    public Result<PurchaseReturnVO> voidReturn(@PathVariable Long id, @Valid @RequestBody ReasonBody req) {
        return Result.ok(service.voidReturn(id, req.getReason()));
    }

    @PutMapping("/{id}/remark")
    @PreAuthorize(EDIT)
    public Result<PurchaseReturnVO> remark(@PathVariable Long id, @Valid @RequestBody DocRemarkRequest req) {
        return Result.ok(service.remark(id, req.getRemark()));
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize(EDIT)
    public Result<PurchaseReturnVO> attach(@PathVariable Long id, @Valid @RequestBody DocAttachRequest req) {
        return Result.ok(service.attach(id, req.getIds()));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(EDIT)
    public Result<PurchaseReturnVO> removeAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        return Result.ok(service.removeAttachment(id, attachmentId));
    }
}

package com.zhul.erp.modules.aftersales.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.aftersales.dto.DisposeReturnRequest;
import com.zhul.erp.modules.aftersales.dto.ReasonBody;
import com.zhul.erp.modules.aftersales.dto.ReceiveReturnRequest;
import com.zhul.erp.modules.aftersales.dto.ReturnQuery;
import com.zhul.erp.modules.aftersales.dto.ReturnReceiptVO;
import com.zhul.erp.modules.aftersales.service.ReturnReceiptService;
import com.zhul.erp.modules.system.doclog.DocAttachRequest;
import com.zhul.erp.modules.system.doclog.DocRemarkRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 退货入库单 RR（spec warehouse/sales-return）：仓库验收，采购员处理去向 */
@RestController
@RequestMapping("/api/v1/warehouse/returns")
@RequiredArgsConstructor
public class ReturnReceiptController {

    private static final String MENU = "@perm.canAccessMenu('/warehouse/returns')";
    private static final String VIEW = MENU + " or @perm.canAccessMenu('/sales/returns') or @perm.canAccessMenu('/sales/after-sales')";
    private static final String RECEIVE = MENU + " and @perm.has('warehouse:return:receive')";
    private static final String DISPOSE = MENU + " and @perm.has('purchase:return:dispose')";

    private final ReturnReceiptService service;

    @PostMapping("/page")
    @PreAuthorize(MENU)
    public Result<PageResult<ReturnReceiptVO>> page(@RequestBody ReturnQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/counts")
    @PreAuthorize(MENU)
    public Result<Map<String, Long>> counts() {
        return Result.ok(service.counts());
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public Result<ReturnReceiptVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize(RECEIVE)
    public Result<ReturnReceiptVO> receive(@PathVariable Long id, @Valid @RequestBody ReceiveReturnRequest req) {
        return Result.ok(service.receive(id, req));
    }

    @PostMapping("/{id}/unreceive")
    @PreAuthorize(RECEIVE)
    public Result<ReturnReceiptVO> unreceive(@PathVariable Long id, @Valid @RequestBody ReasonBody req) {
        return Result.ok(service.unreceive(id, req.getReason()));
    }

    @PostMapping("/{id}/dispose")
    @PreAuthorize(DISPOSE)
    public Result<ReturnReceiptVO> dispose(@PathVariable Long id, @Valid @RequestBody DisposeReturnRequest req) {
        return Result.ok(service.dispose(id, req));
    }

    @PostMapping("/{id}/remark")
    @PreAuthorize(MENU)
    public Result<ReturnReceiptVO> remark(@PathVariable Long id, @Valid @RequestBody DocRemarkRequest req) {
        return Result.ok(service.remark(id, req.getRemark()));
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize(MENU)
    public Result<ReturnReceiptVO> attach(@PathVariable Long id, @Valid @RequestBody DocAttachRequest req) {
        return Result.ok(service.attach(id, req.getIds()));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(MENU)
    public Result<ReturnReceiptVO> removeAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        return Result.ok(service.removeAttachment(id, attachmentId));
    }
}

package com.zhul.erp.modules.sales.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.framework.storage.FileStorageService.FileLink;
import com.zhul.erp.modules.sales.dto.ReceiptDocVO;
import com.zhul.erp.modules.sales.service.ReceiptDocService;
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

/**
 * 收款单详情（change finance-cash-docs）：「收款管理」菜单看全部、维护备注与附件；
 * 只有形式发票或销售订单菜单的用户只读查看自己数据权限内的收款单（范围在 service 里校验）。
 */
@RestController
@RequestMapping("/api/v1/finance/receipts")
@RequiredArgsConstructor
public class ReceiptDocController {

    private static final String READ = "@perm.canAccessMenu('/finance/receipts') or @perm.canAccessMenu('/sales/pi')"
            + " or @perm.canAccessMenu('/sales/orders')";
    private static final String MENU = "@perm.canAccessMenu('/finance/receipts')";

    private final ReceiptDocService service;

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public Result<ReceiptDocVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @GetMapping("/{id}/doc-files/{index}/url")
    @PreAuthorize(READ)
    public Result<FileLink> file(@PathVariable Long id, @PathVariable int index) {
        return Result.ok(service.fileLink(id, index));
    }

    @PostMapping("/{id}/remark")
    @PreAuthorize(MENU)
    public Result<ReceiptDocVO> remark(@PathVariable Long id, @Valid @RequestBody DocRemarkRequest req) {
        return Result.ok(service.remark(id, req.getRemark()));
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize(MENU)
    public Result<ReceiptDocVO> attach(@PathVariable Long id, @Valid @RequestBody DocAttachRequest req) {
        return Result.ok(service.attach(id, req.getIds()));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(MENU)
    public Result<ReceiptDocVO> removeAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        return Result.ok(service.removeAttachment(id, attachmentId));
    }
}

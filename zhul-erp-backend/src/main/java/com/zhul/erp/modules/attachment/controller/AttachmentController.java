package com.zhul.erp.modules.attachment.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.framework.storage.FileStorageService.FileLink;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import com.zhul.erp.modules.attachment.service.AttachmentOwnerScope;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 业务附件上传与读取：按所属单据类型校验菜单权限；读取返回临时签名链接，浏览器直接从存储取文件（支持 Range，视频可以拖动播放）。
 */
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    /** 所属单据类型 → 能看这类附件的菜单（任一即可） */
    private static final Map<String, List<String>> MENUS = Map.ofEntries(
            Map.entry(AttachmentService.SHIPMENT, List.of("/purchase/shipments", "/warehouse/receipts", "/purchase/orders")),
            Map.entry(AttachmentService.RECEIPT, List.of("/warehouse/receipts", "/purchase/shipments", "/purchase/orders")),
            Map.entry(AttachmentService.SHOOT, List.of("/warehouse/shoots")),
            Map.entry(AttachmentService.RECEIPT_DEFECT, List.of("/warehouse/receipts", "/purchase/shipments", "/purchase/orders")),
            Map.entry(AttachmentService.PACKING, List.of("/warehouse/outbounds", "/sales/orders")),
            Map.entry(AttachmentService.OUTBOUND, List.of("/warehouse/outbounds", "/sales/orders")),
            Map.entry(AttachmentService.NETWORK, List.of("/operations/media")),
            Map.entry(AttachmentService.LOGISTICS, List.of("/logistics/shipments")),
            Map.entry(AttachmentService.LOGISTICS_DOC, List.of("/logistics/shipments")),
            Map.entry(AttachmentService.PAYMENT, List.of("/finance/payables", "/finance/payments", "/purchase/orders",
                    "/logistics/statements", "/finance/reimbursements")),
            Map.entry(AttachmentService.COURIER_PROOF, List.of("/finance/reimbursements")),
            Map.entry(AttachmentService.RETURN_PROOF, List.of("/finance/reimbursements", "/purchase/returns")),
            Map.entry(AttachmentService.EXPENSE, List.of("/finance/expenses", "/finance/reimbursements")),
            Map.entry(AttachmentService.REIMBURSE, List.of("/finance/reimbursements", "/finance/payments")),
            Map.entry(AttachmentService.PO_INVOICE, List.of("/purchase/orders", "/finance/payables")),
            Map.entry(AttachmentService.REFUND, List.of("/finance/refunds", "/finance/credits", "/finance/receipts", "/sales/orders")),
            Map.entry(AttachmentService.CREDIT, List.of("/finance/credits")),
            Map.entry(AttachmentService.OTHER_INCOME, List.of("/finance/other-incomes")),
            Map.entry(AttachmentService.SALES_ORDER, List.of("/sales/orders")),
            Map.entry(AttachmentService.AFTER_SALES, List.of("/sales/after-sales", "/sales/orders")),
            Map.entry(AttachmentService.AFTER_SALES_LOG, List.of("/sales/after-sales", "/warehouse/returns", "/finance/refunds")),
            Map.entry(AttachmentService.SALES_RETURN, List.of("/sales/returns", "/sales/after-sales")),
            Map.entry(AttachmentService.RETURN_RECEIPT, List.of("/warehouse/returns", "/sales/returns", "/sales/after-sales")),
            Map.entry(AttachmentService.PURCHASE_RETURN, List.of("/purchase/returns")),
            Map.entry(AttachmentService.REFUND_REQUEST, List.of("/finance/refunds", "/sales/after-sales", "/sales/orders")),
            Map.entry(AttachmentService.SOURCING_QUOTE, List.of("/inquiry/my-tasks", "/inquiry/sourcing-board",
                    "/inquiry/price-history", "/inquiry/customer-inquiries", "/quotation/quotations")));

    private final AttachmentService service;
    private final PermissionChecker perm;
    private final AttachmentOwnerScope ownerScope;

    /** ownerId 选填：实拍任务传入时，素材直接放到该型号的目录 */
    @PostMapping
    public Result<AttachmentVO> upload(@RequestParam String ownerType, @RequestParam(required = false) Long ownerId,
                                       @RequestPart("file") MultipartFile file) {
        requireMenu(ownerType);
        return Result.ok(service.upload(ownerType, ownerId, file));
    }

    /** 附件的临时访问链接（默认 10 分钟）：download=true 时强制下载，否则浏览器内预览、播放 */
    @GetMapping("/{id}/url")
    public Result<FileLink> url(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean download) {
        BizAttachmentDO a = service.require(id);
        requireMenu(a.getOwnerType());
        if (!ownerScope.canSee(a)) {
            throw new org.springframework.security.access.AccessDeniedException("没有权限访问这个附件");
        }
        return Result.ok(service.link(a, download));
    }

    private void requireMenu(String ownerType) {
        List<String> menus = MENUS.get(ownerType);
        if (menus == null || menus.stream().noneMatch(perm::canAccessMenu)) {
            throw new org.springframework.security.access.AccessDeniedException("没有权限访问这个附件");
        }
    }
}

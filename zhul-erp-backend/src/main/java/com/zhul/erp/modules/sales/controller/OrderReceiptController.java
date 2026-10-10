package com.zhul.erp.modules.sales.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.framework.storage.PrivateFileStorage;
import com.zhul.erp.modules.sales.dto.ClaimReceiptRequest;
import com.zhul.erp.modules.sales.dto.ConfirmReceiptRequest;
import com.zhul.erp.modules.sales.dto.PlatformReceiptRequest;
import com.zhul.erp.modules.sales.dto.ReceiptRowVO;
import com.zhul.erp.modules.sales.dto.SalesOrderVO;
import com.zhul.erp.modules.sales.dto.ReceiptDeskQuery;
import com.zhul.erp.modules.sales.dto.ReceiptDeskRowVO;
import com.zhul.erp.modules.sales.dto.VoidReceiptRequest;
import com.zhul.erp.modules.sales.service.PaymentReceiptService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 手动创建的订单的收款：与 PI 收款面板相同的权限（订单菜单代替 PI 菜单），受订单数据范围限制 */
@RestController
@RequestMapping("/api/v1/sales/orders")
@RequiredArgsConstructor
public class OrderReceiptController {

    private final PaymentReceiptService service;

    @PostMapping("/{id}/slips")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders') and @perm.has('sales:pi:receipt-slip')")
    public Result<SalesOrderVO> uploadSlip(@PathVariable Long id, @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                           @RequestParam(required = false) BigDecimal amount,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidDate,
                                           @RequestParam(required = false) String paymentMethod,
                                           @RequestParam(required = false) String note) {
        return Result.ok(service.uploadOrderSlip(id, files, amount, paidDate, paymentMethod, note));
    }

    @DeleteMapping("/{id}/slips/{slipId}")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders') and @perm.has('sales:pi:receipt-slip')")
    public Result<SalesOrderVO> deleteSlip(@PathVariable Long id, @PathVariable Long slipId) {
        return Result.ok(service.deleteOrderSlip(id, slipId));
    }

    @GetMapping("/{id}/slips/{slipId}/files/{index}")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders') or @perm.has('sales:pi:receipt-confirm')")
    public void slipFile(@PathVariable Long id, @PathVariable Long slipId, @PathVariable int index, HttpServletResponse response)
            throws IOException {
        PaymentReceiptService.SlipFile f = service.orderSlipFile(id, slipId, index);
        response.setContentType(PrivateFileStorage.contentTypeOf(f.path().getFileName().toString()));
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''"
                + URLEncoder.encode(f.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader("X-Content-Type-Options", "nosniff");
        Files.copy(f.path(), response.getOutputStream());
    }

    @PostMapping("/{id}/receipts")
    @PreAuthorize("@perm.has('sales:pi:receipt-confirm')")
    public Result<SalesOrderVO> confirm(@PathVariable Long id, @Valid @RequestBody ConfirmReceiptRequest req) {
        return Result.ok(service.confirmOrder(id, req));
    }

    @PostMapping("/{id}/receipts/{receiptId}/void")
    @PreAuthorize("@perm.has('sales:pi:receipt-confirm') or (@perm.canAccessMenu('/sales/orders') and @perm.has('sales:pi:platform-receipt'))")
    public Result<SalesOrderVO> voidReceipt(@PathVariable Long id, @PathVariable Long receiptId, @Valid @RequestBody VoidReceiptRequest req) {
        return Result.ok(service.voidOrderReceipt(id, receiptId, req.getReason()));
    }

    @PostMapping("/{id}/platform-receipts")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders') and @perm.has('sales:pi:platform-receipt')")
    public Result<SalesOrderVO> platformReceipt(@PathVariable Long id, @Valid @RequestBody PlatformReceiptRequest req) {
        return Result.ok(service.orderPlatformReceipt(id, req));
    }

    @GetMapping("/{id}/claimable-receipts")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders') and @perm.has('sales:pi:claim-receipt')")
    public Result<List<ReceiptRowVO>> claimable(@PathVariable Long id) {
        return Result.ok(service.orderClaimable(id));
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders') and @perm.has('sales:pi:claim-receipt')")
    public Result<SalesOrderVO> claim(@PathVariable Long id, @Valid @RequestBody ClaimReceiptRequest req) {
        return Result.ok(service.claimToOrder(id, req));
    }
}

package com.zhul.erp.modules.finance.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.finance.dto.AdjustCreditRequest;
import com.zhul.erp.modules.finance.dto.CreditAccountVO;
import com.zhul.erp.modules.finance.dto.CreditEntryVO;
import com.zhul.erp.modules.finance.dto.CreditQuery;
import com.zhul.erp.modules.finance.dto.CreditReconVO;
import com.zhul.erp.modules.finance.dto.CreditSummaryVO;
import com.zhul.erp.modules.finance.dto.OverpaidVO;
import com.zhul.erp.modules.finance.dto.ProcessOverpaidRequest;
import com.zhul.erp.modules.finance.dto.RefundVO;
import com.zhul.erp.modules.finance.dto.WithdrawCreditRequest;
import com.zhul.erp.modules.finance.service.CustomerCreditService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 余额管理（spec finance/customer-credit）与收款管理的待处理多收（spec sales/payment-receipt） */
@RestController
@RequestMapping("/api/v1/finance/credits")
@RequiredArgsConstructor
public class CustomerCreditController {

    private static final String VIEW = "@perm.canAccessMenu('/finance/credits')";
    /** 开 PI、订单抵扣与客户详情要看客户的可用余额 */
    private static final String VIEW_CUSTOMER = VIEW + " or @perm.canAccessMenu('/sales/pi') or @perm.canAccessMenu('/sales/orders')"
            + " or @perm.canAccessMenu('/customer/list')";

    private final CustomerCreditService service;

    @GetMapping
    @PreAuthorize(VIEW)
    public Result<PageResult<CreditAccountVO>> page(CreditQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/summary")
    @PreAuthorize(VIEW)
    public Result<CreditSummaryVO> summary() {
        return Result.ok(service.summary());
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize(VIEW_CUSTOMER)
    public Result<List<CreditAccountVO>> ofCustomer(@PathVariable Long customerId) {
        return Result.ok(service.ofCustomer(customerId));
    }

    @GetMapping("/entries")
    @PreAuthorize(VIEW_CUSTOMER)
    public Result<List<CreditEntryVO>> entries(@RequestParam Long customerId, @RequestParam String currencyCode,
                                               @RequestParam(required = false) Integer businessUnitId) {
        return Result.ok(service.visibleEntries(customerId, currencyCode, businessUnitId));
    }

    @GetMapping("/reconciliation")
    @PreAuthorize(VIEW)
    public Result<CreditReconVO> reconciliation(@RequestParam(required = false) String month,
                                                @RequestParam(required = false) String currencyCode,
                                                @RequestParam(required = false) Integer businessUnitId) {
        return Result.ok(service.reconciliation(month, currencyCode, businessUnitId));
    }

    @GetMapping("/reconciliation/export")
    @PreAuthorize(VIEW)
    public void exportReconciliation(@RequestParam(required = false) String month, @RequestParam(required = false) String currencyCode,
                                     @RequestParam(required = false) Integer businessUnitId, HttpServletResponse response)
            throws IOException {
        CreditReconVO meta = service.reconciliation(month, currencyCode, businessUnitId);
        byte[] content = service.exportReconciliation(month, currencyCode, businessUnitId);
        String name = "客户余额核对_" + meta.getMonth() + "_" + meta.getCurrencyCode() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setContentLength(content.length);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getOutputStream().write(content);
    }

    @PostMapping("/adjust")
    @PreAuthorize("@perm.has('finance:credit:adjust')")
    public Result<List<CreditEntryVO>> adjust(@Valid @RequestBody AdjustCreditRequest req) {
        return Result.ok(service.adjust(req));
    }

    @PostMapping("/withdraw")
    @PreAuthorize("@perm.has('finance:refund:edit')")
    public Result<RefundVO> withdraw(@Valid @RequestBody WithdrawCreditRequest req) {
        return Result.ok(service.withdraw(req));
    }

    @GetMapping("/overpaid")
    @PreAuthorize("@perm.canAccessMenu('/finance/receipts')")
    public Result<List<OverpaidVO>> overpaid() {
        return Result.ok(service.overpaid());
    }

    @PostMapping("/overpaid")
    @PreAuthorize("@perm.has('sales:pi:receipt-confirm')")
    public Result<List<OverpaidVO>> processOverpaid(@Valid @RequestBody ProcessOverpaidRequest req) {
        return Result.ok(service.processOverpaid(req));
    }
}

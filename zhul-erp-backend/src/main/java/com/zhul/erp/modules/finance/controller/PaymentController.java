package com.zhul.erp.modules.finance.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.finance.dto.PaymentQuery;
import com.zhul.erp.modules.finance.dto.PaymentStatsVO;
import com.zhul.erp.modules.finance.dto.PaymentVO;
import com.zhul.erp.modules.finance.dto.RefundRequest;
import com.zhul.erp.modules.finance.dto.RegisterPaymentRequest;
import com.zhul.erp.modules.finance.dto.VoidPaymentRequest;
import com.zhul.erp.modules.finance.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 付款记录（spec finance/supplier-payment）：公司对公由财务登记，员工垫付由本人登记，权限细分在服务里校验 */
@RestController
@RequestMapping("/api/v1/finance/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final String MENU = "@perm.canAccessMenu('/finance/payments')";
    private static final String PAYER = "@perm.has('finance:payment:register') or @perm.has('finance:payment:advance')";

    private final PaymentService service;

    @GetMapping
    @PreAuthorize(MENU)
    public Result<PageResult<PaymentVO>> page(PaymentQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/stats")
    @PreAuthorize(MENU)
    public Result<PaymentStatsVO> stats(PaymentQuery q) {
        return Result.ok(service.stats(q));
    }

    @PostMapping
    @PreAuthorize(PAYER)
    public Result<PaymentVO> register(@Valid @RequestBody RegisterPaymentRequest req) {
        return Result.ok(service.register(req));
    }

    @PostMapping("/refunds")
    @PreAuthorize("@perm.has('finance:payment:register')")
    public Result<PaymentVO> refund(@Valid @RequestBody RefundRequest req) {
        return Result.ok(service.refund(req));
    }

    /** 作废：登记人本人或有付款登记权限的人（服务里校验） */
    @PostMapping("/{id}/void")
    @PreAuthorize(PAYER)
    public Result<PaymentVO> voidPayment(@PathVariable Long id, @Valid @RequestBody VoidPaymentRequest req) {
        return Result.ok(service.voidPayment(id, req.getReason()));
    }
}

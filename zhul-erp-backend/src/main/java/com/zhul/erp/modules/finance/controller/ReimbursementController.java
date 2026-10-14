package com.zhul.erp.modules.finance.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.modules.finance.dto.AdvanceItemVO;
import com.zhul.erp.modules.finance.dto.PayReimbursementRequest;
import com.zhul.erp.modules.finance.dto.PaymentVO;
import com.zhul.erp.modules.finance.dto.ProofRequest;
import com.zhul.erp.modules.finance.dto.ReceivedRefundRequest;
import com.zhul.erp.modules.finance.dto.RefundReminderVO;
import com.zhul.erp.modules.finance.dto.ReimbursementQuery;
import com.zhul.erp.modules.finance.dto.ReimbursementVO;
import com.zhul.erp.modules.finance.dto.SubmitReimbursementRequest;
import com.zhul.erp.modules.finance.dto.VoidPaymentRequest;
import com.zhul.erp.modules.finance.service.PaymentService;
import com.zhul.erp.modules.finance.service.ReimbursementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** 报销单（spec finance/reimbursement）：员工只看本人的，有报销付款权限的看全部并登记已付 */
@RestController
@RequestMapping("/api/v1/finance/reimbursements")
@RequiredArgsConstructor
public class ReimbursementController {

    private static final String MENU = "@perm.canAccessMenu('/finance/reimbursements')";
    private static final String PAY = MENU + " and @perm.has('finance:reimburse:pay')";

    private final ReimbursementService service;
    private final PaymentService payments;

    @GetMapping("/pending")
    @PreAuthorize(MENU)
    public Result<List<AdvanceItemVO>> pending() {
        return Result.ok(service.pending());
    }

    @GetMapping("/reminders")
    @PreAuthorize(MENU)
    public Result<List<RefundReminderVO>> reminders() {
        return Result.ok(service.reminders());
    }

    @PostMapping("/proofs")
    @PreAuthorize(MENU)
    public Result<AdvanceItemVO> addProof(@Valid @RequestBody ProofRequest req) {
        return Result.ok(service.addProof(req));
    }

    @PostMapping("/refunds")
    @PreAuthorize(MENU)
    public Result<PaymentVO> refundReceived(@Valid @RequestBody ReceivedRefundRequest req) {
        return Result.ok(payments.refundReceived(req, service.advancedBy(req.getPayableId(), SecurityUtils.getCurrentUsername())));
    }

    @PostMapping
    @PreAuthorize(MENU)
    public Result<ReimbursementVO> submit(@Valid @RequestBody SubmitReimbursementRequest req) {
        return Result.ok(service.submit(req));
    }

    @GetMapping
    @PreAuthorize(MENU)
    public Result<PageResult<ReimbursementVO>> page(ReimbursementQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/pending-total")
    @PreAuthorize(MENU)
    public Result<BigDecimal> pendingTotal() {
        return Result.ok(service.pendingTotal());
    }

    @GetMapping("/{id}")
    @PreAuthorize(MENU)
    public Result<ReimbursementVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize(MENU)
    public Result<ReimbursementVO> withdraw(@PathVariable Long id, @Valid @RequestBody VoidPaymentRequest req) {
        return Result.ok(service.withdraw(id, req.getReason()));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize(PAY)
    public Result<ReimbursementVO> pay(@PathVariable Long id, @Valid @RequestBody PayReimbursementRequest req) {
        return Result.ok(service.pay(id, req));
    }

    @PostMapping("/{id}/unpay")
    @PreAuthorize(PAY)
    public Result<ReimbursementVO> unpay(@PathVariable Long id, @Valid @RequestBody VoidPaymentRequest req) {
        return Result.ok(service.unpay(id, req.getReason()));
    }
}

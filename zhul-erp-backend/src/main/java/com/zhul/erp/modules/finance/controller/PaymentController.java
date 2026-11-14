package com.zhul.erp.modules.finance.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.finance.dto.BatchPaymentRequest;
import com.zhul.erp.modules.finance.dto.PaymentQuery;
import com.zhul.erp.modules.finance.dto.PaymentStatsVO;
import com.zhul.erp.modules.finance.dto.PayeeAccountVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 付款单（spec finance/supplier-payment）：公司对公由财务登记，员工垫付由本人登记，权限细分在服务里校验 */
import java.util.List;

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

    /** 登记付款时可选的对方收款账户（完整账号，只给付款登记或垫付登记权限） */
    @GetMapping("/payee-accounts")
    @PreAuthorize(PAYER)
    public Result<List<PayeeAccountVO>> payeeAccounts(@RequestParam Long payableId) {
        return Result.ok(service.payeeAccounts(payableId));
    }

    @PostMapping
    @PreAuthorize(PAYER)
    public Result<PaymentVO> register(@Valid @RequestBody RegisterPaymentRequest req) {
        return Result.ok(service.register(req));
    }

    /** 批量付款：按收款方 + 币种分组，每组一笔付款单，整批同一事务 */
    @PostMapping("/batch")
    @PreAuthorize("@perm.has('finance:payment:register')")
    public Result<List<PaymentVO>> batch(@Valid @RequestBody BatchPaymentRequest req) {
        return Result.ok(service.batchRegister(req));
    }

    @PostMapping("/refunds")
    @PreAuthorize("@perm.has('finance:payment:register')")
    public Result<PaymentVO> refund(@Valid @RequestBody RefundRequest req) {
        return Result.ok(service.refund(req));
    }

    /** 详情（change finance-cash-docs）：付款管理看全部，采购订单菜单只看核销到自己范围内采购订单的（服务里校验） */
    @GetMapping("/{id}")
    @PreAuthorize(MENU + " or @perm.canAccessMenu('/purchase/orders')")
    public Result<PaymentVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping("/{id}/remark")
    @PreAuthorize(MENU)
    public Result<PaymentVO> remark(@PathVariable Long id,
                                    @Valid @RequestBody com.zhul.erp.modules.system.doclog.DocRemarkRequest req) {
        return Result.ok(service.remark(id, req.getRemark()));
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize(MENU)
    public Result<PaymentVO> attach(@PathVariable Long id,
                                    @Valid @RequestBody com.zhul.erp.modules.system.doclog.DocAttachRequest req) {
        return Result.ok(service.attach(id, req.getIds()));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(MENU)
    public Result<PaymentVO> removeAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        return Result.ok(service.removeAttachment(id, attachmentId));
    }

    /** 作废：登记人本人或有付款登记权限的人（服务里校验） */
    @PostMapping("/{id}/void")
    @PreAuthorize(PAYER)
    public Result<PaymentVO> voidPayment(@PathVariable Long id, @Valid @RequestBody VoidPaymentRequest req) {
        return Result.ok(service.voidPayment(id, req.getReason()));
    }
}

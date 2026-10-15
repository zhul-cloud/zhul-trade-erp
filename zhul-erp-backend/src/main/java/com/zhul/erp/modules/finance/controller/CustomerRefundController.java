package com.zhul.erp.modules.finance.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.finance.dto.RefundQuery;
import com.zhul.erp.modules.finance.dto.RefundVO;
import com.zhul.erp.modules.finance.dto.RefundableVO;
import com.zhul.erp.modules.finance.dto.SaveRefundRequest;
import com.zhul.erp.modules.finance.dto.VoidPaymentRequest;
import com.zhul.erp.modules.finance.service.CustomerRefundService;
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

/** 退款单（spec finance/customer-refund）；订单详情也通过这里登记退款 */
@RestController
@RequestMapping("/api/v1/finance/refunds")
@RequiredArgsConstructor
public class CustomerRefundController {

    private static final String VIEW = "@perm.canAccessMenu('/finance/refunds') or @perm.canAccessMenu('/sales/orders')";
    private static final String EDIT = "@perm.has('finance:refund:edit')";

    private final CustomerRefundService service;

    @GetMapping
    @PreAuthorize("@perm.canAccessMenu('/finance/refunds')")
    public Result<PageResult<RefundVO>> page(RefundQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/total")
    @PreAuthorize("@perm.canAccessMenu('/finance/refunds')")
    public Result<BigDecimal> total(RefundQuery q) {
        return Result.ok(service.total(q));
    }

    @GetMapping("/refundable/{soId}")
    @PreAuthorize(EDIT)
    public Result<RefundableVO> refundable(@PathVariable Long soId) {
        return Result.ok(service.refundable(soId));
    }

    @GetMapping("/order/{soId}")
    @PreAuthorize(VIEW)
    public Result<List<RefundVO>> ofOrder(@PathVariable Long soId) {
        return Result.ok(service.ofOrder(soId));
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public Result<RefundVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @PreAuthorize(EDIT)
    public Result<RefundVO> create(@Valid @RequestBody SaveRefundRequest req) {
        return Result.ok(service.create(req));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(EDIT)
    public Result<RefundVO> voidRefund(@PathVariable Long id, @Valid @RequestBody VoidPaymentRequest req) {
        return Result.ok(service.voidRefund(id, req.getReason()));
    }
}

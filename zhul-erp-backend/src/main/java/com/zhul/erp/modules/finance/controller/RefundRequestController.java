package com.zhul.erp.modules.finance.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.aftersales.dto.ReasonBody;
import com.zhul.erp.modules.finance.dto.RefundRequestQuery;
import com.zhul.erp.modules.finance.dto.RefundRequestVO;
import com.zhul.erp.modules.finance.dto.RequestRefundableVO;
import com.zhul.erp.modules.finance.dto.SaveRefundRequest;
import com.zhul.erp.modules.finance.dto.SaveRefundRequestApply;
import com.zhul.erp.modules.finance.service.RefundRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 退款申请（spec finance/refund-request）：业务员提出，审批（可选），财务执行或驳回 */
@RestController
@RequestMapping("/api/v1/finance/refund-requests")
@RequiredArgsConstructor
public class RefundRequestController {

    private static final String APPLY = "@perm.canAccessMenu('/sales/after-sales') or @perm.canAccessMenu('/sales/orders')";
    private static final String MENU = "@perm.canAccessMenu('/finance/refunds')";
    private static final String APPROVE = "@perm.has('finance:refund-request:approve')";
    private static final String EDIT = "@perm.has('finance:refund:edit')";

    private final RefundRequestService service;

    @GetMapping("/refundable/{soId}")
    @PreAuthorize(APPLY)
    public Result<RequestRefundableVO> refundable(@PathVariable Long soId) {
        return Result.ok(service.refundable(soId));
    }

    @GetMapping("/order/{soId}")
    @PreAuthorize(APPLY)
    public Result<List<RefundRequestVO>> ofOrder(@PathVariable Long soId) {
        return Result.ok(service.ofOrder(soId));
    }

    @PostMapping
    @PreAuthorize(APPLY)
    public Result<RefundRequestVO> apply(@Valid @RequestBody SaveRefundRequestApply req) {
        return Result.ok(service.apply(req));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize(APPLY)
    public Result<RefundRequestVO> withdraw(@PathVariable Long id) {
        return Result.ok(service.withdraw(id));
    }

    @PostMapping("/page")
    @PreAuthorize(MENU + " or " + APPROVE)
    public Result<PageResult<RefundRequestVO>> page(@RequestBody RefundRequestQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/counts")
    @PreAuthorize(MENU + " or " + APPROVE)
    public Result<Map<String, Long>> counts() {
        return Result.ok(service.counts());
    }

    @GetMapping("/{id}")
    @PreAuthorize(MENU + " or " + APPLY)
    public Result<RefundRequestVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(APPROVE)
    public Result<RefundRequestVO> approve(@PathVariable Long id) {
        return Result.ok(service.approve(id));
    }

    @PostMapping("/{id}/reject-approval")
    @PreAuthorize(APPROVE)
    public Result<RefundRequestVO> rejectApproval(@PathVariable Long id, @Valid @RequestBody ReasonBody req) {
        return Result.ok(service.reject(id, req.getReason(), true));
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize(EDIT)
    public Result<RefundRequestVO> execute(@PathVariable Long id, @RequestBody SaveRefundRequest req) {
        return Result.ok(service.execute(id, req));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(EDIT)
    public Result<RefundRequestVO> reject(@PathVariable Long id, @Valid @RequestBody ReasonBody req) {
        return Result.ok(service.reject(id, req.getReason(), false));
    }
}

package com.zhul.erp.modules.finance.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.finance.dto.PayableQuery;
import com.zhul.erp.modules.finance.dto.PayableStatsVO;
import com.zhul.erp.modules.finance.dto.PayableVO;
import com.zhul.erp.modules.finance.dto.PayeeOptionVO;
import com.zhul.erp.modules.finance.service.PayableQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 应付单（spec finance/accounts-payable） */
@RestController
@RequestMapping("/api/v1/finance/payables")
@RequiredArgsConstructor
public class PayableController {

    private static final String MENU = "@perm.canAccessMenu('/finance/payables')";
    /** 能登记付款的人（财务的付款登记、采购员的垫付登记）都要能选收款方和应付单 */
    private static final String PAYER = "@perm.has('finance:payment:register') or @perm.has('finance:payment:advance')";
    /** 采购订单、运费账单的付款面板：有应付单，或有对应来源的菜单 */
    private static final String SOURCE = MENU + " or (#sourceType == 1 and @perm.canAccessMenu('/purchase/orders'))"
            + " or (#sourceType == 2 and @perm.canAccessMenu('/logistics/statements'))";

    private final PayableQueryService service;

    @GetMapping
    @PreAuthorize(MENU)
    public Result<PageResult<PayableVO>> page(PayableQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/stats")
    @PreAuthorize(MENU)
    public Result<PayableStatsVO> stats() {
        return Result.ok(service.stats());
    }

    @GetMapping("/by-source")
    @PreAuthorize(SOURCE)
    public Result<PayableVO> bySource(@RequestParam("sourceType") int sourceType, @RequestParam Long sourceId) {
        return Result.ok(service.bySource(sourceType, sourceId));
    }

    @GetMapping("/payees")
    @PreAuthorize(PAYER)
    public Result<List<PayeeOptionVO>> payees(@RequestParam(required = false) String keyword) {
        return Result.ok(service.payees(keyword));
    }

    /** 与这张应付单同一收款方、同一币种，有未付或欠款的应付单（登记付款时选择） */
    @GetMapping("/{id}/open")
    @PreAuthorize(PAYER)
    public Result<List<PayableVO>> open(@PathVariable Long id) {
        return Result.ok(service.openOf(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize(MENU + " or @perm.canAccessMenu('/finance/payments')")
    public Result<PayableVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }
}

package com.zhul.erp.modules.finance.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.finance.dto.OtherIncomeQuery;
import com.zhul.erp.modules.finance.dto.OtherIncomeVO;
import com.zhul.erp.modules.finance.dto.SaveOtherIncomeRequest;
import com.zhul.erp.modules.finance.dto.VoidPaymentRequest;
import com.zhul.erp.modules.finance.service.OtherIncomeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** 其他收入（spec finance/other-income） */
@RestController
@RequestMapping("/api/v1/finance/other-incomes")
@RequiredArgsConstructor
public class OtherIncomeController {

    private static final String MENU = "@perm.canAccessMenu('/finance/other-incomes')";
    private static final String EDIT = MENU + " and @perm.has('finance:income:edit')";

    private final OtherIncomeService service;

    @GetMapping
    @PreAuthorize(MENU)
    public Result<PageResult<OtherIncomeVO>> page(OtherIncomeQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/total")
    @PreAuthorize(MENU)
    public Result<BigDecimal> total(OtherIncomeQuery q) {
        return Result.ok(service.total(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize(MENU)
    public Result<OtherIncomeVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @PreAuthorize(EDIT)
    public Result<OtherIncomeVO> create(@Valid @RequestBody SaveOtherIncomeRequest req) {
        return Result.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(EDIT)
    public Result<OtherIncomeVO> update(@PathVariable Long id, @Valid @RequestBody SaveOtherIncomeRequest req) {
        return Result.ok(service.update(id, req));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(EDIT)
    public Result<OtherIncomeVO> voidIncome(@PathVariable Long id, @Valid @RequestBody VoidPaymentRequest req) {
        return Result.ok(service.voidIncome(id, req.getReason()));
    }
}

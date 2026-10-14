package com.zhul.erp.modules.finance.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.finance.dto.ExpenseQuery;
import com.zhul.erp.modules.finance.dto.ExpenseVO;
import com.zhul.erp.modules.finance.dto.SaveExpenseRequest;
import com.zhul.erp.modules.finance.dto.SoItemOptionVO;
import com.zhul.erp.modules.finance.dto.VoidPaymentRequest;
import com.zhul.erp.modules.finance.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** 费用单（spec finance/expense） */
@RestController
@RequestMapping("/api/v1/finance/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private static final String MENU = "@perm.canAccessMenu('/finance/expenses')";
    private static final String EDIT = MENU + " and @perm.has('finance:expense:edit')";

    private final ExpenseService service;

    @GetMapping
    @PreAuthorize(MENU)
    public Result<PageResult<ExpenseVO>> page(ExpenseQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/month-total")
    @PreAuthorize(MENU)
    public Result<BigDecimal> monthTotal(ExpenseQuery q) {
        return Result.ok(service.monthTotal(q));
    }

    @GetMapping("/so-items")
    @PreAuthorize(EDIT)
    public Result<List<SoItemOptionVO>> soItems(@RequestParam(required = false) String keyword) {
        return Result.ok(service.soItems(keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize(MENU)
    public Result<ExpenseVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @PreAuthorize(EDIT)
    public Result<ExpenseVO> create(@Valid @RequestBody SaveExpenseRequest req) {
        return Result.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(EDIT)
    public Result<ExpenseVO> update(@PathVariable Long id, @Valid @RequestBody SaveExpenseRequest req) {
        return Result.ok(service.update(id, req));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(EDIT)
    public Result<ExpenseVO> voidExpense(@PathVariable Long id, @Valid @RequestBody VoidPaymentRequest req) {
        return Result.ok(service.voidExpense(id, req.getReason()));
    }
}

package com.zhul.erp.modules.aftersales.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.aftersales.dto.AfterSalesCloseRequest;
import com.zhul.erp.modules.aftersales.dto.AfterSalesNoteRequest;
import com.zhul.erp.modules.aftersales.dto.AfterSalesPlanRequest;
import com.zhul.erp.modules.aftersales.dto.AfterSalesQuery;
import com.zhul.erp.modules.aftersales.dto.AfterSalesVO;
import com.zhul.erp.modules.aftersales.dto.ReasonBody;
import com.zhul.erp.modules.aftersales.dto.ReturnVO;
import com.zhul.erp.modules.aftersales.dto.SaveAfterSalesRequest;
import com.zhul.erp.modules.aftersales.service.AfterSalesService;
import com.zhul.erp.modules.aftersales.service.SalesReturnService;
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

/** 售后单（spec sales/after-sales）；订单详情的「售后」也从这里登记 */
@RestController
@RequestMapping("/api/v1/sales/after-sales")
@RequiredArgsConstructor
public class AfterSalesController {

    private static final String MENU = "@perm.canAccessMenu('/sales/after-sales')";
    private static final String MENU_OR_ORDER = "@perm.canAccessMenu('/sales/after-sales') or @perm.canAccessMenu('/sales/orders')";

    private final AfterSalesService service;
    private final SalesReturnService returns;

    @PostMapping("/page")
    @PreAuthorize(MENU)
    public Result<PageResult<AfterSalesVO>> page(@RequestBody AfterSalesQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/counts")
    @PreAuthorize(MENU)
    public Result<Map<String, Long>> counts() {
        return Result.ok(service.counts());
    }

    @GetMapping("/owners")
    @PreAuthorize(MENU_OR_ORDER)
    public Result<List<Map<String, Object>>> owners() {
        return Result.ok(service.owners());
    }

    @GetMapping("/order/{soId}")
    @PreAuthorize(MENU_OR_ORDER + " or @perm.canAccessMenu('/finance/expenses') or @perm.canAccessMenu('/finance/refunds')")
    public Result<List<AfterSalesVO>> ofOrder(@PathVariable Long soId) {
        return Result.ok(service.optionsOfOrder(soId));
    }

    @GetMapping("/{id}")
    @PreAuthorize(MENU_OR_ORDER)
    public Result<AfterSalesVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @GetMapping("/{id}/returns")
    @PreAuthorize(MENU_OR_ORDER)
    public Result<List<ReturnVO>> returnsOf(@PathVariable Long id) {
        service.detail(id);
        return Result.ok(returns.ofAfterSales(id));
    }

    @PostMapping
    @PreAuthorize(MENU_OR_ORDER)
    public Result<AfterSalesVO> create(@Valid @RequestBody SaveAfterSalesRequest req) {
        return Result.ok(service.create(req));
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize(MENU)
    public Result<AfterSalesVO> addNote(@PathVariable Long id, @Valid @RequestBody AfterSalesNoteRequest req) {
        return Result.ok(service.addNote(id, req));
    }

    @PostMapping("/{id}/plan")
    @PreAuthorize(MENU)
    public Result<AfterSalesVO> plan(@PathVariable Long id, @Valid @RequestBody AfterSalesPlanRequest req) {
        return Result.ok(service.updatePlan(id, req));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize(MENU)
    public Result<AfterSalesVO> status(@PathVariable Long id, @RequestBody ReasonBody req) {
        return Result.ok(service.setStatus(id, req.getStatus() == null ? 0 : req.getStatus()));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(MENU)
    public Result<AfterSalesVO> close(@PathVariable Long id, @Valid @RequestBody AfterSalesCloseRequest req) {
        return Result.ok(service.close(id, req));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(MENU)
    public Result<AfterSalesVO> cancel(@PathVariable Long id, @Valid @RequestBody ReasonBody req) {
        return Result.ok(service.cancel(id, req.getReason()));
    }
}

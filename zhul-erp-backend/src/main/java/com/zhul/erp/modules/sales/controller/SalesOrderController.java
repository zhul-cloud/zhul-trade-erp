package com.zhul.erp.modules.sales.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.sales.dto.CancelOrderRequest;
import com.zhul.erp.modules.sales.dto.ChainVO;
import com.zhul.erp.modules.sales.dto.ConvertOrderRequest;
import com.zhul.erp.modules.sales.dto.CreateOrderRequest;
import com.zhul.erp.modules.sales.dto.OrderItemsRequest;
import com.zhul.erp.modules.sales.dto.SalesDateRequest;
import com.zhul.erp.modules.sales.dto.SalesOrderListVO;
import com.zhul.erp.modules.sales.dto.SalesOrderPageQuery;
import com.zhul.erp.modules.sales.dto.SalesOrderVO;
import com.zhul.erp.modules.sales.service.SalesOrderService;
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

/** 销售订单：转订单在 PI 菜单下操作；列表、详情与取消需要「销售订单」菜单；来源 / 去向链路按起点单据的菜单放行 */
@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderService service;

    @PostMapping("/pis/{piId}/convert")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<SalesOrderVO> convert(@PathVariable Long piId, @RequestBody(required = false) ConvertOrderRequest req) {
        return Result.ok(service.convert(piId, req));
    }

    @PostMapping("/orders")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders') and @perm.has('sales:order:create')")
    public Result<SalesOrderVO> create(@Valid @RequestBody CreateOrderRequest req) {
        return Result.ok(service.create(req));
    }

    @PostMapping("/orders/{id}/sales-date")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders')")
    public Result<SalesOrderVO> salesDate(@PathVariable Long id, @Valid @RequestBody SalesDateRequest req) {
        return Result.ok(service.updateSalesDate(id, req.getSalesDate()));
    }

    @PostMapping("/orders/{id}/progress")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders') and @perm.has('sales:order:progress')")
    public Result<SalesOrderVO> progress(@PathVariable Long id, @Valid @RequestBody OrderItemsRequest req) {
        return Result.ok(service.updateProgress(id, req));
    }

    @PostMapping("/orders/{id}/purchaser")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders')")
    public Result<SalesOrderVO> purchaser(@PathVariable Long id, @Valid @RequestBody OrderItemsRequest req) {
        return Result.ok(service.updatePurchaser(id, req));
    }

    @PostMapping("/orders/{id}/stock-type")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders')")
    public Result<SalesOrderVO> stockType(@PathVariable Long id, @Valid @RequestBody OrderItemsRequest req) {
        return Result.ok(service.updateStockType(id, req));
    }

    @PostMapping("/orders/{id}/complete")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders')")
    public Result<SalesOrderVO> complete(@PathVariable Long id) {
        return Result.ok(service.complete(id));
    }

    @PostMapping("/orders/page")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders')")
    public Result<PageResult<SalesOrderListVO>> page(@RequestBody SalesOrderPageQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/orders/stats")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders')")
    public Result<com.zhul.erp.modules.sales.dto.SalesOrderStatsVO> stats() {
        return Result.ok(service.stats());
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders') or @perm.canAccessMenu('/sales/pi')")
    public Result<SalesOrderVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders')")
    public Result<SalesOrderVO> cancel(@PathVariable Long id, @Valid @RequestBody CancelOrderRequest req) {
        return Result.ok(service.cancel(id, req.getReason()));
    }

    @GetMapping("/chain")
    @PreAuthorize("@perm.canAccessMenu('/inquiry/customer-inquiries') or @perm.canAccessMenu('/quotation/quotations')"
            + " or @perm.canAccessMenu('/sales/pi') or @perm.canAccessMenu('/sales/orders')")
    public Result<ChainVO> chain(@RequestParam String type, @RequestParam Long id) {
        return Result.ok(service.chain(type, id));
    }
}

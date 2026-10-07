package com.zhul.erp.modules.sales.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.sales.dto.CancelOrderRequest;
import com.zhul.erp.modules.sales.dto.ChainVO;
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
    public Result<SalesOrderVO> convert(@PathVariable Long piId) {
        return Result.ok(service.convert(piId));
    }

    @PostMapping("/orders/page")
    @PreAuthorize("@perm.canAccessMenu('/sales/orders')")
    public Result<PageResult<SalesOrderListVO>> page(@RequestBody SalesOrderPageQuery query) {
        return Result.ok(service.page(query));
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
